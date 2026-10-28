package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.background
import cn.ellerotta.core.model.ArticleListItem
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.BotButton
import cn.ellerotta.core.model.EphemeralInfo
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.translated
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.cover
import cn.ellerotta.core.model.isVisual
import cn.ellerotta.core.model.jumboEmojiCount
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.VoicePlayback
import cn.ellerotta.core.ui.R

data class BubblePosition(val first: Boolean, val last: Boolean)

private val big = 20.dp
private val small = 6.dp

private val RUN_GAP = 6.dp
private val RUN_MEMBER_GAP = 2.dp

internal val BUBBLE_PADDING = 12.dp

internal val BUBBLE_EDGE = 12.dp

internal val AVATAR_SIZE = 36.dp

private val AVATAR_INSET = BUBBLE_EDGE + AVATAR_SIZE + 6.dp

fun bubbleShape(out: Boolean, position: BubblePosition): RoundedCornerShape = bubbleCorners(out, position).shape()

internal fun bubbleCorners(out: Boolean, position: BubblePosition): Corners {
    val top = if (position.first) big else small
    val bottom = if (position.last) big else small
    return if (out) Corners(topStart = big, topEnd = top, bottomEnd = bottom, bottomStart = big)
    else Corners(topStart = top, topEnd = big, bottomEnd = big, bottomStart = bottom)
}

