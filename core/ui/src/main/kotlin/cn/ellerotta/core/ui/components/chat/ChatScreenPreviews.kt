package cn.ellerotta.core.ui.components.chat

import androidx.compose.runtime.Composable
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme

private val previewPolls = ChatPollActions(onVote = { _, _ -> }, onShowVotes = {}, onMoreVotes = {}, onCloseVotes = {})

@MioPreviews
@Composable
internal fun ChatScreenPrivatePreview() {
    PreviewTheme {
        val dialog = ChatPreviewData.privateDialog
        ChatScreen(
            title = dialog.title,
            messages = ChatPreviewData.privateChat,
            downloads = emptyMap(),
            busy = false,
            onBack = {},
            onSend = {},
            onDownload = {},
            dialog = dialog,
            stickers = ChatPreviewData.stickers,
            avatars = ChatPreviewData.avatars,
            onAttachMedia = {},
            onAttachFile = {},
            onVoiceStart = {},
            initialText = "See you at one",
            onReact = { _, _, _, _ -> },
            onPlayMusic = {},
            onRetry = {},
        )
    }
}

@MioPreviews
@Composable
internal fun ChatScreenGroupPreview() {
    PreviewTheme {
        val dialog = ChatPreviewData.groupDialog
        ChatScreen(
            title = dialog.title,
            messages = ChatPreviewData.groupChat,
            downloads = emptyMap(),
            busy = false,
            onBack = {},
            onSend = {},
            onDownload = {},
            dialog = dialog,
            stickers = ChatPreviewData.stickers,
            avatars = ChatPreviewData.avatars,
            onAttachMedia = {},
            onAttachFile = {},
            onVoiceStart = {},
            onReact = { _, _, _, _ -> },
            polls = previewPolls,
        )
    }
}

@MioPreviews
@Composable
internal fun ChatScreenChannelPreview() {
    PreviewTheme {
        val dialog = ChatPreviewData.channelDialog
        ChatScreen(
            title = dialog.title,
            messages = ChatPreviewData.channelPosts,
            downloads = emptyMap(),
            busy = false,
            onBack = {},
            onSend = {},
            onDownload = {},
            dialog = dialog,
            stickers = ChatPreviewData.stickers,
            avatars = ChatPreviewData.avatars,
            onAttachMedia = {},
            onAttachFile = {},
            onVoiceStart = {},
            onReact = { _, _, _, _ -> },
            onOpenReplies = {},
            polls = previewPolls,
        )
    }
}
