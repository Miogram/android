package app.miogram

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.AvatarStore
import app.miogram.data.DialogsState
import app.miogram.data.DialogsStore
import app.miogram.data.SettingsStore
import app.miogram.data.telegram.OutgoingMedia
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.SignInResult
import app.miogram.lock.AppLock
import app.miogram.nav.Navigator
import app.miogram.push.BackgroundSession
import app.miogram.push.ChatOnScreen
import app.miogram.push.NotificationActions
import app.miogram.push.NotificationAvatars
import app.miogram.push.NotificationHistory
import app.miogram.push.PushHandler
import app.miogram.push.PushNotifier
import app.miogram.push.StoredNotificationPeers
import app.miogram.push.WorkNotificationPreviews
import cn.ellerotta.core.model.ContactCard
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.PollDraft
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.TypeUpdates
import cn.ellerotta.ktproto.api.types.UpdateNewMessage
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.client.UploadSource
import cn.ellerotta.ktproto.client.updates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SentInListTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    private class Sent(val fake: FakeTelegram, val repos: TestRepositories, val store: DialogsStore, val pushed: List<TypeUpdates>)

    private fun withList(block: suspend Sent.() -> Unit): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        FakeTelegram().use { fake ->
            fake.groups.inDialogs = true
            fake.groups.discussion.inDialogs = true
            val repos = TestRepositories(dir, fake)
            withTimeout(120_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                repos.updates.updateState.first { it != null }
                val pushed = CopyOnWriteArrayList<TypeUpdates>()
                scope.launch(start = CoroutineStart.UNDISPATCHED) { repos.telegram.requireClient().updates().collect { pushed += it } }
                val store = DialogsStore(repos.dialogs, repos.updates, AvatarStore(repos.media, scope), scope)
                store.start()
                withTimeout(15_000) { store.state.first { it.loaded } }
                delay(300)
                Sent(fake, repos, store, pushed).block()
            }
            repos.telegram.close()
        }
        scope.cancel()
        dir.deleteRecursively()
    }

    private suspend fun DialogsStore.until(condition: (DialogsState) -> Boolean): DialogsState = withTimeout(15_000) { state.first(condition) }

    private fun DialogsState.row(key: String): DialogItem? = (dialogs + archived).firstOrNull { it.key == key }

    private suspend fun Sent.moves(key: String, shows: (DialogItem) -> Boolean, strict: Boolean = true, send: suspend () -> Unit): DialogItem {
        val before = store.state.value.row(key)!!
        val asked = fake.cluster.updates.differenceRequests.size
        send()
        val pts = fake.cluster.updates.pts
        delay(700)
        val below = fake.cluster.updates.differenceRequests.drop(asked).filter { it < pts }
        if (strict) assertEquals(emptyList(), below, "no updates.getDifference fetched the send to $key (common pts $pts)")
        val state = withTimeoutOrNull(15_000) { store.state.first { s -> s.row(key)?.let { it.topMessage > before.topMessage && it.outgoing && shows(it) } == true } }
            ?: fail("$key did not move: ${store.state.value.row(key)}")
        val row = state.row(key)!!
        val list = if (row.archived) state.archived else state.dialogs
        assertEquals(key, list.first().key, "$key moved to the top of its list: ${list.map { it.key }}")
        assertTrue(row.date > before.date, "$key is dated like its new message")
        return row
    }

    @Test
    fun everyKindOfSendMovesItsRowAtOnce(): Unit = withList {
        val alice = "u501"
        val bob = "u502"
        val self = "u${fake.selfId}"
        val family = "c${FakeGroups.FAMILY_ID}"
        val devs = "ch${FakeGroups.DEVS_ID}"
        val blog = "ch${FakeDiscussion.BLOG_ID}"
        val blogChat = "ch${FakeDiscussion.CHAT_ID}"
        fun file(name: String, seed: Int) = OutgoingMedia(name, "application/octet-stream", UploadSource.of(Random(seed).nextBytes(2_000)))
        repos.messages.send(self, "warm up")
        repos.messages.sendFile(self, file("warm.bin", 0)).toList()
        delay(1_500)
        val dialogsCalls = fake.dialogsCalls.get()

        val text = moves(alice, { it.preview.text == "hello alice" }) { repos.messages.send(alice, "hello alice") }
        assertEquals(fake.lastSentId, text.topMessage)
        moves(family, { it.preview.text == "dinner is ready" }) { repos.messages.send(family, "dinner is ready") }

        moves(devs, { it.preview.text == "release is out" }) { repos.messages.send(devs, "release is out") }
        moves(blog, { it.preview.text == "a new post" }) { repos.messages.send(blog, "a new post") }
        moves(blogChat, { it.preview.text == "first comment here" }) { repos.messages.send(blogChat, "first comment here") }

        val sticker = repos.stickers.panel().recent.first()
        moves(alice, { it.preview.media == MediaKind.Sticker }) { repos.messages.sendDocument(alice, sticker) }
        val archived = moves(bob, { it.preview.media == MediaKind.File }) { repos.messages.sendFile(bob, file("notes.bin", 1)).toList() }
        assertTrue(archived.archived)
        val recording = Files.createTempFile("voice", ".ogg").toFile().apply { writeBytes(Random(2).nextBytes(5_000)) }
        moves(alice, { it.preview.media == MediaKind.Voice }) { repos.messages.sendVoice(alice, recording, 2, listOf(1, 2, 3)).toList() }
        moves(devs, { it.preview.media == MediaKind.File }) { repos.messages.sendAlbum(devs, listOf(file("a.bin", 3), file("b.bin", 4)), FormattedText("two parts")).toList() }

        moves(family, { it.preview.media == MediaKind.Poll }) { repos.messages.awaitOutgoing(repos.attach.sendPoll(family, PollDraft().withQuestion("pizza?").withOption(0, "yes").withOption(1, "no"))) }
        moves(alice, { it.preview.media == MediaKind.Location }) { repos.messages.awaitOutgoing(repos.attach.sendPlace(alice, GeoPlace(48.8566, 2.3522))) }
        moves(alice, { it.preview.media == MediaKind.Contact }) { repos.messages.awaitOutgoing(repos.attach.sendContact(alice, ContactCard("Ada", "Lovelace", "4479460000"))) }
        moves(alice, { it.preview.media == MediaKind.Location }) { repos.attach.startLive(alice, GeoPlace(48.8566, 2.3522), heading = null, seconds = 900, radius = null) }

        moves(alice, { it.preview.text == "Kotlin is a modern language" }) { repos.bots.sendInlineResult(alice, FakeBots.FIRST_QUERY, "kotlin") }
        val note = fake.addSaved { id -> Message(out = true, id = id, peerId = PeerUser(fake.selfId), date = FakeTelegram.NOW, message = "a note to pass on") }
        moves(family, { it.preview.text == "a note to pass on" }) { repos.messages.forward(self, listOf(note), family) }
        fake.sending.hold("later today", FakeTelegram.NOW + 3_600)
        moves(self, { it.preview.text == "later today" }) { repos.scheduled.sendNow(self, listOf(fake.sending.scheduled.single().id)) }

        assertEquals(emptyList(), pushed.toList(), "nothing was pushed")
        assertEquals(dialogsCalls, fake.dialogsCalls.get(), "the list was not loaded again")
        assertEquals(emptyList(), fake.peerDialogsCalls.toList(), "no row was fetched")

        moves(bob, { it.preview.text == "from my laptop" }) {
            val elsewhere = Message(out = true, id = fake.newMessageId(), fromId = PeerUser(fake.selfId), peerId = PeerUser(502), date = fake.sentDate(), message = "from my laptop")
            val pts = fake.cluster.updates.sent(elsewhere)
            fake.cluster.updates.push(Updates(listOf(UpdateNewMessage(elsewhere, pts, 1)), listOf(fake.bob), emptyList(), FakeTelegram.NOW, 0))
        }
    }

    @Test
    fun aReplyFromTheNotificationInTheBackgroundMovesItsRow(): Unit = withList {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val settings = SettingsStore(context)
        val session = BackgroundSession(repos.telegram, ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test"))
        val connection = keptConnected(repos.telegram, scope)
        val onScreen = ChatOnScreen(Navigator(), AppLock(settings, FakeDeviceAuth()))
        val notifier = PushNotifier(context, settings, NotificationHistory(context), StoredNotificationPeers(NotificationAvatars(context.filesDir), repos.notifySettings), onScreen, WorkNotificationPreviews(context))
        PushHandler(repos.push, { "fcm-token" }, notifier, repos.updates, connection, session, context, repos.notifySettings, scope)
        val actions = NotificationActions(notifier, repos.messages, repos.dialogs, store, session, connection, scope)

        connection.foreground(false)
        repos.telegram.disconnect()
        while (fake.cluster.servers.sumOf { it.openConnections() } > 0) delay(50)
        val row = moves("u501", { it.preview.text == "on my way" }, strict = false) { actions.reply("u501", "on my way ") }
        assertEquals("on my way", fake.lastSendMessage?.message)
        assertEquals(fake.lastSentId, row.topMessage)
        assertEquals(emptyList(), pushed.toList(), "nothing was pushed")
        assertEquals(emptyList(), fake.peerDialogsCalls.toList(), "the row was not fetched")
        scope.cancel()
    }
}
