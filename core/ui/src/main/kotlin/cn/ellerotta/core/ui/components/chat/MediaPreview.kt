package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.preview
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.LoopingVideo

@Composable
internal fun MediaPreviewDialog(
    media: MediaItem,
    stickers: Map<String, ByteArray>,
    downloads: Map<String, DownloadState>,
    onSend: (() -> Unit)?,
    onShowSet: (() -> Unit)?,
    onDismiss: () -> Unit,
    faved: Boolean = false,
    onFave: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        val inspection = LocalInspectionMode.current
        val scale = remember { Animatable(if (inspection) 1f else PREVIEW_START_SCALE) }
        val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        LaunchedEffect(Unit) { scale.animateTo(1f, spec) }
        fun closing(action: (() -> Unit)?) = action?.let { act -> { onDismiss(); act() } }
        MediaPreviewContent(
            media,
            stickers,
            downloads,
            onSend = closing(onSend),
            onShowSet = closing(onShowSet),
            faved = faved,
            onFave = closing(onFave),
            onRemove = closing(onRemove),
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        )
    }
}

@Composable
internal fun MediaPreviewContent(
    media: MediaItem,
    stickers: Map<String, ByteArray>,
    downloads: Map<String, DownloadState>,
    onSend: (() -> Unit)?,
    onShowSet: (() -> Unit)?,
    modifier: Modifier = Modifier,
    faved: Boolean = false,
    onFave: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.testTag("mediaPreview"),
    ) {
        Column(Modifier.padding(PREVIEW_PADDING), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PREVIEW_GAP)) {
            if (media.kind == MediaKind.Gif) {
                GifPreview(media, downloads)
            } else {
                media.emoji?.let { Text(it, style = MaterialTheme.typography.headlineMedium) }
                StickerView(media, stickers[media.key], size = PREVIEW_SIDE, preview = stickers.previewOf(media))
            }
            if (onSend != null || onShowSet != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(PREVIEW_BUTTON_GAP)) {
                    onShowSet?.let { FilledTonalButton(onClick = it, modifier = Modifier.testTag("previewSet"), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.chat_preview_set)) } }
                    onSend?.let { Button(onClick = it, modifier = Modifier.testTag("previewSend"), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.chat_preview_send)) } }
                }
            }
            onFave?.let {
                TextButton(onClick = it, modifier = Modifier.testTag("previewFave"), shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(if (faved) R.string.chat_preview_unfave else R.string.chat_preview_fave))
                }
            }
            onRemove?.let {
                TextButton(onClick = it, modifier = Modifier.testTag("previewRemove"), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.chat_preview_remove_gif)) }
            }
        }
    }
}

@Composable
private fun GifPreview(gif: MediaItem, downloads: Map<String, DownloadState>) {
    val ratio = if (gif.width > 0 && gif.height > 0) gif.width.toFloat() / gif.height else 1f
    val width = if (ratio >= 1f) PREVIEW_SIDE else PREVIEW_SIDE * ratio
    val height = if (ratio >= 1f) PREVIEW_SIDE / ratio else PREVIEW_SIDE
    val px = with(LocalDensity.current) { PREVIEW_SIDE.roundToPx() }
    val thumb = remember(gif, px) { gif.preview(px) }
    val file = (downloads[gif.key] as? DownloadState.Done)?.file
    Box(Modifier.size(width, height).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceVariant)) {
        StillImage(gif, thumb?.let { downloads[it.key] })
        if (file != null) LoopingVideo(file, Modifier.fillMaxSize())
    }
}

private const val PREVIEW_START_SCALE = 0.8f
private val PREVIEW_SIDE = 240.dp
private val PREVIEW_PADDING = 20.dp
private val PREVIEW_GAP = 16.dp
private val PREVIEW_BUTTON_GAP = 8.dp
