package app.miogram

import app.miogram.data.telegram.articleOf
import app.miogram.data.telegram.inputRichMessageOf
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
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.ktproto.api.types.InlineButtonTypeDisabled
import cn.ellerotta.ktproto.api.types.InlineButtonTypeUserProfile
import cn.ellerotta.ktproto.api.types.InputDocument
import cn.ellerotta.ktproto.api.types.InputGeoPoint
import cn.ellerotta.ktproto.api.types.InputPageBlockMap
import cn.ellerotta.ktproto.api.types.InputPhoto
import cn.ellerotta.ktproto.api.types.InputUser
import cn.ellerotta.ktproto.api.types.PageBlockButtonRow
import cn.ellerotta.ktproto.api.types.PageBlockDocument
import cn.ellerotta.ktproto.api.types.PageBlockParagraph
import cn.ellerotta.ktproto.api.types.PageBlockPhoto
import cn.ellerotta.ktproto.api.types.RichMessage
import cn.ellerotta.ktproto.api.types.TextPlain
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.junit.Test

class ArticleInputTest {
    private fun p(text: String) = ArticleText.Plain(text)

    private fun media(key: String) = MediaItem(key, key, 1, 2, isPhoto = key.startsWith("photo"))

    private val photo = media("photo11")
    private val video = media("doc12")
    private val file = media("doc13")
    private val image = media("doc14")
    private val inputs = mapOf(
        "photo11" to InputPhoto(11, 1, byteArrayOf(1)),
        "doc12" to InputDocument(12, 1, byteArrayOf(2)),
        "doc13" to InputDocument(13, 1, byteArrayOf(3)),
        "doc14" to InputDocument(14, 1, byteArrayOf(4)),
    )

    private fun convert(article: ArticleMessage, withUsers: Boolean = true) = inputRichMessageOf(
        article,
        photo = { inputs[it.key] as? InputPhoto },
        document = { inputs[it.key] as? InputDocument },
        user = { id -> if (id == ALICE) InputUser(ALICE, 9) else null },
        withUsers = withUsers,
    )

    private fun back(article: ArticleMessage): List<ArticleBlock> {
        val input = convert(article)
        return articleOf(RichMessage(blocks = input.blocks, photos = emptyList(), documents = emptyList()), null, null, null).blocks
    }

