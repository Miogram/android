package app.miogram

import androidx.test.core.app.ApplicationProvider
import app.miogram.data.FileCache
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.TelegramClientHolder
import cn.ellerotta.core.database.ChatDatabase
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.ktproto.client.InMemorySessionStorage
import java.io.File
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StickerCacheTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val cat = "doc${FakeTelegram.CAT_ID}"
    private val catTwo = "doc${FakeTelegram.CAT_TWO_ID}"
    private val star = "doc${FakeTelegram.EMOJI_STAR}"

    private class Process(val repos: TestRepositories, val database: ChatDatabase) {
        suspend fun stop() {
            repos.telegram.close()
            database.close()
        }
    }

    private fun start(dir: File, fake: FakeTelegram, session: InMemorySessionStorage): Process {
        val database = ChatDatabase.open(context, File(dir, "chats.db"))
        val telegram = TelegramClientHolder(dir, database = database) { id, hash, logger -> fake.config(id, hash, logger, session) }
        return Process(TestRepositories(dir, fake, telegram), database)
    }

    private suspend fun login(process: Process, fake: FakeTelegram) {
        val repos = process.repos
        repos.telegram.connect(12345, "hash")
        fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("9996624242"), "22222")).profile.id
        repos.updates.updateState.first { it != null }
    }

    private suspend fun resume(process: Process) = assertTrue(process.repos.telegram.prepare(12345, "hash"))

    @Test
    fun stickersAndCustomEmojiDownloadOnceAlsoInANewProcess(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val session = InMemorySessionStorage()
        FakeTelegram().use { fake ->
            withTimeout(90_000) {
                var process = start(dir, fake, session)
                login(process, fake)
                var repos = process.repos
                val panel = repos.stickers.panel()
                val catThumb = assertNotNull(panel.sets.single().thumb)
                val emoji = repos.stickers.customEmoji(listOf(FakeTelegram.EMOJI_STAR, FakeTelegram.EMOJI_HEART))
                assertEquals(setOf(FakeTelegram.EMOJI_STAR, FakeTelegram.EMOJI_HEART), emoji.keys)
                for (round in 1..2) {
                    assertContentEquals(fake.stickerBytes, repos.media.sticker(cat))
                    assertContentEquals(fake.stickerBytes, repos.media.sticker(star))
                    assertContentEquals(fake.webpBytes, repos.media.sticker("doc${FakeTelegram.EMOJI_HEART}"))
                    assertContentEquals(fake.stickerBytes, repos.media.sticker(catThumb.key))
                }
                assertEquals(listOf(FakeTelegram.CAT_ID, FakeTelegram.EMOJI_STAR, FakeTelegram.EMOJI_HEART), fake.documentDownloads.toList())
                assertEquals(1, fake.setThumbDownloads.size)
                val documentCalls = fake.customEmojiCalls.size
                assertEquals(1, fake.allStickersHashes.size)
                assertEquals(listOf(0L), fake.recentHashes.toList())

                fake.expireStickerReference()
                process.stop()
                process = start(dir, fake, session)
                resume(process)
                repos = process.repos

                assertContentEquals(fake.stickerBytes, repos.media.sticker(cat))
                assertContentEquals(fake.stickerBytes, repos.media.sticker(star))
                assertContentEquals(fake.stickerBytes, repos.media.sticker(catThumb.key))
                assertEquals(emoji.keys, repos.stickers.customEmoji(emoji.keys).keys)
                assertEquals(documentCalls, fake.customEmojiCalls.size)

                val again = repos.stickers.panel()
                assertEquals(panel.sets.map { it.key }, again.sets.map { it.key })
                assertEquals(panel.recent.map { it.key }, again.recent.map { it.key })
                assertEquals(panel.faved.map { it.key }, again.faved.map { it.key })
                assertEquals(2, fake.allStickersHashes.size)
                assertNotEquals(0L, fake.allStickersHashes.last())
                assertEquals(FakeTelegram.RECENT_HASH, fake.recentHashes.last())
                assertEquals(FakeTelegram.FAVED_HASH, fake.favedHashes.last())

                assertEquals(listOf(FakeTelegram.CAT_ID, FakeTelegram.EMOJI_STAR, FakeTelegram.EMOJI_HEART), fake.documentDownloads.toList())
                val fresh = repos.media.refreshed(star)
                assertContentEquals(fake.stickerReference, assertIs<cn.ellerotta.ktproto.api.types.InputDocumentFileLocation>(fresh).fileReference)
                assertContentEquals(fake.stickerBytes, repos.media.sticker(star))
                assertEquals(listOf(FakeTelegram.CAT_ID, FakeTelegram.EMOJI_STAR, FakeTelegram.EMOJI_HEART), fake.documentDownloads.toList())

                assertContentEquals(fake.stickerBytes, repos.media.sticker(catTwo.also { repos.stickers.set(panel.sets.single().key) }))
                assertEquals(FakeTelegram.CAT_TWO_ID, fake.documentDownloads.last())
                process.stop()
            }
        }
        dir.deleteRecursively()
    }

    @Test
    fun emojiSetsComeFromTheDatabaseAndAskWithTheirHash(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val session = InMemorySessionStorage()
        FakeTelegram().use { fake ->
            withTimeout(90_000) {
                var process = start(dir, fake, session)
                login(process, fake)
                fake.installedSets += FakeTelegram.EMOJI_SET
                val sets = process.repos.stickers.emojiSets()
                assertEquals(listOf("set${FakeTelegram.EMOJI_SET}"), sets.map { it.key })
                assertEquals(listOf(listOf(FakeTelegram.EMOJI_STAR)), fake.customEmojiCalls.toList())
                assertContentEquals(fake.stickerBytes, process.repos.media.sticker(assertNotNull(sets.single().thumb).key))
                process.stop()

                process = start(dir, fake, session)
                resume(process)
                val again = process.repos.stickers.emojiSets()
                assertEquals(sets.map { it.key to it.thumb?.key }, again.map { it.key to it.thumb?.key })
                assertEquals(1, fake.customEmojiCalls.size)
                assertEquals(1, fake.documentDownloads.size)
                assertEquals(2, fake.emojiStickersHashes.size)
                assertNotEquals(0L, fake.emojiStickersHashes.last())
                process.stop()
            }
        }
        dir.deleteRecursively()
    }

    @Test
    fun gifsDownloadOnceEachAlsoInANewProcess(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val session = InMemorySessionStorage()
        FakeTelegram().use { fake ->
            withTimeout(90_000) {
                var process = start(dir, fake, session)
                login(process, fake)
                var repos = process.repos

                val saved = repos.stickers.savedGifs().single()
                val found = repos.stickers.searchGifs("cats").gifs.single()
                assertEquals(saved.fileName, found.fileName)
                assertEquals(saved.size, found.size)
                val files = (1..2).map { repos.file(saved) to repos.file(found) }
                assertNotEquals(files[0].first, files[0].second)
                assertEquals(files[0], files[1])
                assertContentEquals(fake.gifBytes, files[0].first.readBytes())
                assertEquals(listOf(FakeTelegram.SAVED_GIF_ID, FakeTelegram.FOUND_GIF_ID), fake.documentDownloads.toList())
                assertEquals("file", repos.media.stream(saved).uri.scheme)

                fake.expireStickerReference()
                process.stop()
                process = start(dir, fake, session)
                resume(process)
                repos = process.repos
                val again = repos.stickers.savedGifs().single()
                assertEquals(saved.key, again.key)
                assertEquals(listOf(0L, FakeTelegram.GIFS_HASH + 1), fake.savedGifsHashes.toList())
                assertEquals(files[0].first, repos.file(again))
                assertEquals(listOf(FakeTelegram.SAVED_GIF_ID, FakeTelegram.FOUND_GIF_ID), fake.documentDownloads.toList())
                process.stop()
            }
        }
        dir.deleteRecursively()
    }

    @Test
    fun theCapEvictsTheLeastRecentlyUsedFile(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        FakeTelegram().use { fake ->
            val repos = TestRepositories(dir, fake)
            val cap = 2L * fake.stickerBytes.size + fake.webpBytes.size - 1
            repos.media.stickerCacheBytes = cap
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("9996624242"), "22222")).profile.id
                val cats = repos.stickers.set(repos.stickers.panel().sets.single().key)
                fake.addDogStickerMessage()
                val dog = assertNotNull(repos.messages.history("self").last().media).key

                assertContentEquals(fake.stickerBytes, repos.media.sticker(cats.stickers[0].key))
                Thread.sleep(30)
                assertContentEquals(fake.stickerBytes, repos.media.sticker(cats.stickers[1].key))
                Thread.sleep(30)
                assertContentEquals(fake.stickerBytes, repos.media.sticker(cat))
                Thread.sleep(30)

                assertContentEquals(fake.webpBytes, repos.media.sticker(dog))
                assertTrue(repos.media.hasSticker(cat))
                assertTrue(repos.media.hasSticker(dog))
                assertFalse(repos.media.hasSticker(catTwo))
                assertTrue(dir.resolve("stickers").listFiles()!!.sumOf { it.length() } <= cap)

                assertContentEquals(fake.stickerBytes, repos.media.sticker(catTwo))
                assertEquals(listOf(FakeTelegram.CAT_ID, FakeTelegram.CAT_TWO_ID, FakeTelegram.DOG_ID, FakeTelegram.CAT_TWO_ID), fake.documentDownloads.toList())
            }
            repos.telegram.close()
        }
        dir.deleteRecursively()
    }

    @Test
    fun anUnchangedSetIsAskedForWithItsHash(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        FakeTelegram().use { fake ->
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("9996624242"), "22222")).profile.id
                fake.addDogStickerMessage()
                val dog = assertNotNull(repos.messages.history("self").last().media)

                val first = assertNotNull(repos.stickers.setOf(dog.key))
                val second = assertNotNull(repos.stickers.setOf(dog.key))
                assertEquals(listOf(FakeTelegram.DOGS_ID to 0, FakeTelegram.DOGS_ID to 12), fake.stickerSetHashes.toList())
                assertEquals(first.stickers.map { it.key }, second.stickers.map { it.key })
                assertEquals("Dogs", second.info.title)

                repos.stickers.install(first.info.key)
                assertTrue(assertNotNull(repos.stickers.setOf(dog.key)).info.installed)
            }
            repos.telegram.close()
        }
        dir.deleteRecursively()
    }

    @Test
    fun theCacheCountsFilesAddedOneByOne() {
        val dir = Files.createTempDirectory("poc").toFile()
        val cache = FileCache(dir, maxBytes = 2_500)
        fun add(n: Int) = cache.file("f$n").apply { writeBytes(ByteArray(1_000)); setLastModified(1_000L * n) }.also { cache.trim(keep = it) }
        add(1)
        add(2)
        assertEquals(2_000L, cache.size())
        add(3)
        assertEquals(listOf("f2", "f3"), dir.list()!!.sorted())
        add(4)
        assertEquals(listOf("f3", "f4"), dir.list()!!.sorted())
        dir.deleteRecursively()
    }

    private suspend fun TestRepositories.file(media: MediaItem): File =
        assertIs<DownloadState.Done>(this.media.download(media).toList().last()).file
}
