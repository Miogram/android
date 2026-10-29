package cn.ellerotta.core.ui.components.music

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cn.ellerotta.core.model.MusicOrder
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.MusicTrack
import cn.ellerotta.core.model.moved
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.voiceTime

@Composable
internal fun MusicQueueSheet(state: MusicState, actions: MusicActions, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        MusicQueueContent(state, actions)
    }
}

@Composable
internal fun MusicQueueContent(state: MusicState, actions: MusicActions, modifier: Modifier = Modifier) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.index + 1)
    val haptics = LocalHapticFeedback.current
    val now by rememberUpdatedState(state)
    var moving by remember { mutableStateOf<List<MusicTrack>?>(null) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var from by remember { mutableIntStateOf(-1) }
    var shift by remember { mutableFloatStateOf(0f) }
    val rows = moving ?: state.queue
    LazyColumn(
        state = list,
        modifier = modifier.fillMaxWidth().testTag("musicQueue"),
        contentPadding = PaddingValues(start = QUEUE_MARGIN, end = QUEUE_MARGIN, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = HEADER) { QueueHeader(state, actions) }
        itemsIndexed(rows, key = { _, track -> track.slot }) { index, track ->
            val lifted = track.slot == dragged
            val interactions = remember { MutableInteractionSource() }
            val lift = remember { arrayOfNulls<DragInteraction.Start>(1) }
            QueueRow(
                track = track,
                index = index,
                count = rows.size,
                current = track.slot == state.track.slot,
                playing = state.playing,
                cover = if (track.slot == state.track.slot) state.cover else track.media.thumb.takeUnless { track.isVoice },
                actions = actions,
                interactions = interactions,
                modifier = if (lifted) Modifier.zIndex(1f).graphicsLayer { translationY = shift } else Modifier.animateItem(),
                handle = Modifier.pointerInput(track.slot) {
                    detectDragGestures(
                        onDragStart = {
                            val start = now.queue
                            moving = start
                            dragged = track.slot
                            from = start.indexOfFirst { it.slot == track.slot }
                            shift = 0f
                            DragInteraction.Start().also { lift[0] = it; interactions.tryEmit(it) }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            shift += amount.y
                            val order = moving ?: return@detectDragGestures
                            val items = list.layoutInfo.visibleItemsInfo
                            val me = items.firstOrNull { it.key == track.slot } ?: return@detectDragGestures
                            val middle = (me.offset + me.size / 2 + shift).toInt()
                            val over = items.firstOrNull { it.key != track.slot && it.key != HEADER && middle in it.offset until it.offset + it.size } ?: return@detectDragGestures
                            val at = order.indexOfFirst { it.slot == track.slot }
                            val to = order.indexOfFirst { it.slot == over.key }
                            if (at < 0 || to < 0) return@detectDragGestures
                            moving = order.moved(at, to)
                            val top = if (to > at) over.offset + over.size - me.size else over.offset
                            shift -= top - me.offset
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        },
                        onDragEnd = {
                            val to = moving?.indexOfFirst { it.slot == track.slot } ?: -1
                            moving = null
                            dragged = null
                            shift = 0f
                            lift[0]?.let { interactions.tryEmit(DragInteraction.Stop(it)) }
                            lift[0] = null
                            if (from >= 0 && to >= 0 && to != from) actions.onMove(from, to)
                        },
                        onDragCancel = {
                            moving = null
                            dragged = null
                            shift = 0f
                            lift[0]?.let { interactions.tryEmit(DragInteraction.Cancel(it)) }
                            lift[0] = null
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun QueueHeader(state: MusicState, actions: MusicActions) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            stringResource(R.string.music_queue),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = ROW_START).semantics { heading() },
        )
        Text(
            pluralStringResource(R.plurals.music_tracks, state.queue.size, state.queue.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = ROW_START),
        )
        if (state.track.isVoice) return@Column
        Spacer(Modifier.size(12.dp))
        val orders = MusicOrder.entries
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            orders.forEachIndexed { index, order ->
                val chosen = state.order == order
                FilledTonalToggleButton(
                    checked = chosen,
                    onCheckedChange = { actions.onOrder(order) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        orders.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton }.testTag("queueOrder_${order.name}"),
                ) {
                    Icon(painterResource(orderIcon(order)), orderName(order), Modifier.size(ToggleButtonDefaults.IconSize))
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    Text(
                        stringResource(
                            when (order) {
                                MusicOrder.Chat -> R.string.music_order_chat_short
                                MusicOrder.Reverse -> R.string.music_order_reverse_short
                                MusicOrder.Shuffle -> R.string.music_shuffle
                            },
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = if (chosen) MaterialTheme.typography.labelLargeEmphasized else LocalTextStyle.current,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    track: MusicTrack,
    index: Int,
    count: Int,
    current: Boolean,
    playing: Boolean,
    cover: ByteArray?,
    actions: MusicActions,
    interactions: MutableInteractionSource,
    modifier: Modifier,
    handle: Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val up = stringResource(R.string.music_move_up)
    val down = stringResource(R.string.music_move_down)
    val next = stringResource(R.string.music_play_next)
    val remove = stringResource(R.string.music_remove)
    SegmentedListItem(
        selected = current,
        onClick = { actions.onPlayAt(index) },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        interactionSource = interactions,
        contentPadding = ROW_PADDING,
        leadingContent = { MusicCover(cover, Modifier.size(ROW_COVER), voice = track.isVoice, shape = MaterialTheme.shapes.small) },
        supportingContent = { Text(track.subheading(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current) {
                    PlayingBars(playing, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.padding(horizontal = 12.dp))
                    Spacer(Modifier.size(48.dp))
                } else {
                    Text(voiceTime(track.media.duration * 1000L))
                    Box {
                        IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag("queueMenu_$index")) {
                            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.music_entry_menu))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(next) },
                                onClick = {
                                    menu = false
                                    actions.onPlayNext(index)
                                },
                                shape = MenuDefaults.itemShape(0, 2).shape,
                                leadingIcon = { Icon(painterResource(R.drawable.ic_playlist_play), null) },
                                modifier = Modifier.testTag("queuePlayNext"),
                            )
                            DropdownMenuItem(
                                text = { Text(remove) },
                                onClick = {
                                    menu = false
                                    actions.onRemove(index)
                                },
                                shape = MenuDefaults.itemShape(1, 2).shape,
                                leadingIcon = { Icon(painterResource(R.drawable.ic_playlist_remove), null) },
                                modifier = Modifier.testTag("queueRemove"),
                            )
                        }
                    }
                }
                Icon(
                    painterResource(R.drawable.ic_drag_handle),
                    stringResource(R.string.music_reorder),
                    modifier = handle.minimumInteractiveComponentSize().testTag("queueHandle_${track.slot}"),
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                customActions = buildList {
                    if (index > 0) add(CustomAccessibilityAction(up) { actions.onMove(index, index - 1); true })
                    if (index < count - 1) add(CustomAccessibilityAction(down) { actions.onMove(index, index + 1); true })
                    if (!current) {
                        add(CustomAccessibilityAction(next) { actions.onPlayNext(index); true })
                        add(CustomAccessibilityAction(remove) { actions.onRemove(index); true })
                    }
                }
            }
            .testTag("queue_$index"),
    ) {
        Text(
            track.heading(),
            style = if (current) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlayingBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val swing = animateFloatAsState(if (playing) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "barsSwing")
    val moving by remember { derivedStateOf { swing.value > 0f } }
    val levels: List<State<Float>>? = if (playing || moving) {
        val transition = rememberInfiniteTransition(label = "bars")
        BAR_PERIODS.mapIndexed { index, period ->
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(period, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(period * index / BAR_PERIODS.size)),
                label = "bar$index",
            )
        }
    } else {
        null
    }
    val label = stringResource(R.string.music_now_playing)
    Canvas(modifier.size(BAR_BOX).semantics { contentDescription = label }) {
        val width = BAR_WIDTH.toPx()
        val gap = BAR_GAP.toPx()
        val low = BAR_LOW.toPx()
        val high = BAR_HIGH.toPx()
        var x = (size.width - width * BAR_REST.size - gap * (BAR_REST.size - 1)) / 2
        BAR_REST.forEachIndexed { index, rest ->
            val level = rest + ((levels?.get(index)?.value ?: rest) - rest) * swing.value
            val height = low + (high - low) * level
            drawRoundRect(color, Offset(x, (size.height - height) / 2), Size(width, height), CornerRadius(width / 2))
            x += width + gap
        }
    }
}

private const val HEADER = "header"
private val QUEUE_MARGIN = 16.dp
private val ROW_START = 16.dp
private val ROW_PADDING = PaddingValues(start = ROW_START, end = 4.dp, top = 10.dp, bottom = 10.dp)
private val ROW_COVER = 56.dp
private val BAR_BOX = 24.dp
private val BAR_WIDTH = 4.dp
private val BAR_GAP = 3.dp
private val BAR_LOW = 5.dp
private val BAR_HIGH = 18.dp
private val BAR_PERIODS = listOf(520, 380, 640)
private val BAR_REST = listOf(0.45f, 0.8f, 0.6f)
