package app.miogram

import androidx.test.core.app.ApplicationProvider
import app.miogram.data.telegram.ContactsRepository
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.TelegramClientHolder
import cn.ellerotta.core.database.ChatDatabase
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.sortedByPresence
import cn.ellerotta.ktproto.api.types.UserStatusOnline
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContactsRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun listHashStoreAddDeleteAndSearch(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val database = ChatDatabase.inMemory(context)
        FakeTelegram().use { fake ->
            val telegram = TelegramClientHolder(dir, database = database) { id, hash, logger -> fake.config(id, hash, logger) }
            val repos = TestRepositories(dir, fake, telegram)
            withTimeout(60_000) {
                telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                val contacts = repos.contacts
                assertEquals(emptyList(), contacts.stored())

                val list = contacts.contacts().sortedByPresence()
                assertEquals(listOf("Erin Online", "Frank", "Bob"), list.map { it.name })
                assertEquals(listOf(Presence.Online, Presence.Offline(FakeTelegram.NOW), Presence.LastWeek), list.map { it.presence })
                assertEquals(listOf(true, false, false), list.map { it.mutual })
                assertEquals("15550002222", list[1].phone)
                assertEquals(listOf(0L), fake.contacts.getCalls)

                fake.contacts.statuses[fake.bob.id] = UserStatusOnline(Int.MAX_VALUE)
                val again = contacts.contacts()
                assertEquals(listOf(0L, fake.contacts.hash()), fake.contacts.getCalls)
                assertEquals(Presence.Online, again.first { it.key == "u${fake.bob.id}" }.presence)

                val restarted = ContactsRepository(telegram)
                assertEquals(setOf("u502", "u506", "u507"), restarted.stored().map { it.key }.toSet())
                assertEquals(setOf("u502", "u506", "u507"), database.chats().contacts(cn.ellerotta.ktproto.api.LAYER).map { it.peer }.toSet())

                assertNull(contacts.import("+1 555 000 9999", "Nobody", ""))
                val carol = assertNotNull(contacts.import("+1 555 000 1111", "Carol", "Writer"))
                assertEquals("u${fake.carol.id}", carol.key)
                assertEquals("15550001111", fake.contacts.imports.last().contacts.single().phone)
                assertTrue("u${fake.carol.id}" in contacts.stored().map { it.key })

                assertEquals(listOf("Alice Example"), contacts.search("ali").map { it.name })
                assertEquals(emptyList(), contacts.search("bo"))

                contacts.add("u${fake.alice.id}", "Alice", "Example", sharePhone = true)
                val added = fake.contacts.adds.single()
                assertTrue(added.addPhonePrivacyException)
                assertEquals("Alice", added.firstName)
                assertTrue("u${fake.alice.id}" in contacts.stored().map { it.key })

                contacts.delete(listOf("u${fake.bob.id}"))
                assertEquals(listOf(listOf(fake.bob.id)), fake.contacts.deletes)
                assertFalse("u${fake.bob.id}" in contacts.stored().map { it.key })
                assertFalse("u${fake.bob.id}" in restarted.contacts().map { it.key })
            }
            telegram.close()
        }
        database.close()
        dir.deleteRecursively()
    }
}
