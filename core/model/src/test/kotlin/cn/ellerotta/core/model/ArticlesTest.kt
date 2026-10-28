package cn.ellerotta.core.model

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ArticlesTest {
    private fun p(text: String) = ArticleText.Plain(text)

    private fun run(text: ArticleText) = text.flatten()

    @Test
    fun stylesAndLinksBecomeEntities() {
        fun entity(text: ArticleText) = run(text).entities.single().type
        assertEquals(EntityType.Bold, entity(ArticleText.Styled(ArticleStyle.Bold, p("a"))))
        assertEquals(EntityType.Italic, entity(ArticleText.Styled(ArticleStyle.Italic, p("a"))))
        assertEquals(EntityType.Underline, entity(ArticleText.Styled(ArticleStyle.Underline, p("a"))))
        assertEquals(EntityType.Strike, entity(ArticleText.Styled(ArticleStyle.Strike, p("a"))))
        assertEquals(EntityType.Code, entity(ArticleText.Styled(ArticleStyle.Fixed, p("a"))))
        assertEquals(EntityType.Spoiler, entity(ArticleText.Styled(ArticleStyle.Spoiler, p("a"))))
        assertEquals(EntityType.TextUrl("https://t.me/a"), entity(ArticleText.Url(p("a"), "https://t.me/a")))
        assertEquals(EntityType.TextUrl("example.com"), entity(ArticleText.Url(p("example.com"), "example.com", auto = true)))
        assertEquals(EntityType.TextUrl("mailto:a@b.co"), entity(ArticleText.Email(p("write"), "a@b.co")))
        assertEquals(EntityType.TextUrl("mailto:a@b.co"), entity(ArticleText.Email(p("a@b.co"), "a@b.co", auto = true)))
        assertEquals(EntityType.TextUrl("tel:+16502530000"), entity(ArticleText.Phone(p("call"), "+1 (650) 253-0000")))
        assertEquals(EntityType.TextUrl("tel:+16502530000"), entity(ArticleText.Phone(p("+16502530000"), "+16502530000", auto = true)))
        assertEquals(EntityType.Mention, entity(ArticleText.Mention(p("@a"))))
        assertEquals(EntityType.MentionName(5), entity(ArticleText.MentionName(p("Al"), 5)))
        assertEquals(EntityType.Hashtag, entity(ArticleText.Hashtag(p("#a"))))
        assertEquals(EntityType.Cashtag, entity(ArticleText.Cashtag(p("\$A"))))
        assertEquals(EntityType.BotCommand, entity(ArticleText.BotCommand(p("/a"))))
        assertEquals(EntityType.BankCard, entity(ArticleText.BankCard(p("4111"))))
        val date = EntityType.FormattedDate(100, shortTime = true, dayOfWeek = true)
        assertEquals(date, entity(ArticleText.Date(p("then"), date)))
        assertEquals(EntityType.CustomEmoji(7), entity(ArticleText.CustomEmoji(7, "\u2B50")))
    }

    @Test
    fun nestedTextKeepsUtf16Offsets() {
        val text = ArticleText.Concat(
            listOf(
                p("\uD83D\uDE00 "),
                ArticleText.Styled(ArticleStyle.Bold, ArticleText.Concat(listOf(p("bo"), ArticleText.Styled(ArticleStyle.Italic, p("th"))))),
                p(" "),
                ArticleText.CustomEmoji(9, ""),
            ),
        )
        val flat = run(text)
        assertEquals("\uD83D\uDE00 both \uD83D\uDE00", flat.text)
        assertEquals(
            listOf(TextEntity(3, 4, EntityType.Bold), TextEntity(5, 2, EntityType.Italic), TextEntity(8, 2, EntityType.CustomEmoji(9))),
            flat.entities,
        )
    }

    @Test
    fun articleOnlyKindsBecomeMarks() {
        fun mark(text: ArticleText) = run(text).marks.single().let { it.type to (it.offset until it.end) }
        assertEquals(ArticleMarkType.Subscript to 0..0, mark(ArticleText.Styled(ArticleStyle.Subscript, p("a"))))
        assertEquals(ArticleMarkType.Superscript to 0..0, mark(ArticleText.Styled(ArticleStyle.Superscript, p("a"))))
        assertEquals(ArticleMarkType.Marked to 0..0, mark(ArticleText.Styled(ArticleStyle.Marked, p("a"))))
        assertEquals("x^2", run(ArticleText.Math("x^2")).text)
        assertEquals(ArticleMarkType.Math("x^2") to 0..2, mark(ArticleText.Math("x^2")))
        assertEquals("new", run(ArticleText.Diff(p("new"), p("old"))).text)
        assertEquals(ArticleMarkType.Changed to 0..2, mark(ArticleText.Diff(p("new"), p("old"))))
        assertEquals("old", run(ArticleText.Diff(ArticleText.Empty, p("old"))).text)
        assertEquals(ArticleMarkType.Removed to 0..2, mark(ArticleText.Diff(ArticleText.Empty, p("old"))))
        assertEquals(ArticleMarkType.Added to 0..2, mark(ArticleText.Diff(p("new"), ArticleText.Empty)))
        val button = BotButton.Copy("Copy", "x")
        assertEquals(ArticleMarkType.Button(button) to 0..3, mark(ArticleText.Button(p("Copy"), button)))
        assertEquals(ArticleMarkType.Anchor("n") to 0..1, mark(ArticleText.Anchor(p("fn"), "n")))
        val place = run(ArticleText.Concat(listOf(p("ab"), ArticleText.Anchor(ArticleText.Empty, "here")))).marks.single()
        assertEquals(ArticleMark(2, 0, ArticleMarkType.Anchor("here")), place)
        val media = MediaItem("doc1", "a.png", 1, 2, isPhoto = false)
        assertEquals("\uFFFC" to listOf(ArticleMark(0, 1, ArticleMarkType.Image(media, 4, 5))), run(ArticleText.Image(1, 4, 5, media)).let { it.text to it.marks })
        assertEquals("", run(ArticleText.Image(1, 4, 5)).text)
        assertTrue(ArticleText.Empty.isEmpty)
    }

    @Test
    fun orderedListsCountLikeTelegram() {
        val items = List(3) { ArticleListItem(listOf(ArticleBlock.Paragraph(p("$it")))) }
        assertEquals(listOf("1", "2", "3"), List(3) { ArticleBlock.Items(items, ordered = true).number(it) })
        assertEquals(listOf("3", "2", "1"), List(3) { ArticleBlock.Items(items, ordered = true, reversed = true).number(it) })
        assertEquals(listOf("5", "6", "7"), List(3) { ArticleBlock.Items(items, ordered = true, start = 5).number(it) })
        assertEquals(listOf("5", "4", "3"), List(3) { ArticleBlock.Items(items, ordered = true, start = 5, reversed = true).number(it) })
        val own = listOf(items[0].copy(num = "a"), items[1].copy(value = 9), items[2])
        assertEquals(listOf("a", "9", "3"), List(3) { ArticleBlock.Items(own, ordered = true).number(it) })
        assertEquals(null, ArticleBlock.Items(items).number(0))
    }

    @Test
    fun anchorsMediaAndEmojiAreFoundInsideBlocks() {
        val photo = MediaItem("photo1", "p.jpg", 1, 2, isPhoto = true, kind = MediaKind.Photo)
        val video = MediaItem("doc2", "v.mp4", 1, 2, isPhoto = false, kind = MediaKind.Video)
        val article = ArticleMessage(
            listOf(
                ArticleBlock.Paragraph(ArticleText.Concat(listOf(ArticleText.Anchor(p("the note"), "Note%20One"), ArticleText.CustomEmoji(3, "a")))),
                ArticleBlock.Photo(photo),
                ArticleBlock.Details(p("more"), listOf(ArticleBlock.Anchor("Deep"), ArticleBlock.Collage(listOf(ArticleBlock.Video(video), ArticleBlock.Photo(null))))),
                ArticleBlock.Items(listOf(ArticleListItem(listOf(ArticleBlock.Paragraph(ArticleText.Anchor(ArticleText.Empty, "place")))))),
                ArticleBlock.Table(p(""), listOf(listOf(ArticleCell(ArticleText.CustomEmoji(4, "b"))))),
            ),
        )
        assertEquals(
            mapOf("note one" to ArticleAnchor.Footnote(p("the note")), "deep" to ArticleAnchor.Block(2), "place" to ArticleAnchor.Block(3)),
            article.anchors(),
        )
        assertEquals("note one", anchorName("#Note%20One"))
        assertEquals(listOf(photo, video), article.visualMedia())
        assertEquals(setOf(3L, 4L), article.customEmojiIds())
        assertEquals(setOf(3L, 4L), customEmojiIds(listOf(MessageItem(1, "", out = false, date = 0, article = article))))
    }

    @Test
    fun previewsJoinBlocksWithEveryInlineKindAsText() {
        val song = MediaItem("doc1", "song.mp3", 1, 2, isPhoto = false, kind = MediaKind.Music)
        val file = MediaItem("doc2", "big.bin", 1, 2, isPhoto = false, kind = MediaKind.File)
        fun item(text: String, checkbox: Boolean = false, checked: Boolean = false) = ArticleListItem(listOf(ArticleBlock.Paragraph(p(text))), checkbox, checked)
        val inline = ArticleText.Concat(
            listOf(
                p("a "), ArticleText.Mention(p("@b")), p(" "), ArticleText.Hashtag(p("#c")), p(" "),
                ArticleText.Date(p("today"), EntityType.FormattedDate(1, shortDate = true)), p(" "), ArticleText.CustomEmoji(1, "\u2B50"), p(" "),
                ArticleText.Math("x"), p(" "), ArticleText.Diff(ArticleText.Empty, p("old")), p(" "), ArticleText.Styled(ArticleStyle.Subscript, p("2")),
                ArticleText.Button(p("B"), BotButton.Copy("B", "x")), ArticleText.Anchor(p("fn"), "n"), ArticleText.MentionName(p("Al"), 1),
                ArticleText.BankCard(p("4111")), ArticleText.Url(p("u"), "#n"), ArticleText.Cashtag(p("\$T")), ArticleText.BotCommand(p("/s")),
                ArticleText.Email(p("e"), "e@x.co", auto = true), ArticleText.Phone(p("f"), "1"), ArticleText.Image(1, 1, 1),
            ),
        )
        val article = ArticleMessage(
            listOf(
                ArticleBlock.Heading(1, p("Title")),
                ArticleBlock.Paragraph(inline),
                ArticleBlock.Items(listOf(item("one"), item("two", checkbox = true, checked = true)), ordered = true),
                ArticleBlock.Items(listOf(item("dot"), item("box", checkbox = true))),
                ArticleBlock.QuoteBlocks(listOf(ArticleBlock.Paragraph(p("inner")))),
                ArticleBlock.Details(p("Summary"), listOf(ArticleBlock.Paragraph(p("hidden")))),
                ArticleBlock.Table(p(""), emptyList()),
                ArticleBlock.Table(p("Scores"), emptyList()),
                ArticleBlock.Math("E"),
                ArticleBlock.Photo(null),
                ArticleBlock.Video(null),
                ArticleBlock.Collage(emptyList()),
                ArticleBlock.Slideshow(emptyList()),
                ArticleBlock.Audio(song),
                ArticleBlock.Audio(null),
                ArticleBlock.Document(file),
                ArticleBlock.Document(null),
                ArticleBlock.Map(0.0, 0.0, 1, 1, 1, ArticleCaption(p("Berlin"))),
                ArticleBlock.Buttons(listOf(ArticleButton(p("Go"), BotButton.Url("Go", "u")), ArticleButton(p("Stop"), BotButton.Disabled("Stop")))),
                ArticleBlock.Divider,
                ArticleBlock.Anchor("x"),
                ArticleBlock.Unsupported,
                ArticleBlock.Pullquote(p("pulled")),
                ArticleBlock.Footer(p("end")),
            ),
        )
        assertEquals(
            "Title  a @b #c today \u2B50 Formula old 2BfnAl4111u\$T/sef  1. one  \u2705 two  \u2022 dot  \u2B1C box  inner  Summary  Table  Scores  " +
                "Formula  Photo  Video  Collage  Slideshow  \uD83C\uDFB5 song.mp3  \uD83C\uDFB5 Audio  \uD83D\uDCCE big.bin  \uD83D\uDCCE File  Map Berlin  Go Stop  " +
                "Unsupported  pulled  end",
            article.preview({ it.name }),
        )
    }

    @Test
    fun previewsAreCutAndSingleMediaBlocksLookLikeMedia() {
        val long = ArticleMessage(listOf(ArticleBlock.Paragraph(p("a".repeat(2_000)))))
        val cut = long.preview({ it.name })
        assertEquals(ARTICLE_PREVIEW_MAX, cut.length)
        assertTrue(cut.endsWith("a\u2026"))
        assertEquals(ARTICLE_DRAFT_PREVIEW_MAX, long.preview({ it.name }, ARTICLE_DRAFT_PREVIEW_MAX).length)
        val emoji = ArticleMessage(listOf(ArticleBlock.Paragraph(p("\uD83D\uDE00".repeat(800))))).preview({ it.name })
        assertTrue(emoji.dropLast(1).last().isLowSurrogate())

        assertTrue(ArticleMessage(listOf(ArticleBlock.Photo(null))).mediaLike)
        assertTrue(ArticleMessage(listOf(ArticleBlock.Math("x"))).mediaLike)
        assertTrue(ArticleMessage(listOf(ArticleBlock.Table(p("t"), emptyList()))).mediaLike)
        assertTrue(!ArticleMessage(listOf(ArticleBlock.Paragraph(p("t")))).mediaLike)
        assertTrue(!ArticleMessage(listOf(ArticleBlock.Photo(null), ArticleBlock.Paragraph(p("t")))).mediaLike)
    }

    @Test
    fun plainTextJoinsBlocksWithFormulasAsTheirSource() {
        val article = ArticleMessage(
            listOf(
                ArticleBlock.Heading(1, p("Notes")),
                ArticleBlock.Paragraph(ArticleText.Concat(listOf(p("so "), ArticleText.Math("x^2"), p(" "), ArticleText.Styled(ArticleStyle.Bold, p("bold"))))),
                ArticleBlock.Items(
                    listOf(
                        ArticleListItem(listOf(ArticleBlock.Paragraph(p("one")), ArticleBlock.Paragraph(p("more")))),
                        ArticleListItem(listOf(ArticleBlock.Paragraph(p("two"))), checkbox = true, checked = true),
                    ),
                    ordered = true,
                ),
                ArticleBlock.Quote(p("quoted"), author = p("Alice")),
                ArticleBlock.Details(p("More"), listOf(ArticleBlock.Paragraph(p("hidden")))),
                ArticleBlock.Table(p("Scores"), listOf(listOf(ArticleCell(p("a")), ArticleCell(null), ArticleCell(p("b"))))),
                ArticleBlock.Math("E = mc^2"),
                ArticleBlock.Divider,
                ArticleBlock.Photo(null, ArticleCaption(p("A photo"), p("by me"))),
                ArticleBlock.Buttons(listOf(ArticleButton(p("Site"), BotButton.Url("Site", "https://t.me")))),
                ArticleBlock.Unsupported,
            ),
        )
        assertEquals(
            "Notes\nso x^2 bold\n1. one\n   more\n\u2705 two\nquoted\nAlice\nMore\nhidden\nScores\na\t\tb\nE = mc^2\nA photo\nby me",
            article.plainText(),
        )
    }
}
