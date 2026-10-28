package app.miogram

import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.stickerSet
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.StickerFormat
import cn.ellerotta.core.model.stickerFormat
import cn.ellerotta.core.model.stickerPreview
import cn.ellerotta.ktproto.api.types.InputDocument
import cn.ellerotta.ktproto.api.types.InputMediaDocument
import cn.ellerotta.ktproto.api.types.InputPeerEmpty
import cn.ellerotta.ktproto.api.types.InputStickerSetShortName
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class StickersRepositoryTest {
    @Test
    fun panelSetsSendingAndInstalling(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("9996624242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "22222")).profile.id

                val panel = repos.stickers.panel()
                assertEquals(listOf("doc${FakeTelegram.CAT_ID}"), panel.recent.map { it.key })
                assertEquals(listOf("doc${FakeTelegram.CAT_TWO_ID}"), panel.faved.map { it.key })
                val cats = panel.sets.single()
                assertEquals("Cats" to 2, cats.title to cats.count)
                assertTrue(cats.installed)
                assertContentEquals(fake.stickerBytes, repos.media.sticker(assertNotNull(cats.thumb).key))

                assertEquals(panel, repos.stickers.panel())
                assertEquals(2, fake.allStickersHashes.size)
                assertEquals(0L, fake.allStickersHashes[0])
                assertTrue(fake.allStickersHashes[1] != 0L)

                val detail = repos.stickers.set(cats.key)
                assertEquals(listOf("\uD83D\uDE00", "\uD83D\uDE3A"), detail.stickers.map { it.emoji })
                assertTrue(detail.stickers.all { it.kind == MediaKind.Sticker && it.animated })
                assertEquals(listOf(fake.outlinePath, fake.outlinePath), detail.stickers.map { it.outline })
                val preview = assertNotNull(detail.stickers.first().stickerPreview())
                assertEquals("doc${FakeTelegram.CAT_ID}@m", preview.key)
                assertFalse(repos.media.hasSticker(preview.key))
                assertContentEquals(fake.stickerPreviewBytes, repos.media.sticker(preview.key))
                assertTrue(repos.media.hasSticker(preview.key))
                assertEquals(listOf("${FakeTelegram.CAT_ID}@m"), fake.stickerThumbDownloads.toList())
                assertNull(detail.stickers[1].stickerPreview())

                val sticker = detail.stickers.first()
                repos.messages.sendDocument("self", sticker.key)
                val sent = assertIs<InputDocument>(assertIs<InputMediaDocument>(assertNotNull(fake.lastSendMedia).media).id)
                assertEquals(FakeTelegram.CAT_ID to FakeTelegram.CAT_ID + 1, sent.id to sent.accessHash)
                assertContentEquals(byteArrayOf(7), sent.fileReference)
                assertEquals(sticker.key, repos.messages.history("self").last().media?.key)

                fake.expireStickerReference()
                repos.messages.sendDocument("self", sticker.key)
                val resent = assertIs<InputDocument>(assertIs<InputMediaDocument>(assertNotNull(fake.lastSendMedia).media).id)
                assertContentEquals(fake.stickerReference, resent.fileReference)
                assertContentEquals(fake.stickerBytes, repos.media.sticker("doc${FakeTelegram.CAT_TWO_ID}"))

                fake.addDogStickerMessage()
                val dog = assertNotNull(repos.messages.history("self").last().media)
                val dogs = assertNotNull(repos.stickers.setOf(dog.key))
                assertEquals("Dogs", dogs.info.title)
                assertFalse(dogs.info.installed)
                repos.stickers.install(dogs.info.key)
                assertTrue(FakeTelegram.DOGS_ID in fake.installedSets)
                assertEquals(listOf("Cats", "Dogs"), repos.stickers.panel().sets.map { it.title })
                repos.stickers.uninstall(dogs.info.key)
                assertFalse(FakeTelegram.DOGS_ID in fake.installedSets)
                assertEquals(listOf("Cats"), repos.stickers.panel().sets.map { it.title })

                assertEquals(listOf("doc${FakeTelegram.DOG_ID}"), repos.stickers.forEmoji("\uD83D\uDC36").map { it.key })
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun featuredSetsAndEmojiCategories(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("9996624242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "22222")).profile.id

                val featured = repos.stickers.featured(emoji = false)
                val sets = featured.sets.map { it.info.key }
                assertEquals(listOf(FakeTelegram.CATS_ID, FakeTelegram.DOGS_ID, FakeTelegram.CLIPS_ID, FakeTelegram.BIRDS_ID).map { "set$it" }, sets)
                assertEquals(listOf(listOf(FakeTelegram.CAT_ID), listOf(FakeTelegram.DOG_ID), listOf(FakeTelegram.CLIP_ID), emptyList()), featured.sets.map { set -> set.covers.map { it.documentId } })
                assertEquals(listOf(true, false, false, false), featured.sets.map { it.info.installed })
                assertEquals(setOf("set${FakeTelegram.DOGS_ID}", "set${FakeTelegram.CLIPS_ID}"), featured.unread)
                assertEquals(featured, repos.stickers.featured(emoji = false))
                assertEquals(listOf(0L, FakeTelegram.FEATURED_HASH), fake.featuredHashes.toList())

                repos.stickers.readFeatured(listOf("set${FakeTelegram.DOGS_ID}"))
                assertEquals(listOf(FakeTelegram.DOGS_ID), fake.readFeatured.toList())
                assertEquals(setOf("set${FakeTelegram.CLIPS_ID}"), repos.stickers.featured(emoji = false).unread)

                repos.stickers.install("set${FakeTelegram.DOGS_ID}")
                assertTrue(repos.stickers.featured(emoji = false).sets.first { it.info.key == "set${FakeTelegram.DOGS_ID}" }.info.installed)

                val emoji = repos.stickers.featured(emoji = true).sets.single()
                assertEquals("set${FakeTelegram.EMOJI_SET}", emoji.info.key)
                assertEquals(listOf(FakeTelegram.EMOJI_STAR, FakeTelegram.EMOJI_HEART), emoji.covers.map { it.documentId })
                fake.customEmojiCalls.clear()

                val categories = repos.stickers.categories(stickers = false)
                assertEquals(listOf("Love" to false, "Hello" to true), categories.map { it.title to it.greeting })
                assertEquals(listOf(FakeTelegram.EMOJI_HEART, FakeTelegram.EMOJI_STAR), categories.map { assertNotNull(it.icon).documentId })
                assertEquals(categories, repos.stickers.categories(stickers = false))
                assertEquals(listOf(0, FakeTelegram.EMOJI_GROUPS_HASH), fake.emojiGroupHashes.toList())
                assertTrue(fake.customEmojiCalls.isEmpty())

                val cats = repos.stickers.categories(stickers = true).single()
                assertEquals(listOf(FakeTelegram.CAT_ID, FakeTelegram.CAT_TWO_ID), repos.stickers.searchCategory(cats, listOf("en", "en"), emojis = false).map { it.documentId })
                assertEquals(listOf(FakeTelegram.EMOJI_HEART), repos.stickers.searchCategory(categories.first(), listOf("en"), emojis = true).map { it.documentId })
                assertEquals(listOf("\uD83D\uDE00 \uD83D\uDE3A" to false, "\u2764 \uD83D\uDE0D" to true), fake.categorySearches.toList())
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun savedGifsGifSearchAndStickerSearch(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("9996624242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "22222")).profile.id

                val saved = repos.stickers.savedGifs()
                assertEquals(listOf("doc${FakeTelegram.SAVED_GIF_ID}"), saved.map { it.key })
                assertEquals(MediaKind.Gif, saved.single().kind)
                assertEquals(saved, repos.stickers.savedGifs())
                assertEquals(listOf(0L, FakeTelegram.GIFS_HASH + 1), fake.savedGifsHashes.toList())

                val first = repos.stickers.searchGifs("cats")
                assertEquals(listOf("doc${FakeTelegram.FOUND_GIF_ID}"), first.gifs.map { it.key })
                assertEquals(FakeBots.NEXT_OFFSET, first.nextOffset)
                assertNull(repos.stickers.searchGifs("cats", FakeBots.NEXT_OFFSET).nextOffset)
                val calls = fake.bots.inlineCalls.toList()
                assertEquals(listOf("cats" to "", "cats" to FakeBots.NEXT_OFFSET), calls.map { it.query to it.offset })
                assertTrue(calls.all { it.peer == InputPeerEmpty })

                val found = first.gifs.single()
                repos.messages.sendDocument("self", found.key)
                val sent = assertIs<InputDocument>(assertIs<InputMediaDocument>(assertNotNull(fake.lastSendMedia).media).id)
                assertEquals(FakeTelegram.FOUND_GIF_ID, sent.id)
                repos.stickers.saveGif(found.key)
                assertEquals(listOf("doc${FakeTelegram.FOUND_GIF_ID}", "doc${FakeTelegram.SAVED_GIF_ID}"), repos.stickers.savedGifs().map { it.key })

                fake.expireStickerReference()
                repos.messages.sendDocument("self", "doc${FakeTelegram.SAVED_GIF_ID}")
                assertContentEquals(fake.stickerReference, assertIs<InputDocument>(assertIs<InputMediaDocument>(assertNotNull(fake.lastSendMedia).media).id).fileReference)

                assertEquals(listOf("doc${FakeTelegram.CAT_ID}", "doc${FakeTelegram.CAT_TWO_ID}"), repos.stickers.searchStickers(" cat ", listOf("en", "en")).map { it.key })
                assertEquals(listOf("cat"), fake.stickerSearches.toList())
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun favesRemovedGifsAndSetOrder(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("9996624242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "22222")).profile.id
                val cat = "doc${FakeTelegram.CAT_ID}"
                val catTwo = "doc${FakeTelegram.CAT_TWO_ID}"

                val panel = repos.stickers.panel()
                fake.expireStickerReference()
                assertEquals(listOf(cat, catTwo), repos.stickers.fave(panel.recent.single(), fave = true).map { it.key })
                assertEquals(listOf(FakeTelegram.CAT_ID, FakeTelegram.CAT_TWO_ID), fake.favedStickers.toList())
                assertEquals(listOf(cat, catTwo), repos.stickers.panel().faved.map { it.key })
                assertEquals(listOf(catTwo), repos.stickers.fave(panel.recent.single(), fave = false).map { it.key })
                assertEquals(listOf(catTwo), repos.stickers.panel().faved.map { it.key })

                val gif = repos.stickers.savedGifs().single()
                repos.stickers.unsaveGif(gif.key)
                assertTrue(fake.savedGifs.isEmpty())
                assertEquals(emptyList(), repos.stickers.savedGifs())

                fake.installedSets += FakeTelegram.DOGS_ID
                assertEquals(listOf("set${FakeTelegram.CATS_ID}", "set${FakeTelegram.DOGS_ID}"), repos.stickers.panel().sets.map { it.key })
                repos.stickers.reorder(listOf("set${FakeTelegram.DOGS_ID}", "set${FakeTelegram.CATS_ID}"), emoji = false)
                assertEquals(false to listOf(FakeTelegram.DOGS_ID, FakeTelegram.CATS_ID), fake.reorders.single())
                assertEquals(listOf("set${FakeTelegram.DOGS_ID}", "set${FakeTelegram.CATS_ID}"), repos.stickers.panel().sets.map { it.key })
                repos.stickers.reorder(listOf("set${FakeTelegram.EMOJI_SET}"), emoji = true)
                assertEquals(true to listOf(FakeTelegram.EMOJI_SET), fake.reorders.last())
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun setThumbnailsByKindDocumentAndFirstSticker(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("9996624242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "22222")).profile.id

                val starsSet = assertNotNull(repos.telegram.stickerSet(InputStickerSetShortName("stars")))
                assertEquals("doc${FakeTelegram.EMOJI_STAR}", starsSet.info.thumb?.key)
                assertTrue(fake.customEmojiCalls.isEmpty())

                fake.installedSets += listOf(FakeTelegram.DOGS_ID, FakeTelegram.CLIPS_ID, FakeTelegram.BIRDS_ID, FakeTelegram.EMOJI_SET)
                val sets = repos.stickers.panel().sets.associateBy { it.title }

                val cats = assertNotNull(sets.getValue("Cats").thumb)
                assertEquals("set${FakeTelegram.CATS_ID}thumb3" to "set${FakeTelegram.CATS_ID}thumb3.tgs", cats.key to cats.fileName)
                assertEquals(true to "application/x-tgsticker", cats.animated to cats.mimeType)
                assertEquals(fake.outlinePath, cats.outline)
                assertEquals(StickerFormat.Lottie, stickerFormat(assertNotNull(repos.media.sticker(cats.key))))
                val clips = assertNotNull(sets.getValue("Clips").thumb)
                assertEquals("set${FakeTelegram.CLIPS_ID}thumb1.webm" to "video/webm", clips.fileName to clips.mimeType)
                assertFalse(clips.animated)
                assertContentEquals(fake.videoStickerBytes, repos.media.sticker(clips.key))
                val birds = assertNotNull(sets.getValue("Birds").thumb)
                assertEquals("set${FakeTelegram.BIRDS_ID}thumb2.webp" to "image/webp", birds.fileName to birds.mimeType)
                assertContentEquals(fake.webpBytes, repos.media.sticker(birds.key))
                val downloads = listOf(
                    FakeTelegram.SetThumbDownload(FakeTelegram.CATS_ID, 3, 2),
                    FakeTelegram.SetThumbDownload(FakeTelegram.CLIPS_ID, 1, 2),
                    FakeTelegram.SetThumbDownload(FakeTelegram.BIRDS_ID, 2, 1),
                )
                assertEquals(downloads, fake.setThumbDownloads.toList())
                assertContentEquals(fake.webpBytes, repos.media.sticker(birds.key))
                assertEquals(downloads, fake.setThumbDownloads.toList())

                val stars = assertNotNull(sets.getValue("Stars").thumb)
                assertEquals("doc${FakeTelegram.EMOJI_STAR}", stars.key)
                assertEquals(listOf(listOf(FakeTelegram.EMOJI_STAR)), fake.customEmojiCalls.toList())
                assertEquals(StickerFormat.Lottie, stickerFormat(assertNotNull(repos.media.sticker(stars.key))))

                assertNull(sets.getValue("Dogs").thumb)
                assertEquals("doc${FakeTelegram.DOG_ID}", repos.stickers.set("set${FakeTelegram.DOGS_ID}").info.thumb?.key)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}
