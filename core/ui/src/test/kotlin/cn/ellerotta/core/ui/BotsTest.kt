package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.AnnotatedString
import cn.ellerotta.core.model.InlineBot
import cn.ellerotta.core.model.InlineResult
import cn.ellerotta.core.model.InlineResultKind
import cn.ellerotta.core.model.InlineResults
import cn.ellerotta.core.model.InlineState
import cn.ellerotta.core.model.InlineSwitchPm
import cn.ellerotta.core.model.InlineSwitchWebView
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.ui.components.chat.ChatInlineActions
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.ChatBotActions
import cn.ellerotta.core.model.ChatMember
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.TextEntity
import androidx.compose.ui.semantics.getOrNull
import cn.ellerotta.core.model.BotButton
import cn.ellerotta.core.model.CallbackAnswer
import cn.ellerotta.core.model.KeyboardChange
import cn.ellerotta.core.model.ReplyKeyboard
import cn.ellerotta.core.model.BotCommand
import cn.ellerotta.core.model.LoginRequest
import cn.ellerotta.core.model.LoginState
import cn.ellerotta.core.model.AttachMenuBot
import cn.ellerotta.core.model.BotMenuButton
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp")
class BotsTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()
    private val bot = InlineBot("u506", "gifbot", "Gif", "Search GIFs")
    private val sticker = MediaItem("doc811", "sticker", 100, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "😀", animated = true)
    private val results = InlineResults(
        results = listOf(
            InlineResult(1, "kotlin", InlineResultKind.Article, title = "Kotlin", description = "a modern language"),
            InlineResult(1, "cat", InlineResultKind.Sticker, thumb = sticker),
        ),
        nextOffset = "page2",
        switchPm = InlineSwitchPm("Set up the gif bot", "inline-setup"),
        switchWebView = InlineSwitchWebView("Open the app", "https://example.com/gifs"),
    )

    private fun editable(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))

    private fun typedText(text: String) = SemanticsMatcher("typed text \"$text\"") { it.config.getOrNull(SemanticsProperties.EditableText)?.text == text }

    @Test
    fun inlineBotsShowPlaceholderResultsAndRecentBots() {
        val texts = mutableListOf<String>()
        val typed = mutableListOf<Pair<String?, Boolean>>()
        val sent = mutableListOf<Pair<String, Int?>>()
        var more = 0
        var switched = 0
        val loaded = mutableListOf<String>()
        val themes = mutableListOf<String>()
        var state by mutableStateOf(InlineState())
        var mentions by mutableStateOf(emptyList<ChatMember>())
        val actions = ChatInlineActions(
            onText = { texts += it },
            onMore = { more++ },
            onSend = { result, replyTo -> sent += result.id to replyTo },
            onSwitchPm = { switched++ },
            onLoad = { loaded += it.key },
            onSwitchWebView = { theme -> themes += theme },
            onMention = { query, atStart -> typed += query to atStart },
        )
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Saved", messages = listOf(MessageItem(4, "answer me", out = false, date = now)), downloads = emptyMap(), busy = false,
                    onBack = {}, onSend = {}, onDownload = {}, inline = state, inlineActions = actions, mentions = mentions,
                )
            }
        }
        compose.onNodeWithTag("messageInput").performTextInput("@gi")
        compose.waitUntil(5_000) { typed.lastOrNull() == ("gi" to true) }
        mentions = listOf(ChatMember(bot.key, bot.name, bot = true, username = bot.username, firstName = bot.name))
        compose.onNodeWithTag("mention_${bot.key}").performClick()
        compose.onNode(editable("@gifbot ")).assertIsDisplayed()
        compose.waitUntil(5_000) { texts.lastOrNull() == "@gifbot " && typed.lastOrNull() == (null to false) }
        assertTrue(compose.onAllNodes(hasTestTag("mentions")).fetchSemanticsNodes().isEmpty())

        state = InlineState(bot = bot, results = results, more = true)
        compose.onNodeWithTag("composerHint", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Kotlin").assertIsDisplayed()
        compose.onNodeWithText("a modern language").assertIsDisplayed()
        compose.onNodeWithTag("inline_cat").assertIsDisplayed()
        compose.waitUntil(5_000) { "doc811" in loaded && more > 0 }

        compose.onNodeWithTag("messageInput").performTextClearance()
        compose.onNodeWithTag("messageInput").performTextInput("@otherbot cats")
        assertTrue(compose.onAllNodes(hasTestTag("inlineResults")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("messageInput").performTextClearance()
        compose.onNodeWithTag("messageInput").performTextInput("@gifbot kotlin")
        compose.onNodeWithTag("inlineResults").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasTestTag("composerHint"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())

        compose.onNodeWithTag("inlineSwitchPm").performClick()
        assertEquals(1, switched)
        compose.onNodeWithText("Open the app").assertIsDisplayed()
        compose.onNodeWithTag("inlineSwitchWebView").performClick()
        assertTrue(themes.single().contains("\"bg_color\":\"#"))

        compose.onNodeWithTag("inline_kotlin").performClick()
        assertEquals(listOf<Pair<String, Int?>>("kotlin" to null), sent)
        compose.onNode(editable("")).assertIsDisplayed()
    }

    @Test
    fun mentionsPutUsernamesOrHighlightedMentionsByName() {
        val typed = mutableListOf<Pair<String?, Boolean>>()
        val sent = mutableListOf<FormattedText>()
        val carol = ChatMember("u503", "Carol Writer", username = "carol_writer", firstName = "Carol")
        val alice = ChatMember("u501", "Alice Example", firstName = "Alice")
        var mentions by mutableStateOf(emptyList<ChatMember>())
        val actions = ChatInlineActions(onText = {}, onMore = {}, onSend = { _, _ -> }, onSwitchPm = {}, onLoad = {}, onMention = { query, atStart -> typed += query to atStart })
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Team", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = { sent += it }, onDownload = {}, inlineActions = actions, mentions = mentions)
            }
        }
        compose.onNodeWithTag("messageInput").performTextInput("hi @ca")
        compose.waitUntil(5_000) { typed.lastOrNull() == ("ca" to false) }
        mentions = listOf(carol)
        compose.onNodeWithTag("mention_u503").performClick()
        compose.onNode(editable("hi @carol_writer ")).assertIsDisplayed()

        compose.onNodeWithTag("messageInput").performTextInput("and @al")
        compose.waitUntil(5_000) { typed.lastOrNull() == ("al" to false) }
        mentions = listOf(alice)
        compose.onNodeWithTag("mention_u501").performClick()
        compose.onNode(typedText("hi @carol_writer and Alice ")).assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasTestTag("mentions")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("send").performClick()
        assertEquals(FormattedText("hi @carol_writer and Alice", listOf(TextEntity(21, 5, EntityType.MentionName(501)))), sent.single())
    }

    @Test
    fun viaHeadersUseTheBotAgain() {
        val messages = listOf(
            MessageItem(1, "", out = true, date = now, media = sticker, viaBot = "stickerbot"),
            MessageItem(2, "plain", out = true, date = now),
            MessageItem(3, "a gif from bob", out = false, date = now, senderName = "Bob", senderId = 7, viaBot = "gifbot"),
        )
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Team", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {})
            }
        }
        compose.onNodeWithText("Bob").assertIsDisplayed()
        compose.onNodeWithTag("via_3").assertIsDisplayed()
        compose.onNodeWithText("via @gifbot").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasTestTag("via_2")).fetchSemanticsNodes().isEmpty())

        compose.onNodeWithTag("via_3").performClick()
        compose.onNode(editable("@gifbot ")).assertIsDisplayed()

        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("via_1"))
        compose.onNodeWithTag("via_1").performClick()
        compose.onNode(editable("@stickerbot ")).assertIsDisplayed()
    }

    @Test
    fun keyboardsSendCallAnswerAndHide() {
        val inline = MessageItem(
            1, "Pick an action", out = false, date = now,
            buttons = listOf(
                listOf(BotButton.Callback("Ping", "ping".toByteArray()), BotButton.Url("News", "https://t.me/kotlinnews")),
                listOf(BotButton.Url("Site", "https://example.com"), BotButton.SwitchInline("Share", "helperbot", "hi", samePeer = false)),
            ),
        )
        val keyboard = ReplyKeyboard(listOf(listOf(BotButton.Text("Yes"), BotButton.Text("No"))), resize = true, singleUse = true, placeholder = "Answer the helper")
        val question = MessageItem(2, "Do you like Kotlin?", out = false, date = now, keyboard = KeyboardChange.Show(keyboard))
        var messages by mutableStateOf(listOf(inline, question))
        var answer by mutableStateOf<CallbackAnswer?>(null)
        val sent = mutableListOf<String>()
        val callbacks = mutableListOf<Pair<Int, String>>()
        val links = mutableListOf<String>()
        val switches = mutableListOf<String>()
        var shown = 0
        val actions = ChatBotActions(onCallback = { id, data -> callbacks += id to data.decodeToString() }, onAnswerShown = { shown++ }, onSwitchInline = { switches += it })
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Helper", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = { sent += it.text }, onDownload = {},
                    onOpenLink = { links += it }, botActions = actions, callbackAnswer = answer,
                )
            }
        }
        compose.onNodeWithTag("botKeyboard").assertIsDisplayed()
        compose.onNodeWithText("Answer the helper").assertIsDisplayed()
        compose.onNodeWithTag("botKeyboardButton").performClick()
        assertTrue(compose.onAllNodes(hasTestTag("botKeyboard")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("botKeyboardButton").performClick()
        compose.onNodeWithTag("keyboardButton_0_1").performClick()
        assertEquals(listOf("No"), sent)
        assertTrue(compose.onAllNodes(hasTestTag("botKeyboard")).fetchSemanticsNodes().isEmpty())

        compose.onNodeWithTag("button_1_0_0").performClick()
        assertEquals(listOf(1 to "ping"), callbacks)
        compose.onNodeWithTag("button_1_0_1").performClick()
        assertEquals(listOf("https://t.me/kotlinnews"), links)
        compose.onNodeWithTag("button_1_1_0").performClick()
        compose.onNodeWithTag("openLinkDialog").assertIsDisplayed()
        compose.onNodeWithTag("openLink").performClick()
        assertEquals("https://example.com", links.last())
        compose.onNodeWithTag("button_1_1_1").performClick()
        assertEquals(listOf("@helperbot hi"), switches)

        answer = CallbackAnswer("Careful", alert = true)
        compose.onNodeWithTag("botAlert").assertIsDisplayed()
        compose.onNodeWithText("Careful").assertIsDisplayed()
        assertEquals(1, shown)
        compose.onNodeWithTag("botAlertOk").performClick()
        assertTrue(compose.onAllNodes(hasTestTag("botAlert")).fetchSemanticsNodes().isEmpty())

        messages = messages + MessageItem(3, "Your name?", out = false, date = now, keyboard = KeyboardChange.ForceReply("Your name"))
        compose.onNodeWithText("Your name").assertIsDisplayed()
        compose.onNodeWithTag("closeStrip").assertIsDisplayed()
        messages = messages + MessageItem(4, "Bye", out = false, date = now, keyboard = KeyboardChange.Hide)
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("botKeyboardButton")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun loginButtonsAskWhatTheSiteWantsAndOpenItsUrl() {
        val login = BotButton.LoginUrl("Log in", "https://example.com/login", 1)
        val messages = listOf(MessageItem(1, "Pick an action", out = false, date = now, buttons = listOf(listOf(login))))
        var state by mutableStateOf(LoginState())
        val pressed = mutableListOf<Pair<Int, BotButton.LoginUrl>>()
        val answers = mutableListOf<Triple<Boolean?, Boolean, Boolean>>()
        val links = mutableListOf<String>()
        var opened = 0
        val actions = ChatBotActions(
            onCallback = { _, _ -> }, onAnswerShown = {}, onSwitchInline = {},
            onLogin = { id, button -> pressed += id to button },
            onAnswerLogin = { accepted, write, phone -> answers += Triple(accepted, write, phone) },
            onLoginOpened = { opened++; state = LoginState() },
        )
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Helper", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    onOpenLink = { links += it }, botActions = actions, login = state,
                )
            }
        }
        compose.onNodeWithTag("button_1_0_0").performClick()
        assertEquals(listOf(1 to login), pressed)

        state = LoginState(request = LoginRequest("example.com", "Helper", writeAccess = true, phone = true))
        compose.onNodeWithTag("loginDialog").assertIsDisplayed()
        compose.onNodeWithText("Log in to example.com?").assertIsDisplayed()
        compose.onNodeWithTag("loginPhone").performClick()
        compose.onNodeWithTag("loginWrite").performClick()
        compose.onNodeWithTag("loginAccept").performClick()
        assertEquals(Triple<Boolean?, Boolean, Boolean>(true, false, true), answers.last())

        state = LoginState(request = LoginRequest("example.com", "Helper"))
        assertTrue(compose.onAllNodes(hasTestTag("loginWrite")).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasTestTag("loginPhone")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("loginDecline").performClick()
        assertEquals(Triple<Boolean?, Boolean, Boolean>(false, false, false), answers.last())

        state = LoginState(url = "https://example.com/login?hash=1")
        compose.waitForIdle()
        assertEquals(listOf("https://example.com/login?hash=1"), links)
        assertEquals(1, opened)
        state = LoginState(url = "https://example.com/plain", confirmed = false)
        compose.onNodeWithTag("openLinkDialog").assertIsDisplayed()
        assertEquals(2, opened)
    }

    @Test
    fun aSlashListsCommandsToSendOrInsert() {
        val commands = listOf(BotCommand("help", "What the helper can do", "helperbot"), BotCommand("weather", "Today's weather", "helperbot"))
        val sent = mutableListOf<Pair<String, Int?>>()
        var loads = 0
        val messages = listOf(MessageItem(1, "hello", out = false, date = now))
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Devs", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = { sent += it.text to null }, onDownload = {},
                    onSendReply = { text, reply -> sent += text.text to reply }, commands = commands, onLoadCommands = { loads++ },
                )
            }
        }
        compose.onNodeWithTag("messageInput").performTextInput("hi")
        assertTrue(compose.onAllNodes(hasTestTag("botCommands")).fetchSemanticsNodes().isEmpty())
        assertEquals(0, loads)
        compose.onNodeWithTag("messageInput").performTextClearance()
        compose.onNodeWithTag("messageInput").performTextInput("/")
        compose.onNodeWithTag("command_/help@helperbot").assertIsDisplayed()
        compose.onNodeWithText("Today's weather").assertIsDisplayed()
        compose.onNodeWithTag("messageInput").performTextInput("w")
        assertEquals(1, loads)
        assertTrue(compose.onAllNodes(hasTestTag("command_/help@helperbot")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("insertCommand_/weather@helperbot").performClick()
        compose.onNode(editable("/weather@helperbot ")).assertIsDisplayed()
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("command_/weather@helperbot")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("messageInput").performTextClearance()
        compose.onNodeWithTag("messageInput").performTextInput("/he")
        compose.onNodeWithTag("command_/help@helperbot").performClick()
        assertEquals(listOf<Pair<String, Int?>>("/help@helperbot" to null), sent)
        compose.onNode(editable("")).assertIsDisplayed()
    }

    @Test
    fun theMenuButtonAndAttachmentMenuBotsOpenTheirApps() {
        val menuThemes = mutableListOf<String>()
        val attached = mutableListOf<Pair<String, Boolean>>()
        val actions = ChatBotActions(
            onCallback = { _, _ -> }, onAnswerShown = {}, onSwitchInline = {},
            onMenuApp = { menuThemes += it },
            onAttachApp = { bot, theme, write ->
                assertTrue(theme.contains("bg_color"))
                attached += bot.key to write
            },
        )
        val bots = listOf(
            AttachMenuBot("u504", "Helper app", attachMenu = true),
            AttachMenuBot("u506", "Gif studio", attachMenu = true, inactive = true, requestWriteAccess = true),
            AttachMenuBot("u507", "Wallet", attachMenu = true, disclaimer = true),
        )
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Helper", messages = listOf(MessageItem(1, "hi", out = false, date = now)), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    onAttachMedia = {}, botActions = actions, menuButton = BotMenuButton("Order food", "https://example.com/menu"), attachBots = bots,
                )
            }
        }
        compose.onNodeWithText("Order food").assertIsDisplayed()
        compose.onNodeWithTag("botMenuButton").performClick()
        assertTrue(menuThemes.single().contains("\"bg_color\":\"#"))

        compose.onNodeWithTag("attach").performClick()
        compose.onNodeWithTag("attachMedia").assertIsDisplayed()
        compose.onNodeWithText("Helper app").assertIsDisplayed()
        compose.onNodeWithTag("attachBot_u504").performClick()
        assertEquals(listOf("u504" to false), attached)

        compose.onNodeWithTag("attach").performClick()
        compose.onNodeWithTag("attachBot_u506").performClick()
        compose.onNodeWithTag("webAppConsent").assertIsDisplayed()
        compose.onNodeWithText("Allow Gif studio to send me messages").assertIsDisplayed()
        compose.onNodeWithTag("webAppConsentWrite").performClick()
        compose.onNodeWithTag("webAppConsentOk").performClick()
        assertEquals("u506" to false, attached.last())
        assertTrue(compose.onAllNodes(hasTestTag("webAppConsent")).fetchSemanticsNodes().isEmpty())

        compose.onNodeWithTag("attach").performClick()
        compose.onNodeWithTag("attachBot_u507").performClick()
        compose.onNodeWithTag("webAppConsentOk").assertIsNotEnabled()
        compose.onNodeWithTag("webAppConsentTerms").performClick()
        compose.onNodeWithTag("webAppConsentOk").assertIsEnabled()
        compose.onNodeWithTag("webAppConsentOk").performClick()
        assertEquals("u507" to false, attached.last())
        assertEquals(3, attached.size)
    }
}