@Composable
fun MessageBubble(
    message: MessageItem,
    position: BubblePosition,
    downloads: Map<String, DownloadState>,
    stickers: Map<String, ByteArray>,
    onDownload: (MediaItem) -> Unit,
    reply: MessageItem? = null,
    onLongPress: (() -> Unit)? = null,
    playback: VoicePlayback? = null,
    onToggleVoice: (MediaItem) -> Unit = {},
    onVoiceSpeed: () -> Unit = {},
    onOpenMedia: () -> Unit = {},
    onOpenSticker: (MediaItem) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    onCommand: ((String) -> Unit)? = null,
    onRemind: ((Int, Int) -> Unit)? = null,
    customEmoji: Map<Long, MediaItem> = emptyMap(),
    avatars: Map<String, ByteArray> = emptyMap(),
    onReact: ((Reaction) -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    onOpenReply: ((Int) -> Unit)? = null,
    onHashtag: ((String) -> Unit)? = null,
    onViaBot: ((String) -> Unit)? = null,
    onButton: ((BotButton) -> Unit)? = null,
    onOpenArticleMedia: (Int) -> Unit = {},
    onShowMore: (() -> Unit)? = null,
    loadingMore: Boolean = false,
    onToggleCheck: ((ArticleListItem) -> Unit)? = null,
    music: MusicState? = null,
    onPlayMusic: ((MessageItem) -> Unit)? = null,
    onOpenReplies: (() -> Unit)? = null,
    onVote: ((List<String>) -> Unit)? = null,
    onShowVotes: (() -> Unit)? = null,
    onShowOriginal: (() -> Unit)? = null,
    album: List<MessageItem> = emptyList(),
    onOpenItem: (MessageItem) -> Unit = {},
    avatarSpace: Boolean = false,
    onTap: (() -> Unit)? = null,
    onReply: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onOpenPlace: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val bubble = remember(colors, message.out) { bubbleColors(colors, message.out) }
    val media = message.media
    val local = message.pending || message.streaming
    val haptics = LocalHapticFeedback.current
    val textPress = remember { TextPress() }
    val open by rememberUpdatedState(onOpenLink)
    val actions = remember(onCommand, onRemind, onHashtag) { TextActions({ open(it) }, onCommand, onRemind, onHashtag) }
    val emoji = remember(customEmoji, stickers) { InlineEmoji(customEmoji, stickers) }
    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    var lifting by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) { if (!pressed) lifting = false }
    val scale by animateFloatAsState(if (pressed && !lifting) PRESS_SCALE else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "press")
    val shrink = Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    val retry = onRetry?.takeIf { message.failed }
    val tapped = retry ?: onTap
    val retryLabel = stringResource(R.string.chat_retry)
    val replyLabel = stringResource(R.string.chat_reply)
    val replyAction = if (onReply == null) {
        Modifier
    } else {
        Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(replyLabel) {
                    onReply()
                    true
                },
            )
        }
    }
    val longPress = onLongPress?.let { lift ->
        {
            if (textPress.copy == null) {
                lifting = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                lift()
            }
        }
    }
    val press = if (onLongPress == null && onDoubleTap == null && tapped == null) {
        replyAction
    } else {
        Modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    textPress.copy = null
                }
            }
            .combinedClickable(
                interactionSource = pressSource,
                indication = null,
                onClickLabel = retry?.let { retryLabel },
                onClick = { tapped?.invoke() },
                onDoubleClick = onDoubleTap,
                onLongClick = longPress,
            )
            .then(replyAction)
            .then(shrink)
    }
    val sides = (if (avatarSpace) AVATAR_INSET else BUBBLE_EDGE) + BUBBLE_EDGE
    val jumbo = remember(message.text, message.entities, media, message.article, message.poll, message.link, message.replyTo, message.translation, message.failed) {
        val plain = media == null && message.article == null && message.poll == null && message.link == null && message.replyTo == null && message.translation == null && !message.failed
        if (plain && message.entities.isEmpty()) jumboEmojiCount(message.text) else 0
    }
    val round = remember(message.id) { RoundVideoState() }
    val visual = if (album.isNotEmpty()) album.takeIf { album.first().media?.isVisual == true }.orEmpty() else listOfNotNull(message.takeIf { media?.isVisual == true })
    val side = if (message.out) Alignment.End else Alignment.Start
    val call = message.call
    Box(
        Modifier.fillMaxWidth().padding(top = if (position.first) RUN_GAP else RUN_MEMBER_GAP, start = if (avatarSpace) AVATAR_INSET else BUBBLE_EDGE, end = BUBBLE_EDGE),
        contentAlignment = if (message.out) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(horizontalAlignment = side, modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())) {
            if (!message.out && message.senderName != null && position.first) SenderHeader(message, bubble, emoji, Modifier.padding(bottom = 4.dp))
            when {
                media?.kind == MediaKind.Sticker || media?.kind == MediaKind.Round || jumbo > 0 -> {
                    val tap = Modifier
                        .combinedClickable(
                            interactionSource = pressSource,
                            indication = null,
                            onClick = {
                                when {
                                    onTap != null -> onTap()
                                    local -> Unit
                                    media?.kind == MediaKind.Sticker -> onOpenSticker(media)
                                    media?.kind == MediaKind.Round -> round.toggle()
                                }
                            },
                            onDoubleClick = onDoubleTap,
                            onLongClick = longPress,
                        )
                        .then(replyAction)
                        .then(shrink)
                    KeyboardUnder(message, onButton, emoji, widen = false) {
                        Column(horizontalAlignment = side, modifier = tap.testTag("message_${message.id}")) {
                            if (message.ephemeral != null || message.viaBot != null || message.forward != null) {
                                GlassPill(MaterialTheme.shapes.medium, Modifier.padding(bottom = 4.dp)) {
                                    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                                        message.ephemeral?.let { EphemeralHeader(message, it, Color.White) }
                                        message.viaBot?.let { ViaHeader(it, Color.White, onViaBot, Modifier.testTag("via_${message.id}")) }
                                        message.forward?.let { ForwardHeader(it, Color.White, onOpenLink, Modifier.testTag("forward_${message.id}")) }
                                    }
                                }
                            }
                            Box {
                                when {
                                    media?.kind == MediaKind.Sticker -> StickerView(media, stickers[media.key], size = STICKER_SIZE, preview = stickers.previewOf(media))
                                    media?.kind == MediaKind.Round -> RoundVideo(media, downloads, onDownload, round)
                                    else -> JumboEmoji(message.text.trim(), jumbo, Modifier.testTag("jumbo_${message.id}"))
                                }
                                MetaPill(message, Modifier.align(Alignment.BottomEnd))
                            }
                        }
                    }
                }
                call != null -> CallBubble(message, call, position, press)
                visual.isNotEmpty() -> VisualBubble(message, position, visual, album, bubble, sides, avatarSpace, local, press, pressSource, longPress, reply, downloads, onDownload, onButton, emoji, actions, textPress, avatars, onViaBot, onOpenLink, onOpenReply, onShowOriginal, onOpenMedia, onOpenItem, onOpenReplies)
                else -> KeyboardUnder(message, onButton, emoji, widen = true) {
                    Surface(
                        shape = bubbleShape(message.out, position),
                        color = bubble.container,
                        contentColor = bubble.content,
                        border = failedBorder(message, bubble),
                        modifier = Modifier.bubbleWidth(sides, avatarSpace).clip(bubbleShape(message.out, position)).then(press).testTag("message_${message.id}"),
                    ) { CompositionLocalProvider(LocalBubbleColors provides bubble) {
                        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec()).padding(horizontal = BUBBLE_PADDING, vertical = 8.dp)) {
                            val shown = message.translated()
                            val replies = message.replies?.takeIf { onOpenReplies != null }
                            val textOnly = media == null && message.poll == null && message.place == null && message.contact == null && shown.article == null && message.translation == null &&
                                message.link == null && replies == null && shown.text.isNotEmpty()
                            if (textOnly) {
                                val lastLine = remember(shown.text, shown.entities) { LastLine() }
                                TextWithTime(
                                    lastLine, TIME_GAP,
                                    headers = { Column { BubbleHeaders(message, reply, onViaBot, onOpenLink, onOpenReply, emoji) } },
                                    text = { MessageText(shown, bubble.accent, actions, textPress, emoji, lastLine) },
                                    time = { MessageMeta(message) },
                                )
                            } else {
                                BubbleHeaders(message, reply, onViaBot, onOpenLink, onOpenReply, emoji)
                                when {
                                    album.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("documents_${message.id}")) {
                                        album.forEach { item ->
                                            val file = item.media ?: return@forEach
                                            if (file.kind == MediaKind.Music && onPlayMusic != null) {
                                                MusicBlock(file, file.cover()?.let { stickers[it.key] }, music?.takeIf { it.track.messageId == item.id }, enabled = !local, onPlay = { onPlayMusic(item) })
                                            } else {
                                                MediaBlock(file, downloads, onDownload, enabled = !local)
                                            }
                                            val caption = if (item.id == message.id) shown else item
                                            if (caption.text.isNotEmpty()) MessageText(caption, bubble.accent, actions, textPress, emoji)
                                        }
                                    }
                                    media == null -> Unit
                                    media.kind == MediaKind.Voice -> VoiceBlock(media, playback, enabled = !local, onToggle = onToggleVoice, onSpeed = onVoiceSpeed, unread = message.mediaUnread && !message.out)
                                    media.kind == MediaKind.Music && onPlayMusic != null -> MusicBlock(media, media.cover()?.let { stickers[it.key] }, music, enabled = !local, onPlay = { onPlayMusic(message) })
                                    else -> MediaBlock(media, downloads, onDownload, enabled = !local)
                                }
                                message.poll?.let { poll -> PollBlock(message, poll, bubble.accent, avatars, actions, textPress, emoji, onVote.takeIf { !local }, onShowVotes.takeIf { !local }) }
                                message.place?.let { PlaceBlock(it, message.id, bubble.accent, System.currentTimeMillis() / 1000, enabled = !local, avatars = avatars, onOpen = onOpenPlace) }
                                message.contact?.let { ContactBlock(it, message.id, enabled = !local) }
                                if (shown.text.isNotEmpty() && album.isEmpty()) MessageText(shown, bubble.accent, actions, textPress, emoji)
                                shown.article?.let { article ->
                                    val host = remember(downloads, onDownload, playback, onToggleVoice, onOpenArticleMedia, onButton, local, onShowMore, loadingMore, onToggleCheck, onVoiceSpeed) {
                                        ArticleHost(downloads, onDownload, playback, onToggleVoice, onOpenArticleMedia, onButton, enabled = !local, onShowMore, loadingMore, onToggleCheck, onVoiceSpeed)
                                    }
                                    val accent = if (message.out) bubble.accent else senderColor(message.senderId, bubble.container)
                                    ArticleView(shown, article, bubble.accent, accent, actions, textPress, emoji, host)
                                }
                                message.translation?.let { TranslatedLine(message.id, it.language, bubble.accent, onShowOriginal) }
                                message.link?.let { LinkPreviewBlock(it, if (message.out) bubble.accent else senderColor(message.senderId, bubble.container), downloads, onDownload, onOpen = { onOpenLink(it.url) }) }
                                MessageMeta(message, Modifier.align(Alignment.End))
                                if (replies != null && onOpenReplies != null) RepliesBar(message, replies, avatars, bubble.accent, onOpenReplies)
                            }
                        }
                    } }
                }
            }
            if (message.reactions.isNotEmpty()) {
                CompositionLocalProvider(LocalBubbleColors provides bubble) {
                    ReactionChips(message, emoji, avatars, onReact, longPress, Modifier.bubbleWidth(sides, avatarSpace), end = message.out)
                }
            }
        }
    }
}

