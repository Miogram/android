package app.miogram

import androidx.test.core.app.ApplicationProvider
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.TelegramClientHolder
import cn.ellerotta.core.database.ChatDatabase
import cn.ellerotta.ktproto.client.InMemorySessionStorage
import java.io.File
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileCacheTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun start(dir: File, fake: FakeTelegram, session: InMemorySessionStorage, database: ChatDatabase): TestRepositories =
        TestRepositories(dir, fake, TelegramClientHolder(dir, database = database) { id, hash, logger -> fake.config(id, hash, logger, session) })

    @Test
    fun profilesShowFromStorageWithoutTheNetwork(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val file = File(dir, "chats.db")
        val session = InMemorySessionStorage()
        val alice = "u501"
        FakeTelegram().use { fake ->
            withTimeout(90_000) {
                var database = ChatDatabase.open(context, file)
                val online = start(dir, fake, session, database)
                online.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(online.auth.signIn(online.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                online.updates.updateState.first { it != null }
                assertTrue(online.dialogs.dialogs().dialogs.any { it.key == alice })

                assertNull(online.profiles.stored(alice))
                val profile = online.profiles.profile(alice)
                val own = online.profiles.profile(null)
                val gifts = online.gifts.gifts(alice)
                online.gifts.gifts(alice, collectionId = FakeTelegram.GIFT_COLLECTION)
                val photos = online.profiles.photos(alice)
                val emoji = online.stickers.customEmoji(listOfNotNull(profile.emojiStatusId, profile.patternEmojiId))
                val avatarKey = assertNotNull(profile.avatarKey)
                val avatar = assertNotNull(online.media.avatar(avatarKey))
                val big = assertNotNull(online.media.avatar(avatarKey, big = true))
                online.telegram.close()
                database.close()

                fake.close()
                database = ChatDatabase.open(context, file)
                val offline = start(dir, fake, session, database)
                assertTrue(offline.telegram.prepare(12345, "hash"))

                assertContentEquals(avatar, offline.media.avatar(avatarKey))
                assertContentEquals(big, offline.media.avatar(avatarKey, big = true))
                val stored = assertNotNull(offline.profiles.stored(alice))
                assertEquals(profile, stored)
                assertEquals(own, offline.profiles.stored(null))
                assertEquals(own.id, offline.profiles.storedSelf()?.id)
                val storedGifts = assertNotNull(offline.gifts.stored(alice))
                assertEquals(gifts.count to gifts.gifts.map { Triple(it.key, it.unique?.num, it.sticker?.key) }, storedGifts.count to storedGifts.gifts.map { Triple(it.key, it.unique?.num, it.sticker?.key) })
                assertEquals(photos.map { it.key }, offline.profiles.storedPhotos(alice)?.map { it.key })
                assertEquals(emoji.keys, offline.stickers.customEmoji(emoji.keys).keys)

                offline.auth.forgetAccount()
                assertNull(offline.profiles.stored(alice))
                assertNull(offline.media.avatar(avatarKey))
                offline.telegram.close()
                database.close()
            }
        }
        dir.deleteRecursively()
    }
}
