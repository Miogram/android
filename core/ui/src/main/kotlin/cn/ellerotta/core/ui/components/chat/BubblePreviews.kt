package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatWallpaper
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.PLAIN_WALLPAPER
import cn.ellerotta.core.model.VoicePlayback
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme
import java.time.Instant
import java.time.ZoneId

@Composable
private fun BubbleStage(wallpaper: ChatWallpaper? = null, content: @Composable ColumnScope.() -> Unit) {
    Box {
        ChatBackground(Modifier.matchParentSize(), wallpaper)
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), content = content)
    }
}

@Composable
private fun PreviewBubble(
    message: MessageItem,
    position: BubblePosition = ChatPreviewData.single,
    reply: MessageItem? = null,
    album: List<MessageItem> = emptyList(),
    playback: VoicePlayback? = null,
    avatarSpace: Boolean = false,
) {
    MessageBubble(
        message, position, downloads = emptyMap(), stickers = ChatPreviewData.stickers, onDownload = {},
        reply = reply,
        onLongPress = {},
        playback = playback,
        avatars = ChatPreviewData.avatars,
        onReact = {},
        onOpenReply = {},
        onButton = {},
        onPlayMusic = {},
        onOpenReplies = {},
        onVote = {},
        onShowVotes = {},
        onShowOriginal = {},
        album = album,
        avatarSpace = avatarSpace,
        onReply = {},
        onRetry = {},
    )
}

@MioPreviews
@Composable
internal fun BubbleTextsPreview() {
    PreviewTheme {
        BubbleStage {
            PreviewBubble(ChatPreviewData.textIn, ChatPreviewData.runFirst)
            PreviewBubble(ChatPreviewData.textEdited, ChatPreviewData.runLast)
            PreviewBubble(ChatPreviewData.textOutRead, ChatPreviewData.runFirst)
            PreviewBubble(ChatPreviewData.textOutUnread, ChatPreviewData.runLast)
        }
    }
}

@MioPreviews
@Composable
internal fun BubbleLongTextPreview() {
    PreviewTheme {
        BubbleStage { PreviewBubble(ChatPreviewData.longText) }
    }
}

@MioPreviews
@Composable
internal fun BubblesOnNoWallpaperPreview() {
    PreviewTheme {
        BubbleStage(ChatWallpaper(PLAIN_WALLPAPER)) {
            CenterChip("Today")
            PreviewBubble(ChatPreviewData.textIn, ChatPreviewData.runFirst)
            PreviewBubble(ChatPreviewData.textOutRead, ChatPreviewData.runFirst)
        }
    }
}

@MioPreviews
@Composable
internal fun BubbleJumboEmojiPreview() {
    PreviewTheme {
        BubbleStage {
            PreviewBubble(ChatPreviewData.emojiOne)
            PreviewBubble(ChatPreviewData.emojiTwo)
            PreviewBubble(ChatPreviewData.emojiThree)
        }
    }
}

@MioPreviews
@Composable
internal fun BubblePhotosPreview() {
    PreviewTheme {
        BubbleStage {
            PreviewBubble(ChatPreviewData.photoCaption)
            PreviewBubble(ChatPreviewData.photoAlone)
            PreviewBubble(ChatPreviewData.videoMessage)
            ChatPreviewData.photoAlone.let { photo -> PreviewBubble(photo.copy(id = photo.id + 1000, media = photo.media?.copy(key = "spoiled", spoiler = true))) }
        }
    }
}

@MioPreviews
@Composable
internal fun AlbumBubblePreview() {
    PreviewTheme {
        BubbleStage {
            val album = ChatPreviewData.album
            PreviewBubble(album.first(), album = album)
        }
    }
}

@MioPreviews
@Composable
internal fun StickerBubblesPreview() {
    PreviewTheme {
        BubbleStage {
            PreviewBubble(ChatPreviewData.stillStickerMessage)
            PreviewBubble(ChatPreviewData.outlineStickerMessage)
            PreviewBubble(ChatPreviewData.emojiStickerMessage)
        }
    }
}

@MioPreviews
@Composable
internal fun BubbleVoicePreview() {
    PreviewTheme {
        BubbleStage {
            PreviewBubble(ChatPreviewData.voiceUnread)
            PreviewBubble(ChatPreviewData.voiceOut, playback = ChatPreviewData.playing)
        }
    }
}

@MioPreviews
@Composable
internal fun BubbleRoundVideoPreview() {
    PreviewTheme {
        BubbleStage { PreviewBubble(ChatPreviewData.roundMessage) }
    }
}

@MioPreviews
@Composable
internal fun BubbleMusicAndFilePreview() {
    PreviewTheme {
        BubbleStage {
            PreviewBubble(ChatPreviewData.songMessage)
            PreviewBubble(ChatPreviewData.fileMessage)
        }
    }
}

@MioPreviews
@Composable
internal fun PollBubblesPreview() {
    PreviewTheme {
        BubbleStage {
            PreviewBubble(ChatPreviewData.pollOpen)
            PreviewBubble(ChatPreviewData.pollVoted)
        }
    }
}

@MioPreviews
@Composable
internal fun BubbleQuizPreview() {
    PreviewTheme {
        BubbleStage {
            PreviewBubble(ChatPreviewData.quizOpen)
            PreviewBubble(ChatPreviewData.quizAnswered)
        }
    }
}

