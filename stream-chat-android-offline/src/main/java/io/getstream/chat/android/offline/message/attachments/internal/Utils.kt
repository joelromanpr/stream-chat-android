package io.getstream.chat.android.offline.message.attachments.internal

import java.util.UUID

internal fun generateUploadId(): String {
    return "upload_id_${UUID.randomUUID()}"
}
