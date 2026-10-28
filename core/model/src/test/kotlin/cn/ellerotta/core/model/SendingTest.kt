package cn.ellerotta.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class SendingTest {
    private fun kinds(vararg types: String) = types.map { AttachmentKind.of(it) }

    private fun runs(items: List<AttachmentKind>, asFile: Boolean = false) = albumsOf(items, asFile) { it }.map { it.size }

    @Test
    fun aFileIsSentAsWhatItsTypeSays() {
        assertEquals(AttachmentKind.Photo, AttachmentKind.of("image/jpeg"))
        assertEquals(AttachmentKind.Photo, AttachmentKind.of("image/png"))
        assertEquals(AttachmentKind.Photo, AttachmentKind.of("image/webp"))
        assertEquals(AttachmentKind.Video, AttachmentKind.of("video/mp4"))
        assertEquals(AttachmentKind.Audio, AttachmentKind.of("audio/mpeg"))
        assertEquals(AttachmentKind.File, AttachmentKind.of("image/gif"))
        assertEquals(AttachmentKind.File, AttachmentKind.of("application/pdf"))
        assertEquals(AttachmentKind.File, AttachmentKind.of("application/octet-stream"))
        assertTrue(AttachmentKind.Photo.visual && AttachmentKind.Video.visual)
        assertFalse(AttachmentKind.Audio.visual || AttachmentKind.File.visual)
    }

    @Test
    fun photosAndVideosMixInOneAlbumAndMusicAndFilesDoNot() {
        assertEquals(listOf(3), runs(kinds("image/jpeg", "video/mp4", "image/png")))
        assertEquals(listOf(2, 2), runs(kinds("image/jpeg", "video/mp4", "audio/mpeg", "audio/ogg")))
        assertEquals(listOf(1, 1, 1), runs(kinds("image/jpeg", "audio/mpeg", "application/pdf")))
        assertEquals(listOf(1, 1, 1), runs(kinds("image/jpeg", "application/pdf", "image/png")))
    }

    @Test
    fun anAlbumHoldsTenAndTheRestStartsAnother() {
        assertEquals(listOf(10), runs(List(10) { AttachmentKind.Photo }))
        assertEquals(listOf(10, 1), runs(List(11) { AttachmentKind.Photo }))
        assertEquals(listOf(10, 10, 5), runs(List(25) { AttachmentKind.Video }))
        assertEquals(emptyList(), runs(emptyList()))
    }

    @Test
    fun everythingSentAsFilesGoesTogether() {
        assertEquals(listOf(4), runs(kinds("image/jpeg", "audio/mpeg", "application/pdf", "video/mp4"), asFile = true))
        assertEquals(listOf(10, 2), runs(List(12) { AttachmentKind.File }, asFile = true))
    }

    @Test
    fun theSpoilerAppliesToPhotosAndVideosThatAreNotFiles() {
        val photo = AttachmentItem(0, "a.jpg", AttachmentKind.Photo, 10)
        val song = AttachmentItem(1, "a.mp3", AttachmentKind.Audio, 10)
        assertTrue(AttachmentDraft(listOf(photo, song)).canSpoiler)
        assertFalse(AttachmentDraft(listOf(photo, song), asFile = true).canSpoiler)
        assertFalse(AttachmentDraft(listOf(song)).canSpoiler)
    }

    @Test
    fun anItemIsEqualByItsPicture() {
        val one = AttachmentItem(0, "a.jpg", AttachmentKind.Photo, 10, byteArrayOf(1, 2))
        assertEquals(one, AttachmentItem(0, "a.jpg", AttachmentKind.Photo, 10, byteArrayOf(1, 2)))
        assertFalse(one == AttachmentItem(0, "a.jpg", AttachmentKind.Photo, 10, byteArrayOf(1, 3)))
        assertEquals(one.hashCode(), AttachmentItem(0, "a.jpg", AttachmentKind.Photo, 10, byteArrayOf(1, 2)).hashCode())
    }

    @Test
    fun sendOptionsTellWhenAMessageWaits() {
        assertFalse(SendOptions.None.scheduled)
        assertFalse(SendOptions(silent = true).scheduled)
        assertTrue(SendOptions(scheduleDate = 1_800_000_000).scheduled)
        assertTrue(SendOptions(scheduleDate = SendOptions.WHEN_ONLINE).scheduled)
        assertEquals(0x7FFFFFFE, SendOptions.WHEN_ONLINE)
    }
}
