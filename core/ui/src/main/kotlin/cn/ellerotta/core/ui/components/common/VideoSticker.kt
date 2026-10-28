package cn.ellerotta.core.ui.components.common

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import cn.ellerotta.core.media.Webm
import cn.ellerotta.core.media.WebmVideo
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

const val MAX_PLAYING_VIDEO_STICKERS = 6

private const val FIRST_FRAMES_BYTES = 16 * 1024 * 1024

internal val videoStickerSlots = PlaybackSlots(MAX_PLAYING_VIDEO_STICKERS)

private val firstFrames = object : LruCache<String, Bitmap>(FIRST_FRAMES_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private val decodeDispatcher = Dispatchers.IO.limitedParallelism(MAX_PLAYING_VIDEO_STICKERS)

private val firstFrameDispatcher = Dispatchers.IO.limitedParallelism(1)

@Composable
fun VideoSticker(key: String, bytes: ByteArray, size: Dp, modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
    val side = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    var video by remember(bytes) { mutableStateOf<WebmVideo?>(null) }
    var failed by remember(bytes) { mutableStateOf(false) }
    var playable by remember(bytes) { mutableStateOf(true) }
    LaunchedEffect(bytes) {
        val parsed = withContext(Dispatchers.Default) { runCatching { Webm.parse(bytes) }.getOrNull() }
        if (parsed == null) failed = true else video = parsed
    }
    val current = video
    val frameSize = remember(current, side) { current?.let { fit(it.width, it.height, side) } }
    val cacheKey = frameSize?.let { "$key@${it.width}x${it.height}" }
    var first by remember(cacheKey) { mutableStateOf(cacheKey?.let { firstFrames.get(it) }) }
    val firstImage = remember(first) { first?.asImageBitmap() }
    var front by remember(cacheKey) { mutableStateOf<ImageBitmap?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }
    val started = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.STARTED)
    val active = visible && started && playable

    LaunchedEffect(current, cacheKey, active) {
        if (current == null || frameSize == null || cacheKey == null || failed) return@LaunchedEffect
        suspend fun ensureFirst() {
            if (first != null) return
            val bitmap = withContext(firstFrameDispatcher) { runCatching { decodeFirstFrame(current, frameSize) }.getOrNull() }
            if (bitmap == null) {
                failed = true
            } else {
                firstFrames.put(cacheKey, bitmap)
                first = bitmap
            }
        }
        if (!active) {
            ensureFirst()
            return@LaunchedEffect
        }
        try {
            videoStickerSlots.play(waiting = { ensureFirst() }) {
                if (failed) return@play
                val w = frameSize.width
                val h = frameSize.height
                val decoder = withContext(decodeDispatcher) { VideoStickerDecoder(current, w, h) }
                try {
                    val bitmaps = Array(2) { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }
                    val images = Array(2) { bitmaps[it].asImageBitmap() }
                    val frames = current.frames
                    var back = 0
                    var previous = -1
                    var loopStart = 0L
                    var skipped = 0
                    while (true) {
                        val expected = (previous + 1) % frames.size
                        val expectedStart = if (expected <= previous) loopStart + current.durationMs else loopStart
                        val late = previous >= 0 && skipped < 2 &&
                            SystemClock.uptimeMillis() > expectedStart + frames[expected].timestampMs + current.frameDurationMs
                        val target = bitmaps[back]
                        val index = withContext(decodeDispatcher) {
                            decoder.next(convert = !late).also { if (!late) target.setPixels(decoder.pixels, 0, w, 0, 0, w, h) }
                        }
                        val now = SystemClock.uptimeMillis()
                        if (previous < 0) loopStart = now - frames[index].timestampMs else if (index <= previous) loopStart += current.durationMs
                        previous = index
                        if (late) {
                            skipped++
                            continue
                        }
                        skipped = 0
                        if (first == null && index == 0) {
                            target.copy(Bitmap.Config.ARGB_8888, false)?.let {
                                firstFrames.put(cacheKey, it)
                                first = it
                            }
                        }
                        val wait = loopStart + frames[index].timestampMs - now
                        if (wait > 0) delay(wait)
                        if (wait < -current.durationMs) loopStart = now - frames[index].timestampMs
                        front = images[back]
                        generation++
                        back = 1 - back
                    }
                } finally {
                    front = null
                    withContext(NonCancellable + decodeDispatcher) { decoder.close() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            playable = false
            if (first == null) failed = true
        }
    }

    Box(modifier.size(size).onVisibilityChanged(minFractionVisible = 0.1f) { visible = it }, contentAlignment = Alignment.Center) {
        val image = front ?: firstImage
        if (image == null) {
            fallback()
        } else {
            Canvas(Modifier.matchParentSize()) {
                if (generation >= 0) {
                    val scale = min(this.size.width / image.width, this.size.height / image.height)
                    val drawn = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt())
                    val offset = IntOffset((this.size.width.roundToInt() - drawn.width) / 2, (this.size.height.roundToInt() - drawn.height) / 2)
                    drawImage(image, dstOffset = offset, dstSize = drawn)
                }
            }
        }
    }
}

private fun fit(width: Int, height: Int, side: Int): IntSize {
    val scale = min(1f, side / max(width, height).toFloat())
    return IntSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
}

private fun decodeFirstFrame(video: WebmVideo, size: IntSize): Bitmap =
    VideoStickerDecoder(video, size.width, size.height).use { decoder ->
        check(decoder.next(convert = true) == 0) { "the first output is not the first frame" }
        Bitmap.createBitmap(decoder.pixels, size.width, size.height, Bitmap.Config.ARGB_8888)
    }
