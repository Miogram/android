package cn.ellerotta.core.ui.components.folders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.FolderLimits
import cn.ellerotta.core.model.SuggestedFolder
import cn.ellerotta.core.model.moved
import cn.ellerotta.core.model.real
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.theme.shrinkFadeOut
import kotlin.math.roundToInt

data class FoldersState(
    val folders: List<ChatFolder> = emptyList(),
    val suggested: List<SuggestedFolder> = emptyList(),
    val suggestedLoading: Boolean = false,
    val limits: FolderLimits = FolderLimits(),
) {
    val canCreate: Boolean get() = limits.canAddFolder(folders.real.size)
}

@Composable
fun FoldersScreen(
    state: FoldersState,
    onBack: () -> Unit,
    onOpen: (ChatFolder) -> Unit,
    onCreate: () -> Unit,
    onReorder: (List<Int>) -> Unit,
    onAddSuggested: (SuggestedFolder) -> Unit,
) {
    val list = rememberLazyListState()
    val title = stringResource(R.string.folders_title)
    var order by remember(state.folders) { mutableStateOf(state.folders.map { it.id }) }
    val byId = remember(state.folders) { state.folders.associateBy { it.id } }
    val drag = remember { DragState() }
    val creatable = state.canCreate
    Page(
        title,
        rememberTopBarState(list),
        onBack = onBack,
        floating = {
            AnimatedVisibility(creatable, enter = MaterialTheme.motionScheme.expandFadeIn(), exit = MaterialTheme.motionScheme.shrinkFadeOut()) {
                MediumExtendedFloatingActionButton(
                    onClick = onCreate,
                    icon = { Icon(painterResource(R.drawable.ic_add), null) },
                    text = { Text(stringResource(R.string.folders_create)) },
                    modifier = Modifier.testTag("folderCreate"),
                    expanded = true,
                )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("folders_list"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "hint") {
                Text(
                    stringResource(R.string.folders_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp),
                )
            }
            if (!creatable) {
                item(key = "limit") {
                    LimitCard(
                        stringResource(if (state.limits.premium) R.string.folders_limit_reached_premium else R.string.folders_limit_reached, state.limits.folders),
                        Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    )
                }
            }
            item(key = "yours") {
                SectionLabel(stringResource(R.string.folders_your), pluralStringResource(R.plurals.folders_count, state.folders.real.size, state.folders.real.size))
            }
            itemsIndexed(order, key = { _, id -> "folder_$id" }) { index, id ->
                val folder = byId[id] ?: return@itemsIndexed
                val dragging = drag.id == id
                val name = if (folder.isAll) stringResource(R.string.chats_all) else folder.title
                FolderRow(
                    folder = folder,
                    title = name,
                    shape = sliceShape(index, order.size),
                    onOpen = { onOpen(folder) },
                    onMove = { delta -> onReorder(order.moved(index, index + delta)) },
                    canMoveUp = index > 0,
                    canMoveDown = index < order.lastIndex,
                    modifier = Modifier
                        .then(if (dragging) Modifier else Modifier.animateItem(fadeInSpec = MaterialTheme.motionScheme.fastEffectsSpec(), placementSpec = MaterialTheme.motionScheme.fastSpatialSpec(), fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec()))
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) drag.offset else 0f },
                    handle = Modifier.pointerInput(id) {
                        detectDragGestures(
                            onDragStart = { drag.start(id) },
                            onDrag = { change, amount ->
                                change.consume()
                                drag.offset += amount.y
                                order = drag.swapped(list, order)
                            },
                            onDragEnd = {
                                val moved = order != state.folders.map { it.id }
                                drag.end()
                                if (moved) onReorder(order)
                            },
                            onDragCancel = {
                                drag.end()
                                order = state.folders.map { it.id }
                            },
                        )
                    },
                    elevated = dragging,
                )
            }
            if (state.suggested.isNotEmpty() || state.suggestedLoading) {
                item(key = "suggested") {
                    SectionLabel(stringResource(R.string.folders_suggested), null, Modifier.padding(top = 16.dp))
                }
                if (state.suggestedLoading && state.suggested.isEmpty()) {
                    item(key = "suggested_loading") {
                        Row(Modifier.fillMaxWidth().padding(16.dp).testTag("suggestedLoading"), horizontalArrangement = Arrangement.Center) {
                            LoadingIndicator(Modifier.size(40.dp))
                        }
                    }
                }
                itemsIndexed(state.suggested, key = { _, s -> "suggested_${s.folder.title}" }) { index, suggestion ->
                    SuggestedRow(
                        suggestion,
                        sliceShape(index, state.suggested.size),
                        enabled = creatable,
                        onAdd = { onAddSuggested(suggestion) },
                        modifier = Modifier.animateItem(fadeInSpec = MaterialTheme.motionScheme.fastEffectsSpec(), placementSpec = MaterialTheme.motionScheme.fastSpatialSpec(), fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                    )
                }
            }
            item(key = "end") { Spacer(Modifier.heightIn(min = 72.dp)) }
        }
    }
}