    @Test
    fun textBlocksAndInlineKindsGoOutAsTheyAre() {
        val date = EntityType.FormattedDate(1_700_000_000, relative = true)
        val inline = ArticleText.Concat(
            listOf(
                p("plain "),
                ArticleText.Styled(ArticleStyle.Bold, p("bold")),
                ArticleText.Styled(ArticleStyle.Italic, p("italic")),
                ArticleText.Styled(ArticleStyle.Underline, p("under")),
                ArticleText.Styled(ArticleStyle.Strike, p("strike")),
                ArticleText.Styled(ArticleStyle.Fixed, p("mono")),
                ArticleText.Styled(ArticleStyle.Spoiler, p("secret")),
                ArticleText.Styled(ArticleStyle.Subscript, p("sub")),
                ArticleText.Styled(ArticleStyle.Superscript, p("sup")),
                ArticleText.Styled(ArticleStyle.Marked, p("marked")),
                ArticleText.Url(p("link"), "https://core.telegram.org"),
                ArticleText.Email(p("mail"), "a@b.co"),
                ArticleText.Phone(p("call"), "+1"),
                ArticleText.MentionName(p("Alice"), ALICE),
                ArticleText.CustomEmoji(5, "\u2B50"),
                ArticleText.Math("x^2"),
                ArticleText.Date(p("then"), date),
                ArticleText.Diff(p("new"), p("old")),
                ArticleText.Anchor(p("note"), "n"),
            ),
        )
        val blocks = listOf(
            ArticleBlock.Paragraph(inline),
            ArticleBlock.Heading(1, p("h1")),
            ArticleBlock.Heading(2, p("h2")),
            ArticleBlock.Heading(3, p("h3")),
            ArticleBlock.Heading(6, p("h6")),
            ArticleBlock.Preformatted(p("val x = 1"), "kotlin"),
            ArticleBlock.Footer(p("footer")),
            ArticleBlock.Items(
                listOf(
                    ArticleListItem(listOf(ArticleBlock.Paragraph(p("todo"))), checkbox = true),
                    ArticleListItem(listOf(ArticleBlock.Paragraph(p("done"))), checkbox = true, checked = true),
                    ArticleListItem(listOf(ArticleBlock.Paragraph(p("outer")), ArticleBlock.Items(listOf(ArticleListItem(listOf(ArticleBlock.Paragraph(p("inner")))))))),
                ),
            ),
            ArticleBlock.Items(listOf(ArticleListItem(listOf(ArticleBlock.Paragraph(p("one"))), num = "a"), ArticleListItem(listOf(ArticleBlock.Paragraph(p("two"))), value = 7)), ordered = true, start = 3, reversed = true),
            ArticleBlock.Quote(p("quoted"), p("Alice"), collapsed = true),
            ArticleBlock.QuoteBlocks(listOf(ArticleBlock.Paragraph(p("inside"))), p("Bob")),
            ArticleBlock.Pullquote(p("pulled"), p("Carol")),
            ArticleBlock.Details(p("More"), listOf(ArticleBlock.Paragraph(p("hidden"))), open = true),
            ArticleBlock.Table(
                p("Scores"),
                listOf(
                    listOf(ArticleCell(p("Name"), header = true), ArticleCell(p("Points"), header = true, align = ArticleAlign.Center)),
                    listOf(ArticleCell(p("Bob"), colspan = 2, valign = ArticleVerticalAlign.Bottom, align = ArticleAlign.End)),
                    listOf(ArticleCell(null, rowspan = 2, valign = ArticleVerticalAlign.Middle)),
                ),
                bordered = true,
                striped = true,
                compact = true,
            ),
            ArticleBlock.Math("E = mc^2"),
            ArticleBlock.Divider,
            ArticleBlock.Anchor("end"),
        )
        assertEquals(blocks, back(ArticleMessage(blocks)))
        val input = convert(ArticleMessage(blocks))
        assertFalse(input.rtl)
        assertFalse(input.noautolink)
        assertNull(input.photos)
        assertNull(input.documents)
    }

    @Test
    fun linksTelegramFindsGoBackAsTheirText() {
        val found = ArticleText.Concat(
            listOf(
                ArticleText.Url(p("example.com"), "example.com", auto = true),
                ArticleText.Mention(p("@carol")),
                ArticleText.Hashtag(p("#tag")),
                ArticleText.Cashtag(p("\$TON")),
                ArticleText.BotCommand(p("/start")),
                ArticleText.BankCard(p("4111")),
            ),
        )
        val sent = back(ArticleMessage(listOf(ArticleBlock.Paragraph(found))))
        assertEquals(listOf(ArticleBlock.Paragraph(ArticleText.Concat(listOf(p("example.com"), p("@carol"), p("#tag"), p("\$TON"), p("/start"), p("4111"))))), sent)
    }

