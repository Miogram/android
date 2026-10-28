package cn.ellerotta.core.ui.components.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import cn.ellerotta.core.model.Wallpaper
import cn.ellerotta.core.model.WallpaperKind
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun WallpaperImage(wallpaper: Wallpaper, file: String?, modifier: Modifier = Modifier, maxSide: Int = MAX_SIDE) {
    if (wallpaper.kind == WallpaperKind.Plain) {
        Box(modifier.background(MaterialTheme.colorScheme.surface))
        return
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val bitmap by produceState<ImageBitmap?>(WallpaperRenders.cached(wallpaper, file, size, maxSide), wallpaper, file, size, maxSide) {
        if (value == null && size != IntSize.Zero) value = withContext(Dispatchers.Default) { WallpaperRenders.render(wallpaper, file, size, maxSide) }
    }
    val placeholder = Color(0xff000000.toInt() or (wallpaper.colors.firstOrNull() ?: if (wallpaper.kind == WallpaperKind.Image) 0x808080 else DEFAULT_FILL))
    Box(
        modifier
            .onSizeChanged { size = it }
            .drawBehind {
                val image = bitmap
                if (image == null) {
                    drawRect(placeholder)
                } else {
                    drawImage(image, dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()), filterQuality = FilterQuality.Low)
                }
            },
    )
}

