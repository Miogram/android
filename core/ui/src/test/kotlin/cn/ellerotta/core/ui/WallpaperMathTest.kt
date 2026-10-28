package cn.ellerotta.core.ui

import cn.ellerotta.core.ui.components.chat.FREEFORM_POINTS_3
import cn.ellerotta.core.ui.components.chat.FREEFORM_POINTS_4
import cn.ellerotta.core.ui.components.chat.boxBlur
import cn.ellerotta.core.ui.components.chat.coverScale
import cn.ellerotta.core.ui.components.chat.freeformPixels
import cn.ellerotta.core.ui.components.chat.gradientLine
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WallpaperMathTest {
    @Test
    fun gradientRunsTopToBottomTurnedClockwise() {
        assertLine(floatArrayOf(50f, 0f, 50f, 200f), gradientLine(100f, 200f, 0))
        assertLine(floatArrayOf(100f, 100f, 0f, 100f), gradientLine(100f, 200f, 90))
        assertLine(floatArrayOf(50f, 200f, 50f, 0f), gradientLine(100f, 200f, 180))
        assertLine(floatArrayOf(0f, 100f, 100f, 100f), gradientLine(100f, 200f, 270))
        assertLine(floatArrayOf(100f, 0f, 0f, 100f), gradientLine(100f, 100f, 45))
    }

    @Test
    fun freeformIsEachColourAtItsPointAndBlendsBetween() {
        val colors = listOf(0xff0000, 0x00ff00, 0x0000ff, 0xffff00)
        val size = 100
        val pixels = freeformPixels(colors, size, size)
        FREEFORM_POINTS_4.forEachIndexed { i, (x, y) ->
            val pixel = pixels[(y * size).toInt() * size + (x * size).toInt()]
            assertNear(colors[i], pixel, tolerance = 24)
        }
        assertTrue(pixels.all { it ushr 24 == 0xff })
        val middle = pixels[size / 2 * size + size / 2]
        assertTrue(colors.none { near(it, middle, 24) }, "the middle blends the colours")

        val three = listOf(0x102030, 0x405060, 0x708090)
        val small = freeformPixels(three, 40, 80)
        FREEFORM_POINTS_3.forEachIndexed { i, (x, y) -> assertNear(three[i], small[(y * 80).toInt() * 40 + (x * 40).toInt()], tolerance = 8) }

        assertTrue(freeformPixels(List(4) { 0x336699 }, 10, 10).all { it == 0xff336699.toInt() })
    }

    @Test
    fun boxBlurAveragesAWindowAndKeepsFlatAreas() {
        val flat = IntArray(25) { 0xff808080.toInt() }
        assertTrue(boxBlur(flat, 5, 5, 2).all { it == 0xff808080.toInt() })
        val dot = IntArray(25) { 0xff000000.toInt() }.also { it[12] = 0xffffffff.toInt() }
        val blurred = boxBlur(dot, 5, 5, 1)
        assertEquals(255 / 3 / 3, blurred[12] and 0xff)
        assertEquals(255 / 3 / 3, blurred[6] and 0xff)
        assertEquals(0, blurred[0] and 0xff)
    }

    @Test
    fun coverFillsTheLongerSide() {
        assertEquals(2f, coverScale(100f, 200f, 200f, 300f))
        assertEquals(3f, coverScale(100f, 100f, 200f, 300f))
    }

    private fun assertLine(expected: FloatArray, actual: FloatArray) =
        assertTrue(expected.indices.all { abs(expected[it] - actual[it]) < 0.01f }, "${actual.toList()} is not ${expected.toList()}")

    private fun near(rgb: Int, argb: Int, tolerance: Int) =
        listOf(16, 8, 0).all { shift -> abs((rgb shr shift and 0xff) - (argb shr shift and 0xff)) <= tolerance }

    private fun assertNear(rgb: Int, argb: Int, tolerance: Int) =
        assertTrue(near(rgb, argb, tolerance), "%06x is not near %06x".format(argb and 0xffffff, rgb))
}
