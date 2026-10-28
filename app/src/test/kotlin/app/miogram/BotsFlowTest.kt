package app.miogram

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import java.time.Duration
import org.json.JSONObject
import org.robolectric.shadows.ShadowDialog
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import app.miogram.links.LinkRouter
import cn.ellerotta.core.ui.R
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.InputPeerEmpty
import cn.ellerotta.ktproto.api.types.InputBotAppShortName
import javax.inject.Inject
import org.robolectric.shadows.ShadowToast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputUser
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class, qualifiers = "w412dp-h892dp")
class BotsFlowTest {
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("poc").toFile()

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @BindValue
    @JvmField
    val server = ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test")

    @BindValue
    @JvmField
    val telegram = TelegramClientHolder(dir) { id, hash, logger -> fake.config(id, hash, logger) }

    @Inject
    lateinit var router: LinkRouter

    @After
    fun tearDown() {
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    @Test
    fun anInlineQueryPagesSendsAResultAndSwitchesToTheBot() {
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("messageInput")

        compose.onNodeWithTag("messageInput").performTextInput("@g")
        compose.waitForTag("inlineBot_gifbot")
        compose.onNodeWithTag("inlineBot_gifbot").performClick()
        compose.waitForTag("composerHint", unmerged = true)

        compose.onNodeWithTag("messageInput").performTextInput("kotlin")
        compose.waitForTag("inline_kotlin")
        compose.waitUntilIdle { fake.bots.inlineCalls.any { it.offset == FakeBots.NEXT_OFFSET } }
        assertTrue(fake.bots.inlineCalls.all { it.query == "kotlin" || it.query == "" })
        assertEquals(InputPeerSelf, fake.bots.inlineCalls.last().peer)
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("inlineList").performScrollToNode(hasTestTag("inline_compose")) }.isSuccess }

        compose.onNodeWithTag("inlineList").performScrollToNode(hasTestTag("inline_kotlin"))
        compose.onNodeWithTag("inline_kotlin").performClick()
        compose.waitUntilIdle { fake.bots.inlineSends.isNotEmpty() }
        val sent = fake.bots.inlineSends.single()
        assertEquals(FakeBots.FIRST_QUERY, sent.queryId)
        assertEquals("kotlin", sent.id)
        assertTrue(sent.clearDraft)
        compose.waitForText("Kotlin is a modern language")
        compose.onNode(editableText("")).assertExists()

        compose.waitForText("via @gifbot")
        compose.onNodeWithText("via @gifbot").performClick()
        compose.onNode(editableText("@gifbot ")).assertExists()

