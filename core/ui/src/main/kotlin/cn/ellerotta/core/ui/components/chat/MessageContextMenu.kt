package cn.ellerotta.core.ui.components.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatReactionState
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.ui.R
import kotlin.math.roundToInt

internal data class ContextTarget(val message: MessageItem, val bounds: Rect, val end: Boolean = message.out)

@Composable
internal fun MessageContextMenu(
    target: ContextTarget,
    lifted: GraphicsLayer,
    reveal: () -> Float,
    lift: () -> Float,
    actions: List<MessageAction>,
    reactions: ChatReactionState,
    chosen: Set<Reaction>,
    emoji: InlineEmoji,
    onReact: (Reaction, Rect?, Boolean) -> Unit,
    onAction: (MessageAction) -> Unit,
    onDismiss: () -> Unit,
    pickerEmoji: PickerEmoji = PickerEmoji(),
    pickerActions: PickerActions = PickerActions(),
    avatars: Map<String, ByteArray> = emptyMap(),
    onShowReactors: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    BackHandler { if (expanded) expanded = false else onDismiss() }
    val density = LocalDensity.current
    val scrim = MaterialTheme.colorScheme.scrim
    val label = stringResource(R.string.chat_message_actions)
    val safe = WindowInsets.safeDrawing
    val ime = WindowInsets.ime
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val alignEnd = target.end
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(scrim.copy(alpha = SCRIM_ALPHA * reveal().coerceIn(0f, 1f))) }
            .semantics {
                paneTitle = label
                dismiss { onDismiss(); true }
            }
            .testTag("messageMenu"),
    ) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val edge = with(density) { EDGE.toPx() }
        val gap = with(density) { GAP.toPx() }
        val top = safe.getTop(density) + edge
        val bottom = height - maxOf(safe.getBottom(density), ime.getBottom(density)) - edge
        val bounds = target.bounds
        val barHeight = if (reactions.allowed.isEmpty()) 0 else barSize.height
        val groupTop = bounds.top - (if (barHeight > 0) barHeight + gap else 0f)
        val menuHeight = if (expanded) 0 else menuSize.height
        val groupBottom = bounds.bottom + (if (menuHeight > 0) gap + menuHeight else 0f)
        val groupHeight = groupBottom - groupTop
        val tall = groupHeight > bottom - top
        val shift = when {
            tall || groupTop < top -> top - groupTop
            groupBottom > bottom -> bottom - groupBottom
            else -> 0f
        }
        val contentHeight = if (tall) (groupHeight + top + (height - bottom)).roundToInt() else height
        fun x(itemWidth: Int): Int {
            val start = if (alignEnd) bounds.right - itemWidth else bounds.left
            return start.coerceIn(edge, (width - edge - itemWidth).coerceAtLeast(edge)).roundToInt()
        }
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState(), enabled = tall)
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
        ) {
            Spacer(Modifier.height(with(density) { contentHeight.toDp() }).fillMaxWidth())
            Box(
                Modifier
                    .offset { IntOffset(bounds.left.roundToInt(), (bounds.top + shift * lift()).roundToInt()) }
                    .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                    .drawBehind { drawLayer(lifted) },
            )
            if (reactions.allowed.isNotEmpty()) {
                val barTop = { bounds.top + shift * lift() - gap - barSize.height }
                if (!expanded) ReactionTail(alignEnd, Modifier.offset { IntOffset(x(barSize.width), barTop().roundToInt()) }.graphicsLayer { alpha = reveal().coerceIn(0f, 1f) }, barSize)
                ReactionPicker(
                    reactions,
                    chosen,
                    emoji,
                    pickerEmoji,
                    pickerActions,
                    maxWidth = with(density) { (width - 2 * edge).toDp() },
                    alignEnd = alignEnd,
                    expanded = expanded,
                    onExpand = { expanded = it },
                    onPick = onReact,
                    modifier = Modifier
                        .offset { IntOffset(x(barSize.width), barTop().roundToInt()) }
                        .onSizeChanged { barSize = it }
                        .graphicsLayer {
                            val shown = reveal().coerceIn(0f, 1f)
                            alpha = shown
                            scaleX = 0.88f + 0.12f * shown
                            scaleY = scaleX
                            transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 1f)
                        },
                )
            }
            if (actions.isNotEmpty() && !expanded) {
                ContextActions(
                    actions,
                    onAction,
                    Modifier
                        .offset { IntOffset(x(menuSize.width), (bounds.bottom + shift * lift() + gap).roundToInt()) }
                        .onSizeChanged { menuSize = it }
                        .graphicsLayer {
                            val shown = reveal().coerceIn(0f, 1f)
                            alpha = shown
                            scaleX = 0.92f + 0.08f * shown
                            scaleY = scaleX
                            transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0f)
                        },
                    message = target.message,
                    avatars = avatars,
                    onShowReactors = onShowReactors,
                )
            }
        }
    }
}

private val ICON_ACTIONS = listOf(MessageAction.Reply, MessageAction.Copy, MessageAction.Edit, MessageAction.Delete)

@Composable
private fun ContextActions(
    actions: List<MessageAction>,
    onAction: (MessageAction) -> Unit,
    modifier: Modifier = Modifier,
    message: MessageItem? = null,
    avatars: Map<String, ByteArray> = emptyMap(),
    onShowReactors: (() -> Unit)? = null,
) {
    val icons = actions.filter { it in ICON_ACTIONS }
    val lines = actions.filterNot { it in ICON_ACTIONS }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.widthIn(min = MENU_MIN_WIDTH, max = MENU_MAX_WIDTH).width(IntrinsicSize.Max),
    ) {
        Column(Modifier.padding(10.dp)) {
            if (message != null && onShowReactors != null) {
                Box(Modifier.clip(RoundedCornerShape(12.dp))) { ReactorsLine(message, avatars, onShowReactors) }
            }
            if (icons.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().heightIn(min = ACTION_HEIGHT), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    icons.forEach { action ->
                        val tint = if (action == MessageAction.Delete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        IconButton(onClick = { onAction(action) }, modifier = Modifier.size(ACTION_HEIGHT).testTag(action.tag), shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(action.icon), stringResource(action.label), tint = tint)
                        }
                    }
                }
            }
            lines.forEach { action ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = ACTION_HEIGHT)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAction(action) }
                        .semantics { role = Role.Button }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag(action.tag),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(painterResource(action.icon), null, Modifier.size(20.dp))
                    Text(stringResource(action.label), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ReactionTail(alignEnd: Boolean, modifier: Modifier, barSize: IntSize) {
    val color = MaterialTheme.colorScheme.surfaceContainerHighest
    Spacer(
        modifier.size(with(LocalDensity.current) { barSize.width.toDp() }, with(LocalDensity.current) { barSize.height.toDp() }).drawBehind {
            val large = 7.dp.toPx()
            val small = 3.5.dp.toPx()
            val inset = 30.dp.toPx()
            val x = if (alignEnd) inset else size.width - inset
            val step = if (alignEnd) -1f else 1f
            drawCircle(color, large, Offset(x, size.height + 1.dp.toPx()))
            drawCircle(color, small, Offset(x + step * 10.dp.toPx(), size.height + 10.dp.toPx()))
        },
    )
}

private const val SCRIM_ALPHA = 0.28f
private val EDGE = 12.dp
private val GAP = 8.dp
private val MENU_MIN_WIDTH = 200.dp
private val MENU_MAX_WIDTH = 280.dp
private val ACTION_HEIGHT = 48.dp

internal val CONTEXT_BLUR = 20.dp