@Composable
private fun VisualBubble(
    message: MessageItem,
    position: BubblePosition,
    visual: List<MessageItem>,
    album: List<MessageItem>,
    bubble: BubbleColors,
    sides: Dp,
    avatarSpace: Boolean,
    local: Boolean,
    press: Modifier,
    pressSource: MutableInteractionSource,
    onLongPress: (() -> Unit)?,
    reply: MessageItem?,
    downloads: Map<String, DownloadState>,
    onDownload: (MediaItem) -> Unit,
    onButton: ((BotButton) -> Unit)?,
    emoji: InlineEmoji,
    actions: TextActions,
    textPress: TextPress,
    avatars: Map<String, ByteArray>,
    onViaBot: ((String) -> Unit)?,
    onOpenLink: (String) -> Unit,
    onOpenReply: ((Int) -> Unit)?,
    onShowOriginal: (() -> Unit)?,
    onOpenMedia: () -> Unit,
    onOpenItem: (MessageItem) -> Unit,
    onOpenReplies: (() -> Unit)?,
) {
    BoxWithConstraints {
        val corners = bubbleCorners(message.out, position)
        val widest = min(bubbleMaxWidth(maxWidth + sides, avatarSpace), maxWidth) - MEDIA_INSET * 2
        val grid = remember(visual, widest) { albumMosaic(visual, widest) }
        val meta = visual.last().let { last -> last.copy(edited = visual.any { it.edited }) }
        val shown = message.translated()
        val caption = shown.text.isNotEmpty()
        val headed = hasHeaders(message)
        KeyboardUnder(message, onButton, emoji, widen = false) {
            Surface(
                shape = corners.shape(),
                color = bubble.container,
                contentColor = bubble.content,
                border = failedBorder(message, bubble),
                modifier = Modifier.clip(corners.shape()).then(press).testTag("message_${message.id}"),
            ) { CompositionLocalProvider(LocalBubbleColors provides bubble) {
                Column(Modifier.width(grid.width.dp + MEDIA_INSET * 2).animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())) {
                    if (headed) {
                        Column(Modifier.padding(start = BUBBLE_PADDING, end = BUBBLE_PADDING, top = 8.dp, bottom = 4.dp)) {
                            BubbleHeaders(message, reply, onViaBot, onOpenLink, onOpenReply, emoji)
                        }
                    }
                    Box(Modifier.padding(MEDIA_INSET)) {
                        val photos = if (caption) null else rememberChatBackdrop()
                        AlbumGrid(
                            visual, grid, corners.inner(MEDIA_INSET, above = headed, below = caption), downloads, onDownload,
                            onOpen = { item -> if (!local) { if (album.isEmpty()) onOpenMedia() else onOpenItem(item) } },
                            modifier = photos?.let { Modifier.backdropSource(it) } ?: Modifier,
                            pressSource = pressSource,
                            onLongPress = onLongPress,
                        )
                        if (!caption) MetaPill(meta, Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp), backdrop = photos)
                    }
                    if (caption) {
                        Column(Modifier.fillMaxWidth().padding(start = BUBBLE_PADDING, end = BUBBLE_PADDING, top = 4.dp, bottom = 8.dp)) {
                            if (message.translation == null) {
                                val lastLine = remember(shown.text, shown.entities) { LastLine() }
                                TextWithTime(
                                    lastLine, TIME_GAP,
                                    headers = {},
                                    text = { MessageText(shown, bubble.accent, actions, textPress, emoji, lastLine) },
                                    time = { MessageMeta(meta) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                MessageText(shown, bubble.accent, actions, textPress, emoji)
                                message.translation?.let { TranslatedLine(message.id, it.language, bubble.accent, onShowOriginal) }
                                MessageMeta(meta, Modifier.align(Alignment.End))
                            }
                        }
                    }
                    val replies = message.replies
                    if (replies != null && onOpenReplies != null) {
                        Box(Modifier.padding(horizontal = BUBBLE_PADDING)) { RepliesBar(message, replies, avatars, bubble.accent, onOpenReplies) }
                    }
                }
            } }
        }
    }
}

