package cn.ellerotta.core.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import cn.ellerotta.core.model.ArticleAlign
import cn.ellerotta.core.model.ArticleBlock
import cn.ellerotta.core.model.ArticleButton
import cn.ellerotta.core.model.ArticleCaption
import cn.ellerotta.core.model.ArticleCell
import cn.ellerotta.core.model.ArticleListItem
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.ArticleStyle
import cn.ellerotta.core.model.ArticleText
import cn.ellerotta.core.model.ArticleVerticalAlign
import cn.ellerotta.core.model.BotButton
import cn.ellerotta.core.model.BotButtonStyle
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.plainText
import cn.ellerotta.core.ui.components.chat.BubblePosition
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.MessageBubble
import cn.ellerotta.core.ui.components.chat.bubbleColors
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
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
class ArticleTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clipboard get() = context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()

    private fun p(text: String) = ArticleText.Plain(text)

    private fun concat(vararg parts: ArticleText) = ArticleText.Concat(parts.toList())

    private fun message(vararg blocks: ArticleBlock) = MessageItem(1, "", out = false, date = 1_790_000_000, article = ArticleMessage(blocks.toList()))

    private fun SemanticsNodeInteraction.annotated(): AnnotatedString = fetchSemanticsNode().config[SemanticsProperties.Text].first()

    private fun AnnotatedString.styleAt(index: Int): SpanStyle =
        spanStyles.filter { index >= it.start && index < it.end }.fold(SpanStyle()) { merged, range -> merged.merge(range.item) }

    private fun bubble(
        message: MessageItem,
        onOpenLink: (String) -> Unit = {},
        onButton: (BotButton) -> Unit = {},
        uri: UriHandler? = null,
        scroll: ((ScrollState) -> Unit)? = null,
        onDownload: (MediaItem) -> Unit = {},
        onToggleVoice: (MediaItem) -> Unit = {},
        onOpenMedia: (Int) -> Unit = {},
    ) {
        compose.setContent {
            MioTheme {
                val handler = uri ?: LocalUriHandler.current
                CompositionLocalProvider(LocalUriHandler provides handler) {
                    val content = @Composable {
                        MessageBubble(
                            message, BubblePosition(first = true, last = true), emptyMap(), emptyMap(), onDownload,
                            onToggleVoice = onToggleVoice,
                            onOpenLink = onOpenLink,
                            onButton = onButton,
                            onOpenArticleMedia = onOpenMedia,
                        )
                    }
                    if (scroll == null) {
                        content()
                    } else {
                        val state = rememberScrollState()
                        scroll(state)
                        Column(Modifier.height(240.dp).verticalScroll(state)) { content() }
                    }
                }
            }
        }
    }

    @Test
    fun textBlocksShowInTheirStyles() {
        val opened = mutableListOf<String>()
        val uri = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }
        bubble(
            message(
                *(1..6).map { ArticleBlock.Heading(it, p("Heading $it")) }.toTypedArray(),
                ArticleBlock.Paragraph(p("A paragraph")),
                ArticleBlock.Footer(p("the footer")),
                ArticleBlock.Thinking(p("thinking it over")),
                ArticleBlock.Preformatted(p("fun main() {}"), "kotlin"),
                ArticleBlock.Pullquote(p("pulled"), p("Carol")),
                ArticleBlock.Divider,
                ArticleBlock.Math("E = mc^2"),
                ArticleBlock.Anchor("end"),
                ArticleBlock.Unsupported,
            ),
            uri = uri,
            scroll = {},
        )
        val first = compose.onNodeWithTag("heading1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        val last = compose.onNodeWithTag("heading6", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        assertTrue(first > last * 1.4f, "heading 1 is $first high, heading 6 $last")
        compose.onNodeWithText("A paragraph").assertExists()
        compose.onNodeWithTag("footer", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("thinking").assertExists()
        compose.onNodeWithText("thinking it over").assertExists()
        compose.onNodeWithText("kotlin").assertExists()
        val pre = compose.onNodeWithTag("preText", useUnmergedTree = true)
        assertEquals("fun main() {}", pre.annotated().text)
        compose.onNodeWithText("pulled").assertExists()
        compose.onNodeWithText("Carol").assertExists()
        compose.onNodeWithTag("divider").assertExists()
        compose.onNodeWithTag("math").assertExists()
        compose.onNodeWithText("E = mc^2").assertExists()
        compose.onNodeWithTag("articleUnsupported").assertExists()
        compose.onNodeWithTag("articleUpdate").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("market://details?id=${context.packageName}", opened.single())
    }

    @Test
    fun inlineMarksStyleTheirPieces() {
        val text = concat(
            p("a"), ArticleText.Styled(ArticleStyle.Subscript, p("sub")), p(" "),
            ArticleText.Styled(ArticleStyle.Superscript, p("sup")), p(" "),
            ArticleText.Styled(ArticleStyle.Marked, p("marked")), p(" "),
            ArticleText.Diff(ArticleText.Empty, p("gone")), p(" "),
            ArticleText.Diff(p("added"), ArticleText.Empty), p(" "),
            ArticleText.Diff(p("changed"), p("before")), p(" "),
            ArticleText.Math("x^2"), p(" "),
            ArticleText.Styled(ArticleStyle.Bold, p("bold")),
        )
        var error = Color.Unspecified
        var primary = Color.Unspecified
        compose.setContent {
            MioTheme {
                val bubble = bubbleColors(MaterialTheme.colorScheme, out = false)
                error = bubble.danger
                primary = bubble.accent
                MessageBubble(message(ArticleBlock.Paragraph(text)), BubblePosition(first = true, last = true), emptyMap(), emptyMap(), {})
            }
        }
        val shown = compose.onNode(hasText("asub sup marked gone added changed x^2 bold"), useUnmergedTree = true).annotated()
        fun at(part: String) = shown.styleAt(shown.text.indexOf(part))
        assertEquals(BaselineShift.Subscript, at("sub").baselineShift)
        assertEquals(BaselineShift.Superscript, at("sup").baselineShift)
        assertTrue(at("marked").background != Color.Unspecified)
        assertEquals(error, at("gone").color)
        assertEquals(TextDecoration.LineThrough, at("gone").textDecoration)
        assertEquals(primary, at("added").color)
        assertEquals(Color.Unspecified, at("changed").color)
        assertTrue("before" !in shown.text)
        assertEquals(FontFamily.Monospace, at("x^2").fontFamily)
    }

    @Test
    fun listsShowMarkersCheckboxesAndNesting() {
        bubble(
            message(
                ArticleBlock.Items(
                    listOf(
                        ArticleListItem(listOf(ArticleBlock.Paragraph(p("first")))),
                        ArticleListItem(listOf(ArticleBlock.Paragraph(p("todo"))), checkbox = true),
                        ArticleListItem(listOf(ArticleBlock.Paragraph(p("done"))), checkbox = true, checked = true),
                        ArticleListItem(listOf(ArticleBlock.Paragraph(p("nested")), ArticleBlock.Items(listOf(ArticleListItem(listOf(ArticleBlock.Paragraph(p("deeper")))))))),
                    ),
                ),
                ArticleBlock.Items(
                    listOf(
                        ArticleListItem(listOf(ArticleBlock.Paragraph(p("lettered"))), num = "a"),
                        ArticleListItem(listOf(ArticleBlock.Paragraph(p("valued"))), value = 7),
                        ArticleListItem(listOf(ArticleBlock.Paragraph(p("counted")))),
                    ),
                    ordered = true,
                    start = 3,
                    reversed = true,
                ),
            ),
        )
        val markers = compose.onAllNodesWithTag("marker", useUnmergedTree = true).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].first().text }
        assertEquals(listOf("\u2022", "\u2022", "\u2022", "a.", "7.", "1."), markers)
        compose.onNodeWithTag("checkboxOpen", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("checkboxChecked", useUnmergedTree = true).assertExists()
        val nested = compose.onNode(hasText("nested"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        val deeper = compose.onNode(hasText("deeper"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        assertTrue(deeper > nested + 10, "nested at $nested, deeper at $deeper")
    }

    @Test
    @Config(qualifiers = "w412dp-h892dp")
    fun collapsedQuotesAndDetailsOpenInPlace() {
        val long = (1..12).joinToString(" ") { "line $it of a long quote that wraps" }
        bubble(
            message(
                ArticleBlock.Quote(p(long), author = p("Alice"), collapsed = true),
                ArticleBlock.QuoteBlocks(listOf(ArticleBlock.Paragraph(p("inside"))), p("Bob")),
                ArticleBlock.Details(p("More"), listOf(ArticleBlock.Paragraph(p("hidden")))),
            ),
        )
        compose.onNodeWithText("Alice").assertExists()
        compose.onNodeWithText("inside").assertExists()
        compose.onNodeWithText("Bob").assertExists()
        val collapsed = compose.onNode(hasText(long), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("quoteExpand").performClick()
        compose.waitForIdle()
        val expanded = compose.onNode(hasText(long), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        assertTrue(expanded > collapsed, "collapsed $collapsed, expanded $expanded")
        assertTrue(compose.onAllNodes(hasText("hidden")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("detailsToggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("hidden").assertExists()
        compose.onNodeWithTag("detailsToggle").performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasText("hidden")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun anchorLinksOpenFootnotes() {
        val opened = mutableListOf<String>()
        bubble(
            message(
                ArticleBlock.Paragraph(concat(p("read "), ArticleText.Url(p("the note"), "#No%74e"), p(" "), ArticleText.Anchor(p("the footnote text"), "note"))),
            ),
            onOpenLink = { opened += it },
        )
        val text = compose.onNode(hasText("read the note the footnote text"), useUnmergedTree = true)
        text.performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == "#No%74e" }
        compose.waitForIdle()
        assertEquals("the footnote text", compose.onNodeWithTag("footnoteText", useUnmergedTree = true).annotated().text)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun anchorLinksScrollToTheirBlock() {
        val opened = mutableListOf<String>()
        var scroll: ScrollState? = null
        bubble(
            message(
                ArticleBlock.Paragraph(concat(p("go "), ArticleText.Url(p("jump"), "#end"), p(" or "), ArticleText.Url(p("leave"), "https://core.telegram.org"))),
                *(1..30).map { ArticleBlock.Paragraph(p("filler paragraph $it")) }.toTypedArray(),
                ArticleBlock.Anchor("end"),
                ArticleBlock.Paragraph(p("the end")),
            ),
            onOpenLink = { opened += it },
            scroll = { scroll = it },
        )
        val jump = compose.onNode(hasText("go jump or leave"), useUnmergedTree = true)
        jump.performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == "https://core.telegram.org" }
        compose.waitForIdle()
        assertEquals(listOf("https://core.telegram.org"), opened)
        assertEquals(0, scroll!!.value)
        jump.performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == "#end" }
        compose.waitForIdle()
        assertTrue(scroll!!.value > 0, "scrolled to ${scroll!!.value}")
        assertEquals(1, opened.size)
    }

    @Test
    fun buttonsInsideTextArePressedSpoilersRevealed() {
        val pressed = mutableListOf<BotButton>()
        val ping = BotButton.Callback("Ping", "ping".encodeToByteArray(), BotButtonStyle.Primary)
        bubble(
            message(
                ArticleBlock.Paragraph(
                    concat(
                        p("press "), ArticleText.Button(p("Ping"), ping), p(" not "), ArticleText.Button(p("Off"), BotButton.Disabled("Off")),
                        p(" and "), ArticleText.Styled(ArticleStyle.Spoiler, p("secret")),
                    ),
                ),
            ),
            onButton = { pressed += it },
        )
        compose.onNodeWithTag("inlineButton_Ping", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf<BotButton>(ping), pressed)
        compose.onNodeWithTag("inlineButton_Off", useUnmergedTree = true).assertIsNotEnabled()
        val node = compose.onNode(hasText("press Ping not Off and secret"), useUnmergedTree = true)
        assertEquals(Color.Transparent, node.annotated().styleAt(node.annotated().text.indexOf("secret")).color)
        node.performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == "spoiler" }
        compose.waitForIdle()
        assertTrue(node.annotated().styleAt(node.annotated().text.indexOf("secret")).color != Color.Transparent)
    }

    @Test
    fun copyGivesThePlainTextOfAllBlocks() {
        val message = message(
            ArticleBlock.Heading(1, p("Notes")),
            ArticleBlock.Paragraph(concat(p("so "), ArticleText.Math("x^2"))),
            ArticleBlock.Math("E = mc^2"),
        )
        compose.setContent { MioTheme { ChatScreen("Saved", listOf(message), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}) } }
        compose.onNodeWithTag("message_1").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNode(hasTestTag("actionCopy")).performClick()
        compose.waitForIdle()
        assertEquals("Notes\nso x^2\nE = mc^2", clipboard)
        assertEquals(message.article!!.plainText(), clipboard)
    }

    private fun photo(key: String, width: Int = 800, height: Int = 600) = MediaItem(key, "$key.jpg", 1_000, 2, isPhoto = true, kind = MediaKind.Photo, width = width, height = height)

    private fun uriHandler(opened: MutableList<String>) = object : UriHandler {
        override fun openUri(uri: String) {
            opened += uri
        }
    }

    @Test
    fun photosAndVideosFillTheBubbleCappedAndOpenTheViewer() {
        val opened = mutableListOf<Int>()
        val video = MediaItem("doc9", "clip.mp4", 5_000, 2, isPhoto = false, kind = MediaKind.Video, width = 640, height = 360, duration = 75)
        bubble(
            message(
                ArticleBlock.Photo(photo("photo1", 500, 4_000), ArticleCaption(p("A tall photo"), p("by me"))),
                ArticleBlock.Video(video),
                ArticleBlock.Photo(photo("photo2"), spoiler = true),
                ArticleBlock.Photo(null, ArticleCaption(p("missing"))),
            ),
            onOpenMedia = { opened += it },
            scroll = {},
        )
        val density = compose.density.density
        val configuration = context.resources.configuration
        val cap = maxOf(configuration.screenWidthDp, configuration.screenHeightDp) * 0.55f * density
        val tall = compose.onNodeWithTag("articleMedia_0").fetchSemanticsNode().size
        val wide = compose.onNodeWithTag("articleMedia_1").fetchSemanticsNode().size
        assertEquals(tall.width, wide.width)
        assertTrue(tall.height <= cap + 1 && tall.height > cap - 2, "tall photo ${tall.height}, cap $cap")
        assertTrue(kotlin.math.abs(wide.width * 360 / 640 - wide.height) <= 2, "video ${wide.width} by ${wide.height}")
        compose.onNodeWithText("A tall photo").assertExists()
        compose.onNodeWithText("by me").assertExists()
        compose.onNodeWithText("missing").assertExists()
        compose.onNodeWithTag("videoDuration", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("articleMedia_0").performClick()
        compose.onNodeWithTag("articleMedia_1").performScrollTo().performClick()
        compose.onNodeWithTag("mediaSpoiler", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("articleMedia_2").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithTag("mediaSpoiler", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("articleMedia_2").performClick()
        compose.waitForIdle()
        assertEquals(listOf(0, 1, 2), opened)
    }

    @Test
    fun collagesAndSlideshowsShowEveryItem() {
        val opened = mutableListOf<Int>()
        bubble(
            message(
                ArticleBlock.Collage(listOf(ArticleBlock.Photo(photo("a")), ArticleBlock.Photo(photo("b")), ArticleBlock.Photo(photo("c"))), ArticleCaption(p("Side by side"))),
                ArticleBlock.Slideshow(listOf(ArticleBlock.Photo(photo("d")), ArticleBlock.Photo(photo("e")), ArticleBlock.Photo(null))),
            ),
            onOpenMedia = { opened += it },
            scroll = {},
        )
        val cells = (0..2).map { compose.onNodeWithTag("articleMedia_$it").fetchSemanticsNode().boundsInRoot }
        assertTrue(cells.all { kotlin.math.abs(it.top - cells[0].top) < 1f && kotlin.math.abs(it.width - it.height) < 2f })
        assertTrue(cells[0].left < cells[1].left && cells[1].left < cells[2].left)
        compose.onNodeWithText("Side by side").assertExists()
        compose.onNodeWithTag("articleMedia_2").performClick()
        compose.onNodeWithText("1 of 2").assertExists()
        compose.onNodeWithTag("slideshow").performScrollTo().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("2 of 2").assertExists()
        compose.onNodeWithTag("articleMedia_4").performClick()
        compose.waitForIdle()
        assertEquals(listOf(2, 4), opened)
    }

    @Test
    fun audioDocumentsAndMapsUseTheirRows() {
        val toggled = mutableListOf<String>()
        val downloads = mutableListOf<String>()
        val opened = mutableListOf<String>()
        val song = MediaItem("doc4545", "song.mp3", 4_000, 2, isPhoto = false, kind = MediaKind.Music, duration = 30)
        val file = MediaItem("doc777", "big.bin", 10_000, 2, isPhoto = false, kind = MediaKind.File)
        bubble(
            message(
                ArticleBlock.Audio(song),
                ArticleBlock.Document(file, ArticleCaption(p("The file"))),
                ArticleBlock.Map(52.5, 13.4, 15, 600, 300, ArticleCaption(p("Berlin"))),
            ),
            uri = uriHandler(opened),
            onDownload = { downloads += it.key },
            onToggleVoice = { toggled += it.key },
        )
        compose.onNodeWithText("song.mp3").assertExists()
        compose.onNodeWithTag("voiceToggle_doc4545").performClick()
        compose.onNodeWithTag("download_doc777").performClick()
        compose.onNodeWithText("The file").assertExists()
        compose.onNodeWithText("Berlin").assertExists()
        compose.onNodeWithTag("map").performClick()
        compose.waitForIdle()
        assertEquals(listOf("doc4545"), toggled)
        assertEquals(listOf("doc777"), downloads)
        assertEquals(listOf("geo:52.5,13.4?z=15"), opened)
    }

    @Test
    fun tablesHonourSpansAndScrollWhenWide() {
        fun cell(text: String, header: Boolean = false, colspan: Int = 1, rowspan: Int = 1) = ArticleCell(p(text), header = header, colspan = colspan, rowspan = rowspan)
        bubble(
            message(
                ArticleBlock.Table(
                    p("Scores"),
                    listOf(
                        listOf(cell("Name", header = true), cell("Points", header = true)),
                        listOf(cell("Alice"), ArticleCell(p("3"), align = ArticleAlign.End, valign = ArticleVerticalAlign.Middle)),
                        listOf(cell("Bob", colspan = 2)),
                        listOf(cell("Tall", rowspan = 2), cell("x")),
                        listOf(cell("y")),
                    ),
                    bordered = true,
                    striped = true,
                ),
                ArticleBlock.Table(ArticleText.Empty, listOf((1..8).map { cell("a rather long cell number $it") }), compact = true),
            ),
            scroll = {},
        )
        compose.onNodeWithText("Scores").assertExists()
        fun bounds(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        assertEquals(bounds("cell_0_0").width + bounds("cell_0_1").width, bounds("cell_2_0").width, 1f)
        assertEquals(bounds("cell_3_1").left, bounds("cell_4_1").left, 1f)
        assertTrue(bounds("cell_4_1").top > bounds("cell_3_1").top)
        val tables = compose.onAllNodesWithTag("table").fetchSemanticsNodes()
        val grids = compose.onAllNodesWithTag("tableGrid", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(tables[0].size.width, grids[0].size.width)
        assertTrue(grids[1].size.width > tables[1].size.width * 2)
    }

    @Test
    fun buttonRowsGoToTheKeyboardHandler() {
        val pressed = mutableListOf<BotButton>()
        val site = BotButton.Url("Site", "https://core.telegram.org")
        val copy = BotButton.Copy("Copy", "copied text", BotButtonStyle.Link)
        bubble(
            message(
                ArticleBlock.Buttons(listOf(ArticleButton(p("Site"), site), ArticleButton(p("Copy"), copy), ArticleButton(p("Off"), BotButton.Disabled("Off", BotButtonStyle.Danger))), ArticleAlign.Center),
                ArticleBlock.Buttons(listOf(ArticleButton(p("Left"), BotButton.Callback("Left", byteArrayOf(1))), ArticleButton(p("Right"), BotButton.Callback("Right", byteArrayOf(2))))),
            ),
            onButton = { pressed += it },
        )
        compose.onNodeWithTag("articleButton_Site").performClick()
        compose.onNodeWithTag("articleButton_Copy").performClick()
        compose.onNodeWithTag("articleButton_Off").assertIsNotEnabled()
        compose.waitForIdle()
        assertEquals(listOf<BotButton>(site, copy), pressed)
        val rows = compose.onAllNodesWithTag("articleButtons").fetchSemanticsNodes()
        val site0 = compose.onNodeWithTag("articleButton_Site").fetchSemanticsNode().boundsInRoot
        assertTrue(site0.left > rows[0].boundsInRoot.left + 1f)
        val left = compose.onNodeWithTag("articleButton_Left").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("articleButton_Right").fetchSemanticsNode().boundsInRoot
        assertEquals(left.width, right.width, 1f)
        assertEquals(rows[1].boundsInRoot.right, right.right, 1f)
    }

    @Test
    fun partsAreCutWithAFadeAndShowMore() {
        val more = mutableListOf<Int>()
        var loading by mutableStateOf(false)
        val blocks = (1..80).map { ArticleBlock.Paragraph(p("paragraph $it of a long part")) }
        val part = MessageItem(1, "", out = false, date = 1_790_000_000, article = ArticleMessage(blocks, part = true))
        val whole = MessageItem(2, "", out = false, date = 1_790_000_000, article = ArticleMessage(blocks.take(2)))
        compose.setContent {
            MioTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    MessageBubble(part, BubblePosition(first = true, last = true), emptyMap(), emptyMap(), {}, onShowMore = { more += 1 }, loadingMore = loading)
                    MessageBubble(whole, BubblePosition(first = true, last = true), emptyMap(), emptyMap(), {}, onShowMore = { more += 2 })
                }
            }
        }
        val height = compose.onNodeWithTag("articleBlocks_1", useUnmergedTree = true).fetchSemanticsNode().size.height
        assertEquals(900 * compose.density.density, height.toFloat(), 1f)
        compose.onNodeWithTag("showMore_1").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(1), more)
        loading = true
        compose.waitForIdle()
        compose.onNodeWithTag("showMoreProgress", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("showMore_1").performClick()
        compose.waitForIdle()
        assertEquals(listOf(1, 1), more)
        assertTrue(compose.onAllNodesWithTag("showMore_2").fetchSemanticsNodes().isEmpty())
    }
}
