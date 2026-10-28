package cn.ellerotta.core.ui.components.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import cn.ellerotta.core.lottie.Lottie
import cn.ellerotta.core.lottie.LottieData
import cn.ellerotta.core.lottie.lottieAvailable
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.STICKER_OUTLINE_BOX
import cn.ellerotta.core.model.StickerFormat
import cn.ellerotta.core.model.stickerFormat
import cn.ellerotta.core.model.stickerPreviewKey
import cn.ellerotta.core.ui.components.common.VideoSticker
import kotlin.math.min

@Composable
fun StickerView(media: MediaItem, bytes: ByteArray?, modifier: Modifier = Modifier, size: Dp = 168.dp, preview: ByteArray? = null) {
    val format = remember(bytes) { bytes?.let(::stickerFormat) }
    val lottie = format == StickerFormat.Lottie || (media.animated && format == StickerFormat.Image)
    val json = remember(bytes) { bytes?.takeIf { lottie }?.let { runCatching { if (LottieData.isGzip(it)) LottieData.fromTgs(it) else it }.getOrNull() } }
    val image = remember(bytes) { bytes?.takeIf { !lottie && format == StickerFormat.Image }?.let(::decodeImage) }
    var visible by remember { mutableStateOf(false) }
    val started = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.STARTED)
    Box(modifier.size(size).testTag("sticker_${media.key}"), contentAlignment = Alignment.Center) {
        when {
            json != null && lottieAvailable -> {
                var framed by remember(json) { mutableStateOf(false) }
                if (!framed) StickerPlaceholder(media, preview, size)
                Lottie(
                    json,
                    Modifier.size(size).onVisibilityChanged(minFractionVisible = 0.1f) { visible = it },
                    playing = visible && started,
                    onFrame = { framed = true },
                )
            }
            format == StickerFormat.Video && bytes != null -> VideoSticker(media.key, bytes, size) { StickerPlaceholder(media, preview, size) }
            image != null -> Image(image, media.emoji, Modifier.size(size))
            else -> StickerPlaceholder(media, preview, size)
        }
    }
}

private fun decodeImage(bytes: ByteArray): ImageBitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

internal fun Map<String, ByteArray>.previewOf(media: MediaItem): ByteArray? = media.stickerPreviewKey?.let(::get)

@Composable
private fun StickerPlaceholder(media: MediaItem, preview: ByteArray?, size: Dp) {
    val still = remember(preview, media.thumb) { listOfNotNull(preview, media.thumb).firstNotNullOfOrNull(::decodeImage) }
    val outline = remember(media.outline) { media.outline?.let(::stickerOutlinePath) }
    when {
        still != null -> Image(still, media.emoji, Modifier.size(size).testTag("stickerPreview_${media.key}"))
        outline != null -> StickerOutline(outline, Modifier.size(size).testTag("stickerOutline_${media.key}"))
        else -> Text(media.emoji ?: "🖼", fontSize = (size.value * 0.43f).sp)
    }
}

@Composable
private fun StickerOutline(path: Path, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = OUTLINE_ALPHA)
    Canvas(modifier) {
        val side = min(this.size.width, this.size.height)
        translate((this.size.width - side) / 2, (this.size.height - side) / 2) {
            scale(side / STICKER_OUTLINE_BOX, pivot = Offset.Zero) {
                drawPath(path, color)
            }
        }
    }
}

fun stickerOutlinePath(outline: String): Path? = runCatching { PathParser().parsePathString(outline).toPath() }.getOrNull()

private const val OUTLINE_ALPHA = 0.12f