@Composable
private fun SenderHeader(message: MessageItem, bubble: BubbleColors, emoji: InlineEmoji, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, color = bubble.container, contentColor = bubble.content, modifier = modifier.testTag("sender_${message.id}")) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                message.senderName.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = senderColor(message.senderId, bubble.container),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            message.senderEmojiStatus?.let { id ->
                val document = emoji.documents[id]
                val bytes = document?.let { emoji.files[it.key] }
                if (document != null && bytes != null) StickerView(document, bytes, Modifier.testTag("senderStatus_${message.id}"), size = STATUS_SIZE)
            }
            message.senderRank?.let { rank ->
                Text(
                    rank,
                    style = MaterialTheme.typography.labelMedium,
                    color = bubble.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(bubble.accent.copy(alpha = RANK_TINT).compositeOver(bubble.container), MaterialTheme.shapes.small)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("senderRank_${message.id}"),
                )
            }
        }
    }
}

private val STATUS_SIZE = 18.dp

internal const val RANK_TINT = 0.12f

private val TIME_GAP = 8.dp

private fun failedBorder(message: MessageItem, bubble: BubbleColors): BorderStroke? = if (message.failed) BorderStroke(1.dp, bubble.danger) else null

@Composable
private fun JumboEmoji(text: String, count: Int, modifier: Modifier = Modifier) {
    val type = MaterialTheme.typography
    val size = when (count) {
        1 -> type.displayLarge.fontSize * 1.4f
        2 -> type.displayMedium.fontSize * 1.2f
        else -> type.displaySmall.fontSize
    }
    Text(text, style = type.displayLarge.copy(fontSize = size, lineHeight = size * 1.15f), modifier = modifier.padding(4.dp))
}

