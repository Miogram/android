package cn.ellerotta.core.media

import java.io.ByteArrayOutputStream
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class WebmTest {
    private fun resource(name: String): ByteArray = javaClass.getResourceAsStream("/webm/$name")!!.use { it.readBytes() }

    @Test
    fun alphaFramesComeFromBlockAdditions() {
        val video = Webm.parse(resource("alpha.webm"))
        assertEquals(64, video.width)
        assertEquals(48, video.height)
        assertTrue(video.alphaMode)
        assertTrue(video.hasAlpha)
        assertEquals(listOf(0L, 100L, 200L, 300L, 400L), video.frames.map { it.timestampMs })
        assertEquals(listOf(true, false, false, false, false), video.frames.map { it.keyframe })
        assertEquals(100, video.frameDurationMs)
        assertEquals(500, video.durationMs)
        assertEquals(297, video.frames[0].color.size)
        assertEquals(78, video.frames[0].alpha!!.size)
        for (frame in video.frames) {
            assertTrue(frame.color.isVp9Frame())
            assertTrue(frame.alpha!!.isVp9Frame())
        }
    }

    @Test
    fun simpleBlocksWithoutAlpha() {
        val video = Webm.parse(resource("opaque.webm"))
        assertFalse(video.alphaMode)
        assertFalse(video.hasAlpha)
        assertEquals(listOf(0L, 100L, 200L, 300L, 400L), video.frames.map { it.timestampMs })
        assertEquals(listOf(true, false, false, false, false), video.frames.map { it.keyframe })
        assertTrue(video.frames.all { it.alpha == null && it.color.isVp9Frame() })
    }

    @Test
    fun clustersOfUnknownSize() {
        val original = resource("opaque.webm")
        val streamed = withUnknownClusterSizes(original)
        assertTrue(streamed.size < original.size)
        val a = Webm.parse(original)
        val b = Webm.parse(streamed)
        assertEquals(a.frames.map { it.timestampMs }, b.frames.map { it.timestampMs })
        assertEquals(a.frames.map { it.color.toList() }, b.frames.map { it.color.toList() })
    }

    @Test
    fun otherCodecsAndDamageAreRejected() {
        val vp8 = assertFailsWith<WebmException> { Webm.parse(resource("vp8.webm")) }
        assertContains(vp8.message!!, "V_VP8")
        assertFailsWith<WebmException> { Webm.parse("RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray()) }
        assertFailsWith<WebmException> { Webm.parse(ByteArray(0)) }
        val alpha = resource("alpha.webm")
        assertFailsWith<WebmException> { Webm.parse(alpha.copyOf(alpha.size - 200)) }
        assertFailsWith<WebmException> { Webm.parse(alpha.copyOf(300)) }
    }

    @Test
    fun timecodeScaleNegativeOffsetsAndOtherTracks() {
        val file = ebmlFile(
            el(INFO, uint(TIMECODE_SCALE, 500_000)),
            el(
                TRACKS,
                el(TRACK_ENTRY, uint(TRACK_NUMBER, 1), uint(TRACK_TYPE, 2), str(CODEC_ID, "A_OPUS")),
                el(TRACK_ENTRY, uint(TRACK_NUMBER, 2), uint(TRACK_TYPE, 1), str(CODEC_ID, "V_VP9"), el(VIDEO, uint(PIXEL_WIDTH, 8), uint(PIXEL_HEIGHT, 6))),
            ),
            el(
                CLUSTER,
                uint(TIMECODE, 100),
                simpleBlock(track = 2, relative = -20, flags = 0x80, data = byteArrayOf(0x82.toByte(), 1)),
                simpleBlock(track = 1, relative = 0, flags = 0x80, data = byteArrayOf(9, 9, 9)),
                el(
                    BLOCK_GROUP,
                    block(track = 2, relative = 20, data = byteArrayOf(0x86.toByte(), 2)),
                    uint(REFERENCE_BLOCK, 1),
                    el(BLOCK_ADDITIONS, el(BLOCK_MORE, uint(BLOCK_ADD_ID, 2), el(BLOCK_ADDITIONAL, 7))),
                ),
                el(
                    BLOCK_GROUP,
                    block(track = 2, relative = 60, data = byteArrayOf(0x86.toByte(), 3)),
                    uint(REFERENCE_BLOCK, 1),
                    el(BLOCK_ADDITIONS, el(BLOCK_MORE, el(BLOCK_ADDITIONAL, 0x82, 4))),
                ),
            ),
        )
        val video = Webm.parse(file)
        assertEquals(8, video.width)
        assertEquals(6, video.height)
        assertEquals(listOf(40L, 60L, 80L), video.frames.map { it.timestampMs })
        assertEquals(listOf(1, 2, 3), video.frames.map { it.color[1].toInt() })
        assertNull(video.frames[0].alpha)
        assertNull(video.frames[1].alpha)
        assertNotNull(video.frames[2].alpha)
        assertFalse(video.hasAlpha)
        assertFalse(video.frames[1].keyframe)
        assertEquals(20, video.frameDurationMs)
        assertEquals(100, video.durationMs)
    }

    @Test
    fun lacingAndAFirstFrameThatIsNoKeyframeAreRejected() {
        fun file(flags: Int) = ebmlFile(
            el(TRACKS, el(TRACK_ENTRY, uint(TRACK_NUMBER, 1), uint(TRACK_TYPE, 1), str(CODEC_ID, "V_VP9"), el(VIDEO, uint(PIXEL_WIDTH, 8), uint(PIXEL_HEIGHT, 8)))),
            el(CLUSTER, uint(TIMECODE, 0), simpleBlock(track = 1, relative = 0, flags = flags, data = byteArrayOf(0x82.toByte()))),
        )
        assertEquals(1, Webm.parse(file(0x80)).frames.size)
        assertContains(assertFailsWith<WebmException> { Webm.parse(file(0x82)) }.message!!, "laced")
        assertContains(assertFailsWith<WebmException> { Webm.parse(file(0x00)) }.message!!, "keyframe")
    }

    private fun ByteArray.isVp9Frame(): Boolean = isNotEmpty() && (this[0].toInt() and 0xc0) == 0x80

    private fun withUnknownClusterSizes(bytes: ByteArray): ByteArray {
        fun vintLength(at: Int) = Integer.numberOfLeadingZeros(bytes[at].toInt() and 0xff) - 23
        fun vintValue(at: Int): Int {
            val length = vintLength(at)
            var value = (bytes[at].toInt() and 0xff) and ((0x80 shr (length - 1)) - 1)
            for (k in 1 until length) value = (value shl 8) or (bytes[at + k].toInt() and 0xff)
            return value
        }
        val out = ByteArrayOutputStream()
        val headerEnd = 4 + vintLength(4) + vintValue(4)
        val segmentData = headerEnd + 4 + vintLength(headerEnd + 4)
        out.write(bytes, 0, segmentData)
        var i = segmentData
        while (i < bytes.size) {
            val idLength = vintLength(i)
            val sizeAt = i + idLength
            val dataAt = sizeAt + vintLength(sizeAt)
            val end = dataAt + vintValue(sizeAt)
            val isCluster = bytes[i] == 0x1f.toByte() && bytes[i + 1] == 0x43.toByte() && bytes[i + 2] == 0xb6.toByte() && bytes[i + 3] == 0x75.toByte()
            if (isCluster) {
                out.write(bytes, i, idLength)
                out.write(0xff)
                out.write(bytes, dataAt, end - dataAt)
            } else {
                out.write(bytes, i, end - i)
            }
            i = end
        }
        return out.toByteArray()
    }

    private fun ebmlFile(vararg segment: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(el(0x1A45DFA3, str(0x4282, "webm")))
        out.write(byteArrayOf(0x18, 0x53, 0x80.toByte(), 0x67, 0x01, -1, -1, -1, -1, -1, -1, -1))
        segment.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun el(id: Long, vararg children: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply { children.forEach { write(it) } }.toByteArray()
        return idBytes(id) + sizeBytes(body.size) + body
    }

    private fun el(id: Long, vararg data: Int): ByteArray = el(id, ByteArray(data.size) { data[it].toByte() })

    private fun uint(id: Long, value: Long): ByteArray = el(id, byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte()))

    private fun str(id: Long, value: String): ByteArray = el(id, value.toByteArray())

    private fun simpleBlock(track: Int, relative: Int, flags: Int, data: ByteArray): ByteArray =
        el(SIMPLE_BLOCK, blockBody(track, relative, flags, data))

    private fun block(track: Int, relative: Int, data: ByteArray): ByteArray = el(BLOCK, blockBody(track, relative, 0, data))

    private fun blockBody(track: Int, relative: Int, flags: Int, data: ByteArray): ByteArray =
        byteArrayOf((0x80 or track).toByte(), (relative shr 8).toByte(), relative.toByte(), flags.toByte()) + data

    private fun idBytes(id: Long): ByteArray {
        val length = (64 - java.lang.Long.numberOfLeadingZeros(id) + 7) / 8
        return ByteArray(length) { (id shr (8 * (length - 1 - it))).toByte() }
    }

    private fun sizeBytes(size: Int): ByteArray = byteArrayOf((0x10 or (size shr 24)).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte())

    private companion object {
        const val INFO = 0x1549A966L
        const val TIMECODE_SCALE = 0x2AD7B1L
        const val TRACKS = 0x1654AE6BL
        const val TRACK_ENTRY = 0xAEL
        const val TRACK_NUMBER = 0xD7L
        const val TRACK_TYPE = 0x83L
        const val CODEC_ID = 0x86L
        const val VIDEO = 0xE0L
        const val PIXEL_WIDTH = 0xB0L
        const val PIXEL_HEIGHT = 0xBAL
        const val CLUSTER = 0x1F43B675L
        const val TIMECODE = 0xE7L
        const val SIMPLE_BLOCK = 0xA3L
        const val BLOCK_GROUP = 0xA0L
        const val BLOCK = 0xA1L
        const val REFERENCE_BLOCK = 0xFBL
        const val BLOCK_ADDITIONS = 0x75A1L
        const val BLOCK_MORE = 0xA6L
        const val BLOCK_ADD_ID = 0xEEL
        const val BLOCK_ADDITIONAL = 0xA5L
    }
}
