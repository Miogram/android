package app.miogram

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.miogram.connection.ConnectionKeeper
import app.miogram.data.AvatarStore
import app.miogram.data.DialogsStore
import app.miogram.data.SettingsStore
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.SignInResult
import app.miogram.lock.AppLock
import app.miogram.nav.Navigator
import app.miogram.push.BackgroundSession
import app.miogram.push.ChatOnScreen
import app.miogram.push.NotificationAvatars
import app.miogram.push.NotificationHistory
import app.miogram.push.PushHandler
import app.miogram.push.PushNotifier
import app.miogram.push.StoredNotificationPeers
import app.miogram.push.WorkNotificationPreviews
import cn.ellerotta.ktproto.api.types.NotifyBroadcasts
import cn.ellerotta.ktproto.api.types.NotifyChats
import cn.ellerotta.ktproto.api.types.NotifyPeer
import cn.ellerotta.ktproto.api.types.PeerNotifySettings
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.UpdateNotifySettings
import cn.ellerotta.ktproto.api.types.UpdateShort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MutedChatsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notifications: ShadowNotificationManager get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private class World(val fake: FakeTelegram, val repos: TestRepositories, val store: DialogsStore, val connection: ConnectionKeeper, val notifier: PushNotifier, val push: PushHandler)

    private fun world(server: FakeTelegram.() -> Unit = {}, test: suspend World.() -> Unit) = runBlocking<Unit> {
        val dir = Files.createTempDirectory("poc").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        FakeTelegram().use { fake ->
            fake.server()
            val repos = TestRepositories(dir, fake)
            val settings = SettingsStore(context)
            val store = DialogsStore(repos.dialogs, repos.updates, AvatarStore(repos.media, scope), scope)
            val session = BackgroundSession(repos.telegram, ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test"))
            val connection = keptConnected(repos.telegram, scope)
            val onScreen = ChatOnScreen(Navigator(), AppLock(settings, FakeDeviceAuth()))
            val notifier = PushNotifier(context, settings, NotificationHistory(context), StoredNotificationPeers(NotificationAvatars(dir), repos.notifySettings), onScreen, WorkNotificationPreviews(context))
            val push = PushHandler(repos.push, { "fcm-token" }, notifier, repos.updates, connection, session, context, repos.notifySettings, scope)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("+999 66 1 4242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "11111")).profile.id
                push.register()
                repos.updates.updateState.first { it != null }
                store.start()
                store.state.first { it.loaded }
                connection.foreground(true)
                World(fake, repos, store, connection, notifier, push).test()
                push.unregister()
            }
            repos.telegram.close()
        }
        scope.cancel()
        dir.deleteRecursively()
    }

    private suspend fun World.aliceWrites(text: String) {
        fake.cluster.updates.message(501, text)
        withTimeout(20_000) { while (text !in texts("u501")) delay(50) }
    }

    private fun texts(key: String): List<String> = notifications.getNotification(key, PushNotifier.CHAT_ID)
        ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }?.messages.orEmpty().map { it.text.toString() }

    private fun World.fcm(json: String) = fake.cluster.push.fcmData("fcm-token", json)

    @Test
    fun aMutedChatBeyondTheLoadedPageGivesNoLiveNotification(): Unit = world {
        fake.muted += 9_100L
        fake.cluster.updates.groupMessage(9_100, 502, "chatter in a muted group")
        aliceWrites("after the group")
        assertNull(notifications.getNotification("ch9100", PushNotifier.CHAT_ID), "a muted group beyond the loaded page stays quiet")
        assertEquals(listOf("after the group"), texts("u501"), "an unmuted chat still notifies")
    }

    @Test
    fun aGroupMutedOnlyByTheDefaultGivesNone(): Unit = world(server = { defaultMutes["chats"] = Int.MAX_VALUE }) {
        fake.cluster.updates.groupMessage(9_101, 502, "chatter in a group muted by default")
        aliceWrites("private chats still notify")
        assertNull(notifications.getNotification("ch9101", PushNotifier.CHAT_ID), "groups follow the default for groups")
        assertEquals(listOf("private chats still notify"), texts("u501"))

        fake.defaultMutes["chats"] = 0
        fake.cluster.updates.push(UpdateShort(UpdateNotifySettings(NotifyChats, PeerNotifySettings(muteUntil = 0)), FakeTelegram.NOW))
        aliceWrites("after the default changed")
        fake.cluster.updates.groupMessage(9_101, 502, "now it notifies")
        withTimeout(20_000) { while ("now it notifies" !in texts("ch9101")) delay(50) }
    }

    @Test
    fun channelsAndSupergroupsFollowTheirOwnDefaults(): Unit = world(server = { defaultMutes["broadcasts"] = Int.MAX_VALUE }) {
        push.onMessage(fcm("""{"loc_key":"CHANNEL_MESSAGE_TEXT","loc_args":["News","a post"],"custom":{"channel_id":"9200","msg_id":"3"}}"""))
        assertNull(notifications.getNotification("ch9200", PushNotifier.CHAT_ID), "a channel follows the default for channels")
        push.onMessage(fcm("""{"loc_key":"CHAT_MESSAGE_TEXT","loc_args":["Bob","Supergroup","hello"],"custom":{"channel_id":"9201","chat_from_id":"502","msg_id":"4"}}"""))
        assertEquals(listOf("hello"), texts("ch9201"))

        fake.defaultMutes["broadcasts"] = 0
        fake.cluster.updates.push(UpdateShort(UpdateNotifySettings(NotifyBroadcasts, PeerNotifySettings(muteUntil = 0)), FakeTelegram.NOW))
        aliceWrites("after the default for channels changed")
        push.onMessage(fcm("""{"loc_key":"CHANNEL_MESSAGE_TEXT","loc_args":["News","post after the default ended"],"custom":{"channel_id":"9200","msg_id":"6"}}"""))
        assertEquals(listOf("post after the default ended"), texts("ch9200"))
    }

    @Test
    fun aPushForAChatTheUserJustMutedIsDropped(): Unit = world {
        push.onMessage(fcm("""{"loc_key":"MESSAGE_TEXT","loc_args":["Alice Example","before the mute"],"custom":{"from_id":"501","msg_id":"41"}}"""))
        assertEquals(listOf("before the mute"), texts("u501"))

        repos.dialogs.setMuted("u501", true)
        store.change("u501") { it.copy(muted = true) }
        push.onMessage(fcm("""{"loc_key":"MESSAGE_TEXT","loc_args":["Alice Example","after the mute"],"custom":{"from_id":"501","msg_id":"42"}}"""))
        assertEquals(listOf("before the mute"), texts("u501"), "the push of a chat muted since is dropped")

        val unmuted = UpdateNotifySettings(NotifyPeer(PeerUser(501)), PeerNotifySettings(muteUntil = 0))
        fake.cluster.updates.push(UpdateShort(unmuted, FakeTelegram.NOW))
        withTimeout(20_000) { while (store.state.value.dialogs.first { it.key == "u501" }.muted) delay(50) }
        push.onMessage(fcm("""{"loc_key":"MESSAGE_TEXT","loc_args":["Alice Example","unmuted elsewhere"],"custom":{"from_id":"501","msg_id":"43"}}"""))
        assertEquals(listOf("before the mute", "unmuted elsewhere"), texts("u501"))
    }

    @Test
    fun aMutedChatWithAMentionFollowsTheMentionRule(): Unit = world {
        fake.muted += 9_102L
        fake.cluster.updates.groupMessage(9_102, 502, "chatter")
        aliceWrites("sentinel")
        assertNull(notifications.getNotification("ch9102", PushNotifier.CHAT_ID), "a muted group's messages stay quiet")

        push.onMessage(fcm("""{"loc_key":"CHAT_MESSAGE_TEXT","loc_args":["Bob","Group 9102","more chatter"],"custom":{"channel_id":"9102","chat_from_id":"502","msg_id":"7"}}"""))
        assertNull(notifications.getNotification("ch9102", PushNotifier.CHAT_ID), "a push without a mention is dropped")

        push.onMessage(fcm("""{"loc_key":"CHAT_MESSAGE_TEXT","loc_args":["Bob","Group 9102","@test look"],"custom":{"channel_id":"9102","chat_from_id":"502","msg_id":"8","mention":"1"}}"""))
        assertEquals(listOf("@test look"), texts("ch9102"))
        assertEquals(Notification.GROUP_ALERT_ALL, notifications.getNotification("ch9102", PushNotifier.CHAT_ID).groupAlertBehavior)
        push.onMessage(fcm("""{"loc_key":"CHAT_MESSAGE_TEXT","loc_args":["Bob","Group 9102","@test quietly"],"custom":{"channel_id":"9102","chat_from_id":"502","msg_id":"9","mention":"1","silent":"1"}}"""))
        assertEquals(listOf("@test look", "@test quietly"), texts("ch9102"))
        assertEquals(Notification.GROUP_ALERT_SUMMARY, notifications.getNotification("ch9102", PushNotifier.CHAT_ID).groupAlertBehavior)
    }
}
