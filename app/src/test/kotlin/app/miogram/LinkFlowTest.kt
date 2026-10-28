package app.miogram

import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.LinkAnnotation
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
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
class LinkFlowTest {
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

    @After
    fun tearDown() {
        scenario?.close()
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    private fun view(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url), ApplicationProvider.getApplicationContext(), MainActivity::class.java)

    private fun launch(intent: Intent? = null) {
        scenario = if (intent == null) ActivityScenario.launch(MainActivity::class.java) else ActivityScenario.launch(intent)
    }

    private fun deliver(url: String) {
        assertNotNull(scenario).onActivity { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, view(url)) }
    }

    private fun logIn(toChats: Boolean = true) {
        compose.waitForTag("start")
        compose.onNodeWithTag("start").performClick()
        compose.waitForTag("phone")
        compose.onNodeWithTag("phone").performTextClearance()
        compose.onNodeWithTag("phone").performTextInput("6627777")
        compose.onNodeWithTag("sendCode").performClick()
        compose.waitForTag("confirmPhone")
        compose.onNodeWithTag("confirmPhone").performClick()
        compose.waitForTag("code")
        compose.onNodeWithTag("code").performTextInput("22222")
        if (toChats) compose.waitForTag("openChats")
    }

    @Test
    fun aLinkFromBeforeTheLoginOpensAfterIt() {
        launch(view("https://t.me/carol_writer"))
        compose.waitForTag("start")
        assertTrue(compose.onAllNodes(hasTestTag("messageInput")).fetchSemanticsNodes().isEmpty())
        logIn(toChats = false)
        compose.waitForText("Carol Writer")
        compose.waitForTag("messageInput")
    }

    @Test
    fun resolveLinksOpenChatsBotsAndMessages() {
        launch()
        logIn()

        deliver("tg://resolve?domain=helperbot&start=ref-42")
        compose.waitForText("Helper")
        compose.waitForTag("startBot")
        compose.onNodeWithTag("startBot").performClick()
        compose.waitUntilIdle { fake.lastStartBot != null }
        assertEquals("ref-42", fake.lastStartBot?.startParam)
        compose.waitForTag("messageInput")

        deliver("https://t.me/kotlinnews/30")
        compose.waitForTag("highlight_30")
        compose.onNodeWithText("post 30").assertIsDisplayed()
        assertTrue(Triple(FakeTelegram.NEWS_ID, 31, -25) in fake.historyCalls, "history around the post: ${fake.historyCalls}")
        compose.waitForTag("joinChat")
        compose.onNodeWithTag("joinChat").performClick()
        compose.waitUntilIdle { FakeTelegram.NEWS_ID in fake.joinedChannels }
        compose.waitForTag("messageInput")
    }

    @Test
    fun anInviteShowsAPreviewAndJoins() {
        launch()
        logIn()
        deliver("https://t.me/+${FakeTelegram.BOOK_CLUB_INVITE}")
        compose.waitForTag("inviteSheet")
        compose.waitForText("we read about kotlin")
        compose.onNodeWithTag("inviteMembers", useUnmergedTree = true).assertIsDisplayed()
        compose.waitForText("42 members")
        compose.onNodeWithTag("joinInvite").performClick()
        compose.waitUntilIdle { FakeTelegram.BOOK_CLUB_ID in fake.joinedChannels }
        compose.waitForText("Book club")
        compose.waitForTag("messageInput")
    }

    @Test
    fun setsSharesCodesAndUnsupportedLinks() {
        launch()
        logIn()

        deliver("https://t.me/addstickers/dogs")
        compose.waitForTag("addSet")
        compose.onNodeWithTag("addSet").performClick()
        compose.waitUntilIdle { FakeTelegram.DOGS_ID in fake.installedSets }
        compose.waitForTag("removeSet")

        val proxy = "tg://proxy?server=proxy.example.com&port=443&secret=ee00"
        deliver(proxy)
        compose.waitForTag("unsupportedLink")
        compose.waitForText(proxy)
        compose.onNodeWithTag("copyLink").performClick()
        val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
        compose.waitUntilIdle { clipboard.primaryClip?.getItemAt(0)?.text?.toString() == proxy }

        deliver("https://t.me/login/12345")
        compose.waitForTag("loginCodeSheet")
        compose.waitForText("12345")

        deliver("https://t.me/share/url?url=https%3A%2F%2Fexample.com&text=look")
        compose.waitForTag("pickChats")
        compose.waitForTag("dialog_u${fake.selfId}")
        compose.onNodeWithTag("dialog_u${fake.selfId}").performClick()
        compose.waitUntilIdle { compose.onAllNodes(editableText("https://example.com\nlook"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun linksTappedInMessagesGoThroughTheRouter() {
        fake.addLinkMessage()
        fake.addEntityMessage()
        val richId = fake.addRichMessage()
        launch()
        logIn()
        compose.openSavedMessages(fake.selfId)

        val browserApp = ComponentName("com.example.browser", "com.example.browser.Browser")
        val packages = shadowOf(ApplicationProvider.getApplicationContext<Context>().packageManager)
        packages.addActivityIfNotPresent(browserApp)
        packages.addIntentFilterForActivity(
            browserApp,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("https")
            },
        )
        val preview = "link_https://core.telegram.org/api/files"
        compose.waitForTag("message_${richId}")
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag(preview))
        compose.waitForTag(preview)
        compose.onNodeWithTag(preview).performClick()
        var started: Intent? = null
        compose.waitUntilIdle {
            assertNotNull(scenario).onActivity { activity -> shadowOf(activity).nextStartedActivity?.let { started = it } }
            started != null
        }
        val browser = assertNotNull(started)
        assertEquals(Intent.ACTION_VIEW to "https://core.telegram.org/api/files", browser.action to browser.dataString)
        assertEquals("com.example.browser", browser.`package`)

        compose.onNodeWithTag("messages").performScrollToNode(hasText(fake.entityText))
        compose.waitForText(fake.entityText)
        compose.onNode(hasText(fake.entityText), useUnmergedTree = true).performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == "https://t.me/carol_writer" }
        compose.waitForText("Carol Writer")
        compose.waitForTag("messageInput")

        compose.onNodeWithTag("back").performClick()
        compose.waitForTag("messages")
        compose.onNodeWithTag("messages").performScrollToNode(hasText(fake.entityText))
        compose.waitForText(fake.entityText)
        compose.onNode(hasText(fake.entityText), useUnmergedTree = true).performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == "https://t.me/kotlinnews/30" }
        compose.waitForTag("highlight_30")
        compose.waitForTag("joinChat")

        compose.onNodeWithTag("back").performClick()
        compose.waitForTag("messages")
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("message_${richId}"))
        val mention = hasText("Alice", substring = true)
        compose.waitUntilIdle { compose.onAllNodes(mention, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(mention, useUnmergedTree = true).performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == "tg://user?id=501" }
        compose.waitForText("Alice Example")
        compose.waitForTag("messageInput")
    }
}
