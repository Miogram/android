package cn.ellerotta.core.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.core.app.ApplicationProvider
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RichTextTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clipboard get() = context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()

    private fun at(text: String, part: String, type: EntityType) = TextEntity(text.indexOf(part), part.length, type)

    private fun message(text: String, vararg entities: TextEntity) = MessageItem(1, text, out = false, date = 1_790_000_000, entities = entities.toList())

    private fun SemanticsNodeInteraction.annotated(): AnnotatedString = fetchSemanticsNode().config[SemanticsProperties.Text].first()

    private fun AnnotatedString.styleAt(index: Int): SpanStyle =
        spanStyles.filter { index >= it.start && index < it.end }.fold(SpanStyle()) { merged, range -> merged.merge(range.item) }

    private fun AnnotatedString.tags(): List<String> = getLinkAnnotations(0, length).mapNotNull { (it.item as? LinkAnnotation.Clickable)?.tag }

    @Test
    fun stylesNestAndOverlapAsSpans() {
        val text = "\uD83D\uDE00 bold italic both under strike code"
        val entities = arrayOf(
            at(text, "bold", EntityType.Bold),
            at(text, "italic both", EntityType.Italic),
            at(text, "both", EntityType.Bold),
            at(text, "under strike", EntityType.Underline),
            at(text, "strike", EntityType.Strike),
            at(text, "code", EntityType.Code),
        )
        compose.setContent { MioTheme { ChatScreen("Saved", listOf(message(text, *entities)), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}) } }
        val shown = compose.onNode(hasText(text), useUnmergedTree = true).annotated()
        assertEquals(FontWeight.Bold, shown.styleAt(3).fontWeight)
        assertEquals(null, shown.styleAt(8).fontWeight)
        assertEquals(FontStyle.Italic, shown.styleAt(8).fontStyle)
        val both = shown.styleAt(text.indexOf("both"))
        assertEquals(FontWeight.Bold to FontStyle.Italic, both.fontWeight to both.fontStyle)
        assertEquals(TextDecoration.Underline, shown.styleAt(text.indexOf("under")).textDecoration)
        val struck = shown.styleAt(text.indexOf("strike")).textDecoration!!
        assertTrue(struck.contains(TextDecoration.Underline) && struck.contains(TextDecoration.LineThrough))
        assertEquals(FontFamily.Monospace, shown.styleAt(text.indexOf("code")).fontFamily)
    }

    @Test
    fun spoilersStayHiddenUntilTapped() {
        val text = "the answer is 42, see example.com"
        val entities = arrayOf(at(text, "42, see example", EntityType.Spoiler), at(text, "example.com", EntityType.Url))
        val opened = mutableListOf<String>()
        compose.setContent { MioTheme { ChatScreen("Saved", listOf(message(text, *entities)), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, onOpenLink = { opened += it }) } }
        val node = compose.onNode(hasText(text), useUnmergedTree = true)
        val hidden = node.annotated()
        assertEquals(Color.Transparent, hidden.styleAt(text.indexOf("42")).color)
        assertEquals(listOf("spoiler"), hidden.tags())
        node.performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == "spoiler" }
        compose.waitForIdle()
        val shown = node.annotated()
        assertNotEquals(Color.Transparent, shown.styleAt(text.indexOf("42")).color)
        assertEquals(listOf("example.com"), shown.tags())
        node.performFirstLinkClick()
        compose.waitForIdle()
        assertEquals(listOf("example.com"), opened)
    }

    @Test
    fun tapsOpenLinksMentionsAppsAndCommandsOrCopy() {
        val text = "@carol docs Alice #kotlin +16502530000 a@b.co /start"
        val entities = arrayOf(
            at(text, "@carol", EntityType.Mention),
            at(text, "docs", EntityType.TextUrl("https://core.telegram.org/api/entities")),
            at(text, "Alice", EntityType.MentionName(501)),
            at(text, "#kotlin", EntityType.Hashtag),
            at(text, "+16502530000", EntityType.Phone),
            at(text, "a@b.co", EntityType.Email),
            at(text, "/start", EntityType.BotCommand),
        )
        val opened = mutableListOf<String>()
        val system = mutableListOf<String>()
        val commands = mutableListOf<String>()
        val handler = object : UriHandler {
            override fun openUri(uri: String) {
                system += uri
            }
        }
        compose.setContent {
            MioTheme {
                CompositionLocalProvider(LocalUriHandler provides handler) {
                    ChatScreen("Saved", listOf(message(text, *entities)), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, onOpenLink = { opened += it }, onCommand = { commands += it })
                }
            }
        }
        val node = compose.onNode(hasText(text), useUnmergedTree = true)
        for (tag in node.annotated().tags()) {
            node.performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == tag }
            compose.waitForIdle()
        }
        assertEquals(listOf("https://t.me/carol", "https://core.telegram.org/api/entities", "tg://user?id=501"), opened)
        assertEquals(listOf("tel:+16502530000", "mailto:a@b.co"), system)
        assertEquals(listOf("/start"), commands)
        assertEquals("#kotlin", clipboard)
    }

    @Test
    fun aLongPressCopiesTheEntityInsteadOfOpeningActions() {
        val text = "read the docs please, it is all there and more"
        val opened = mutableListOf<String>()
        compose.setContent {
            MioTheme {
                ChatScreen("Saved", listOf(message(text, at(text, "docs", EntityType.TextUrl("https://core.telegram.org")))), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, onOpenLink = { opened += it })
            }
        }
        val node = compose.onNodeWithTag("messageText_1", useUnmergedTree = true)
        val layout = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layout)
        val box = layout.single().getBoundingBox(text.indexOf("docs") + 1)
        node.performTouchInput { longClick(box.center) }
        compose.waitForIdle()
        assertEquals("https://core.telegram.org", clipboard)
        assertEquals(emptyList(), opened)
        compose.onNodeWithTag("actionReply").assertDoesNotExist()
        val result = layout.single()
        assertEquals(2, result.lineCount)
        val under = box.center + Offset(0f, result.getLineBottom(0) - result.getLineTop(0))
        node.performTouchInput { click(under) }
        compose.waitForIdle()
        assertEquals(emptyList(), opened)
        node.performTouchInput { longClick(under) }
        compose.onNodeWithTag("actionReply").assertIsDisplayed()
    }

    @Test
    fun preBlocksCopyAndQuotesExpand() {
        val code = "fun main() {\n    println(\"hi\")\n}"
        val quote = (1..6).joinToString("\n") { "line $it of the quote" }
        val text = "look:\n$code\n$quote\nend"
        val entities = arrayOf(at(text, code, EntityType.Pre("kotlin")), at(text, quote, EntityType.Blockquote(collapsed = true)), at(text, "line 1", EntityType.Bold))
        compose.setContent { MioTheme { ChatScreen("Saved", listOf(message(text, *entities)), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}) } }
        compose.onNodeWithText("look:", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("kotlin", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(code, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("copyCode").performClick()
        assertEquals(code, clipboard)
        val quoted = compose.onNode(hasText(quote), useUnmergedTree = true).annotated()
        assertEquals(FontWeight.Bold, quoted.styleAt(0).fontWeight)
        compose.onNodeWithTag("quoteExpand", useUnmergedTree = true).assertContentDescriptionEquals("Show more").performClick()
        compose.onNodeWithTag("quoteExpand", useUnmergedTree = true).assertContentDescriptionEquals("Show less")
        compose.onNodeWithText("end", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun datesAreWrittenForTheUserAndOfferAReminder() {
        val now = System.currentTimeMillis() / 1000
        val text = "meet in a while, at noon"
        val entities = arrayOf(
            at(text, "in a while", EntityType.FormattedDate((now + 2 * 3_600 + 100).toInt(), relative = true)),
            at(text, "at noon", EntityType.FormattedDate((now + 86_400).toInt())),
        )
        val reminders = mutableListOf<Pair<Int, Int>>()
        compose.setContent { MioTheme { ChatScreen("Saved", listOf(message(text, *entities)), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, onRemind = { id, date -> reminders += id to date }) } }
        val node = compose.onNode(hasText("meet in 2 hours, at noon"), useUnmergedTree = true)
        assertEquals(listOf("date:${now + 2 * 3_600 + 100}", "date:${now + 86_400}"), node.annotated().tags())
        node.performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == "date:${now + 86_400}" }
        compose.onNodeWithTag("dateCopy").assertIsDisplayed()
        compose.onNodeWithTag("dateCalendar").assertIsDisplayed()
        compose.onNodeWithTag("dateRemind").performClick()
        assertEquals(listOf(1 to (now + 86_400).toInt()), reminders)
        assertFalse(compose.onAllNodesWithTagExists("dateRemind"))
    }

    @Test
    fun customEmojiAreDrawnInlineOnceTheirFilesAreThere() {
        val text = "nice \u2B50 and \u2764 done"
        val entities = arrayOf(at(text, "\u2B50", EntityType.CustomEmoji(5)), at(text, "\u2764", EntityType.CustomEmoji(6)), at(text, "nice", EntityType.Bold))
        val star = MediaItem("doc5", "emoji", 10, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "\u2B50", animated = true, textColor = true)
        val heart = MediaItem("doc6", "emoji", 10, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "\u2764")
        var files by mutableStateOf(mapOf("doc5" to "{}".toByteArray()))
        compose.setContent {
            MioTheme {
                ChatScreen("Saved", listOf(message(text, *entities)), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, stickers = files, customEmoji = mapOf(5L to star, 6L to heart))
            }
        }
        compose.onNodeWithTag("emoji_5", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("emoji_6", useUnmergedTree = true).assertDoesNotExist()
        val node = compose.onNode(hasText(text), useUnmergedTree = true)
        val size = compose.onNodeWithTag("emoji_5", useUnmergedTree = true).fetchSemanticsNode().size
        assertTrue(size.height in 1..100, "$size")
        assertEquals(FontWeight.Bold, node.annotated().styleAt(0).fontWeight)
        files = files + ("doc6" to "RIFF".toByteArray())
        compose.onNodeWithTag("emoji_6", useUnmergedTree = true).assertExists()
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagExists(tag: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
}
