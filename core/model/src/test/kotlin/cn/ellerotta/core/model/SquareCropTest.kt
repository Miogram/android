package cn.ellerotta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SquareCropTest {
    @Test
    fun startsAsTheCentredShortSide() {
        assertEquals(CropRect(left = 100, top = 0, side = 200), SquareCrop(400, 200).rect())
        assertEquals(CropRect(left = 0, top = 50, side = 100), SquareCrop(100, 200).rect())
        assertEquals(CropRect(0, 0, 300), SquareCrop(300, 300).rect())
    }

    @Test
    fun zoomShrinksTheWindowAroundItsCentre() {
        val zoomed = SquareCrop(400, 200).zoomed(2f)
        assertEquals(100f, zoomed.side)
        assertEquals(CropRect(left = 150, top = 50, side = 100), zoomed.rect())
        assertEquals(1f, SquareCrop(400, 200).zoomed(0.2f).zoom)
        assertEquals(SquareCrop.MAX_ZOOM, SquareCrop(400, 200).zoomed(100f).zoom)
    }

    @Test
    fun draggingMovesTheImageUnderTheFinger() {
        val dragged = SquareCrop(800, 200).panned(dx = -10f, dy = 0f, viewSize = 100f)
        assertEquals(420f, dragged.centerX)
        assertEquals(CropRect(left = 320, top = 0, side = 200), dragged.rect())
    }

    @Test
    fun theWindowNeverLeavesTheImage() {
        val far = SquareCrop(800, 200).panned(dx = 10_000f, dy = 10_000f, viewSize = 100f)
        assertEquals(CropRect(left = 0, top = 0, side = 200), far.rect())
        val other = SquareCrop(800, 200).panned(dx = -10_000f, dy = -10_000f, viewSize = 100f)
        assertEquals(CropRect(left = 600, top = 0, side = 200), other.rect())
        val edge = SquareCrop(800, 200, zoom = 4f, centerX = 25f).zoomed(1f)
        assertEquals(CropRect(left = 0, top = 0, side = 200), edge.rect())
        assertEquals(SquareCrop(800, 200), SquareCrop(800, 200).panned(5f, 5f, 0f))
    }

    @Test
    fun theRectIsAlwaysInsideAndSquare() {
        for (zoom in listOf(1f, 1.7f, 3.3f, 8f)) for (dx in listOf(-5000f, -3f, 0f, 41f, 5000f)) {
            val rect = SquareCrop(1234, 777).zoomed(zoom).panned(dx, -dx, 300f).rect()
            assertTrue(rect.side in 1..777 && rect.left >= 0 && rect.top >= 0 && rect.left + rect.side <= 1234 && rect.top + rect.side <= 777, "$zoom $dx $rect")
        }
    }
}
