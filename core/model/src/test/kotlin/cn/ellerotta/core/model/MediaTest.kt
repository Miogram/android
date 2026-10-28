package cn.ellerotta.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Test

class MediaTest {
    private val sizes = listOf(MediaSize("m", 320, 240, 20_000), MediaSize("x", 800, 600, 80_000), MediaSize("w", 1280, 960, 200_000))
    private val photo = MediaItem("photo1", "photo_1_w.jpg", 200_000, 2, isPhoto = true, kind = MediaKind.Photo, width = 1280, height = 960, sizes = sizes)

    @Test
    fun theSmallestSizeCoveringTheBubbleWins() {
        assertEquals("m", sizes.fitting(300)?.type)
        assertEquals("x", sizes.fitting(780)?.type)
        assertEquals("w", sizes.fitting(1000)?.type)
        assertEquals("w", sizes.fitting(4000)?.type)
        assertNull(emptyList<MediaSize>().fitting(100))
    }

    @Test
    fun previewsAreOwnItemsUnlessTheyAreTheFullPhoto() {
        val preview = photo.preview(780)!!
        assertEquals("photo1@x", preview.key)
        assertEquals(80_000L, preview.size)
        assertEquals(800 to 600, preview.width to preview.height)
        assertSame(photo, photo.preview(1200))
        val bare = photo.copy(sizes = emptyList())
        assertSame(bare, bare.preview(500))
    }

    @Test
    fun videosPreviewTheirThumbnail() {
        val video = MediaItem("doc5", "clip.mp4", 5_000_000, 2, isPhoto = false, kind = MediaKind.Video, sizes = listOf(MediaSize("m", 320, 180, 9_000)))
        val preview = video.preview(780)!!
        assertEquals("doc5@m", preview.key)
        assertEquals(MediaKind.Photo, preview.kind)
        assertNull(video.copy(sizes = emptyList()).preview(780))
    }
}
