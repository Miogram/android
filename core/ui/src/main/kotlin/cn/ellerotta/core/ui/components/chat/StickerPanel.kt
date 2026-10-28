package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.StickerSetDetail
import cn.ellerotta.core.ui.R

@Composable
internal fun StickerGrid(list: List<MediaItem>, stickers: Map<String, ByteArray>, onLoadSticker: (MediaItem) -> Unit, onPick: ((MediaItem) -> Unit)?, modifier: Modifier = Modifier) {
    var previewed by remember { mutableStateOf<MediaItem?>(null) }
    val previewLabel = stringResource(R.string.chat_preview)
    LazyVerticalGrid(GridCells.Adaptive(CELL), modifier.testTag("stickerGrid"), contentPadding = PaddingValues(8.dp)) {
        items(list, key = { it.key }) { sticker ->
            LaunchedEffect(sticker.key) { if (stickers[sticker.key] == null) onLoadSticker(sticker) }
            Box(
                Modifier
                    .padding(4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(onLongClickLabel = previewLabel, onLongClick = { previewed = sticker }, onClick = { onPick?.invoke(sticker) })
                    .testTag("pick_${sticker.key}"),
                contentAlignment = Alignment.Center,
            ) {
                StickerView(sticker, stickers[sticker.key], size = CELL - 8.dp, preview = stickers.previewOf(sticker))
            }
        }
    }
    previewed?.let { sticker ->
        MediaPreviewDialog(sticker, stickers, emptyMap(), onSend = onPick?.let { pick -> { pick(sticker) } }, onShowSet = null, onDismiss = { previewed = null })
    }
}

@Composable
fun StickerSuggestions(list: List<MediaItem>, stickers: Map<String, ByteArray>, onLoadSticker: (MediaItem) -> Unit, onSend: (MediaItem) -> Unit) {
    var previewed by remember { mutableStateOf<MediaItem?>(null) }
    val previewLabel = stringResource(R.string.chat_preview)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().testTag("stickerSuggestions")) {
        LazyRow(contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(list, key = { it.key }) { sticker ->
                LaunchedEffect(sticker.key) { if (stickers[sticker.key] == null) onLoadSticker(sticker) }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(onLongClickLabel = previewLabel, onLongClick = { previewed = sticker }, onClick = { onSend(sticker) })
                        .testTag("suggest_${sticker.key}"),
                ) {
                    StickerView(sticker, stickers[sticker.key], size = 64.dp, preview = stickers.previewOf(sticker))
                }
            }
        }
    }
    previewed?.let { sticker ->
        MediaPreviewDialog(sticker, stickers, emptyMap(), onSend = { onSend(sticker) }, onShowSet = null, onDismiss = { previewed = null })
    }
}

@Composable
fun StickerSetSheet(
    detail: StickerSetDetail,
    stickers: Map<String, ByteArray>,
    onLoadSticker: (MediaItem) -> Unit,
    onSend: ((MediaItem) -> Unit)?,
    onInstall: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)), modifier = Modifier.testTag("stickerSetSheet")) {
        StickerSetContent(detail, stickers, onLoadSticker, onSend, onInstall)
    }
}

@Composable
internal fun StickerSetContent(detail: StickerSetDetail, stickers: Map<String, ByteArray>, onLoadSticker: (MediaItem) -> Unit, onSend: ((MediaItem) -> Unit)?, onInstall: (Boolean) -> Unit) {
    Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
        Text(detail.info.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            pluralStringResource(R.plurals.stickers_count, detail.info.count, detail.info.count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StickerGrid(detail.stickers, stickers, onLoadSticker, onSend, Modifier.heightIn(max = 360.dp).padding(vertical = 8.dp))
        if (detail.info.installed) {
            FilledTonalButton(onClick = { onInstall(false) }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("removeSet"), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.stickers_remove))
            }
        } else {
            Button(onClick = { onInstall(true) }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("addSet"), shapes = ButtonDefaults.shapes()) {
                Text(pluralStringResource(R.plurals.stickers_add, detail.info.count, detail.info.count))
            }
        }
    }
}

private val CELL = 80.dp
