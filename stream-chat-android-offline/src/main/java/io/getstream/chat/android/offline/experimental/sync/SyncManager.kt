package io.getstream.chat.android.offline.experimental.sync

import androidx.annotation.VisibleForTesting
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.api.models.QueryChannelsRequest
import io.getstream.chat.android.client.call.await
import io.getstream.chat.android.client.extensions.cidToTypeAndId
import io.getstream.chat.android.client.extensions.enrichWithCid
import io.getstream.chat.android.client.extensions.isPermanent
import io.getstream.chat.android.client.logger.ChatLogger
import io.getstream.chat.android.client.models.Attachment
import io.getstream.chat.android.client.models.Channel
import io.getstream.chat.android.client.models.Filters
import io.getstream.chat.android.client.models.Message
import io.getstream.chat.android.client.models.Reaction
import io.getstream.chat.android.client.models.User
import io.getstream.chat.android.client.models.UserEntity
import io.getstream.chat.android.client.utils.SyncStatus
import io.getstream.chat.android.client.utils.onSuccessSuspend
import io.getstream.chat.android.offline.experimental.channel.logic.ChannelLogic
import io.getstream.chat.android.offline.experimental.global.GlobalMutableState
import io.getstream.chat.android.offline.experimental.plugin.logic.LogicRegistry
import io.getstream.chat.android.offline.experimental.plugin.state.StateRegistry
import io.getstream.chat.android.offline.extensions.users
import io.getstream.chat.android.offline.message.users
import io.getstream.chat.android.offline.model.ChannelConfig
import io.getstream.chat.android.offline.model.ConnectionState
import io.getstream.chat.android.offline.model.SyncState
import io.getstream.chat.android.offline.repository.RepositoryFacade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

private const val MESSAGE_LIMIT = 30
private const val MEMBER_LIMIT = 30
private const val INITIAL_CHANNEL_OFFSET = 0
private const val CHANNEL_LIMIT = 30
private const val QUERIES_TO_RETRY = 3

/**
 * This class is responsible to sync messages, reactions and channel data. It tries to sync then, if necessary, when connection
 * is reestabilished or when a health check even happens.
 */
