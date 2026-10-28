package cn.ellerotta.core.ui.components.chat

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import cn.ellerotta.core.model.ChatWallpaper
import cn.ellerotta.core.model.WallpaperKind
import cn.ellerotta.core.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

@Composable
internal fun ChatBackground(modifier: Modifier = Modifier, wallpaper: ChatWallpaper? = null) {
    if (wallpaper?.wallpaper?.kind == WallpaperKind.Plain) {
        Box(modifier.background(MaterialTheme.colorScheme.surface).testTag("chatPlain"))
        return
    }
    if (wallpaper != null) {
        val motion = wallpaper.wallpaper.motion
        val tilt = rememberTilt(motion)
        Box(modifier.clipToBounds().testTag("chatWallpaper")) {
            WallpaperImage(
                wallpaper.wallpaper, wallpaper.file,
                Modifier.matchParentSize().then(
                    if (motion) {
                        Modifier.graphicsLayer {
                            val shift = parallaxOffset(tilt.value, size)
                            scaleX = PARALLAX_SCALE
                            scaleY = PARALLAX_SCALE
                            translationX = shift.x
                            translationY = shift.y
                        }.testTag("chatWallpaperMotion")
                    } else {
                        Modifier
                    },
                ),
            )
        }
        return
    }
    val colors = MaterialTheme.colorScheme
    val resources = LocalContext.current.resources
    val pattern by produceState(ChatPattern.cached, resources) {
        if (value == null) value = withContext(Dispatchers.Default) { ChatPattern.load(resources) }
    }
    val dark = colors.surface.luminance() < DARK_LUMINANCE
    val tint = colors.onSurface.copy(alpha = if (dark) DARK_PATTERN_ALPHA else LIGHT_PATTERN_ALPHA)
    Box(
        modifier
            .testTag("chatBackground")
            .drawWithCache {
                val glow = glowBrushes(colors, size)
                val layer = Paint().apply { alpha = GLOW_ALPHA }
                onDrawBehind {
                    drawRect(colors.surface)
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(size.toRect(), layer)
                        drawRect(colors.surfaceContainer)
                        glow.forEach { (brush, alpha) -> drawRect(brush, alpha = alpha) }
                        canvas.restore()
                    }
                    pattern?.let { drawPattern(it, tint) }
                }
            },
    )
}

internal val BOTTOM_SCRIM_STOPS = listOf(0f to 0f, 0.4f to 0.6f, 1f to 1f)

private fun glowBrushes(colors: ColorScheme, size: Size): List<Pair<Brush, Float>> {
    val sx = size.width / FRAME_WIDTH
    val sy = size.height / FRAME_HEIGHT
    return listOf(
        ellipse(colors.surfaceContainer, floatArrayOf(0f, 1f), Offset(412f * sx, 892f * sy), 254.078f * sx, 572.879f * sy) to 0.86f,
        ellipse(colors.surfaceContainerHighest, floatArrayOf(0.39354f, 1f), Offset(0f, 892f * sy), 290.628f * sx, 648.128f * sy) to 1f,
        ellipse(colors.surfaceContainerHigh, floatArrayOf(0f, 0.980916f), Offset(0f, 230.69f * sy), 412f * sx, 711.293f * sy) to 0.9f,
    )
}

private fun ellipse(color: Color, stops: FloatArray, center: Offset, radiusX: Float, radiusY: Float): Brush {
    val shader = RadialGradient(0f, 0f, 1f, intArrayOf(color.toArgb(), color.copy(alpha = 0f).toArgb()), stops, Shader.TileMode.CLAMP)
    shader.setLocalMatrix(Matrix().apply {
        setScale(radiusX.coerceAtLeast(1f), radiusY.coerceAtLeast(1f))
        postTranslate(center.x, center.y)
    })
    return ShaderBrush(shader)
}

private fun DrawScope.drawPattern(pattern: ImageBitmap, tint: Color) {
    if (pattern.width == 0 || pattern.height == 0 || size.width <= 0f) return
    val scale = size.width / pattern.width
    val tileHeight = pattern.height * scale
    val filter = ColorFilter.tint(tint.copy(alpha = 1f), BlendMode.SrcIn)
    val tiles = ceil(size.height / tileHeight).toInt().coerceAtLeast(1)
    val width = size.width.toInt()
    val height = ceil(tileHeight).toInt()
    for (tile in 0 until tiles) {
        drawImage(
            pattern,
            dstOffset = IntOffset(0, (tile * tileHeight).toInt()),
            dstSize = IntSize(width, height),
            alpha = tint.alpha,
            colorFilter = filter,
        )
    }
}

private fun Size.toRect() = Rect(Offset.Zero, this)

internal object ChatPattern {
    @Volatile
    var cached: ImageBitmap? = null
        private set

    fun load(resources: Resources): ImageBitmap? = cached ?: synchronized(this) {
        cached ?: runCatching {
            val full = BitmapFactory.decodeResource(resources, R.drawable.core_ui_chat_wallpaper, BitmapFactory.Options().apply { inScaled = false })
            val alpha = full.extractAlpha()
            full.recycle()
            alpha.asImageBitmap()
        }.getOrNull().also { cached = it }
    }
}

private const val FRAME_WIDTH = 412f
private const val FRAME_HEIGHT = 892f
private const val GLOW_ALPHA = 0.23f
private const val LIGHT_PATTERN_ALPHA = 0.15f
private const val DARK_PATTERN_ALPHA = 0.1f
private const val DARK_LUMINANCE = 0.5f
