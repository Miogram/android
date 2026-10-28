package app.miogram

import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.GiftRarity
import cn.ellerotta.core.model.GiftRarityTier
import cn.ellerotta.core.model.ServiceKind
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

class GiftsRepositoryTest {
    @Test
    fun giftsCatalogueAndServiceMessages(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("9996624242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "22222")).profile.id

                val first = repos.gifts.gifts(null)
                assertEquals(3, first.count)
                assertEquals("2", first.nextOffset)
                val teddy = first.gifts[0]
                assertEquals("Alice Example", teddy.fromName)
                assertEquals(50L, teddy.stars)
                assertEquals("happy birthday", teddy.message)
                assertEquals(40L, teddy.convertStars)
                assertEquals(1_000, teddy.limitedTotal)
                assertNotNull(teddy.manageKey)
                assertContentEquals(fake.stickerBytes, repos.media.sticker(assertNotNull(teddy.sticker).key))

                val pepe = assertNotNull(first.gifts[1].unique)
                assertEquals("Plush Pepe" to 123, pepe.title to pepe.num)
                assertEquals("Gold", pepe.model?.name)
                assertEquals(GiftRarity.Permille(15), pepe.model?.rarity)
                assertEquals("Midnight", pepe.backdrop?.name)
                assertEquals(0x334455, pepe.backdrop?.centerColor)
                assertEquals(GiftRarity.Tier(GiftRarityTier.Rare), pepe.backdrop?.rarity)
                assertEquals("Star", pepe.symbol?.name)
                assertEquals(500 to 1_000, pepe.issued to pepe.total)
                assertEquals("Bob", first.gifts[1].fromName)

                val second = repos.gifts.gifts(null, assertNotNull(first.nextOffset))
                assertNull(second.nextOffset)
                val hidden = second.gifts.single()
                assertTrue(hidden.nameHidden)
                assertNull(hidden.fromName)
                assertFalse(hidden.saved)
                repos.gifts.setSaved(assertNotNull(hidden.manageKey), saved = true)
                assertFalse(fake.ownGifts[2].unsaved)
                assertTrue(repos.gifts.gifts(null, "2").gifts.single().saved)

                val catalogue = repos.gifts.catalogue()
                assertEquals(listOf(50L, 2_500L), catalogue.map { it.stars })
                assertTrue(catalogue[1].soldOut)
                assertEquals(120 to 1_000, catalogue[0].remains to catalogue[0].total)
                assertEquals(catalogue, repos.gifts.catalogue())
                assertEquals(listOf(0, FakeTelegram.STAR_GIFTS_HASH), fake.starGiftHashes.toList())

                val alice = repos.dialogs.dialogs().dialogs.single { it.title == "Alice Example" }
                val (pinned, theirs) = repos.gifts.gifts(alice.key).gifts
                assertTrue(pinned.pinned)
                assertFalse(theirs.pinned)
                assertEquals("Test User", theirs.fromName)
                assertNull(theirs.manageKey)

                fake.addGiftServiceMessage()
                val service = repos.messages.history("self").last()
                assertEquals(ServiceKind.Gift, service.service)
                val gift = assertNotNull(service.gift)
                assertEquals(50L, gift.stars)
                assertEquals("for you", gift.message)
                assertEquals("doc${FakeTelegram.GIFT_STICKERS[0]}", gift.sticker?.key)
                assertEquals("Alice", service.senderName)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun giftCollectionsAndTheirPages(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("9996624242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "22222")).profile.id

                val collection = repos.gifts.collections(null).single()
                assertEquals(FakeTelegram.GIFT_COLLECTION to "Favourites", collection.id to collection.title)
                assertEquals(1, collection.count)
                assertContentEquals(fake.stickerBytes, repos.media.sticker(assertNotNull(collection.icon).key))

                assertEquals(3, repos.gifts.gifts(null).count)
                val favourites = repos.gifts.gifts(null, collectionId = FakeTelegram.GIFT_COLLECTION)
                assertEquals(1, favourites.count)
                assertEquals("Plush Pepe", favourites.gifts.single().unique?.title)

                val alice = repos.dialogs.dialogs().dialogs.single { it.title == "Alice Example" }
                assertTrue(repos.gifts.collections(alice.key).isEmpty())
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}
