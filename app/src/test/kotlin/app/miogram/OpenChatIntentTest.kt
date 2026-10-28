package app.miogram

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ShortcutManager
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.miogram.data.telegram.PushAction
import app.miogram.data.telegram.PushMessage
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import app.miogram.push.PushNotifier
import cn.ellerotta.core.model.PeerKind
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class OpenChatIntentTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("poc").toFile()
    private var scenario: ActivityScenario<MainActivity>? = null

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @BindValue
    @JvmField
    val server = ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test")

    @BindValue
    @JvmField
    val telegram = TelegramClientHolder(dir) { id, hash, logger -> fake.config(id, hash, logger) }

    @Inject lateinit var navigator: Navigator

    @Inject lateinit var notifier: PushNotifier

    @Before
    fun setUp() {
        hilt.inject()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        scenario?.close()
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    private fun forged() = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(PushNotifier.EXTRA_DIALOG, "u501")
        .putExtra("app.miogram.title", "Name spoofed")
        .putExtra(PushNotifier.EXTRA_MESSAGE, 41)

    private fun deliver(intent: Intent) {
        assertNotNull(scenario).onActivity { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, intent) }
        compose.waitForIdle()
    }

    private fun assertNothingOpened() {
        assertEquals(Route.Chats, navigator.current)
        assertTrue(compose.onAllNodes(hasText("Name spoofed")).fetchSemanticsNodes().isEmpty(), "the forged title is nowhere")
    }

    @Test
    fun anotherAppsIntentOpensNoChat() {
        scenario = ActivityScenario.launch(forged())
        compose.logIn()
        compose.waitForIdle()
        assertNothingOpened()

        deliver(forged())
        assertNothingOpened()
    }

    @Test
    fun theNotificationAndTheShortcutOpenTheChatWithItsOwnTitle(): Unit = runBlocking {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.logIn()
        notifier.handle(
            PushMessage(
                action = PushAction.Show, dialogKey = "u501", kind = PeerKind.User, title = "Alice Liddell", sender = null, text = "see you at 8", media = null,
                messageId = 41, messageIds = emptyList(), maxId = null, silent = false, senderKey = "u501",
            ),
        )
        val shown = assertNotNull(shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification("u501", PushNotifier.CHAT_ID))
        deliver(shadowOf(shown.contentIntent).savedIntent)
        compose.waitUntilIdle { navigator.current is Route.Chat }
        assertEquals(Route.Chat("u501", "Alice Example", messageId = 41), navigator.current)

        compose.runOnIdle { navigator.back() }
        assertEquals(Route.Chats, navigator.current)
        val shortcut = context.getSystemService(ShortcutManager::class.java).dynamicShortcuts.single { it.id == "u501" }
        deliver(assertNotNull(shortcut.intent))
        compose.waitUntilIdle { navigator.current is Route.Chat }
        assertEquals(Route.Chat("u501", "Alice Example"), navigator.current)
    }
}
