package app.miogram

import android.Manifest
import android.app.Application
import android.app.NotificationManager
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
import cn.ellerotta.ktproto.session.DcAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PushServiceTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private class Stack(val telegram: TelegramClientHolder, val repos: TestRepositories, val push: PushHandler)

    private fun stack(fake: FakeTelegram, dir: File, scope: CoroutineScope, storage: FileSessionStorage): Stack {
        val telegram = TelegramClientHolder(dir, chatMutes = ChatMutes(context)) { id, hash, logger -> fake.config(id, hash, logger, storage) }
        val repos = TestRepositories(dir, fake, telegram)
        val settings = SettingsStore(context)
        val onScreen = ChatOnScreen(Navigator(), AppLock(settings, FakeDeviceAuth()))
        val notifier = PushNotifier(context, settings, NotificationHistory(context), StoredNotificationPeers(NotificationAvatars(dir), repos.notifySettings), onScreen, WorkNotificationPreviews(context))
        val session = BackgroundSession(telegram, ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test"))
        return Stack(telegram, repos, PushHandler(repos.push, { "fcm-token" }, notifier, repos.updates, keptConnected(telegram, scope), session, context, repos.notifySettings, scope))
    }

    private suspend fun FakeTelegram.earlierRun(dir: File, scope: CoroutineScope, storage: FileSessionStorage): Long {
        val app = stack(this, dir, scope, storage)
        app.telegram.connect(12345, "hash")
        val request = app.repos.auth.sendCode("+999 66 1 4242")
        selfId = assertIs<SignInResult.LoggedIn>(app.repos.auth.signIn(request, "11111")).profile.id
        app.repos.push.registerPush("fcm-token")
        app.push.onMessage(cluster.push.fcmData("fcm-token", """{"loc_key":"MESSAGE_TEXT","loc_args":["Alice Liddell","before"],"custom":{"from_id":"501","msg_id":"1"}}"""))
        app.telegram.close()
        return selfId
    }

    private fun <T> world(test: suspend FakeTelegram.(File, CoroutineScope, FileSessionStorage) -> T) = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val result = FakeTelegram().use { fake -> withTimeout(90_000) { fake.test(dir, scope, FileSessionStorage(File(dir, "session"))) } }
        scope.cancel()
        dir.deleteRecursively()
        result
    }

    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Test
    fun aPushedAddressIsKeptForTheNextConnectionWithoutAClient(): Unit = world { dir, scope, storage ->
        earlierRun(dir, scope, storage)
        val accepted = cluster.connectionsAccepted()
        val cold = stack(this, dir, scope, storage)
        val before = assertNotNull(storage.load()).dcs

        cold.push.onMessage(cluster.push.fcmData("fcm-token", """{"loc_key":"DC_UPDATE","user_id":"$selfId","custom":{"dc":"2","addr":"203.0.113.9:5222"}}"""))
        assertEquals(DcAddress(2, "203.0.113.9", 5222, test = true), storage.load()?.dcs?.first())
        assertEquals(before.size + 1, storage.load()?.dcs?.size)
        assertEquals(accepted, cluster.connectionsAccepted(), "stored, not dialed")
        assertFalse(cold.telegram.isPrepared)

        for (bad in listOf("""{"dc":"2"}""", """{"dc":"2","addr":"not an address"}""", """{"dc":"0","addr":"203.0.113.10:443"}""", """{"addr":"203.0.113.11:443"}""")) {
            cold.push.onMessage(cluster.push.fcmData("fcm-token", """{"loc_key":"DC_UPDATE","user_id":"$selfId","custom":$bad}"""))
        }
        assertEquals(before.size + 1, storage.load()?.dcs?.size)
        assertEquals("203.0.113.9", storage.load()?.dcs?.first()?.host)

        assertEquals(true, cold.telegram.prepare(12345, "hash"))
        cold.telegram.close()
    }

    @Test
    fun aPushedAddressGoesIntoTheRunningClient(): Unit = world { dir, scope, storage ->
        earlierRun(dir, scope, storage)
        val app = stack(this, dir, scope, storage)
        assertEquals(true, app.telegram.prepare(12345, "hash"))
        app.push.onMessage(cluster.push.fcmData("fcm-token", """{"loc_key":"DC_UPDATE","custom":{"dc":"1","addr":"203.0.113.8:443"}}"""))
        assertEquals(DcAddress(1, "203.0.113.8", 443, test = true), app.telegram.requireClient().dcs.first())
        assertEquals(DcAddress(1, "203.0.113.8", 443, test = true), storage.load()?.dcs?.first(), "saved with the session")
        app.telegram.close()
    }

    @Test
    fun aRevokedSessionEndsTheLoginWithoutAClientAndClearsTheNotifications(): Unit = world { dir, scope, storage ->
        val me = earlierRun(dir, scope, storage)
        assertNotNull(notifications.getNotification("u501", PushNotifier.CHAT_ID), "the earlier run left a notification")
        val accepted = cluster.connectionsAccepted()
        val cold = stack(this, dir, scope, storage)

        cold.push.onMessage(cluster.push.fcmData("fcm-token", """{"loc_key":"SESSION_REVOKE","user_id":"${me + 1}"}"""))
        assertNotNull(storage.load()?.userId, "the login stays")
        assertNotNull(notifications.getNotification("u501", PushNotifier.CHAT_ID))

        val secret = assertNotNull(storage.load()?.pushSecret)
        val payload = cluster.push.fcmData("fcm-token", """{"loc_key":"SESSION_REVOKE","user_id":"$me"}""")
        cold.push.onMessage(payload)
        assertNull(storage.load(), "the stored session is gone")
        assertNull(notifications.getNotification("u501", PushNotifier.CHAT_ID), "its notifications went")
        assertEquals(accepted, cluster.connectionsAccepted(), "nothing was sent to telegram")
        assertFalse(cold.telegram.isPrepared)
        assertTrue(secret.isNotEmpty())

        cold.push.onMessage(cluster.push.fcmData("fcm-token", """{"loc_key":"MESSAGE_TEXT","loc_args":["Alice Liddell","after"],"custom":{"from_id":"501","msg_id":"2"}}"""))
        assertEquals(0, notifications.size())
    }

    @Test
    fun aRevokedSessionEndsTheRunningClientsLogin(): Unit = world { dir, scope, storage ->
        val me = earlierRun(dir, scope, storage)
        val app = stack(this, dir, scope, storage)
        assertEquals(true, app.telegram.prepare(12345, "hash"))
        val ended = CoroutineScope(Dispatchers.Default).async { app.repos.auth.sessionEnded.first() }

        app.push.onMessage(cluster.push.fcmData("fcm-token", """{"loc_key":"SESSION_REVOKE","user_id":"$me"}"""))
        withTimeout(20_000) { ended.await() }
        assertFalse(app.telegram.isAuthorized)
        assertNull(storage.load()?.userId)
        assertNull(storage.load()?.pushSecret)
        assertNull(notifications.getNotification("u501", PushNotifier.CHAT_ID))
        app.telegram.close()
    }
}
