package app.miogram

import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.DownloadsSaver
import app.miogram.data.SettingsStore
import app.miogram.data.freeName
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadsSaverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = FakeMediaStore.install()
    private val settings = SettingsStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saver = DownloadsSaver(context, settings, scope)
    private val dir = Files.createTempDirectory("saver").toFile()

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun aCopyGoesIntoDownloadKtprotoWithItsNameAndType(): Unit = runBlocking {
        val bytes = ByteArray(70_000) { it.toByte() }
        val file = dir.resolve("download.part").apply { writeBytes(bytes) }
        val saved = saver.save(file, music("Song.mp3"))

        val row = store.rows.single()
        assertEquals(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), row.collection)
        assertEquals("Download/ktproto", row.inserted.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals("Song.mp3", row.inserted.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
        assertEquals("audio/mpeg", row.inserted.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertEquals(1, row.inserted.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        assertEquals(0, row.values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        assertContentEquals(bytes, row.file.readBytes())
        assertEquals("Song.mp3", saved.name)
        assertEquals("audio/mpeg", saved.mimeType)
        assertEquals(row.id, saved.uri.lastPathSegment?.toLong())
    }

    @Test
    fun takenNamesGetANumberBeforeTheExtension(): Unit = runBlocking {
        val file = dir.resolve("f").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val names = (1..3).map { saver.save(file, music("Song.mp3")).name } + saver.save(file, music("song.MP3")).name
        assertEquals(listOf("Song.mp3", "Song (1).mp3", "Song (2).mp3", "song (3).MP3"), names)
        assertEquals(names, store.rows.map { it.inserted.getAsString(MediaStore.MediaColumns.DISPLAY_NAME) })

        assertEquals("README (1)", freeName("README", setOf("readme")))
        assertEquals("archive.tar (1).gz", freeName("archive.tar.gz", setOf("archive.tar.gz")))
        assertEquals(".nomedia (1)", freeName(".nomedia", setOf(".nomedia")))
        assertEquals("a (3).txt", freeName("a.txt", setOf("a.txt", "a (1).txt", "a (2).txt")))
        assertEquals("free.txt", freeName("free.txt", setOf("a.txt")))
    }

    @Test
    fun downloadsAreCopiedByThemselvesOnlyWithTheSetting(): Unit = runBlocking {
        val file = dir.resolve("f").apply { writeBytes(byteArrayOf(4, 5)) }
        saver.downloaded(music("off.mp3"), file)
        delay(300)
        assertTrue(store.rows.isEmpty())

        settings.setAutoSaveDownloads(true)
        try {
            saver.downloaded(music("thumb.jpg").copy(key = "doc1@m"), file)
            saver.downloaded(music("loop.mp4").copy(kind = MediaKind.Gif), file)
            saver.downloaded(music("on.mp3"), file)
            withTimeout(10_000) { while (store.rows.isEmpty()) delay(20) }
            delay(300)
            assertEquals(listOf("on.mp3"), store.rows.map { it.inserted.getAsString(MediaStore.MediaColumns.DISPLAY_NAME) })
        } finally {
            settings.setAutoSaveDownloads(false)
        }
    }

    private fun music(name: String) = MediaItem(key = "doc1", fileName = name, size = 3, dcId = 2, isPhoto = false, kind = MediaKind.Music, mimeType = "audio/mpeg")
}
