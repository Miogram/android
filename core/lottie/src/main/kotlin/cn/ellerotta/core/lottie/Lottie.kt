package cn.ellerotta.core.lottie

import android.graphics.Bitmap
import androidx.annotation.RawRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val renderDispatcher = Dispatchers.Default.limitedParallelism(2)

private const val MAX_RENDER_SIDE = 2048

val lottieAvailable: Boolean by lazy { runCatching { LottieNative.hashCode() }.isSuccess }

@Composable
fun Lottie(
    data: ByteArray?,
    modifier: Modifier = Modifier,
    layerColors: Map<String, Color> = emptyMap(),
    playing: Boolean = true,
    repeat: Boolean = true,
    speed: Float = 1f,
    crop: Boolean = false,
    resolution: Float = 1f,
    onFrame: ((Float) -> Unit)? = null,
) {
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    var front by remember { mutableStateOf<Bitmap?>(null) }
    var drawSize by remember { mutableStateOf(IntSize.Zero) }
    var generation by remember { mutableIntStateOf(0) }
    val isPlaying by rememberUpdatedState(playing)
    val frameCallback by rememberUpdatedState(onFrame)
    val colors = layerColors.mapValues { it.value.toArgb() }

    LaunchedEffect(data, colors, bounds, crop, repeat, speed, resolution) {
        if (data == null || bounds.width <= 0 || bounds.height <= 0 || !lottieAvailable) return@LaunchedEffect
        val animation = withContext(renderDispatcher) { LottieAnimation.load(data, colors) } ?: return@LaunchedEffect
        try {
            val sx = bounds.width / animation.width.coerceAtLeast(1).toFloat()
            val sy = bounds.height / animation.height.coerceAtLeast(1).toFloat()
            val scale = if (crop) max(sx, sy) else min(sx, sy)
            val drawn = IntSize((animation.width * scale).roundToInt().coerceAtLeast(1), (animation.height * scale).roundToInt().coerceAtLeast(1))
            val renderScale = min(resolution, MAX_RENDER_SIDE / max(drawn.width, drawn.height).toFloat()).coerceAtMost(1f)
            val w = (drawn.width * renderScale).roundToInt().coerceAtLeast(1)
            val h = (drawn.height * renderScale).roundToInt().coerceAtLeast(1)
            val bitmaps = Array(2) { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }
            var back = 0
            var played = 0f
            var last = withFrameNanos { it }
            while (true) {
                if (!isPlaying) {
                    snapshotFlow { isPlaying }.first { it }
                    last = withFrameNanos { it }
                }
                val now = withFrameNanos { it }
                played += (now - last) / 1e9f * speed
                last = now
                val total = animation.frameCount.toFloat()
                val raw = played * animation.frameRate
                val frame = if (repeat) raw % total else min(raw, total - 1)
                val target = bitmaps[back]
                val rendered = withContext(renderDispatcher) {
                    animation.render(frame, w, h).also { if (it) target.copyPixelsFromBuffer(animation.pixels.rewind()) }
                }
                if (rendered) {
                    front = target
                    drawSize = drawn
                    back = 1 - back
                    generation++
                }
                frameCallback?.invoke(frame)
                if (!repeat && raw >= total - 1) break
            }
        } finally {
            withContext(NonCancellable + renderDispatcher) { animation.close() }
        }
    }

    Canvas(modifier.onSizeChanged { bounds = it }) {
        val bitmap = front
        if (generation >= 0 && bitmap != null) {
            val offset = IntOffset((size.width.roundToInt() - drawSize.width) / 2, (size.height.roundToInt() - drawSize.height) / 2)
            drawImage(bitmap.asImageBitmap(), dstOffset = offset, dstSize = drawSize)
        }
    }
}

@Composable
fun rememberLottieResource(@RawRes id: Int): ByteArray? {
    val context = LocalContext.current
    val data by produceState<ByteArray?>(null, id) {
        value = withContext(Dispatchers.IO) { context.resources.openRawResource(id).use { it.readBytes() } }
    }
    return data
}
