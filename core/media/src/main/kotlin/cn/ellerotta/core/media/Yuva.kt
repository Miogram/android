package cn.ellerotta.core.media

import java.nio.ByteBuffer

class Plane(var buffer: ByteBuffer = EMPTY, var start: Int = 0, var rowStride: Int = 0, var pixelStride: Int = 1) {
    fun set(buffer: ByteBuffer, start: Int, rowStride: Int, pixelStride: Int): Plane {
        this.buffer = buffer
        this.start = start
        this.rowStride = rowStride
        this.pixelStride = pixelStride
        return this
    }

    internal fun at(x: Int, y: Int): Int = buffer.get(start + y * rowStride + x * pixelStride).toInt() and 0xff

    private companion object {
        val EMPTY: ByteBuffer = ByteBuffer.allocate(0)
    }
}

fun yuvaToArgb(
    width: Int,
    height: Int,
    y: Plane,
    u: Plane,
    v: Plane,
    alpha: Plane?,
    out: IntArray,
    outWidth: Int,
    outHeight: Int,
) {
    require(width > 0 && height > 0 && outWidth > 0 && outHeight > 0) { "empty frame" }
    require(out.size >= outWidth * outHeight) { "output too small" }
    val stepX = (width.toLong() shl 16) / outWidth
    val stepY = (height.toLong() shl 16) / outHeight
    var i = 0
    for (oy in 0 until outHeight) {
        val sy = ((oy * stepY + stepY / 2) - 0x8000).coerceIn(0, (height - 1).toLong() shl 16)
        val y0 = (sy shr 16).toInt()
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fy = (sy and 0xffff).toInt() shr 8
        for (ox in 0 until outWidth) {
            val sx = ((ox * stepX + stepX / 2) - 0x8000).coerceIn(0, (width - 1).toLong() shl 16)
            val x0 = (sx shr 16).toInt()
            val x1 = (x0 + 1).coerceAtMost(width - 1)
            val fx = (sx and 0xffff).toInt() shr 8
            out[i++] = if (fx == 0 && fy == 0) {
                argb(x0, y0, y, u, v, alpha)
            } else {
                blend(
                    argb(x0, y0, y, u, v, alpha), argb(x1, y0, y, u, v, alpha),
                    argb(x0, y1, y, u, v, alpha), argb(x1, y1, y, u, v, alpha),
                    fx, fy,
                )
            }
        }
    }
}

private fun argb(x: Int, row: Int, y: Plane, u: Plane, v: Plane, alpha: Plane?): Int {
    val c = (y.at(x, row) - 16) * 298
    val d = u.at(x shr 1, row shr 1) - 128
    val e = v.at(x shr 1, row shr 1) - 128
    val r = clamp((c + 409 * e + 128) shr 8)
    val g = clamp((c - 100 * d - 208 * e + 128) shr 8)
    val b = clamp((c + 516 * d + 128) shr 8)
    val a = alpha?.at(x, row) ?: 255
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

private fun blend(p00: Int, p10: Int, p01: Int, p11: Int, fx: Int, fy: Int): Int {
    val a00 = (p00 ushr 24).toLong() * ((256 - fx) * (256 - fy))
    val a10 = (p10 ushr 24).toLong() * (fx * (256 - fy))
    val a01 = (p01 ushr 24).toLong() * ((256 - fx) * fy)
    val a11 = (p11 ushr 24).toLong() * (fx * fy)
    val a = a00 + a10 + a01 + a11
    if (a == 0L) return 0
    val outA = ((a + 32768) shr 16).toInt()
    val r = channel(p00 shr 16, p10 shr 16, p01 shr 16, p11 shr 16, a00, a10, a01, a11, a)
    val g = channel(p00 shr 8, p10 shr 8, p01 shr 8, p11 shr 8, a00, a10, a01, a11, a)
    val b = channel(p00, p10, p01, p11, a00, a10, a01, a11, a)
    return (outA shl 24) or (r shl 16) or (g shl 8) or b
}

private fun channel(c00: Int, c10: Int, c01: Int, c11: Int, a00: Long, a10: Long, a01: Long, a11: Long, a: Long): Int =
    (((c00 and 0xff) * a00 + (c10 and 0xff) * a10 + (c01 and 0xff) * a01 + (c11 and 0xff) * a11 + a / 2) / a).toInt()

private fun clamp(value: Int): Int = if (value < 0) 0 else if (value > 255) 255 else value
