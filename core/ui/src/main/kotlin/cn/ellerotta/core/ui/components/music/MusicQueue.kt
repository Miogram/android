package cn.ellerotta.core.ui.components.music

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
    LazyColumn(state = list, modifier = modifier.fillMaxWidth().testTag("musicQueue"), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = HEADER) { QueueHeader(state, actions) }
        itemsIndexed(rows, key = { _, track -> track.slot }) { index, track ->
            val lifted = track.slot == dragged
            QueueRow(
                track = track,
                index = index,
                count = rows.size,
                current = track.slot == state.track.slot,
                cover = if (track.slot == state.track.slot) state.cover else track.media.thumb.takeUnless { track.isVoice },
                lifted = lifted,
                actions = actions,
                modifier = if (lifted) Modifier.zIndex(1f).graphicsLayer { translationY = shift } else Modifier.animateItem(),
                handle = Modifier.pointerInput(track.slot) {
                    detectDragGestures(
                        onDragStart = {
                            val start = now.queue
                            moving = start
                            dragged = track.slot
                            from = start.indexOfFirst { it.slot == track.slot }
                            shift = 0f
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
                            if (from >= 0 && to >= 0 && to != from) actions.onMove(from, to)
                        },
                        onDragCancel = {
                            moving = null
                            dragged = null
                            shift = 0f
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun QueueHeader(state: MusicState, actions: MusicActions) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp)) {
        Text(
            stringResource(R.string.music_queue),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            pluralStringResource(R.plurals.music_tracks, state.queue.size, state.queue.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                ToggleButton(
                    checked = chosen,
                    onCheckedChange = { actions.onOrder(order) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        orders.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = modeColors(),
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
                        fontWeight = if (chosen) FontWeight.SemiBold else null,
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
    cover: ByteArray?,
    lifted: Boolean,
    actions: MusicActions,
    modifier: Modifier,
    handle: Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val content = if (current) scheme.onSecondaryContainer else scheme.onSurface
    val secondary = if (current) scheme.onSecondaryContainer else scheme.onSurfaceVariant
    val up = stringResource(R.string.music_move_up)
    val down = stringResource(R.string.music_move_down)
    val next = stringResource(R.string.music_play_next)
    val remove = stringResource(R.string.music_remove)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(ROW_CORNER))
            .background(
                when {
                    current -> scheme.secondaryContainer
                    lifted -> scheme.surfaceContainerHighest
                    else -> Color.Transparent
                },
            )
            .selectable(selected = current, onClick = { actions.onPlayAt(index) })
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
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
            .testTag("queue_$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MusicCover(cover, Modifier.size(48.dp), voice = track.isVoice)
        Column(Modifier.weight(1f)) {
            Text(
                track.heading(),
                style = MaterialTheme.typography.bodyLarge,
                color = content,
                fontWeight = if (current) FontWeight.SemiBold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(track.subheading(), style = MaterialTheme.typography.bodyMedium, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (current) {
            Icon(painterResource(R.drawable.ic_graphic_eq), stringResource(R.string.music_now_playing), tint = content)
            Spacer(Modifier.size(48.dp))
        } else {
            Text(voiceTime(track.media.duration * 1000L), style = MaterialTheme.typography.labelMedium, color = secondary)
            Box {
                IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag("queueMenu_$index")) {
                    Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.music_entry_menu), tint = secondary)
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
            tint = secondary,
            modifier = handle.minimumInteractiveComponentSize().testTag("queueHandle_${track.slot}"),
        )
    }
}

private const val HEADER = "header"
private val ROW_CORNER = 20.dp
