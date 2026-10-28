package app.miogram

import app.miogram.data.AvatarStore
import app.miogram.data.DialogsState
import app.miogram.data.DialogsStore
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.ktproto.api.types.DialogPeer
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.UpdateDialogPinned
import cn.ellerotta.ktproto.api.types.UpdateReadHistoryInbox
import cn.ellerotta.ktproto.api.types.UpdateShort
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class DialogsLiveTest {
    @Test
    fun followsMessagesReadsAndPinsWithoutReloading(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            fake.listed += listOf(601L, 602L, 603L).map(fake.cluster.updates::user)
            withTimeout(60_000) {
                val store = loggedIn(fake, repos, scope)
                val self = "u${fake.selfId}"

                fake.messageFromStranger(701, "hello, new here")
                val stranger = store.until { s -> s.dialogs.any { it.key == "u701" } }.dialogs.first()
                assertEquals("u701", stranger.key)
                assertEquals("hello, new here", stranger.preview.text)
                assertEquals(1, stranger.unread)
                assertEquals(listOf(listOf("u701")), fake.peerDialogsCalls)

                val senders = listOf(501L, 601L, 602L, 603L, 502L)
                val sent = (0 until 20).map { i -> fake.cluster.updates.message(senders[i % senders.size], "burst $i") }
                val burst = store.until { s -> s.archived.firstOrNull()?.preview?.text == "burst 19" && s.dialogs.firstOrNull()?.preview?.text == "burst 18" }
                assertEquals(listOf("u603", "u602", "u601", "u501", "u701", self), burst.dialogs.map { it.key })
                assertEquals(listOf(5, 5, 5, 5, 1, 0), burst.dialogs.map { it.unread })
                assertEquals(listOf("burst 18", "burst 17", "burst 16", "burst 15"), burst.dialogs.take(4).map { it.preview.text })
                assertEquals(sent[18].id, burst.dialogs.first().topMessage)
                assertEquals(5, burst.archived.single { it.key == "u502" }.unread)
                assertEquals(2, fake.dialogsCalls.get())
                assertEquals(1, fake.peerDialogsCalls.size)

                fake.cluster.updates.push(UpdateShort(UpdateDialogPinned(pinned = true, peer = DialogPeer(PeerUser(602))), FakeTelegram.NOW))
                val pinned = store.until { s -> s.dialogs.first().key == "u602" }
                assertTrue(pinned.dialogs.first().pinned)
                assertEquals(listOf("u602", "u603", "u601", "u501", "u701", self), pinned.dialogs.map { it.key })

                fake.cluster.updates.deleteMessages(listOf(sent[15].id))
                val deleted = store.until { s -> s.dialogs.single { it.key == "u501" }.preview.text == "hi from alice" }
                assertEquals(listOf("u501"), fake.peerDialogsCalls.last())
                assertEquals(listOf("u602", "u603", "u601", "u701", "u501", self), deleted.dialogs.map { it.key })

                val read = UpdateReadHistoryInbox(peer = PeerUser(603), maxId = sent[13].id, stillUnreadCount = 2, pts = fake.cluster.updates.pts + 1, ptsCount = 1)
                fake.cluster.updates.push(UpdateShort(read, FakeTelegram.NOW))
                store.until { s -> s.dialogs.single { it.key == "u603" }.unread == 2 }

                assertEquals(2, fake.dialogsCalls.get())
                assertEquals(2, fake.peerDialogsCalls.size)
            }
            scope.cancel()
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun aResyncReloadsAtMostEveryTenSeconds(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            withTimeout(60_000) {
                val started = TimeSource.Monotonic.markNow()
                val store = loggedIn(fake, repos, scope)
                assertEquals(2, fake.dialogsCalls.get())

                fake.cluster.updates.tooLongAfter = 0
                fake.cluster.updates.message(501, "while away", push = false)
                fake.cluster.updates.tooLong()
                while (fake.dialogsCalls.get() < 4) delay(100)
                assertTrue(started.elapsedNow() >= 10.seconds)
                store.until { !it.refreshing }
                delay(1_000)
                assertEquals(4, fake.dialogsCalls.get())
            }
            scope.cancel()
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    private suspend fun loggedIn(fake: FakeTelegram, repos: TestRepositories, scope: CoroutineScope): DialogsStore {
        repos.telegram.connect(12345, "hash")
        fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
        repos.updates.updateState.first { it != null }
        val store = DialogsStore(repos.dialogs, repos.updates, AvatarStore(repos.media, scope), scope)
        store.start()
        store.until { it.loaded }
        delay(300)
        return store
    }

    private suspend fun DialogsStore.until(condition: (DialogsState) -> Boolean): DialogsState = withTimeout(15_000) { state.first(condition) }
}
