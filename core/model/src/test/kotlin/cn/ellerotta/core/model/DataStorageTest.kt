package cn.ellerotta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataStorageTest {
    private val mb = AutoDownloadSettings.MB

    private fun media(kind: MediaKind, size: Long?) = MediaItem(key = "m", fileName = "f", size = size, dcId = 1, isPhoto = kind == MediaKind.Photo, kind = kind)

    @Test
    fun theDefaultsDownloadVoiceAndSmallGifsAndOnWifiPhotosAndVideos() {
        val settings = AutoDownloadSettings()
        assertTrue(settings.allows(media(MediaKind.Voice, 300_000), NetworkType.Mobile))
        assertTrue(settings.allows(media(MediaKind.Gif, 2 * mb), NetworkType.Mobile))
        assertFalse(settings.allows(media(MediaKind.Gif, 20 * mb), NetworkType.Mobile), "over the limit")
        assertFalse(settings.allows(media(MediaKind.Photo, 200_000), NetworkType.Mobile))
        assertTrue(settings.allows(media(MediaKind.Photo, 200_000), NetworkType.Wifi))
        assertTrue(settings.allows(media(MediaKind.Video, 8 * mb), NetworkType.Wifi))
        assertFalse(settings.allows(media(MediaKind.File, 1_000), NetworkType.Wifi), "files wait for a tap")
        assertFalse(settings.allows(media(MediaKind.Voice, 1_000), NetworkType.Roaming))
        assertFalse(settings.allows(media(MediaKind.Voice, 1_000), null), "nothing without a network")
    }

    @Test
    fun photosAndVoiceIgnoreTheLimitOthersNeedAKnownSize() {
        val settings = AutoDownloadSettings(mobile = AutoDownloadRule(AutoDownloadKind.entries.toSet(), 1 * mb))
        assertTrue(settings.allows(media(MediaKind.Photo, 3 * mb), NetworkType.Mobile))
        assertTrue(settings.allows(media(MediaKind.Round, 3 * mb), NetworkType.Mobile))
        assertTrue(settings.allows(media(MediaKind.File, 1 * mb), NetworkType.Mobile))
        assertFalse(settings.allows(media(MediaKind.File, null), NetworkType.Mobile))
        assertFalse(settings.allows(media(MediaKind.Sticker, 10), NetworkType.Mobile), "stickers load anyway")
        assertFalse(settings.allows(media(MediaKind.Music, 10), NetworkType.Mobile), "songs stream")
    }

    @Test
    fun theSettingsSurviveTheirLine() {
        val settings = AutoDownloadSettings()
            .with(NetworkType.Roaming, AutoDownloadRule(setOf(AutoDownloadKind.Photos), 5 * mb))
            .with(NetworkType.Mobile, AutoDownloadRule(emptySet(), 1 * mb))
        assertEquals(settings, AutoDownloadSettings.decode(settings.encode()))
        assertEquals(AutoDownloadSettings(), AutoDownloadSettings.decode(null))
        assertEquals(AutoDownloadSettings(), AutoDownloadSettings.decode("Photos|nope;;"))
        assertEquals(AutoDownloadSettings(), AutoDownloadSettings.decode("Unknown|5;|5;|5"))
    }

    @Test
    fun usageAddsUp() {
        assertEquals(30, StorageUsage(mapOf(StorageCategory.Media to 10, StorageCategory.Music to 20)).total)
    }
}
