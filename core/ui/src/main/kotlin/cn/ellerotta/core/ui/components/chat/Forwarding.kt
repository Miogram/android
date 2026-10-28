package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.ForwardInfo
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chats.DialogAvatar
import cn.ellerotta.core.ui.components.common.CheckedSwitch
import cn.ellerotta.core.ui.components.common.TopBarPill
import cn.ellerotta.core.ui.components.common.TopBarTokens

data class ForwardChoice(val ids: List<Int>, val to: DialogItem, val dropAuthor: Boolean = false, val dropCaptions: Boolean = false)

internal const val MAX_SELECTED = 100

@Composable
internal fun SelectionHeader(count: Int, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp).height(TopBarTokens.PillSize), verticalAlignment = Alignment.CenterVertically) {
        TopBarPill(R.drawable.ic_close, stringResource(R.string.common_cancel), onClose, Modifier.testTag("closeSelection"))
        Text(
            pluralStringResource(R.plurals.chat_selected, count, count),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).testTag("selectedCount"),
        )
        Spacer(Modifier.size(TopBarTokens.PillSize))
    }
}

@Composable
internal fun SelectionBar(count: Int, limited: Boolean, onForward: (() -> Unit)?, modifier: Modifier = Modifier, onReport: (() -> Unit)? = null) {
    Surface(
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (limited) pluralStringResource(R.plurals.chat_selection_limit, MAX_SELECTED, MAX_SELECTED) else pluralStringResource(R.plurals.chat_selected, count, count),
                style = MaterialTheme.typography.titleSmall,
                color = if (limited) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).testTag("selectionStatus"),
            )
            if (onReport != null) {
                FilledTonalIconButton(onClick = onReport, enabled = count > 0, modifier = Modifier.padding(end = 8.dp).testTag("reportSelected"), shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_report), stringResource(R.string.report_action))
                }
            }
            if (onForward != null) {
                FilledTonalButton(onClick = onForward, enabled = count > 0, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 48.dp).testTag("forwardSelected")) {
                    Icon(painterResource(R.drawable.ic_forward), null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.chat_forward))
                }
            }
        }
    }
}

@Composable
internal fun SelectableRow(active: Boolean, selected: Boolean, out: Boolean, selectable: Boolean, onToggle: () -> Unit, tag: String, content: @Composable BoxScope.() -> Unit) {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    val shift by animateDpAsState(
        when {
            !active || !selectable -> 0.dp
            out -> -SELECTION_SHIFT
            else -> SELECTION_SHIFT
        },
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "selectionShift",
    )
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.graphicsLayer { translationX = shift.toPx() * direction }, content = content)
        val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        AnimatedVisibility(
            active && selectable,
            modifier = Modifier.align(if (out) Alignment.CenterEnd else Alignment.CenterStart),
            enter = fadeIn(effects) + scaleIn(spatial),
            exit = fadeOut(effects) + scaleOut(spatial),
        ) {
            Box(Modifier.size(SELECTION_SHIFT), contentAlignment = Alignment.Center) {
                Surface(
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    tonalElevation = 3.dp,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(28.dp),
                ) {
                    if (selected) Icon(painterResource(R.drawable.ic_check), null, Modifier.padding(5.dp))
                }
            }
        }
        if (active && selectable) {
            Box(Modifier.matchParentSize().toggleable(value = selected, interactionSource = null, indication = null, role = Role.Checkbox, onValueChange = { onToggle() }).testTag(tag))
        }
    }
}

private val SELECTION_SHIFT = 44.dp

@Composable
internal fun ForwardSheet(targets: List<DialogItem>, avatars: Map<String, ByteArray>, onPick: (DialogItem, dropAuthor: Boolean, dropCaptions: Boolean) -> Unit, onDismiss: () -> Unit) {
    var dropAuthor by rememberSaveable { mutableStateOf(false) }
    var dropCaptions by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        ForwardPicker(targets, avatars, dropAuthor, dropCaptions, onDropAuthor = { dropAuthor = it }, onDropCaptions = { dropCaptions = it }, onPick = onPick)
    }
}

@Composable
internal fun ForwardPicker(
    targets: List<DialogItem>,
    avatars: Map<String, ByteArray>,
    dropAuthor: Boolean,
    dropCaptions: Boolean,
    onDropAuthor: (Boolean) -> Unit,
    onDropCaptions: (Boolean) -> Unit,
    onPick: (DialogItem, dropAuthor: Boolean, dropCaptions: Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("forwardSheet")) {
        Text(
            stringResource(R.string.chat_forward_to),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        ForwardOptions(dropAuthor, dropCaptions, onDropAuthor, onDropCaptions)
        val saved = stringResource(R.string.saved_messages)
        LazyVerticalGrid(
            GridCells.Adaptive(FORWARD_ITEM_WIDTH),
            modifier = Modifier.fillMaxWidth().testTag("forwardTargets"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(targets, key = { it.key }) { dialog ->
                Column(
                    Modifier
                        .width(FORWARD_ITEM_WIDTH)
                        .clip(MaterialTheme.shapes.large)
                        .clickable { onPick(dialog, dropAuthor || dropCaptions, dropCaptions) }
                        .padding(vertical = 4.dp)
                        .testTag("forwardTo_${dialog.key}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DialogAvatar(dialog, avatars, size = 56)
                    Text(
                        if (dialog.isSaved) saved else dialog.title,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

private val FORWARD_ITEM_WIDTH = 80.dp

@Composable
internal fun ForwardHeader(forward: ForwardInfo, accent: Color, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val link = forward.link
    val open = if (link != null) Modifier.clickable { onOpen(link) } else Modifier
    Column(modifier.then(open).padding(bottom = 4.dp)) {
        Text(stringResource(R.string.chat_forwarded), style = MaterialTheme.typography.labelMedium, color = accent)
        if (forward.name.isNotEmpty()) {
            Text(forward.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ForwardOptions(dropAuthor: Boolean, dropCaptions: Boolean, onDropAuthor: (Boolean) -> Unit, onDropCaptions: (Boolean) -> Unit) {
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    Column {
        ListItem(
            headlineContent = { Text(stringResource(R.string.chat_forward_hide_sender)) },
            trailingContent = { CheckedSwitch(checked = dropAuthor || dropCaptions, onCheckedChange = onDropAuthor, enabled = !dropCaptions, modifier = Modifier.testTag("dropAuthor")) },
            colors = colors,
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.chat_forward_hide_captions)) },
            trailingContent = { CheckedSwitch(checked = dropCaptions, onCheckedChange = onDropCaptions, modifier = Modifier.testTag("dropCaptions")) },
            colors = colors,
        )
    }
}
