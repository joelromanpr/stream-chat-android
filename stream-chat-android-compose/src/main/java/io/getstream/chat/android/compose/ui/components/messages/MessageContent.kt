package io.getstream.chat.android.compose.ui.components.messages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.getstream.chat.android.client.models.Message
import io.getstream.chat.android.compose.R
import io.getstream.chat.android.compose.state.imagepreview.ImagePreviewResult
import io.getstream.chat.android.compose.state.messages.list.GiphyAction
import io.getstream.chat.android.compose.ui.attachments.content.MessageAttachmentsContent
import io.getstream.chat.android.compose.ui.messages.list.DefaultMessageTextContent
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import io.getstream.chat.android.compose.ui.util.isDeleted
import io.getstream.chat.android.compose.ui.util.isGiphyEphemeral

/**
 * Represents the default message content within the bubble that can show different UI based on the message state.
 *
 * @param message The message to show.
 * @param modifier Modifier for styling.
 * @param onLongItemClick Handler when the item is long clicked.
 * @param onGiphyActionClick Handler for Giphy actions.
 * @param onImagePreviewResult Handler when selecting images in the default content.
 * @param giphyEphemeralContent Composable that represents the default Giphy message content.
 * @param deletedMessageContent Composable that represents the default content of a deleted message.
 * @param regularMessageContent Composable that represents the default regular message content, such as attachments and
 * text.
 */
@Composable
public fun MessageContent(
    message: Message,
    modifier: Modifier = Modifier,
    onLongItemClick: (Message) -> Unit = {},
    onGiphyActionClick: (GiphyAction) -> Unit = {},
    onImagePreviewResult: (ImagePreviewResult?) -> Unit = {},
    giphyEphemeralContent: @Composable () -> Unit = {
        DefaultMessageGiphyContent(
            message = message,
            onGiphyActionClick = onGiphyActionClick
        )
    },
    deletedMessageContent: @Composable () -> Unit = {
        DefaultMessageDeletedContent(modifier = modifier)
    },
    regularMessageContent: @Composable () -> Unit = {
        DefaultMessageContent(
            message = message,
            onLongItemClick = onLongItemClick,
            onImagePreviewResult = onImagePreviewResult
        )
    },
) {
    when {
        message.isGiphyEphemeral() -> giphyEphemeralContent()
        message.isDeleted() -> deletedMessageContent()
        else -> regularMessageContent()
    }
}

/**
 * Represents the default ephemeral Giphy message content.
 *
 * @param message The message to show.
 * @param onGiphyActionClick Handler for Giphy actions.
 */
@Composable
internal fun DefaultMessageGiphyContent(
    message: Message,
    onGiphyActionClick: (GiphyAction) -> Unit,
) {
    GiphyMessageContent(
        message = message,
        onGiphyActionClick = onGiphyActionClick
    )
}

/**
 * Represents the default deleted message content.
 *
 * @param modifier Modifier for styling.
 */
@Composable
internal fun DefaultMessageDeletedContent(
    modifier: Modifier,
) {
    Text(
        modifier = modifier
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        text = stringResource(id = R.string.stream_compose_message_deleted),
        color = ChatTheme.colors.textLowEmphasis,
        style = ChatTheme.typography.footnoteItalic
    )
}

/**
 * Represents the default regular message content that can contain attachments and text.
 *
 * @param message The message to show.
 * @param onLongItemClick Handler when the item is long clicked.
 * @param onImagePreviewResult Handler when selecting images in the default content.
 */
@Composable
internal fun DefaultMessageContent(
    message: Message,
    onLongItemClick: (Message) -> Unit,
    onImagePreviewResult: (ImagePreviewResult?) -> Unit,
) {
    Column {
        MessageAttachmentsContent(
            message = message,
            onLongItemClick = onLongItemClick,
            onImagePreviewResult = onImagePreviewResult,
        )

        if (message.text.isNotEmpty()) {
            DefaultMessageTextContent(
                message = message,
                onLongItemClick = onLongItemClick
            )
        }
    }
}
