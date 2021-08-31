package io.getstream.chat.android.client.models

import com.google.gson.annotations.SerializedName
import io.getstream.chat.android.client.parser.IgnoreDeserialisation
import io.getstream.chat.android.client.parser.IgnoreSerialisation
import io.getstream.chat.android.client.utils.SyncStatus
import java.util.Date

public data class Channel(
    var cid: String = "",
    var id: String = "",
    var type: String = "",
    var watcherCount: Int = 0,
    var frozen: Boolean = false,

    @SerializedName("last_message_at")
    var lastMessageAt: Date? = null,
    @SerializedName("created_at")
    var createdAt: Date? = null,
    @SerializedName("deleted_at")
    var deletedAt: Date? = null,
    @SerializedName("updated_at")
    var updatedAt: Date? = null,

    @IgnoreSerialisation
    var syncStatus: SyncStatus = SyncStatus.COMPLETED,

    @SerializedName("member_count")
    val memberCount: Int = 0,
    var messages: List<Message> = mutableListOf(),
    var members: List<Member> = mutableListOf(),
    var watchers: List<User> = mutableListOf(),
    var read: List<ChannelUserRead> = mutableListOf(),
    var config: Config = Config(),
    @SerializedName("created_by")
    var createdBy: User = User(),

    @IgnoreSerialisation
    @IgnoreDeserialisation
    var unreadCount: Int? = null,

    val team: String = "",

    @IgnoreSerialisation
    @IgnoreDeserialisation
    override var extraData: MutableMap<String, Any> = mutableMapOf(),

    @IgnoreSerialisation
    @IgnoreDeserialisation
    var hidden: Boolean? = null,

    @IgnoreSerialisation
    @IgnoreDeserialisation
    var hiddenMessagesBefore: Date? = null,

    /**
     *  Cooldown period after sending each message in seconds
     */
    val cooldown: Int = 0,

    @SerializedName("pinned_messages")
    var pinnedMessages: List<Message> = mutableListOf(),
) : CustomObject {

    var name: String
        get() = getExternalField(this, EXTRA_NAME)
        set(value) {
            extraData[EXTRA_NAME] = value
        }

    var image: String
        get() = getExternalField(this, EXTRA_IMAGE)
        set(value) {
            extraData[EXTRA_IMAGE] = value
        }

    val lastUpdated: Date?
        get() = lastMessageAt?.takeIf { createdAt == null || it.after(createdAt) } ?: createdAt

    val hasUnread: Boolean
        get() = unreadCount?.let { it > 0 } ?: false
}
