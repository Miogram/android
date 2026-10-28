package cn.ellerotta.core.lottie

import org.junit.Assume.assumeTrue
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LottieAnimationTest {
    private val welcome = javaClass.getResource("/welcome.json")!!.readBytes()

    @Before
    fun needsHostLibrary() = assumeTrue("host tlottie library not built", System.getProperty("ktlottie.library") != null)

    private fun LottieAnimation.pixelArray(): IntArray {
        val ints = IntArray(pixels.capacity() / 4)
        pixels.rewind()
        pixels.asIntBuffer().get(ints)
        return ints
    }

    @Test
    fun rendersTheWelcomeAnimation() {
        LottieAnimation.load(welcome)!!.use { animation ->
            assertEquals(4096, animation.width)
            assertEquals(4096, animation.height)
            assertEquals(540, animation.frameCount)
            assertEquals(60f, animation.frameRate)
            assertEquals(9f, animation.duration)
            assertTrue(animation.render(0f, 256, 256))
            val first = animation.pixelArray()
            assertEquals(256 * 256, first.size)
            assertTrue(first.count { it ushr 24 != 0 } > 100, "frame 0 draws something")
            assertTrue(animation.render(120f, 256, 256))
            assertFalse(first.contentEquals(animation.pixelArray()), "the animation moves")
            assertTrue(animation.render(120f, 64, 32))
            assertEquals(64 * 32, animation.pixelArray().size)
        }
    }

    @Test
    fun layerColorsReachThePixels() {
        val red = mapOf(".primaryContainer" to 0xFFFF0000.toInt(), ".secondaryContainer" to 0xFFFF0000.toInt())
        LottieAnimation.load(welcome, red)!!.use { animation ->
            assertTrue(animation.render(60f, 128, 128))
            val drawn = animation.pixelArray().filter { it ushr 24 != 0 }
            assertTrue(drawn.isNotEmpty())
            assertTrue(drawn.all { it and 0xff == it ushr 24 && it shr 8 and 0xffff == 0 })
        }
    }

    @Test
    fun badInputIsRejected() {
        assertNull(LottieAnimation.load("not json".encodeToByteArray()))
        assertNull(LottieAnimation.load(ByteArray(0)))
        assertNull(LottieAnimation.load(welcome, fitz = 9))
        val animation = assertNotNull(LottieAnimation.load(welcome))
        assertFalse(animation.render(0f, 0, 10))
        assertFalse(animation.render(0f, 5000, 10))
        animation.close()
        assertFalse(animation.render(1f, 32, 32))
        animation.close()
    }
}