internal class SyncManager(
    private val chatClient: ChatClient,
    private val globalState: GlobalMutableState,
    private val repos: RepositoryFacade,
    private val logicRegistry: LogicRegistry,
    private val stateRegistry: StateRegistry,
    private val userPresence: Boolean
) {

    private val entitiesRetryMutex = Mutex()
    private var logger = ChatLogger.get("SyncManager")
    internal val syncStateFlow: MutableStateFlow<SyncState?> = MutableStateFlow(null)
    private var firstConnect = true

    internal suspend fun connectionRecovered() {
        if (firstConnect) {
            firstConnect = false
            connectionRecovered(false)
        } else {
            // the second time (ie coming from background, or reconnecting we should recover all)
            connectionRecovered(true)
        }
    }

    internal fun clearState() {
        globalState.run {
            _totalUnreadCount.value = 0
            _channelUnreadCount.value = 0
            _initialized.value = false
            _connectionState.value = ConnectionState.OFFLINE
            _banned.value = false
            _mutedUsers.value = emptyList()
        }
    }

    internal suspend fun storeSyncState() {
        syncStateFlow.value?.let { syncState ->
            val newSyncState = syncState.copy(activeChannelIds = logicRegistry.getActiveChannelsLogic().map { it.cid })
            repos.insertSyncState(newSyncState)
            syncStateFlow.value = newSyncState
        }
    }

    internal suspend fun updateAllReadStateForDate(userId: String, currentDate: Date) {
        val selectedState = repos.selectSyncState(userId)

        selectedState?.let { state ->
            if (state.markedAllReadAt == null || state.markedAllReadAt.before(currentDate)) {
                repos.insertSyncState(state.copy(markedAllReadAt = currentDate))
            }
        }

        syncStateFlow.value = selectedState ?: SyncState(userId)
    }

    internal suspend fun loadSyncStateForUser(userId: String) {
        syncStateFlow.value = repos.selectSyncState(userId) ?: SyncState(userId)
    }

    internal suspend fun retryFailedEntities() {
        entitiesRetryMutex.withLock {
            // retry channels, messages and reactions in that order..
            val channels = retryChannels()
            val messages = retryMessages()
            val reactions = retryReactions()
            logger.logI("Retried ${channels.size} channel entities, ${messages.size} messages and ${reactions.size} reaction entities")
        }
    }

    private suspend fun connectionRecovered(recoverAll: Boolean = false) {
        // 0. ensure load is complete

        val online = globalState.isOnline()

        // 1. Retry any failed requests first (synchronous)
        if (online) {
            retryFailedEntities()
        }

        var queriesToRetry = 0

        // 2. update the results for queries that are actively being shown right now (synchronous)
        val updatedChannelIds = mutableSetOf<String>()

        logicRegistry.getActiveQueryChannelsLogic()
            .filter { queryChannelsLogic -> queryChannelsLogic.state().recoveryNeeded.value || recoverAll }
            .take(QUERIES_TO_RETRY)
            .also { queryChannelStateList ->
                queriesToRetry = queryChannelStateList.size
            }
            .forEach { queryLogic ->
                // val
                val request = QueryChannelsRequest(
                    filter = queryLogic.state().filter,
                    offset = INITIAL_CHANNEL_OFFSET,
                    limit = CHANNEL_LIMIT,
                    querySort = queryLogic.state().sort,
                    messageLimit = MESSAGE_LIMIT,
                    memberLimit = MEMBER_LIMIT,
                )

                queryLogic.runQueryOnline(request)
                    .onSuccessSuspend { channels ->
                        queryLogic.updateOnlineChannels(channels, true)
                        updatedChannelIds.addAll(channels.map { it.cid })
                    }
            }

        // 3. update the data for all channels that are being show right now...
        // exclude ones we just updated
        // (synchronous)

        val cids: List<String> = stateRegistry.getActiveChannelStates()
            .asSequence()
            .filter { (it.recoveryNeeded || recoverAll) && !updatedChannelIds.contains(it.cid) }
            .take(30)
            .map { it.cid }
            .toList()

        logger.logI("recovery called: recoverAll: $recoverAll, online: $online retrying $queriesToRetry queries and ${cids.size} channels")

        var missingChannelIds = listOf<String>()

        if (cids.isNotEmpty() && online) {
            val filter = Filters.`in`("cid", cids)
            val request = QueryChannelsRequest(filter, 0, 30)
            chatClient.queryChannelsInternal(request)
                .await()
                .onSuccessSuspend { channels ->
                    val foundChannelIds = channels.map { it.id }

                    channels.forEach { channel ->
                        val channelLogic = logicRegistry.channel(channel.type, channel.id)
                        addTypingChannel(channelLogic)
                        channelLogic.updateDataFromChannel(channel)
                    }

                    missingChannelIds = cids.filterNot { foundChannelIds.contains(it) }
                    storeStateForChannels(channels)
                }

            // create channels that are not present on the API
            missingChannelIds.map { cid ->
                val (type, id) = cid.cidToTypeAndId()
                logicRegistry.channel(type, id)
            }.forEach { channelLogic ->
                channelLogic.watch(userPresence = userPresence)
            }
        }
    }

    private suspend fun retryChannels(): List<Channel> {
        return repos.selectChannelsSyncNeeded().onEach { channel ->
            val result = chatClient.createChannel(
                channel.type,
                channel.id,
                channel.members.map(UserEntity::getUserId),
                channel.extraData
            ).await()

            when {
                result.isSuccess -> {
                    channel.syncStatus = SyncStatus.COMPLETED
                    repos.insertChannel(channel)
                }
                result.isError && result.error().isPermanent() -> {
                    channel.syncStatus = SyncStatus.FAILED_PERMANENTLY
                    repos.insertChannel(channel)
                }
            }
        }
    }

    @VisibleForTesting
    private suspend fun retryMessages(): List<Message> {
        return retryMessagesWithSyncedAttachments() + retryMessagesWithPendingAttachments()
    }

    private suspend fun retryReactions(): List<Reaction> {
        return repos.selectReactionsBySyncStatus(SyncStatus.SYNC_NEEDED).onEach { reaction ->
            val result = if (reaction.deletedAt != null) {
                chatClient.deleteReaction(reaction.messageId, reaction.type)
            } else {
                chatClient.sendReaction(reaction, reaction.enforceUnique)
            }.await()

            if (result.isSuccess) {
                reaction.syncStatus = SyncStatus.COMPLETED
            } else if (result.error().isPermanent()) {
                reaction.syncStatus = SyncStatus.FAILED_PERMANENTLY
            }

            repos.insertReaction(reaction)
        }
    }

    private suspend fun retryMessagesWithSyncedAttachments(): List<Message> {
        val (messages, nonCorrectStateMessages) = repos.selectMessageBySyncState(SyncStatus.SYNC_NEEDED).partition {
            it.attachments.all { attachment -> attachment.uploadState === Attachment.UploadState.Success }
        }

        if (nonCorrectStateMessages.isNotEmpty()) {
            val message = nonCorrectStateMessages.first()
            val attachmentUploadState =
                message.attachments.firstOrNull { it.uploadState != Attachment.UploadState.Success }
                    ?: Attachment.UploadState.Success
            logger.logE(
                "Logical error. Messages with non-synchronized attachments should have another sync status!" +
                    "\nMessage has ${message.syncStatus} syncStatus, while attachment has $attachmentUploadState upload state"
            )
        }

        messages.forEach { message ->
            val channelClient = chatClient.channel(message.cid)

            when {
                message.deletedAt != null -> {
                    logger.logD("Deleting message: ${message.id}")
                    channelClient.deleteMessage(message.id).await()
                }
                message.updatedLocallyAt != null -> {
                    logger.logD("Updating message: ${message.id}")
                    channelClient.updateMessage(message).await()
                }
                else -> {
                    logger.logD("Sending message: ${message.id}")
                    val result = channelClient.sendMessage(message).await()

                    if (result.isSuccess) {
                        repos.insertMessage(message.copy(syncStatus = SyncStatus.COMPLETED))
                    } else if (result.isError && result.error().isPermanent()) {
                        markMessageAsFailed(message)
                    }
                }
            }
        }

        return messages
    }

    /**
     * Retries messages with [SyncStatus.AWAITING_ATTACHMENTS] status.
     */
    private suspend fun retryMessagesWithPendingAttachments(): List<Message> {
        val retriedMessages = repos.selectMessageBySyncState(SyncStatus.AWAITING_ATTACHMENTS)

        val (failedMessages, needToBeSync) = retriedMessages.partition { message ->
            message.attachments.any { it.uploadState is Attachment.UploadState.Failed }
        }

        failedMessages.forEach { markMessageAsFailed(it) }

        needToBeSync.forEach { message ->
            val (channelType, channelId) = message.cid.cidToTypeAndId()
            chatClient.sendMessage(channelType, channelId, message, true).await()
        }

        return retriedMessages
    }

    private suspend fun markMessageAsFailed(message: Message) =
        repos.insertMessage(message.copy(syncStatus = SyncStatus.FAILED_PERMANENTLY, updatedLocallyAt = Date()))

    private suspend fun storeStateForChannels(channelsResponse: Collection<Channel>) {
        val users = mutableMapOf<String, User>()
        val configs: MutableCollection<ChannelConfig> = mutableSetOf()
        // start by gathering all the users
        val messages = mutableListOf<Message>()

        for (channel in channelsResponse) {
            users.putAll(channel.users().associateBy { it.id })
            configs += ChannelConfig(channel.type, channel.config)

            channel.messages.forEach { message ->
                message.enrichWithCid(channel.cid)
                users.putAll(message.users().associateBy { it.id })
            }

            messages.addAll(channel.messages)
        }

        repos.storeStateForChannels(
            configs = configs,
            users = users.values.toList(),
            channels = channelsResponse,
            messages = messages
        )

        logger.logI("storeStateForChannels stored ${channelsResponse.size} channels, ${configs.size} configs, ${users.size} users and ${messages.size} messages")
    }

    private suspend fun addTypingChannel(channelLogic: ChannelLogic) {
        globalState._typingChannels.emitAll(channelLogic.state().typing)
    }
}
