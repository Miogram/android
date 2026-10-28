package app.miogram

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.miogram.data.SettingsStore
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
import cn.ellerotta.ktproto.client.InMemorySessionStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PushRegistrationTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun workManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private class App(val telegram: TelegramClientHolder, val repos: TestRepositories, val connection: app.miogram.connection.ConnectionKeeper, val push: PushHandler)

    private fun app(fake: FakeTelegram, dir: java.io.File, scope: CoroutineScope, session: InMemorySessionStorage, token: () -> String?): App {
        val repos = TestRepositories(dir, fake, TelegramClientHolder(dir) { id, hash, logger -> fake.config(id, hash, logger, session) })
        val settings = SettingsStore(context)
        val sessionOf = BackgroundSession(repos.telegram, ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test"))
        val connection = keptConnected(repos.telegram, scope)
        val onScreen = ChatOnScreen(Navigator(), AppLock(settings, FakeDeviceAuth()))
        val notifier = PushNotifier(context, settings, NotificationHistory(context), StoredNotificationPeers(NotificationAvatars(dir), repos.notifySettings), onScreen, WorkNotificationPreviews(context))
        val push = PushHandler(repos.push, { token() }, notifier, repos.updates, connection, sessionOf, context, repos.notifySettings, scope)
        return App(repos.telegram, repos, connection, push)
    }

    private suspend fun until(condition: () -> Boolean) = withTimeout(30_000) { while (!condition()) delay(20) }

    @Test
    fun registersByItselfAfterTheLoginAndAfterARestoreWhenDue(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = InMemorySessionStorage()
        var token: String? = "fcm-1"
        FakeTelegram().use { fake ->
            val requests = fake.cluster.push.registerRequests
            withTimeout(120_000) {
                val first = app(fake, dir, scope, session) { token }
                first.telegram.connect(12345, "hash")
                first.connection.foreground(true)
                delay(300)
                assertEquals(0, requests.size, "nothing to register before the login")

                val request = first.repos.auth.sendCode("+999 66 1 4242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(first.repos.auth.signIn(request, "11111")).profile.id
                until { requests.size == 1 }
                assertEquals("fcm-1", requests.single().token)
                assertEquals(true, requests.single().noMuted)

                val start = System.currentTimeMillis()
                first.push.clock = { start + TimeUnit.HOURS.toMillis(23) }
                first.connection.foreground(false)
                delay(200)
                first.connection.foreground(true)
                delay(500)
                assertEquals(1, requests.size, "registered 23 hours ago: not due")

                first.push.clock = { start + TimeUnit.HOURS.toMillis(25) }
                first.connection.foreground(false)
                delay(200)
                first.connection.foreground(true)
                until { requests.size == 2 }

                token = "fcm-2"
                first.connection.foreground(false)
                delay(200)
                first.connection.foreground(true)
                until { requests.size == 3 }
                assertEquals("fcm-2", requests.last().token)
                first.telegram.close()

                val restarted = app(fake, dir, scope, session) { token }
                restarted.push.clock = { start + TimeUnit.HOURS.toMillis(26) }
                assertEquals(true, restarted.telegram.prepare(12345, "hash"))
                restarted.connection.foreground(true)
                delay(500)
                assertEquals(3, requests.size, "a restored session registered a day ago: not due")
                restarted.telegram.close()

                val later = app(fake, dir, scope, session) { token }
                later.push.clock = { start + TimeUnit.HOURS.toMillis(50) }
                assertEquals(true, later.telegram.prepare(12345, "hash"))
                later.connection.foreground(true)
                until { requests.size == 4 }
                assertEquals("fcm-2", requests.last().token)
                later.push.unregister()
                later.telegram.close()
            }
        }
        scope.cancel()
        dir.deleteRecursively()
    }
}
