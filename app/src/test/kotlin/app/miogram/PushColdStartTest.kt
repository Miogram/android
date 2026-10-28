package app.miogram

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.miogram.data.SettingsStore
import app.miogram.data.telegram.ChatMutes
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.TelegramClientHolder
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
import cn.ellerotta.ktproto.client.FileSessionStorage
import cn.ellerotta.ktproto.client.SessionStorage
import cn.ellerotta.ktproto.client.StoredSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PushColdStartTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun aPushWithoutAClientShowsTheNotificationAndOpensNoConnection(): Unit = runBlocking {
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        val dir = Files.createTempDirectory("poc").toFile()
        val file = FileSessionStorage(File(dir, "session"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        FakeTelegram().use { fake ->
            withTimeout(90_000) {
                val app = TelegramClientHolder(dir, chatMutes = ChatMutes(context)) { id, hash, logger -> fake.config(id, hash, logger, file) }
                val repos = TestRepositories(dir, fake, app)
                app.connect(12345, "hash")
                val request = repos.auth.sendCode("+999 66 1 4242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "11111")).profile.id
                repos.push.registerPush("fcm-token")
                repos.updates.updateState.first { it != null }
                repos.dialogs.dialogs()
                repos.dialogs.setMuted("u502", true)
                app.close()
                assertNotNull(file.load()?.pushSecret)

                val loads = AtomicInteger()
                val counting = object : SessionStorage by file {
                    override suspend fun load(): StoredSession? = file.load().also { loads.incrementAndGet() }
                }
                val watchers = AtomicInteger()
                val cold = TelegramClientHolder(dir, networkWatcher = { watchers.incrementAndGet(); null }, chatMutes = ChatMutes(context)) { id, hash, logger -> fake.config(id, hash, logger, counting) }
                val coldRepos = TestRepositories(dir, fake, cold)
                val settings = SettingsStore(context)
                val onScreen = ChatOnScreen(Navigator(), AppLock(settings, FakeDeviceAuth()))
                val notifier = PushNotifier(context, settings, NotificationHistory(context), StoredNotificationPeers(NotificationAvatars(dir), coldRepos.notifySettings), onScreen, WorkNotificationPreviews(context))
                val session = BackgroundSession(cold, ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test"))
                val push = PushHandler(coldRepos.push, { "fcm-token" }, notifier, coldRepos.updates, keptConnected(cold, scope), session, context, coldRepos.notifySettings, scope)
                val accepted = fake.cluster.connectionsAccepted()

                push.onMessage(fake.cluster.push.fcmData("fcm-token", """{"loc_key":"MESSAGE_TEXT","loc_args":["Alice Liddell","are you up"],"custom":{"from_id":"501","msg_id":"71"}}"""))
                push.onMessage(fake.cluster.push.fcmData("fcm-token", """{"loc_key":"MESSAGE_TEXT","loc_args":["Bob","muted chatter"],"custom":{"from_id":"502","msg_id":"72"}}"""))
                push.onMessage(fake.cluster.push.fcmData("fcm-token", """{"loc_key":"MESSAGE_TEXT","loc_args":["Alice Liddell","hello?"],"custom":{"from_id":"501","msg_id":"73"}}"""))

                val shown = assertNotNull(notifications.getNotification("u501", PushNotifier.CHAT_ID))
                assertEquals(listOf("are you up", "hello?"), assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(shown)).messages.map { it.text.toString() })
                assertNull(notifications.getNotification("u502", PushNotifier.CHAT_ID), "bob's chat is muted, which the process knew without a database")
                assertEquals(accepted, fake.cluster.connectionsAccepted(), "no connection was opened")
                assertFalse(cold.isPrepared, "no client was built")
                assertNull(cold.updates.value, "no updates manager")
                assertEquals(0, watchers.get(), "no network callback was registered")
                assertEquals(1, loads.get(), "the session file was read once for three pushes")
            }
        }
        scope.cancel()
        dir.deleteRecursively()
    }
}