        compose.onNodeWithTag("messageInput").performTextClearance()
        compose.onNodeWithTag("messageInput").performTextInput("@gifbot cats")
        compose.waitForTag("inlineSwitchPm")
        compose.onNodeWithTag("inlineSwitchPm").performClick()
        compose.waitUntilIdle { fake.lastStartBot != null }
        assertEquals(FakeBots.GIF_BOT_ID, (fake.lastStartBot!!.bot as InputUser).userId)
        assertEquals(FakeBots.START_PARAMETER, fake.lastStartBot!!.startParam)
        compose.waitForText("Gif")
    }

    @Test
    fun keyboardButtonsSendAskOpenCopyAndFollowEdits() {
        hilt.inject()
        compose.logIn()
        compose.runOnUiThread { router.submit("tg://resolve?domain=helperbot") }
        compose.waitForText("Pick an action")

        compose.waitForTag("botKeyboard")
        compose.waitForText("Answer the helper")
        compose.onNodeWithTag("botKeyboardButton").performClick()
        compose.waitUntilIdle { compose.onAllNodesWithTag("botKeyboard").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("botKeyboardButton").performClick()
        compose.waitForTag("botKeyboard")
        compose.onNodeWithTag("keyboardButton_0_0").performClick()
        compose.waitUntilIdle { fake.lastSendMessage?.message == "Yes" }
        assertEquals(fake.helperBot.id, (fake.lastSendMessage!!.peer as InputPeerUser).userId)
        compose.waitUntilIdle { compose.onAllNodesWithTag("botKeyboard").fetchSemanticsNodes().isEmpty() }

        val menu = FakeBots.MENU_ID
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("button_${menu}_0_0"))
        compose.onNodeWithTag("button_${menu}_0_0").performClick()
        compose.waitUntilIdle { fake.bots.callbackCalls.isNotEmpty() }
        assertEquals("ping", fake.bots.callbackCalls.last().data?.decodeToString())
        compose.waitUntilIdle { ShadowToast.showedToast("Pong") }
        compose.waitForText("Pinged")
        compose.waitForText("Ping again")

        compose.onNodeWithTag("button_${menu}_0_1").performClick()
        compose.waitForText("Careful, this is an alert")
        compose.onNodeWithTag("botAlertOk").performClick()
        compose.waitUntilIdle { compose.onAllNodesWithTag("botAlert").fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithTag("button_${menu}_1_0").performClick()
        compose.waitUntilIdle { fake.bots.callbackCalls.size == 3 }
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithTag("botAlert").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithTag("error").fetchSemanticsNodes().isEmpty())

        compose.onNodeWithTag("button_${menu}_2_0").performClick()
        compose.waitForTag("openLinkDialog")
        compose.onNodeWithText("https://example.com/docs").assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.common_cancel)).performClick()
        compose.waitUntilIdle { compose.onAllNodesWithTag("openLinkDialog").fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithTag("button_${menu}_4_0").performClick()
        val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
        compose.waitUntilIdle { clipboard.primaryClip?.getItemAt(0)?.text?.toString() == "A1B2" }
        compose.onNodeWithTag("button_${menu}_4_1").performClick()
        compose.waitUntilIdle { ShadowToast.showedToast(compose.activity.getString(R.string.chat_button_unsupported)) }

        compose.onNodeWithTag("button_${menu}_3_1").performClick()
        compose.onNode(editableText("@helperbot cats")).assertExists()
        compose.onNodeWithTag("messageInput").performTextClearance()

        compose.onNodeWithTag("button_${menu}_3_0").performClick()
        compose.waitForTag("pickChats")
        compose.onNodeWithTag("pickChats").performScrollToNode(hasText("Alice Example"))
        compose.onNodeWithText("Alice Example").performClick()
        compose.waitUntilIdle { compose.onAllNodes(editableText("@helperbot hi")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun nextOpened(): Intent {
        var started: Intent? = null
        compose.waitUntilIdle {
            shadowOf(compose.activity).nextStartedActivity?.takeIf { it.action == Intent.ACTION_VIEW }?.let { started = it }
            started != null
        }
        assertEquals("com.example.browser", started!!.`package`)
        return started!!
    }

    @Test
    fun loginButtonsAskThenOpenTheSiteLoggedIn() {
        hilt.inject()
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
        compose.logIn()
        compose.runOnUiThread { router.submit("tg://resolve?domain=helperbot") }
        compose.waitForText("Pick an action")
        val menu = FakeBots.MENU_ID
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("button_${menu}_6_0"))

        compose.onNodeWithTag("button_${menu}_6_0").performClick()
        compose.waitForTag("loginDialog")
        compose.onNodeWithText(compose.activity.getString(R.string.chat_login_title, "example.com")).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.chat_login_write, "Helper")).assertExists()
        compose.onNodeWithTag("loginAccept").performClick()
        compose.waitUntilIdle { fake.bots.loginAccepts.isNotEmpty() }
        val accepted = fake.bots.loginAccepts.single()
        assertTrue(accepted.writeAllowed)
        assertFalse(accepted.sharePhoneNumber)
        assertEquals(FakeBots.LOGGED_IN_URL, nextOpened().dataString)
        assertTrue(compose.onAllNodesWithTag("loginDialog").fetchSemanticsNodes().isEmpty())

        compose.onNodeWithTag("button_${menu}_6_0").performClick()
        compose.waitForTag("loginDialog")
        compose.onNodeWithTag("loginDecline").performClick()
        assertEquals(FakeBots.LOGIN_URL, nextOpened().dataString)
        assertEquals(1, fake.bots.loginAccepts.size)

        compose.onNodeWithTag("button_${menu}_6_1").performClick()
        assertEquals(FakeBots.LOGGED_IN_URL, nextOpened().dataString)

        compose.onNodeWithTag("button_${menu}_6_2").performClick()
        compose.waitForTag("openLinkDialog")
        compose.onNodeWithText(FakeBots.PLAIN_URL).assertExists()
        assertEquals(listOf(FakeBots.ASKING_LOGIN, FakeBots.ASKING_LOGIN, FakeBots.KNOWN_LOGIN, FakeBots.PLAIN_LOGIN), fake.bots.loginRequests.map { it.buttonId })
    }

    private fun webView(): WebView? = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.let(::findWebView)

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
        else -> null
    }

    private fun post(page: WebView, event: String, data: String) {
        val proxy = shadowOf(page).getJavascriptInterface("TelegramWebviewProxy")
        val postEvent = proxy.javaClass.getMethod("postEvent", String::class.java, String::class.java).apply { isAccessible = true }
        compose.runOnUiThread { postEvent.invoke(proxy, event, data) }
        compose.waitForIdle()
    }

    private fun webAppShown() = compose.onAllNodesWithTag("webApp").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun webAppsOpenOverTheChatStayAliveAndSendData() {
        hilt.inject()
        compose.logIn()
        compose.runOnUiThread { router.submit("tg://resolve?domain=helperbot") }
        compose.waitForText("Pick an action")
        val menu = FakeBots.MENU_ID
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("button_${menu}_4_2"))

        compose.onNodeWithTag("button_${menu}_4_2").performClick()
        compose.waitUntilIdle { webView()?.let { shadowOf(it).lastLoadedUrl } == "${FakeBots.APP_URL}#tgWebAppData=app" }
        val theme = JSONObject(fake.bots.webAppRequests.single().themeParams!!.data)
        assertTrue(theme.getString("bg_color").startsWith("#"))
        val page = webView()!!

        post(page, "web_app_request_theme", "")
        compose.waitUntilIdle { shadowOf(page).lastEvaluatedJavascript.orEmpty().contains("\"theme_changed\"") }
        post(page, "web_app_setup_main_button", """{"is_visible":true,"is_active":true,"text":"Pay 5"}""")
        compose.waitForTag("webAppMainButton")
        compose.onNodeWithText("Pay 5").performClick()
        compose.waitUntilIdle { shadowOf(page).lastEvaluatedJavascript.orEmpty().contains("\"main_button_pressed\"") }

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61))
        compose.waitUntilIdle { fake.bots.webAppProlongs.size == 1 }
        assertEquals(FakeBots.WEB_APP_QUERY, fake.bots.webAppProlongs.single().queryId)
        assertTrue(webAppShown())
        fake.bots.webAppForgotten = true
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61))
        compose.waitUntilIdle { !webAppShown() }

        compose.onNodeWithTag("keyboardButton_1_0").performClick()
        compose.waitUntilIdle { webView()?.let { shadowOf(it).lastLoadedUrl } == "${FakeBots.ORDER_URL}#tgWebAppData=simple" }
        post(webView()!!, "web_app_data_send", """{"data":"two coffees"}""")
        compose.waitUntilIdle { fake.bots.webAppData.isNotEmpty() && !webAppShown() }
        assertEquals("Order" to "two coffees", fake.bots.webAppData.single().let { it.buttonText to it.data })
    }

    private fun loadedUrl(): String? = webView()?.let { shadowOf(it).lastLoadedUrl }

    private fun closeWebApp() {
        compose.onNodeWithTag("webAppClose").performClick()
        compose.waitUntilIdle { !webAppShown() }
    }

    @Test
    fun theMenuButtonAndTheAttachmentMenuOpenTheirApps() {
        hilt.inject()
        compose.logIn()
        compose.runOnUiThread { router.submit("tg://resolve?domain=helperbot") }
        compose.waitForText("Pick an action")

        compose.waitForTag("botMenuButton")
        compose.onNodeWithTag("botMenuButton").performClick()
        compose.waitUntilIdle { loadedUrl() == "${FakeBots.MENU_APP_URL}#tgWebAppData=menu" }
        val menu = fake.bots.webAppRequests.single()
        assertTrue(menu.fromBotMenu)
        assertEquals(FakeBots.MENU_APP_URL, menu.url)
        assertEquals(fake.helperBot.id, (menu.peer as InputPeerUser).userId)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61))
        compose.waitUntilIdle { fake.bots.webAppProlongs.size == 1 }
        closeWebApp()

        compose.onNodeWithTag("attach").performClick()
        compose.waitForTag("attachBot_u${fake.helperBot.id}")
        compose.onNodeWithText("Helper app").assertExists()
        compose.onNodeWithTag("attachBot_u${fake.helperBot.id}").performClick()
        compose.waitUntilIdle { loadedUrl() == "${FakeBots.ATTACH_URL}#tgWebAppData=attach" }
        val attach = fake.bots.webAppRequests.last()
        assertFalse(attach.fromBotMenu)
        assertEquals(null, attach.url)
        assertEquals(fake.helperBot.id, (attach.bot as InputUser).userId)
        assertEquals(fake.helperBot.id, (attach.peer as InputPeerUser).userId)
        closeWebApp()

        compose.onNodeWithTag("attach").performClick()
        compose.waitForTag("attachBot_u${FakeBots.GIF_BOT_ID}")
        compose.onNodeWithTag("attachBot_u${FakeBots.GIF_BOT_ID}").performClick()
        compose.waitForTag("webAppConsent")
        compose.onNodeWithTag("webAppConsentWrite", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("webAppConsentOk").performClick()
        compose.waitUntilIdle { fake.bots.attachToggles.isNotEmpty() && webAppShown() }
        val toggle = fake.bots.attachToggles.single()
        assertTrue(toggle.enabled && toggle.writeAllowed)
        compose.waitUntilIdle { (fake.bots.webAppRequests.last().bot as InputUser).userId == FakeBots.GIF_BOT_ID }
        assertTrue(fake.bots.gifAttached)
    }

    @Test
    fun miniAppLinksOpenOverWhateverIsShown() {
        hilt.inject()
        compose.logIn()

        compose.runOnUiThread { router.submit("https://t.me/helperbot/game?startapp=lvl1") }
        compose.waitUntilIdle { loadedUrl() == "${FakeBots.GAME_URL}#tgWebAppData=gamelvl1" }
        assertEquals(FakeBots.GAME_APP, (fake.bots.botAppCalls.single().app as InputBotAppShortName).shortName)
        val game = fake.bots.appWebViewRequests.single()
        assertEquals("lvl1", game.startParam)
        assertEquals(fake.helperBot.id, (game.peer as InputPeerUser).userId)
        assertTrue(JSONObject(game.themeParams!!.data).getString("bg_color").startsWith("#"))
        compose.onNodeWithTag("webAppTitle").assertExists()
        compose.onNodeWithText("Game").assertExists()
        closeWebApp()

        compose.runOnUiThread { router.submit("tg://resolve?domain=helperbot&appname=quiz") }
        compose.waitForTag("webAppConsent")
        compose.onNodeWithTag("webAppConsentOk").performClick()
        compose.waitUntilIdle { loadedUrl() == "${FakeBots.QUIZ_URL}#tgWebAppData=quiz" }
        assertTrue(fake.bots.appWebViewRequests.last().writeAllowed)
        closeWebApp()

        compose.runOnUiThread { router.submit("https://t.me/helperbot?startapp=ref") }
        compose.waitUntilIdle { loadedUrl() == "${FakeBots.MAIN_URL}#tgWebAppData=mainref" }
        val main = fake.bots.mainAppRequests.single()
        assertEquals(InputPeerEmpty, main.peer)
        assertEquals("ref", main.startParam)
        closeWebApp()

        compose.runOnUiThread { router.submit("tg://resolve?domain=helperbot&profile") }
        compose.waitForTag("peerProfile")
        compose.waitForTag("openMainApp")
        compose.onNodeWithTag("openMainApp").performClick()
        compose.waitUntilIdle { fake.bots.mainAppRequests.size == 2 && loadedUrl() == "${FakeBots.MAIN_URL}#tgWebAppData=main" }
        assertEquals(null, fake.bots.mainAppRequests.last().startParam)
    }

    @Test
    fun theSideMenuAndInlineModeOpenTheirApps() {
        hilt.inject()
        compose.logIn()

        compose.waitForTag("openSettings")
        compose.onNodeWithTag("openSettings").performClick()
        compose.waitForTag("settings")
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("sideApp_u${fake.helperBot.id}")) }.isSuccess }
        compose.onNodeWithTag("sideApp_u${fake.helperBot.id}").performClick()
        compose.waitUntilIdle { loadedUrl() == "${FakeBots.SIDE_URL}#tgWebAppData=side" }
        assertTrue(fake.bots.simpleWebAppRequests.single().fromSideMenu)
        closeWebApp()

        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("messageInput")
        compose.onNodeWithTag("messageInput").performTextInput("@gifbot cats")
        compose.waitForTag("inlineSwitchWebView")
        compose.onNodeWithTag("inlineSwitchWebView").performClick()
        compose.waitUntilIdle { loadedUrl() == "${FakeBots.GIF_APP_URL}#tgWebAppData=simple" }
        val inline = fake.bots.simpleWebAppRequests.last()
        assertTrue(inline.fromSwitchWebview)
        assertEquals(FakeBots.GIF_APP_URL, inline.url)
        post(webView()!!, "web_app_switch_inline_query", """{"query":"dogs","chat_types":[]}""")
        compose.waitUntilIdle { !webAppShown() }
        compose.onNode(editableText("@gifbot dogs")).assertExists()
    }

    @Test
    fun aSlashListsTheBotsCommandsToSendOrInsert() {
        hilt.inject()
        compose.logIn()
        compose.runOnUiThread { router.submit("tg://resolve?domain=helperbot") }
        compose.waitForText("Pick an action")

        compose.onNodeWithTag("messageInput").performTextInput("/")
        compose.waitForTag("command_/weather")
        compose.onNodeWithText("Today's weather").assertExists()
        compose.onNodeWithTag("messageInput").performTextInput("w")
        compose.waitUntilIdle { compose.onAllNodesWithTag("command_/help").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("insertCommand_/weather").performClick()
        compose.onNode(editableText("/weather ")).assertExists()
        compose.waitUntilIdle { compose.onAllNodesWithTag("botCommands").fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithTag("messageInput").performTextClearance()
        compose.onNodeWithTag("messageInput").performTextInput("/he")
        compose.waitForTag("command_/help")
        compose.onNodeWithTag("command_/help").performClick()
        compose.waitUntilIdle { fake.lastSendMessage?.message == "/help" }
        assertEquals(fake.helperBot.id, (fake.lastSendMessage!!.peer as InputPeerUser).userId)
        compose.onNode(editableText("")).assertExists()
    }

    private fun WebView.lastScript(): String = shadowOf(this).lastEvaluatedJavascript.orEmpty()

    @Test
    fun aMiniAppAsksToWriteForThePhoneAndReadsTheClipboard() {
        hilt.inject()
        compose.logIn()
        compose.runOnUiThread { router.submit("tg://resolve?domain=helperbot") }
        compose.waitForText("Pick an action")
        compose.waitForTag("botMenuButton")
        compose.onNodeWithTag("botMenuButton").performClick()
        compose.waitUntilIdle { loadedUrl() == "${FakeBots.MENU_APP_URL}#tgWebAppData=menu" }
        val page = webView()!!

        post(page, "web_app_request_write_access", "")
        compose.waitForTag("webAppQuestion")
        compose.onNodeWithText("Helper would like to send you messages.").assertExists()
        compose.onNodeWithTag("webAppAllow").performClick()
        compose.waitUntilIdle { page.lastScript().contains("\"write_access_requested\", {\"status\":\"allowed\"}") }
        assertEquals(fake.helperBot.id, (fake.bots.allowWriteCalls.single().bot as InputUser).userId)
        post(page, "web_app_request_write_access", "")
        compose.waitUntilIdle { fake.bots.canWriteCalls.size == 2 }
        compose.onNodeWithTag("webAppQuestion").assertDoesNotExist()
        assertEquals(1, fake.bots.allowWriteCalls.size)

        fake.bots.helperBlocked = true
        post(page, "web_app_request_phone", "")
        compose.waitForTag("webAppQuestion")
        compose.onNodeWithTag("webAppAllow").performClick()
        compose.waitUntilIdle { page.lastScript().contains("\"phone_requested\", {\"status\":\"sent\"}") }
        assertEquals(fake.helperBot.id, (fake.bots.unblocks.single().id as InputPeerUser).userId)
        assertEquals(fake.helperBot.id, (fake.contactSends.single().peer as InputPeerUser).userId)
        post(page, "web_app_request_phone", "")
        compose.waitForTag("webAppQuestion")
        compose.onNodeWithTag("webAppDeny").performClick()
        compose.waitUntilIdle { page.lastScript().contains("\"phone_requested\", {\"status\":\"cancelled\"}") }
        assertEquals(1, fake.contactSends.size)

        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("code", "A1B2"))
        compose.runOnUiThread { page.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1f, 1f, 0)) }
        post(page, "web_app_read_text_from_clipboard", """{"req_id":"c1"}""")
        compose.waitUntilIdle { page.lastScript().contains("{\"req_id\":\"c1\",\"data\":\"A1B2\"}") }
    }

    @Test
    fun attachmentMenuLinksAddTheBotAndOpenItsAppInTheirChat() {
        hilt.inject()
        compose.logIn()

        compose.runOnUiThread { router.submit("tg://resolve?domain=helperbot&startattach=hi") }
        compose.waitUntilIdle { loadedUrl() == "${FakeBots.ATTACH_URL}#tgWebAppData=attach" }
        val helper = fake.bots.webAppRequests.last()
        assertEquals("hi", helper.startParam)
        assertEquals(null, helper.url)
        assertEquals(fake.helperBot.id, (helper.peer as InputPeerUser).userId)
        assertEquals(fake.helperBot.id, (fake.bots.attachBotCalls.single().bot as InputUser).userId)
        closeWebApp()

        compose.runOnUiThread { router.submit("https://t.me/+15550001111?attach=gifbot&startattach=x") }
        compose.waitForTag("webAppConsent")
        compose.onNodeWithTag("webAppConsentOk").performClick()
        compose.waitUntilIdle { webAppShown() && (fake.bots.webAppRequests.last().bot as InputUser).userId == FakeBots.GIF_BOT_ID }
        assertTrue(fake.bots.attachToggles.single().enabled)
        val carol = fake.bots.webAppRequests.last()
        assertEquals(fake.carol.id, (carol.peer as InputPeerUser).userId)
        assertEquals("x", carol.startParam)
        closeWebApp()

        compose.runOnUiThread { router.submit("tg://resolve?domain=gifbot&startattach&choose=users") }
        compose.waitForTag("pickChats")
        compose.onNodeWithTag("pickChats").performScrollToNode(hasText("Alice Example"))
        compose.onNodeWithText("Alice Example").performClick()
        compose.waitUntilIdle { webAppShown() && (fake.bots.webAppRequests.last().peer as? InputPeerUser)?.userId == fake.alice.id }
        closeWebApp()

        compose.runOnUiThread { router.submit("https://t.me/kotlinnews?attach=gifbot") }
        compose.waitForText("The mini app of Gif studio does not open in this chat.")
        assertFalse(webAppShown())
    }

    @Test
    fun aSideMenuAppIsRemovedFromTheMenus() {
        hilt.inject()
        compose.logIn()
        compose.waitForTag("openSettings")
        compose.onNodeWithTag("openSettings").performClick()
        compose.waitForTag("settings")
        val entry = "sideApp_u${fake.helperBot.id}"
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("settings").performScrollToNode(hasTestTag(entry)) }.isSuccess }

        compose.onNodeWithTag(entry).performTouchInput { longClick() }
        compose.waitForTag("confirmRemoveApp")
        compose.onNodeWithTag("confirmRemoveApp").performClick()
        compose.waitUntilIdle { fake.bots.attachToggles.isNotEmpty() && compose.onAllNodesWithTag(entry).fetchSemanticsNodes().isEmpty() }
        val removal = fake.bots.attachToggles.single()
        assertFalse(removal.enabled)
        assertEquals(fake.helperBot.id, (removal.bot as InputUser).userId)
        assertFalse(webAppShown())
    }
}
