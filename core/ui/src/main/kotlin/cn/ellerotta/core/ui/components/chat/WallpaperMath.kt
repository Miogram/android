package cn.ellerotta.core.ui.components.chat

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun gradientLine(width: Float, height: Float, rotation: Int): FloatArray {
    val angle = Math.toRadians(rotation.toDouble())
    val dx = (-sin(angle)).toFloat()
    val dy = cos(angle).toFloat()
    val half = (abs(dx) * width + abs(dy) * height) / 2f
    val cx = width / 2f
    val cy = height / 2f
    return floatArrayOf(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half)
}

internal val FREEFORM_POINTS_3 = listOf(0.2f to 0.15f, 0.85f to 0.45f, 0.3f to 0.9f)
internal val FREEFORM_POINTS_4 = listOf(0.2f to 0.1f, 0.85f to 0.3f, 0.75f to 0.9f, 0.15f to 0.65f)

internal fun freeformPixels(colors: List<Int>, width: Int, height: Int): IntArray {
    require(colors.size in 3..4) { "a freeform gradient has 3 or 4 colours" }
    val points = if (colors.size == 3) FREEFORM_POINTS_3 else FREEFORM_POINTS_4
    val reds = FloatArray(colors.size) { (colors[it] shr 16 and 0xff).toFloat() }
    val greens = FloatArray(colors.size) { (colors[it] shr 8 and 0xff).toFloat() }
    val blues = FloatArray(colors.size) { (colors[it] and 0xff).toFloat() }
    val out = IntArray(width * height)
    for (y in 0 until height) {
        val fy = (y + 0.5f) / height
        for (x in 0 until width) {
            val fx = (x + 0.5f) / width
            var sum = 0f
            var r = 0f
            var g = 0f
            var b = 0f
            for (i in points.indices) {
                val ddx = fx - points[i].first
                val ddy = fy - points[i].second
                val near = ddx * ddx + ddy * ddy + FREEFORM_SOFTNESS
                val weight = 1f / (near * near)
                sum += weight
                r += weight * reds[i]
                g += weight * greens[i]
                b += weight * blues[i]
            }
            out[y * width + x] = (0xff shl 24) or (channel(r / sum) shl 16) or (channel(g / sum) shl 8) or channel(b / sum)
        }
    }
    return out
}

private const val FREEFORM_SOFTNESS = 0.02f

private fun channel(value: Float) = value.roundToInt().coerceIn(0, 255)

internal fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
    if (radius <= 0 || width == 0 || height == 0) return pixels.copyOf()
    val pass = boxPass(pixels, width, height, radius, horizontal = true)
    return boxPass(pass, width, height, radius, horizontal = false)
}

private fun boxPass(source: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean): IntArray {
    val out = IntArray(source.size)
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val window = 2 * radius + 1
    for (line in 0 until lines) {
        fun at(i: Int): Int {
            val clamped = i.coerceIn(0, length - 1)
            return if (horizontal) source[line * width + clamped] else source[clamped * width + line]
        }
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val c = at(i)
            a += c ushr 24
            r += c shr 16 and 0xff
            g += c shr 8 and 0xff
            b += c and 0xff
        }
        for (i in 0 until length) {
            val index = if (horizontal) line * width + i else i * width + line
            out[index] = (a / window shl 24) or (r / window shl 16) or (g / window shl 8) or (b / window)
            val gone = at(i - radius)
            val next = at(i + radius + 1)
            a += (next ushr 24) - (gone ushr 24)
            r += (next shr 16 and 0xff) - (gone shr 16 and 0xff)
            g += (next shr 8 and 0xff) - (gone shr 8 and 0xff)
            b += (next and 0xff) - (gone and 0xff)
        }
    }
    return out
}

internal fun coverScale(contentWidth: Float, contentHeight: Float, width: Float, height: Float): Float =
    if (contentWidth <= 0f || contentHeight <= 0f) 1f else max(width / contentWidth, height / contentHeight)
