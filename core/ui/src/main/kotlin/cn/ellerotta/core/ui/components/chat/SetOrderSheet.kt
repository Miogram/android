package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.StickerSetInfo
import cn.ellerotta.core.ui.R

@Composable
internal fun SetOrderSheet(sets: List<StickerSetInfo>, stickers: Map<String, ByteArray>, onLoad: (MediaItem) -> Unit, onOrder: (List<String>) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        modifier = Modifier.testTag("setOrderSheet"),
    ) {
        SetOrderContent(sets, stickers, onLoad, onOrder)
    }
}

@Composable
internal fun SetOrderContent(sets: List<StickerSetInfo>, stickers: Map<String, ByteArray>, onLoad: (MediaItem) -> Unit, onOrder: (List<String>) -> Unit) {
    val motion = MaterialTheme.motionScheme
    fun move(from: Int, to: Int) = onOrder(sets.map { it.key }.toMutableList().apply { add(to, removeAt(from)) })
    Column(Modifier.navigationBarsPadding()) {
        Text(
            stringResource(R.string.chat_sets_reorder),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp).semantics { heading() },
        )
        LazyColumn(Modifier.heightIn(max = ORDER_LIST_MAX).testTag("setOrder")) {
            itemsIndexed(sets, key = { _, set -> set.key }) { index, set ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = ORDER_ROW)
                        .padding(start = 16.dp, end = 4.dp)
                        .animateItem(fadeInSpec = motion.fastEffectsSpec(), placementSpec = motion.fastSpatialSpec(), fadeOutSpec = motion.fastEffectsSpec())
                        .testTag("order_${set.key}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(ORDER_THUMB), contentAlignment = Alignment.Center) {
                        val thumb = set.thumb
                        if (thumb != null) {
                            LaunchedEffect(thumb.key) { if (stickers[thumb.key] == null) onLoad(thumb) }
                            StickerView(thumb, stickers[thumb.key], size = ORDER_THUMB, preview = stickers.previewOf(thumb))
                        } else {
                            Text(set.title.take(1), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(set.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { move(index, index - 1) }, enabled = index > 0, modifier = Modifier.testTag("moveUp_${set.key}"), shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_keyboard_arrow_up), stringResource(R.string.chat_sets_move_up, set.title))
                    }
                    IconButton(onClick = { move(index, index + 1) }, enabled = index < sets.lastIndex, modifier = Modifier.testTag("moveDown_${set.key}"), shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_keyboard_arrow_down), stringResource(R.string.chat_sets_move_down, set.title))
                    }
                }
            }
        }
    }
}

private val ORDER_LIST_MAX = 480.dp
private val ORDER_ROW = 56.dp
private val ORDER_THUMB = 36.dp
