package app.miogram.push

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import cn.ellerotta.core.ui.components.common.avatarPalette
import cn.ellerotta.core.ui.components.common.initials

internal const val PLACEHOLDER_PHOTO = 192

internal fun roundPhoto(bytes: ByteArray): Bitmap? {
    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val size = minOf(source.width, source.height).takeIf { it > 0 } ?: return null
    val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    shader.setLocalMatrix(Matrix().apply { setTranslate((size - source.width) / 2f, (size - source.height) / 2f) })
    return circle(size) { shader }
}

internal fun placeholderPhoto(name: String, key: String, size: Int = PLACEHOLDER_PHOTO): Bitmap {
    val palette = avatarPalette(key.trimStart { !it.isDigit() && it != '-' }.toLongOrNull() ?: key.hashCode().toLong())
    val photo = circle(size) { LinearGradient(0f, 0f, 0f, size.toFloat(), palette.top.toArgb(), palette.bottom.toArgb(), Shader.TileMode.CLAMP) }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.content.toArgb()
        textSize = size * 0.4f
        textAlign = Paint.Align.CENTER
    }
    val baseline = size / 2f - (text.descent() + text.ascent()) / 2f
    Canvas(photo).drawText(initials(name), size / 2f, baseline, text)
    return photo
}

private inline fun circle(size: Int, fill: () -> Shader): Bitmap {
    val photo = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = fill() }
    Canvas(photo).drawCircle(size / 2f, size / 2f, size / 2f, paint)
    return photo
}