@MioPreviews
@Composable
internal fun CallBubblesPreview() {
    PreviewTheme {
        BubbleStage {
            PreviewBubble(ChatPreviewData.callIncoming)
            PreviewBubble(ChatPreviewData.callMissed)
            PreviewBubble(ChatPreviewData.callOutgoing)
            PreviewBubble(ChatPreviewData.callVideo)
            PreviewBubble(ChatPreviewData.callCancelled)
        }
    }
}

@MioPreviews
@Composable
internal fun GiftChipPreview() {
    PreviewTheme {
        BubbleStage {
            GiftChip(ChatPreviewData.giftIn, ChatPreviewData.stickers)
            GiftChip(ChatPreviewData.giftOut, ChatPreviewData.stickers)
        }
    }
}

@MioPreviews
@Composable
internal fun ServiceChipsPreview() {
    PreviewTheme {
        BubbleStage {
            val zone = ZoneId.systemDefault()
            CenterChip(dayText(Instant.ofEpochSecond(ChatPreviewData.NOW.toLong()).atZone(zone).toLocalDate(), zone))
            CenterChip(serviceText(ChatPreviewData.created, PeerKind.Group))
            CenterChip(serviceText(ChatPreviewData.joined, PeerKind.Group))
            CenterChip(serviceText(ChatPreviewData.added, PeerKind.Group))
            CenterChip(serviceText(ChatPreviewData.pinned, PeerKind.Group))
            CenterChip(serviceText(ChatPreviewData.titleChanged, PeerKind.Group))
        }
    }
}

@MioPreviews
@Composable
internal fun BubbleLinkPreviewPreview() {
    PreviewTheme {
        BubbleStage { PreviewBubble(ChatPreviewData.linkMessage) }
    }
}

@MioPreviews
@Composable
internal fun BubbleReplyForwardPreview() {
    PreviewTheme {
        BubbleStage {
            PreviewBubble(ChatPreviewData.textIn)
            PreviewBubble(ChatPreviewData.replyOut, reply = ChatPreviewData.textIn)
            PreviewBubble(ChatPreviewData.forwarded)
            PreviewBubble(ChatPreviewData.viaBot)
        }
    }
}

@MioPreviews
@Composable
internal fun ReactionChipsPreview() {
    PreviewTheme {
        BubbleStage {
            PreviewBubble(ChatPreviewData.reacted)
            PreviewBubble(ChatPreviewData.reactedOwn)
        }
    }
}

@MioPreviews
@Composable
internal fun BubbleSendStatesPreview() {
    PreviewTheme {
        BubbleStage {
            PreviewBubble(ChatPreviewData.pending, ChatPreviewData.runFirst)
            PreviewBubble(ChatPreviewData.failed, ChatPreviewData.runLast)
        }
    }
}

@MioPreviews
@Composable
internal fun InlineKeyboardPreview() {
    PreviewTheme {
        BubbleStage { PreviewBubble(ChatPreviewData.keyboardMessage) }
    }
}

@MioPreviews
@Composable
internal fun ReplyKeyboardPanelPreview() {
    PreviewTheme {
        ReplyKeyboardPanel(ChatPreviewData.replyKeyboard, onButton = {})
    }
}

@MioPreviews
@Composable
internal fun ArticleBubblePreview() {
    PreviewTheme {
        BubbleStage { PreviewBubble(ChatPreviewData.articleMessage) }
    }
}

@MioPreviews
@Composable
internal fun ArticleMediaBubblePreview() {
    PreviewTheme {
        BubbleStage { PreviewBubble(ChatPreviewData.articleMediaMessage) }
    }
}

@MioPreviews
@Composable
internal fun BubbleTranslationPreview() {
    PreviewTheme {
        BubbleStage { PreviewBubble(ChatPreviewData.translated) }
    }
}

@MioPreviews
@Composable
internal fun RepliesBarPreview() {
    PreviewTheme {
        BubbleStage {
            PreviewBubble(ChatPreviewData.withReplies)
            PreviewBubble(ChatPreviewData.post)
        }
    }
}

@MioPreviews
@Composable
internal fun StopDraftBarPreview() {
    PreviewTheme {
        BubbleStage {
            PreviewBubble(ChatPreviewData.streaming)
            StopDraftBar(onStop = {})
        }
    }
}

@MioPreviews
@Composable
internal fun BubbleEphemeralPreview() {
    PreviewTheme {
        BubbleStage {
            PreviewBubble(ChatPreviewData.ephemeralOut)
            PreviewBubble(ChatPreviewData.ephemeralIn)
        }
    }
}

@MioPreviews
@Composable
internal fun GroupRunPreview() {
    PreviewTheme {
        BubbleStage {
            ChatPreviewData.groupRun.forEach { (message, position) ->
                val others = !message.out
                Box {
                    PreviewBubble(message, position, avatarSpace = others)
                    if (others && position.last) {
                        Avatar(
                            message.senderName.orEmpty(), message.senderAvatar?.let(ChatPreviewData.avatars::get), size = AVATAR_SIZE.value.toInt(), seed = message.senderId,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = BUBBLE_EDGE),
                        )
                    }
                }
            }
        }
    }
}
