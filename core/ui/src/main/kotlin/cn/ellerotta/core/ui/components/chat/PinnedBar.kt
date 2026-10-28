package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PinnedMessages
import cn.ellerotta.core.model.isVisual
import cn.ellerotta.core.model.preview
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chats.mediaLabel
import cn.ellerotta.core.ui.components.common.CheckRow
import cn.ellerotta.core.ui.theme.MioShapes
import kotlin.math.abs
import kotlin.math.max

class ChatPinActions(
    val canPin: Boolean,
    val onTapped: () -> Int?,
    val onPin: (id: Int, silent: Boolean, forBoth: Boolean) -> Unit,
    val onUnpin: (id: Int) -> Unit,
    val onUnpinAll: () -> Unit,
    val onHide: () -> Unit = {},
    val onShow: (id: Int) -> Unit = {},
)

@Composable
internal fun PinnedBar(
    pinned: PinnedMessages,
    downloads: Map<String, DownloadState>,
    onDownload: (MediaItem) -> Unit,
    onTap: () -> Unit,
    onList: () -> Unit,
    onClose: (() -> Unit)?,
    closeLabel: String,
    modifier: Modifier = Modifier,
    stickers: Map<String, ByteArray> = emptyMap(),
) {
    val message = pinned.shown ?: return
    val count = pinned.messages.size
    val motion = MaterialTheme.motionScheme
    Surface(shape = MioShapes.previewBar, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = BAR_HEIGHT)
                .height(IntrinsicSize.Min)
                .combinedClickable(onClick = onTap, onLongClick = onList)
                .padding(start = 16.dp, end = 4.dp)
                .testTag("pinnedBar"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PinnedStripe(pinned.index, count, MaterialTheme.colorScheme.primary, Modifier.width(STRIPE_WIDTH).fillMaxHeight())
            val thumb = message.media?.takeIf { it.isVisual || it.kind == MediaKind.Sticker }
            AnimatedVisibility(
                thumb != null,
                enter = scaleIn(motion.fastSpatialSpec(), initialScale = 0.1f) + fadeIn(motion.fastEffectsSpec()),
                exit = scaleOut(motion.fastSpatialSpec(), targetScale = 0.1f) + fadeOut(motion.fastEffectsSpec()),
            ) {
                val last = remember { LastMedia() }
                if (thumb != null) last.media = thumb
                last.media?.let { PinnedThumb(it, downloads, stickers, onDownload, Modifier.padding(start = 9.dp)) }
            }
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
                PinnedTitle(pinned.index, count)
                AnimatedContent(
                    message,
                    transitionSpec = { slideBetween(initialState.id, targetState.id, motion) },
                    contentKey = { it.id },
                    label = "pinnedText",
                ) { shown ->
                    val plain = shown.media == null || shown.media?.kind == MediaKind.Link
                    Text(
                        pinnedText(shown),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (plain) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("pinnedText"),
                    )
                }
            }
            if (count > 1) {
                IconButton(onClick = onList, modifier = Modifier.testTag("pinnedList"), shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_list), stringResource(R.string.chat_pinned_list), Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("closePinned"), shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_close), closeLabel, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun slideBetween(from: Int, to: Int, motion: MotionScheme): ContentTransform {
    val sign = if (to > from) 1 else -1
    val spec = motion.fastSpatialSpec<IntOffset>()
    return ContentTransform(
        slideInVertically(spec) { sign * it / 2 } + fadeIn(motion.fastEffectsSpec()),
        slideOutVertically(spec) { -sign * it / 2 } + fadeOut(motion.fastEffectsSpec()),
        sizeTransform = SizeTransform(clip = false),
    )
}

@Composable
private fun PinnedTitle(index: Int, count: Int) {
    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.primary
    val motion = MaterialTheme.motionScheme
    val numbered = count > 2 && index != count - 1
    val plain = when {
        count == 2 && index == 0 -> stringResource(R.string.chat_pinned_previous)
        numbered -> null
        else -> stringResource(R.string.chat_pinned_message)
    }
    if (plain != null) {
        Text(plain, style = style, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("pinnedTitle"))
        return
    }
    val number = index + 1
    val (before, after) = stringResource(R.string.chat_pinned_message_number).split(NUMBER_ARG).let { it.first() to it.getOrElse(1) { "" } }
    val title = stringResource(R.string.chat_pinned_message_number, number)
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = title }.testTag("pinnedTitle")) {
        if (before.isNotEmpty()) Text(before, style = style, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
        AnimatedContent(
            number,
            transitionSpec = { slideBetween(-initialState, -targetState, motion) },
            label = "pinnedNumber",
        ) { Text(it.toString(), style = style, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1) }
        if (after.isNotEmpty()) Text(after, style = style, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
internal fun pinnedText(message: MessageItem): String {
    val text = messagePreview(message).replace('\n', ' ')
    val media = message.media
    return when {
        media?.kind == MediaKind.Sticker -> listOfNotNull(media.emoji, mediaLabel(MediaKind.Sticker)).joinToString(" ")
        text.isNotEmpty() -> text
        media != null -> media.kind?.let { mediaLabel(it) } ?: media.fileName
        else -> stringResource(R.string.chat_message)
    }
}

@Composable
private fun PinnedThumb(media: MediaItem, downloads: Map<String, DownloadState>, stickers: Map<String, ByteArray>, onDownload: (MediaItem) -> Unit, modifier: Modifier = Modifier) {
    if (media.kind == MediaKind.Sticker) {
        Box(modifier.size(THUMB_SIDE).testTag("pinnedThumb")) {
            StickerView(media, stickers[media.key], size = THUMB_SIDE, preview = stickers.previewOf(media))
        }
        return
    }
    val px = with(LocalDensity.current) { THUMB_SIDE.roundToPx() }
    val preview = remember(media, px) { media.preview(px) }
    LaunchedEffect(preview?.key) { if (preview != null && downloads[preview.key] == null) onDownload(preview) }
    Box(modifier.size(THUMB_SIDE).clip(MioShapes.media).testTag("pinnedThumb")) {
        StillImage(media, preview?.let { downloads[it.key] })
    }
}

@Composable
internal fun PinnedStripe(index: Int, count: Int, color: Color, modifier: Modifier = Modifier) {
    val position = remember { Animatable(index.toFloat()) }
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(index, count) {
        val wrapped = count > WRAP_SNAP && abs(position.value - index) > 1f
        if (wrapped) position.snapTo(index.toFloat()) else position.animateTo(index.toFloat(), spec)
    }
    val faded = color.copy(alpha = STRIPE_FADED)
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.testTag("pinnedStripe")) {
        if (count <= 0) return@Canvas
        val inset = STRIPE_INSET.toPx()
        val gap = SEGMENT_GAP.toPx()
        val available = size.height - inset * 2
        val segment = max((available - gap * (count - 1)) / count, SEGMENT_MIN.toPx())
        val all = count * segment + (count - 1) * gap
        val step = segment + gap
        val at = position.value
        val above = at * step
        val below = all - above - segment
        var offset = (size.height - segment) / 2
        if (offset - above > inset) offset = above + inset
        if (offset + segment + below < size.height - inset) offset = size.height - inset - segment - below
        val radius = CornerRadius(size.width / 2, size.width / 2)
        for (i in 0 until count) {
            val top = offset + (i - at) * step
            if (top + segment < 0 || top > size.height) continue
            drawRoundRect(if (i == index) color else faded, Offset(0f, top), Size(size.width, segment), radius)
        }
        if (all > available) {
            val fade = inset / size.height
            drawRect(
                Brush.verticalGradient(0f to Color.Transparent, fade to Color.Black, 1f - fade to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}

@Composable
internal fun PinDialog(peerName: String?, notifies: Boolean, onConfirm: (silent: Boolean, forBoth: Boolean) -> Unit, onDismiss: () -> Unit) {
    var forBoth by remember { mutableStateOf(true) }
    var notify by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_pin_title)) },
        text = {
            when {
                peerName != null -> CheckRow(forBoth, { forBoth = it }, stringResource(R.string.chat_pin_for_both, peerName), "pinForBoth")
                notifies -> CheckRow(notify, { notify = it }, stringResource(R.string.chat_pin_notify), "pinNotify")
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(notifies && !notify, peerName == null || forBoth) }, modifier = Modifier.testTag("confirmPin")) {
                Text(stringResource(R.string.chat_pin))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private class LastMedia {
    var media: MediaItem? = null
}

private val BAR_HEIGHT = 48.dp
private val STRIPE_WIDTH = 3.dp
private val STRIPE_INSET = 8.dp
private val SEGMENT_GAP = 2.dp
private val SEGMENT_MIN = 8.dp
private val THUMB_SIDE = 36.dp
private const val STRIPE_FADED = 0.4f

private const val WRAP_SNAP = 4
private const val NUMBER_ARG = "%1\$d"