private fun hasHeaders(message: MessageItem): Boolean =
    message.ephemeral != null || message.viaBot != null || message.forward != null || message.replyTo != null

@Composable
private fun BubbleHeaders(
    message: MessageItem,
    reply: MessageItem?,
    onViaBot: ((String) -> Unit)?,
    onOpenLink: (String) -> Unit,
    onOpenReply: ((Int) -> Unit)?,
    emoji: InlineEmoji,
) {
    val bubble = bubbleColors()
    message.ephemeral?.let { EphemeralHeader(message, it, bubble.secondary) }
    message.viaBot?.let { via -> ViaHeader(via, if (message.out) bubble.accent else senderColor(message.senderId, bubble.container), onViaBot, Modifier.testTag("via_${message.id}")) }
    message.forward?.let { forward ->
        ForwardHeader(forward, if (message.out) bubble.accent else senderColor(message.senderId, bubble.container), onOpenLink, Modifier.testTag("forward_${message.id}"))
    }
    val replyTo = message.replyTo
    if (replyTo != null) {
        val quoted = reply?.let { messagePreview(it) }?.ifEmpty { null } ?: reply?.media?.fileName ?: stringResource(R.string.chat_message)
        val entities = reply?.takeIf { it.article == null && it.text.isNotEmpty() }?.entities.orEmpty()
        val author = reply?.let { if (it.out) stringResource(R.string.chats_you) else it.senderName }
        ReplyQuote(author, quoted, if (message.out) bubble.accent else senderColor(reply?.senderId ?: 0, bubble.container), onOpenReply?.let { open -> { open(replyTo) } }, entities, emoji)
    }
}

@Composable
internal fun MetaPill(message: MessageItem, modifier: Modifier = Modifier, backdrop: ChatBackdrop? = LocalChatBackdrop.current) {
    GlassPill(CircleShape, modifier.testTag("metaPill_${message.id}"), backdrop) {
        MessageMeta(message, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), color = Color.White)
    }
}

private val STICKER_SIZE = 190.dp

@Composable
private fun EphemeralHeader(message: MessageItem, info: EphemeralInfo, color: Color) {
    val bot = info.botUsername
    val text = when {
        !message.out -> stringResource(R.string.chat_ephemeral_you)
        bot != null -> stringResource(R.string.chat_ephemeral_to, bot)
        else -> stringResource(R.string.chat_ephemeral_to_bot)
    }
    Row(
        Modifier.padding(bottom = 2.dp).testTag("ephemeral_${message.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(R.drawable.ic_visibility), null, Modifier.size(14.dp), tint = color)
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun ViaHeader(username: String, color: Color, onClick: ((String) -> Unit)?, modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.chat_via_bot, username),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier.then(if (onClick == null) Modifier else Modifier.clickable { onClick(username) }),
    )
}