internal class DragState {
    var id by mutableStateOf<Int?>(null)
    var offset by mutableFloatStateOf(0f)

    fun start(id: Int) {
        this.id = id
        offset = 0f
    }

    fun end() {
        id = null
        offset = 0f
    }

    fun swapped(list: LazyListState, order: List<Int>): List<Int> {
        val dragged = id ?: return order
        val items = list.layoutInfo.visibleItemsInfo
        val own = items.firstOrNull { it.key == "folder_$dragged" } ?: return order
        val middle = own.offset + own.size / 2f + offset
        val target = items.firstOrNull { item ->
            val key = item.key as? String
            key != null && key.startsWith("folder_") && key != own.key && middle >= item.offset && middle < item.offset + item.size
        } ?: return order
        val from = order.indexOf(dragged)
        val to = order.indexOf((target.key as String).removePrefix("folder_").toInt())
        if (from < 0 || to < 0) return order
        offset -= (target.offset - own.offset).toFloat()
        return order.moved(from, to)
    }
}

@Composable
private fun FolderRow(
    folder: ChatFolder,
    title: String,
    shape: Shape,
    onOpen: () -> Unit,
    onMove: (Int) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    modifier: Modifier,
    handle: Modifier,
    elevated: Boolean,
) {
    val moveUp = stringResource(R.string.folders_move_up, title)
    val moveDown = stringResource(R.string.folders_move_down, title)
    Surface(
        shape = shape,
        color = if (elevated) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = if (elevated) 6.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .semantics {
                customActions = listOfNotNull(
                    if (canMoveUp) CustomAccessibilityAction(moveUp) { onMove(-1); true } else null,
                    if (canMoveDown) CustomAccessibilityAction(moveDown) { onMove(1); true } else null,
                )
            }
            .testTag("folderRow_${folder.id}"),
    ) {
        Row(
            Modifier
                .heightIn(min = 64.dp)
                .then(if (folder.isAll) Modifier else Modifier.clickable(onClick = onOpen))
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).then(handle).testTag("folderHandle_${folder.id}"), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_drag_handle), stringResource(R.string.folders_reorder, title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FolderTile(folder)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!folder.isAll) {
                    Text(folderSummary(folder), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SuggestedRow(suggestion: SuggestedFolder, shape: Shape, enabled: Boolean, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).testTag("suggested_${suggestion.folder.title}"),
    ) {
        Row(Modifier.heightIn(min = 64.dp).padding(start = 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            FolderTile(suggestion.folder)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(suggestion.folder.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(suggestion.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onAdd, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag("suggestedAdd_${suggestion.folder.title}"), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.folders_add))
            }
        }
    }
}

@Composable
internal fun FolderTile(folder: ChatFolder, modifier: Modifier = Modifier) {
    Box(
        modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val emoticon = folder.emoticon
        if (emoticon != null) {
            Text(emoticon, style = MaterialTheme.typography.titleMedium)
        } else {
            Icon(painterResource(if (folder.isAll) R.drawable.ic_chat else R.drawable.ic_folder), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
internal fun folderSummary(folder: ChatFolder): String {
    val parts = buildList {
        if (folder.shared) add(stringResource(R.string.folders_shared))
        if (folder.contacts) add(stringResource(R.string.folders_type_contacts))
        if (folder.nonContacts) add(stringResource(R.string.folders_type_non_contacts))
        if (folder.groups) add(stringResource(R.string.folders_type_groups))
        if (folder.broadcasts) add(stringResource(R.string.folders_type_channels))
        if (folder.bots) add(stringResource(R.string.folders_type_bots))
        val named = folder.explicit.size
        if (named > 0) add(pluralStringResource(R.plurals.folders_chats_count, named, named))
    }
    return parts.joinToString(", ")
}

@Composable
internal fun SectionLabel(text: String, count: String?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f).semantics { heading() })
        if (count != null) Text(count, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun LimitCard(text: String, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = modifier.fillMaxWidth().testTag("folderLimit")) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(16.dp))
    }
}

internal fun sliceShape(index: Int, count: Int): Shape = when {
    count == 1 -> RoundedCornerShape(20.dp)
    index == 0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    index == count - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    else -> RoundedCornerShape(4.dp)
}
