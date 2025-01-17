package io.getstream.chat.android.compose.ui.components.channels

import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.getstream.sdk.chat.viewmodel.messages.getCreatedAtOrThrow
import io.getstream.chat.android.client.models.Channel
import io.getstream.chat.android.client.models.Message
import io.getstream.chat.android.client.models.User
import io.getstream.chat.android.client.utils.SyncStatus
import io.getstream.chat.android.compose.R
import io.getstream.chat.android.compose.previewdata.PreviewMessageData
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import io.getstream.chat.android.compose.ui.util.getReadStatuses

/**
 * Shows a delivery status indicator for a particular message.
 *
 * @param channel The channel with channel reads to check.
 * @param message The message with sync status to check.
 * @param currentUser The currently logged in user.
 * @param modifier Modifier for styling.
 */
@Composable
public fun MessageReadStatusIcon(
    channel: Channel,
    message: Message,
    currentUser: User?,
    modifier: Modifier = Modifier,
) {
    val readStatues = channel.getReadStatuses(userToIgnore = currentUser)
    val readCount = readStatues.count { it.time >= message.getCreatedAtOrThrow().time }
    val isMessageRead = readCount != 0

    MessageReadStatusIcon(
        message = message,
        isMessageRead = isMessageRead,
        modifier = modifier,
    )
}

/**
 * Shows a delivery status indicator for a particular message.
 *
 * @param message The message with sync status to check.
 * @param isMessageRead If the message is read by any member.
 * @param modifier Modifier for styling.
 */
@Composable
public fun MessageReadStatusIcon(
    message: Message,
    isMessageRead: Boolean,
    modifier: Modifier = Modifier,
) {
    val syncStatus = message.syncStatus

    when {
        isMessageRead -> {
            Icon(
                modifier = modifier,
                painter = painterResource(id = R.drawable.stream_compose_message_seen),
                contentDescription = null,
                tint = ChatTheme.colors.primaryAccent,
            )
        }
        syncStatus == SyncStatus.SYNC_NEEDED || syncStatus == SyncStatus.AWAITING_ATTACHMENTS -> {
            Icon(
                modifier = modifier,
                painter = painterResource(id = R.drawable.stream_compose_ic_clock),
                contentDescription = null,
                tint = ChatTheme.colors.textLowEmphasis,
            )
        }
        syncStatus == SyncStatus.COMPLETED -> {
            Icon(
                modifier = modifier,
                painter = painterResource(id = R.drawable.stream_compose_message_sent),
                contentDescription = null,
                tint = ChatTheme.colors.textLowEmphasis,
            )
        }
    }
}

/**
 * Preview of [MessageReadStatusIcon] for a seen message.
 *
 * Should show a double tick indicator.
 */
@Preview(showBackground = true, name = "MessageReadStatusIcon Preview (Seen message)")
@Composable
private fun SeenMessageReadStatusIcon() {
    ChatTheme {
        MessageReadStatusIcon(
            message = PreviewMessageData.message2,
            isMessageRead = true,
        )
    }
}
