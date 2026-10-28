package cn.ellerotta.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class StickersTest {
    @Test
    fun formatsAreToldByTheirFirstBytes() {
        assertEquals(StickerFormat.Lottie, stickerFormat(byteArrayOf(0x1f, 0x8b.toByte(), 8)))
        assertEquals(StickerFormat.Video, stickerFormat(byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte(), 1)))
        assertEquals(StickerFormat.Image, stickerFormat("RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray()))
    }

    @Test
    fun onlyASingleEmojiAsksForStickers() {
        listOf("😀", " 👍🏽 ", "❤️", "👨‍👩‍👧", "🇳🇱").forEach { assertTrue(isSingleEmoji(it), it) }
        listOf("", "hi", "😀😀", "😀 a", "1", "🇳🇱🇩🇪").forEach { assertFalse(isSingleEmoji(it), it) }
    }

    @Test
    fun oneToThreeEmojiShowLarge() {
        assertEquals(1, jumboEmojiCount(" 👍🏽 "))
        assertEquals(1, jumboEmojiCount("👨‍👩‍👧"))
        assertEquals(1, jumboEmojiCount("❤️"))
        assertEquals(2, jumboEmojiCount("🇳🇱🇩🇪"))
        assertEquals(3, jumboEmojiCount("😀 😀😀"))
        listOf("", "😀😀😀😀", "😀 a", "1", "❤", "©").forEach { assertEquals(0, jumboEmojiCount(it), it) }
    }

    @Test
    fun outlinesInflateAsTheSpecDescribes() {
        val lookup = "AACAAAAHAAALMAAAQASTAVAAAZaacaaaahaaalmaaaqastava.az0123456789-,"
        assertEquals(64, lookup.length)
        lookup.forEachIndexed { i, c -> assertEquals("M${c}z", stickerOutline(byteArrayOf((192 + i).toByte()))) }
        assertEquals("M0z", stickerOutline(byteArrayOf(0)))
        assertEquals("M63z", stickerOutline(byteArrayOf(63)))
        assertEquals("M-0z", stickerOutline(byteArrayOf(64)))
        assertEquals("M-63z", stickerOutline(byteArrayOf(127)))
        assertEquals("M,0z", stickerOutline(byteArrayOf(128.toByte())))
        assertEquals("M,63z", stickerOutline(byteArrayOf(191.toByte())))
        assertEquals("Mz", stickerOutline(ByteArray(0)))
    }

    @Test
    fun anOutlineIsAPathInTheSpecsBox() {
        val l = (192 + 37).toByte()
        val c = (192 + 28).toByte()
        val dot = (192 + 49).toByte()
        val one = (192 + 53).toByte()
        val two = (192 + 54).toByte()
        val encoded = byteArrayOf(10, (128 + 20).toByte(), l, (64 + 5).toByte(), (128 + 30).toByte(), c, one, two, dot, 5, (128 + 2).toByte(), (64 + 3).toByte(), (128 + 40).toByte())
        assertEquals("M10,20l-5,30c12.5,2-3,40z", stickerOutline(encoded))
        assertEquals(512f, STICKER_OUTLINE_BOX)
    }

    @Test
    fun animatedAndVideoStickersHaveTheirLargestStillSizeAsPreview() {
        val sizes = listOf(MediaSize("s", 100, 100, 900), MediaSize("m", 128, 128, 1_500))
        val lottie = MediaItem("doc1", "a.tgs", 9_000, 2, isPhoto = false, kind = MediaKind.Sticker, animated = true, emoji = "😀", sizes = sizes, mimeType = "application/x-tgsticker")
        val preview = assertNotNull(lottie.stickerPreview())
        assertEquals("doc1@m" to 1_500L, preview.key to preview.size)
        assertEquals(Triple(MediaKind.Sticker, false, "😀"), Triple(preview.kind, preview.animated, preview.emoji))
        assertEquals("doc1@m", lottie.stickerPreviewKey)
        val video = lottie.copy(key = "doc2", animated = false, mimeType = "video/webm")
        assertEquals("doc2@m", video.stickerPreviewKey)
        assertNull(lottie.copy(animated = false, mimeType = "image/webp").stickerPreview())
        assertNull(lottie.copy(sizes = emptyList()).stickerPreviewKey)
        assertNull(lottie.copy(kind = MediaKind.File).stickerPreview())
    }

    @Test
    fun setsFollowAnOrderAndKeepTheRestAfterIt() {
        fun set(key: String) = StickerSetInfo(key, key, key, 1, installed = true)
        val sets = listOf(set("a"), set("b"), set("c"), set("d"))
        assertEquals(listOf("c", "a", "b", "d"), sets.orderedBy(listOf("c", "a", "gone")).map { it.key })
        assertEquals(listOf("a", "b", "c", "d"), sets.orderedBy(emptyList()).map { it.key })
    }
}
