package app.miogram

import androidx.test.core.app.ApplicationProvider
import app.miogram.data.AvatarStore
import app.miogram.data.DialogsState
import app.miogram.data.DialogsStore
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.TelegramClientHolder
import cn.ellerotta.core.database.ChatDatabase
import cn.ellerotta.ktproto.api.LAYER
import cn.ellerotta.ktproto.client.InMemorySessionStorage
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalDatabaseFlowTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private class App(val repos: TestRepositories, val store: DialogsStore, val scope: CoroutineScope) {
        suspend fun close() {
            scope.cancel()
            repos.telegram.close()
        }
    }

    private fun start(dir: File, fake: FakeTelegram, session: InMemorySessionStorage, database: ChatDatabase): App {
        val telegram = TelegramClientHolder(dir, database = database) { id, hash, logger -> fake.config(id, hash, logger, session) }
        val repos = TestRepositories(dir, fake, telegram)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        return App(repos, DialogsStore(repos.dialogs, repos.updates, AvatarStore(repos.media, scope), scope), scope)
    }

    private suspend fun App.logIn(fake: FakeTelegram) {
        repos.telegram.connect(12345, "hash")
        fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
        repos.updates.updateState.first { it != null }
        store.start()
        store.until { it.loaded }
        delay(300)
    }

    @Test
    fun aRestartWithoutTheNetworkShowsTheStoredChatsAndHistory(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val file = File(dir, "chats.db")
        val session = InMemorySessionStorage()
        FakeTelegram().use { fake ->
            withTimeout(90_000) {
                fake.addSavedMessages(120)
                var database = ChatDatabase.open(context, file)
                val first = start(dir, fake, session, database)
                first.logIn(fake)
                val saved = "u${fake.selfId}"

                val newest = first.repos.messages.history(saved, 50)
                val second = first.repos.messages.history(saved, 50, before = newest.first().id)
                val third = first.repos.messages.history(saved, 50, before = second.first().id)
                assertEquals(20, third.size)
                assertEquals(3, fake.historyCalls.size)
                assertEquals(emptyList(), first.repos.messages.history("u501"))
                val dao = database.chats()
                assertEquals(120, dao.span(saved, 0).count)
                assertTrue(dao.history(saved, 0)!!.complete)

                assertEquals(second.map { it.id }, first.repos.messages.history(saved, 50, before = newest.first().id).map { it.id })
                assertEquals(4, fake.historyCalls.size)

                fake.cluster.updates.message(502, "in the archive")
                val pushed = fake.cluster.updates.message(501, "while you look")
                first.store.until { s -> s.dialogs.first { it.key == "u501" }.unread == 2 }
                until { dao.dialogsOf(listOf("u501")).single().let { it.topMessage == pushed.id && it.unread == 2 } }
                until { dao.message("u501", pushed.id).isNotEmpty() }
                val rows = first.store.state.value.dialogs.map { it.key }
                first.close()
                database.close()
                println("database for the fake account: ${file.length()} bytes, wal ${File(dir, "chats.db-wal").length()} bytes")

                fake.close()
                database = ChatDatabase.open(context, file)
                val offline = start(dir, fake, session, database)
                assertTrue(offline.repos.telegram.prepare(12345, "hash"))
                offline.store.start()
                val shown = offline.store.until { it.dialogs.isNotEmpty() }
                assertFalse(shown.loaded)
                assertEquals(rows, shown.dialogs.map { it.key })
                val alice = shown.dialogs.first { it.key == "u501" }
                assertEquals("while you look", alice.preview.text)
                assertEquals(2, alice.unread)
                assertEquals("Alice Example", alice.title)
                assertEquals(listOf("u502"), shown.archived.map { it.key })
                assertEquals(listOf("", "Contacts"), shown.folders.map { it.title })

                val history = assertNotNull(offline.repos.messages.stored(saved, 50))
                assertEquals(newest.map { it.id to it.text }, history.messages.map { it.id to it.text })
                assertFalse(history.complete)
                assertEquals(second.map { it.id }, offline.repos.messages.history(saved, 50, before = history.messages.first().id).map { it.id })
                assertEquals(third.map { it.id }, offline.repos.messages.history(saved, 50, before = second.first().id).map { it.id })
                assertEquals(listOf("while you look"), assertNotNull(offline.repos.messages.stored("u501")).messages.map { it.text })
                assertEquals(4, fake.historyCalls.size)
                offline.close()
                database.close()
            }
        }
        dir.deleteRecursively()
    }

    @Test
    fun logOutsClearTheDatabase(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val database = ChatDatabase.inMemory(context)
        val dao = database.chats()
        FakeTelegram().use { fake ->
            withTimeout(60_000) {
                val app = start(dir, fake, InMemorySessionStorage(), database)
                app.logIn(fake)
                app.repos.messages.history("u${fake.selfId}")
                assertTrue(dao.dialogs(LAYER).isNotEmpty())
                assertEquals(fake.selfId.toString(), dao.meta("owner"))

                app.repos.auth.logOut()
                assertEquals(emptyList(), dao.dialogs(LAYER))
                assertNull(dao.history("u${fake.selfId}", 0))
                assertEquals(emptyList(), dao.folders(LAYER))
                assertNull(dao.meta("owner"))
                assertNull(app.repos.dialogs.stored())
                app.store.clear()

                app.logIn(fake)
                assertTrue(dao.dialogs(LAYER).isNotEmpty())
                fake.revokeSession()
                until { dao.dialogs(LAYER).isEmpty() && dao.meta("owner") == null }
                app.close()
            }
        }
        database.close()
        dir.deleteRecursively()
    }

    @Test
    fun differenceTooLongInvalidatesTheStoredHistories(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val database = ChatDatabase.inMemory(context)
        val dao = database.chats()
        FakeTelegram().use { fake ->
            withTimeout(60_000) {
                fake.addSavedMessages(120)
                val app = start(dir, fake, InMemorySessionStorage(), database)
                app.logIn(fake)
                val saved = "u${fake.selfId}"
                val newest = app.repos.messages.history(saved, 50)
                app.repos.messages.history(saved, 50, before = newest.first().id)
                assertEquals(100, dao.span(saved, 0).count)

                fake.cluster.updates.tooLongAfter = 0
                fake.cluster.updates.message(501, "while away", push = false)
                fake.cluster.updates.tooLong()
                until { dao.history(saved, 0)!!.stale }
                assertEquals(newest.map { it.id }, assertNotNull(app.repos.messages.stored(saved, 50)).messages.map { it.id })
                val calls = fake.historyCalls.size
                app.repos.messages.history(saved, 50, before = newest.first().id)
                assertEquals(calls + 1, fake.historyCalls.size)

                app.repos.messages.history(saved, 50)
                assertEquals(50, dao.span(saved, 0).count)
                assertEquals(newest.first().id, dao.span(saved, 0).low)
                assertFalse(dao.history(saved, 0)!!.stale)
                app.close()
            }
        }
        database.close()
        dir.deleteRecursively()
    }

    private suspend fun DialogsStore.until(condition: (DialogsState) -> Boolean): DialogsState = withTimeout(15_000) { state.first(condition) }

    private suspend fun until(condition: suspend () -> Boolean) = withTimeout(15_000) {
        while (!condition()) delay(50)
    }
}
