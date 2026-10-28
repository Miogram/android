package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.clickable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.geometry.Rect
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.model.ArticleListItem
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.ReactionArt
import cn.ellerotta.core.model.VoicePlayback
import java.time.ZoneId
import cn.ellerotta.core.model.BotButton

@Composable
internal fun ChatMessageList(
    rows: List<ChatEntry>,
    zone: ZoneId,
    list: LazyListState,
    dialog: DialogItem?,
    byId: Map<Int, MessageItem>,
    downloads: Map<String, DownloadState>,
    stickers: Map<String, ByteArray>,
    loadingOlder: Boolean,
    playback: VoicePlayback?,
    onDownload: (MediaItem) -> Unit,
    onToggleVoice: (MediaItem) -> Unit,
    onSelect: (MessageItem, Rect) -> Unit,
    onOpenMedia: (MessageItem) -> Unit,
    onOpenSticker: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Int? = null,
    onVoiceSpeed: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    onCommand: ((String) -> Unit)? = null,
    onRemind: ((Int, Int) -> Unit)? = null,
    customEmoji: Map<Long, MediaItem> = emptyMap(),
    avatars: Map<String, ByteArray> = emptyMap(),
    onReact: ((MessageItem, Reaction) -> Unit)? = null,
    defaultReaction: Reaction? = null,
    onOpenReply: (Int) -> Unit = {},
    selection: Set<Int> = emptySet(),
    onToggleSelect: (MessageItem) -> Unit = {},
    onHashtag: ((String) -> Unit)? = null,
    onViaBot: ((String) -> Unit)? = null,
    onButton: ((MessageItem, BotButton) -> Unit)? = null,
    onOpenArticleMedia: (MessageItem, Int) -> Unit = { _, _ -> },
    onShowMore: ((Int) -> Unit)? = null,
    loadingArticles: Set<Int> = emptySet(),
    onToggleCheck: ((Int, ArticleListItem) -> Unit)? = null,
    music: MusicState? = null,
    onPlayMusic: ((MessageItem) -> Unit)? = null,
    onOpenReplies: ((Int) -> Unit)? = null,
    onVote: ((Int, List<String>) -> Unit)? = null,
    onShowVotes: ((Int) -> Unit)? = null,
    onShowOriginal: ((Int) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    lifted: GraphicsLayer? = null,
    liftedId: Int? = null,
    onLiftedBounds: (Rect) -> Unit = {},
    backdrop: ChatBackdrop? = null,
    onTap: ((MessageItem) -> Unit)? = null,
    reactionArt: Map<Reaction, ReactionArt> = emptyMap(),
    reactionEffects: ReactionEffects? = null,
    onReply: ((MessageItem) -> Unit)? = null,
    onRetry: ((MessageItem) -> Unit)? = null,
    onOpenSender: ((MessageItem) -> Unit)? = null,
    wallpapers: ChatWallpaperActions? = null,
    onOpenPlace: ((MessageItem) -> Unit)? = null,
) = CompositionLocalProvider(LocalChatBackdrop provides backdrop, LocalReactionArt provides reactionArt, LocalReactionEffects provides reactionEffects) {
    val spans = remember(rows) { runSpans(rows) }
    val days = remember(rows) { dayOfRows(rows) }
    Box(modifier) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("messages"),
            state = list,
            reverseLayout = true,
            contentPadding = contentPadding,
        ) {
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                when (row) {
                    is ChatEntry.Day -> CenterChip(dayText(row.date, zone), Modifier.animateItem())
                    ChatEntry.Unread -> CenterChip(stringResource(R.string.chat_unread_comments), Modifier.animateItem().testTag("unreadLine"))
                    is ChatEntry.Bubble -> {
                        if (row.message.wallpaper != null) {
                            WallpaperChip(row.message, wallpapers, Modifier.animateItem())
                        } else if (row.message.gift != null) {
                            GiftChip(row.message, stickers, Modifier.animateItem())
                        } else if (row.message.service != null && row.message.call == null) {
                            CenterChip(serviceText(row.message, dialog?.kind ?: PeerKind.Group), Modifier.animateItem())
                        } else {
                            val message = row.message
                            val drawn = remember(message, dialog?.kind) { if (message.out && !message.onEndSide(dialog)) message.copy(out = false) else message }
                            val local = message.pending || message.streaming
                            val ephemeral = message.ephemeral != null
                            val selecting = selection.isNotEmpty()
                            val chosen = message.id in selection
                            val avatarSpace = !message.out && dialog?.kind == PeerKind.Group
                            val span = if (avatarSpace) spans[index] else null
                            val avatarShown by remember(span, index) { derivedStateOf { span != null && list.layoutInfo.avatarLift(span, index, 0) != null } }
                            val tint = if (row.shows(highlighted)) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)).testTag("highlight_${message.id}") else Modifier
                            val bounds = remember { RowBounds() }
                            val lift = if (lifted != null && message.id == liftedId) {
                                Modifier.drawWithContent { lifted.record { this@drawWithContent.drawContent() } }
                            } else {
                                Modifier
                            }
                            Box(
                                Modifier
                                    .animateItem()
                                    .zIndex(if (avatarShown) 1f else 0f)
                                    .then(lift)
                                    .then(tint)
                                    .onGloballyPositioned { coordinates ->
                                        bounds.rect = coordinates.boundsInRoot()
                                        if (message.id == liftedId) onLiftedBounds(bounds.rect)
                                    },
                            ) {
                                val toggle = { if (row.album.isEmpty()) onToggleSelect(message) else row.album.filter { (it.id in selection) == chosen }.forEach(onToggleSelect) }
                                val menu = !selecting && (!local || message.failed)
                                val answer = onReply?.takeIf { !local && !selecting }?.let { reply -> { reply(message) } }
                                SelectableRow(selecting, chosen, drawn.out, selectable = !local && !ephemeral, onToggle = toggle, tag = "select_${message.id}") {
                                    SwipeToReply(answer) {
                                        MessageBubble(
                                            drawn, row.position, downloads, stickers, onDownload,
                                            reply = message.replyTo?.let(byId::get),
                                            onLongPress = if (menu) ({ onSelect(message, bounds.rect) }) else null,
                                            playback = playback?.takeIf { it.key == message.media?.key || message.article != null },
                                            onToggleVoice = onToggleVoice,
                                            onVoiceSpeed = onVoiceSpeed,
                                            onOpenMedia = { if (!ephemeral) onOpenMedia(message) },
                                            onOpenSticker = onOpenSticker,
                                            onOpenLink = onOpenLink,
                                            onCommand = onCommand,
                                            onRemind = onRemind,
                                            customEmoji = customEmoji,
                                            avatars = avatars,
                                            onReact = onReact?.takeIf { !local && !ephemeral }?.let { react -> { reaction -> react(message, reaction) } },
                                            onDoubleTap = defaultReaction?.takeIf { onReact != null && !local && !ephemeral }?.let { reaction -> { onReact?.invoke(message, reaction) } },
                                            onOpenReply = onOpenReply,
                                            onHashtag = onHashtag,
                                            onViaBot = onViaBot,
                                            onButton = onButton?.let { press -> { button -> press(message, button) } },
                                            onOpenArticleMedia = { index -> if (!ephemeral) onOpenArticleMedia(message, index) },
                                            onShowMore = onShowMore?.takeIf { !local && !ephemeral }?.let { more -> { more(message.id) } },
                                            loadingMore = message.id in loadingArticles,
                                            onToggleCheck = onToggleCheck?.takeIf { message.out && !local && !ephemeral && message.article?.part == false }?.let { toggle -> { item -> toggle(message.id, item) } },
                                            music = music?.takeIf { it.track.messageId == message.id || row.album.any { item -> item.id == it.track.messageId } },
                                            onPlayMusic = onPlayMusic?.takeIf { !ephemeral },
                                            onOpenReplies = onOpenReplies?.takeIf { !local && !ephemeral }?.let { open -> { open(message.id) } },
                                            onVote = onVote?.takeIf { !local && !ephemeral }?.let { vote -> { options -> vote(message.id, options) } },
                                            onShowVotes = onShowVotes?.takeIf { !local && !ephemeral }?.let { show -> { show(message.id) } },
                                            onShowOriginal = onShowOriginal?.let { show -> { show(message.id) } },
                                            album = row.album,
                                            onOpenItem = { item -> if (!ephemeral) onOpenMedia(item) },
                                            avatarSpace = avatarSpace,
                                            onTap = onTap?.let { tap -> { tap(message) } },
                                            onReply = answer,
                                            onRetry = onRetry?.takeIf { message.failed && !selecting }?.let { retry -> { retry(message) } },
                                            onOpenPlace = onOpenPlace?.takeIf { !local && !ephemeral && !selecting }?.let { open -> { open(message) } },
                                        )
                                        if (span != null && avatarShown) {
                                            val avatarPx = with(LocalDensity.current) { AVATAR_SIZE.roundToPx() }
                                            val newest = (rows[span.first] as ChatEntry.Bubble).message
                                            val open = onOpenSender?.takeIf { message.senderKey != null }
                                            Avatar(
                                                message.senderName.orEmpty(), message.senderAvatar?.let(avatars::get), size = AVATAR_SIZE.value.toInt(), seed = message.senderId,
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(start = BUBBLE_EDGE)
                                                    .graphicsLayer { translationY = -(list.layoutInfo.avatarLift(span, index, avatarPx) ?: 0).toFloat() }
                                                    .then(if (open != null) Modifier.clickable(onClickLabel = stringResource(R.string.chat_open_profile)) { open(message) } else Modifier)
                                                    .testTag("senderAvatar_${newest.id}"),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (loadingOlder) {
                item(key = "older") {
                    Box(Modifier.fillMaxWidth().padding(16.dp).testTag("loadingOlder"), contentAlignment = Alignment.Center) {
                        LoadingIndicator(Modifier.size(40.dp))
                    }
                }
            }
        }
        StickyDay(list, rows, days, zone, Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding()))
    }
}

internal fun MessageItem.onEndSide(dialog: DialogItem?): Boolean = out && dialog?.kind != PeerKind.Channel

internal fun dayOfRows(rows: List<ChatEntry>): List<LocalDate?> {
    val days = arrayOfNulls<LocalDate>(rows.size)
    var day: LocalDate? = null
    for (i in rows.indices.reversed()) {
        (rows[i] as? ChatEntry.Day)?.let { day = it.date }
        days[i] = day
    }
    return days.asList()
}

internal fun LazyListLayoutInfo.topDay(rows: List<ChatEntry>, days: List<LocalDate?>): LocalDate? {
    val line = viewportEndOffset - afterContentPadding
    val top = visibleItemsInfo.lastOrNull { it.offset < line && it.index < rows.size } ?: return null
    val day = days.getOrNull(top.index) ?: return null
    val chip = visibleItemsInfo.firstOrNull { it.index >= top.index && (rows.getOrNull(it.index) as? ChatEntry.Day)?.date == day }
    return day.takeIf { chip == null || chip.offset + chip.size > line }
}

@Composable
private fun StickyDay(list: LazyListState, rows: List<ChatEntry>, days: List<LocalDate?>, zone: ZoneId, modifier: Modifier) {
    val day by remember(list, rows, days) { derivedStateOf { list.layoutInfo.topDay(rows, days) } }
    var dragged by remember { mutableStateOf(false) }
    var scrolling by remember { mutableStateOf(false) }
    LaunchedEffect(list) { list.interactionSource.interactions.collect { if (it is DragInteraction.Start) dragged = true } }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collectLatest { moving ->
            if (!moving) {
                delay(STICKY_DAY_MS)
                dragged = false
            }
            scrolling = moving
        }
    }
    val shown = remember { DayHolder() }
    day?.let { shown.day = it }
    AnimatedVisibility(scrolling && dragged && day != null, modifier, enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()), exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())) {
        shown.day?.let { CenterChip(dayText(it, zone), Modifier.testTag("stickyDay")) }
    }
}

private class DayHolder {
    var day: LocalDate? = null
}

private const val STICKY_DAY_MS = 1_500L

private class RowBounds {
    var rect = Rect.Zero
}

internal fun runSpans(rows: List<ChatEntry>): Map<Int, IntRange> {
    val spans = HashMap<Int, IntRange>()
    var start = -1
    rows.forEachIndexed { i, row ->
        if (row !is ChatEntry.Bubble) return@forEachIndexed
        if (row.position.last) start = i
        if (start >= 0 && row.position.first) {
            val span = start..i
            for (j in span) if (rows[j] is ChatEntry.Bubble) spans[j] = span
            start = -1
        }
    }
    return spans
}

internal fun LazyListLayoutInfo.avatarLift(span: IntRange, index: Int, avatar: Int): Int? {
    val shown = visibleItemsInfo.filter { it.index in span }
    val owner = shown.firstOrNull { it.offset + it.size > 0 } ?: shown.lastOrNull() ?: return null
    if (owner.index != index) return null
    val top = shown.firstOrNull { it.index == span.last }?.let { it.offset + it.size } ?: Int.MAX_VALUE
    return maxOf(owner.offset, minOf(maxOf(owner.offset, 0), top - avatar)) - owner.offset
}