    @Test
    fun mediaGoByIdWithTheirInputsOnce() {
        val article = ArticleMessage(
            listOf(
                ArticleBlock.Photo(photo, ArticleCaption(p("a photo"), p("me")), spoiler = true),
                ArticleBlock.Video(video, loop = true),
                ArticleBlock.Collage(listOf(ArticleBlock.Photo(photo), ArticleBlock.Video(video))),
                ArticleBlock.Document(file, ArticleCaption(p("the file"))),
                ArticleBlock.Paragraph(ArticleText.Image(14, 16, 16, image)),
                ArticleBlock.Photo(media("photo99")),
                ArticleBlock.Map(52.5, 13.4, 15, 600, 300, ArticleCaption(p("Berlin"))),
            ),
        )
        val input = convert(article)
        assertEquals(listOf(11L), input.photos!!.map { (it as InputPhoto).id })
        assertEquals(listOf(12L, 13L, 14L), input.documents!!.map { (it as InputDocument).id })
        assertEquals(6, input.blocks.size)
        val first = assertIs<PageBlockPhoto>(input.blocks[0])
        assertEquals(11L to true, first.photoId to first.spoiler)
        assertEquals(13L, assertIs<PageBlockDocument>(input.blocks[3]).documentId)
        val map = assertIs<InputPageBlockMap>(input.blocks[5])
        val geo = assertIs<InputGeoPoint>(map.geo)
        assertEquals(listOf(52.5, 13.4), listOf(geo.lat, geo.long))
        assertEquals(listOf(15, 600, 300), listOf(map.zoom, map.w, map.h))
        val mapped = articleOf(RichMessage(blocks = input.blocks.take(5), photos = emptyList(), documents = emptyList()), null, null, null).blocks
        assertEquals(
            listOf(
                ArticleBlock.Photo(null, ArticleCaption(p("a photo"), p("me")), spoiler = true),
                ArticleBlock.Video(null, loop = true),
                ArticleBlock.Collage(listOf(ArticleBlock.Photo(null), ArticleBlock.Video(null))),
                ArticleBlock.Document(null, ArticleCaption(p("the file"))),
                ArticleBlock.Paragraph(ArticleText.Image(14, 16, 16)),
            ),
            mapped,
        )
    }

    @Test
    fun profileButtonsBringTheirUsers() {
        val row = ArticleBlock.Buttons(
            listOf(
                ArticleButton(p("Site"), BotButton.Url("Site", "https://core.telegram.org", BotButtonStyle.Primary)),
                ArticleButton(p("Copy"), BotButton.Copy("Copy", "copied", BotButtonStyle.Link)),
                ArticleButton(p("Alice"), BotButton.UserProfile("Alice", ALICE)),
                ArticleButton(p("Stranger"), BotButton.UserProfile("Stranger", 999)),
            ),
            ArticleAlign.Center,
        )
        val input = convert(ArticleMessage(listOf(row)))
        assertEquals(listOf(ALICE), input.users!!.map { (it as InputUser).userId })
        val sent = assertIs<PageBlockButtonRow>(input.blocks.single())
        assertEquals(true, sent.alignCenter)
        assertEquals(ALICE, assertIs<InlineButtonTypeUserProfile>(sent.buttons[2].type).userId)
        assertIs<InlineButtonTypeDisabled>(sent.buttons[3].type)
        assertEquals(true, sent.buttons[0].style?.bgPrimary)
        assertEquals(true, sent.buttons[1].style?.link)
        assertEquals(
            listOf(
                BotButton.Url("Site", "https://core.telegram.org", BotButtonStyle.Primary),
                BotButton.Copy("Copy", "copied", BotButtonStyle.Link),
                BotButton.UserProfile("Alice", ALICE),
            ),
            (back(ArticleMessage(listOf(row))).single() as ArticleBlock.Buttons).buttons.take(3).map { it.action },
        )
        val draft = convert(ArticleMessage(listOf(row)), withUsers = false)
        assertNull(draft.users)
        assertIs<InlineButtonTypeUserProfile>(assertIs<PageBlockButtonRow>(draft.blocks.single()).buttons[3].type)
    }

    @Test
    fun aParagraphIsOneBlock() {
        val input = convert(ArticleMessage(listOf(ArticleBlock.Paragraph(p("hello")))))
        assertEquals("hello", (assertIs<PageBlockParagraph>(input.blocks.single()).text as TextPlain).text)
        assertNull(input.users)
    }

    private companion object {
        const val ALICE = 501L
    }
}