internal object WallpaperRenders {
    private val renders = object : LruCache<String, ImageBitmap>(RENDER_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val patterns = LruCache<String, TgvImage>(PATTERN_CACHE)

    private fun keyOf(wallpaper: Wallpaper, file: String?, size: IntSize, maxSide: Int) = "$wallpaper|$file|${size.width}x${size.height}|$maxSide"

    fun cached(wallpaper: Wallpaper, file: String?, size: IntSize, maxSide: Int): ImageBitmap? =
        if (size == IntSize.Zero) null else renders.get(keyOf(wallpaper, file, size, maxSide))

    fun renderCount(): Int = renders.putCount()

    fun clear() {
        renders.evictAll()
        patterns.evictAll()
    }

    fun render(wallpaper: Wallpaper, file: String?, size: IntSize, maxSide: Int): ImageBitmap? {
        val key = keyOf(wallpaper, file, size, maxSide)
        renders.get(key)?.let { return it }
        val scale = min(1f, maxSide.toFloat() / max(size.width, size.height))
        val width = (size.width * scale).roundToInt().coerceAtLeast(1)
        val height = (size.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = runCatching { renderWallpaper(wallpaper, file?.let(::File), width, height) }.getOrNull() ?: return null
        return bitmap.asImageBitmap().also { renders.put(key, it) }
    }

    fun pattern(file: File): TgvImage? {
        patterns.get(file.path)?.let { return it }
        val bytes = file.readBytes()
        if (!Tgv.isGzip(bytes) && bytes.firstOrNull() != '<'.code.toByte()) return null
        return Tgv.parse(bytes).also { patterns.put(file.path, it) }
    }
}

internal fun renderWallpaper(wallpaper: Wallpaper, file: File?, width: Int, height: Int): Bitmap {
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val readable = file?.takeIf { it.isFile }
    when (wallpaper.kind) {
        WallpaperKind.Fill -> drawFill(canvas, wallpaper, width, height)
        WallpaperKind.Image -> {
            val image = readable?.let { decodeCover(it, width, height, wallpaper.blur) }
            if (image == null) {
                drawFill(canvas, wallpaper, width, height)
            } else {
                drawCover(canvas, image, width, height)
                image.recycle()
            }
        }
        WallpaperKind.Pattern -> {
            val mask = readable?.let { patternMask(it, width, height) }
            val intensity = wallpaper.intensity.coerceIn(-100, 100)
            if (intensity >= 0) {
                drawFill(canvas, wallpaper, width, height)
                if (mask != null) canvas.drawBitmap(mask, 0f, 0f, Paint().apply { alpha = intensity * 255 / 100 })
            } else {
                canvas.drawColor(android.graphics.Color.BLACK)
                if (mask != null) {
                    val layer = canvas.saveLayerAlpha(RectF(0f, 0f, width.toFloat(), height.toFloat()), abs(intensity) * 255 / 100)
                    drawFill(canvas, wallpaper, width, height)
                    canvas.drawBitmap(mask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
                    canvas.restoreToCount(layer)
                }
            }
            mask?.recycle()
        }
        WallpaperKind.Plain -> Unit
    }
    return out
}

private fun drawFill(canvas: Canvas, wallpaper: Wallpaper, width: Int, height: Int) {
    val colors = wallpaper.colors.map { 0xff000000.toInt() or (it and 0xffffff) }
    when (colors.size) {
        0 -> canvas.drawColor(0xff000000.toInt() or DEFAULT_FILL)
        1 -> canvas.drawColor(colors[0])
        2 -> {
            val (x0, y0, x1, y1) = gradientLine(width.toFloat(), height.toFloat(), wallpaper.rotation).toList()
            val paint = Paint().apply { shader = LinearGradient(x0, y0, x1, y1, colors[0], colors[1], Shader.TileMode.CLAMP) }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        else -> {
            val w = FREEFORM_SIDE
            val h = (FREEFORM_SIDE * height / width.coerceAtLeast(1)).coerceIn(1, FREEFORM_SIDE * 4)
            val small = Bitmap.createBitmap(freeformPixels(colors.take(4).map { it and 0xffffff }, w, h), w, h, Bitmap.Config.ARGB_8888)
            canvas.drawBitmap(small, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
            small.recycle()
        }
    }
}

private fun patternMask(file: File, width: Int, height: Int): Bitmap? {
    val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(mask)
    val vector = runCatching { WallpaperRenders.pattern(file) }.getOrNull()
    if (vector != null) {
        val scale = coverScale(vector.width, vector.height, width.toFloat(), height.toFloat())
        vector.draw(canvas, scale, (width - vector.width * scale) / 2f, (height - vector.height * scale) / 2f)
        return mask
    }
    val image = decodeCover(file, width, height, blur = false)
    if (image == null) {
        mask.recycle()
        return null
    }
    val alpha = image.extractAlpha()
    image.recycle()
    drawCover(canvas, alpha, width, height, Paint(Paint.FILTER_BITMAP_FLAG).apply { color = android.graphics.Color.BLACK })
    alpha.recycle()
    return mask
}

private fun decodeCover(file: File, width: Int, height: Int, blur: Boolean): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val wantWidth = if (blur) BLUR_FIT else width
    val wantHeight = if (blur) BLUR_FIT else height
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= wantWidth && bounds.outHeight / (sample * 2) >= wantHeight) sample *= 2
    val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    if (!blur) return decoded
    val fit = min(1f, BLUR_FIT.toFloat() / max(decoded.width, decoded.height))
    val w = (decoded.width * fit).roundToInt().coerceAtLeast(1)
    val h = (decoded.height * fit).roundToInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(decoded, w, h, true)
    if (small !== decoded) decoded.recycle()
    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)
    small.recycle()
    return Bitmap.createBitmap(boxBlur(pixels, w, h, BLUR_RADIUS), w, h, Bitmap.Config.ARGB_8888)
}

private fun drawCover(canvas: Canvas, image: Bitmap, width: Int, height: Int, paint: Paint = Paint(Paint.FILTER_BITMAP_FLAG)) {
    val scale = coverScale(image.width.toFloat(), image.height.toFloat(), width.toFloat(), height.toFloat())
    val w = image.width * scale
    val h = image.height * scale
    canvas.drawBitmap(image, null, RectF((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f), paint)
}

private const val DEFAULT_FILL = 0xffffff
private const val MAX_SIDE = 1600
private const val FREEFORM_SIDE = 64
private const val BLUR_FIT = 450
private const val BLUR_RADIUS = 12
private const val RENDER_CACHE_BYTES = 32 * 1024 * 1024
private const val PATTERN_CACHE = 4
