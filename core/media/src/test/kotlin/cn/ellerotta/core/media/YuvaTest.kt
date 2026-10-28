package cn.ellerotta.core.media

import java.nio.ByteBuffer
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class YuvaTest {
    private val w = 64
    private val h = 48
    private val raw = javaClass.getResourceAsStream("/webm/alpha_frame2.yuva")!!.use { it.readBytes() }
    private val reference = javaClass.getResourceAsStream("/webm/alpha_frame2.png")!!.use { ImageIO.read(it) }

    private val y = Plane(ByteBuffer.wrap(raw), 0, w, 1)
    private val u = Plane(ByteBuffer.wrap(raw), w * h, w / 2, 1)
    private val v = Plane(ByteBuffer.wrap(raw), w * h * 5 / 4, w / 2, 1)
    private val a = Plane(ByteBuffer.wrap(raw), w * h * 3 / 2, w, 1)

    @Test
    fun matchesTheReferenceFrame() {
        val out = IntArray(w * h)
        yuvaToArgb(w, h, y, u, v, a, out, w, h)
        var transparent = 0
        for (row in 0 until h) for (x in 0 until w) {
            val expected = reference.getRGB(x, row)
            val actual = out[row * w + x]
            assertEquals(expected ushr 24, actual ushr 24, "alpha at $x,$row")
            for (shift in intArrayOf(16, 8, 0)) {
                val diff = abs(((expected shr shift) and 0xff) - ((actual shr shift) and 0xff))
                assertTrue(diff <= 2, "channel $shift at $x,$row differs by $diff")
            }
            if (actual ushr 24 == 0) transparent++
        }
        assertTrue(transparent > w * h / 2)
        assertEquals(0, out[5 * w + 40] ushr 24)
        assertEquals(255, out[24 * w + 28] ushr 24)
        assertEquals(128, out[5 * w + 2] ushr 24)
    }

    @Test
    fun interleavedChromaGivesTheSameFrame() {
        val uv = ByteArray(w * h / 2)
        for (i in 0 until w * h / 4) {
            uv[2 * i] = raw[w * h + i]
            uv[2 * i + 1] = raw[w * h * 5 / 4 + i]
        }
        val planar = IntArray(w * h).also { yuvaToArgb(w, h, y, u, v, a, it, w, h) }
        val interleaved = IntArray(w * h).also {
            yuvaToArgb(w, h, y, Plane(ByteBuffer.wrap(uv), 0, w, 2), Plane(ByteBuffer.wrap(uv), 1, w, 2), a, it, w, h)
        }
        assertTrue(planar.contentEquals(interleaved))
    }

    @Test
    fun withoutAlphaEveryPixelIsOpaque() {
        val out = IntArray(w * h)
        yuvaToArgb(w, h, y, u, v, null, out, w, h)
        assertTrue(out.all { it ushr 24 == 255 })
    }

    @Test
    fun scalingDownBlendsWithoutTheHiddenColor() {
        val out = IntArray(w / 2 * h / 2)
        yuvaToArgb(w, h, y, u, v, a, out, w / 2, h / 2)
        assertEquals(255, out[12 * (w / 2) + 14] ushr 24)
        assertEquals(0, out[2 * (w / 2) + 20] ushr 24)
        assertEquals(128, out[2 * (w / 2) + 1] ushr 24)
        for (row in 0 until h / 2) for (x in 0 until w / 2) {
            val pixel = out[row * (w / 2) + x]
            if (pixel ushr 24 == 0) continue
            val expected = nearestVisible(x * 2 + 1, row * 2 + 1)
            for (shift in intArrayOf(16, 8, 0)) {
                val diff = abs(((expected shr shift) and 0xff) - ((pixel shr shift) and 0xff))
                assertTrue(diff <= 24, "channel $shift at $x,$row differs by $diff")
            }
        }
    }

    private fun nearestVisible(cx: Int, cy: Int): Int {
        for (dy in 0..1) for (dx in 0..1) {
            val x = (cx - dx).coerceIn(0, w - 1)
            val row = (cy - dy).coerceIn(0, h - 1)
            val pixel = reference.getRGB(x, row)
            if (pixel ushr 24 != 0) return pixel
        }
        error("no visible pixel near $cx,$cy")
    }
}
