package cn.ellerotta.core.lottie

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LottieDataTest {
    private val welcome = javaClass.getResource("/welcome.json")!!.readBytes()

    private fun gzip(bytes: ByteArray) = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    @Test
    fun tgsIsUnpackedWithALimit() {
        val packed = gzip(welcome)
        assertTrue(LottieData.isGzip(packed))
        assertFalse(LottieData.isGzip(welcome))
        assertContentEquals(welcome, LottieData.fromTgs(packed))
        val bomb = gzip(ByteArray(64 * 1024 * 1024))
        assertTrue(bomb.size < 256 * 1024)
        assertFailsWith<IOException> { LottieData.fromTgs(bomb) }
    }
}
