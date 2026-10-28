package cn.ellerotta.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class ArticleComposeTest {
    private fun p(text: String) = ArticleText.Plain(text)

    private fun bold(text: ArticleText) = ArticleText.Styled(ArticleStyle.Bold, text)

    private fun media(key: String) = MediaItem(key, key, 1, 2, isPhoto = key.startsWith("photo"))

    @Test
    fun appConfigGivesPostingAndLimitsWithDefaults() {
        val defaults = AppConfig()
        assertEquals(ArticlePosting.Premium, defaults.articlePosting)
        assertEquals(ArticleLimits(32_768, 500, 16, 50, 20), defaults.articleLimits)
        val config = AppConfig(
            mapOf(
                "rich_message_posting" to "enabled",
                "rich_message_length_limit" to 100.0,
                "rich_message_max_blocks" to 5.0,
                "rich_message_max_depth" to 3.0,
                "rich_message_max_media" to 2.0,
                "rich_message_max_table_cols" to 4.0,
            ),
        )
        assertEquals(ArticlePosting.Enabled, config.articlePosting)
        assertEquals(ArticleLimits(100, 5, 3, 2, 4), config.articleLimits)
        assertEquals(ArticlePosting.Disabled, AppConfig(mapOf("rich_message_posting" to "disabled")).articlePosting)
        assertEquals(ArticlePosting.Premium, AppConfig(mapOf("rich_message_posting" to "something new")).articlePosting)
    }

    @Test
    fun gateFollowsPostingAndPremium() {
        assertEquals(true to true, ArticleGate(ArticlePosting.Enabled).let { it.available to it.allowed })
        assertEquals(true to false, ArticleGate(ArticlePosting.Premium).let { it.available to it.allowed })
        assertEquals(true to true, ArticleGate(ArticlePosting.Premium, premium = true).let { it.available to it.allowed })
        assertEquals(false to false, ArticleGate(ArticlePosting.Disabled, premium = true).let { it.available to it.allowed })
    }

    @Test
    fun countsFollowTheServersRules() {
        val article = ArticleMessage(
            listOf(
                ArticleBlock.Paragraph(ArticleText.Concat(listOf(p("ab"), bold(ArticleText.Url(p("cde"), "https://t.me"))))),
                ArticleBlock.Items(
                    listOf(
                        ArticleListItem(listOf(ArticleBlock.Paragraph(p("x")))),
                        ArticleListItem(listOf(ArticleBlock.Paragraph(p("y")), ArticleBlock.Items(listOf(ArticleListItem(listOf(ArticleBlock.Paragraph(p("z")))))))),
                    ),
                ),
                ArticleBlock.Table(p("T"), listOf(listOf(ArticleCell(p("a"), colspan = 2), ArticleCell(p("b"))), listOf(ArticleCell(null)))),
                ArticleBlock.Math("x^2"),
                ArticleBlock.Photo(media("photo1"), ArticleCaption(p("cap"), p("me"))),
                ArticleBlock.Collage(listOf(ArticleBlock.Photo(media("photo1")), ArticleBlock.Video(media("doc2")))),
                ArticleBlock.Paragraph(ArticleText.Concat(listOf(ArticleText.CustomEmoji(5, "\u2B50"), ArticleText.Image(9, 1, 1)))),
                ArticleBlock.Divider,
            ),
        )
        val counts = article.counts()
        assertEquals(5 + 1 + 1 + 1 + 3 + 3 + 5 + 1, counts.length)
        assertEquals(1 + (1 + 2 + 1 + 1 + 1) + (1 + 2) + 1 + 1 + (1 + 2) + 1 + 1, counts.blocks)
        assertEquals(3, counts.depth)
        assertEquals(4, counts.media)
        assertEquals(3, counts.tableColumns)
        assertEquals(emptySet(), counts.over(ArticleLimits()))
        assertEquals(
            setOf(ArticleLimit.Length, ArticleLimit.Blocks, ArticleLimit.Depth, ArticleLimit.Media, ArticleLimit.TableColumns),
            counts.over(ArticleLimits(length = 19, blocks = 16, depth = 2, media = 3, tableColumns = 2)),
        )
        assertEquals(emptySet(), counts.over(ArticleLimits(length = 20, blocks = 17, depth = 3, media = 4, tableColumns = 3)))
    }

    @Test
    fun inlineWrappersCountTowardsTheDepth() {
        val deep = ArticleMessage(listOf(ArticleBlock.Paragraph(bold(ArticleText.Styled(ArticleStyle.Italic, ArticleText.Url(p("a"), "https://t.me"))))))
        assertEquals(4, deep.counts().depth)
        val flat = ArticleMessage(listOf(ArticleBlock.Paragraph(ArticleText.Concat(listOf(p("a"), ArticleText.Concat(listOf(p("b"))))))))
        assertEquals(1, flat.counts().depth)
    }

    @Test
    fun paragraphsOfEntitiesGoOutAsText() {
        val article = ArticleMessage(
            listOf(
                ArticleBlock.Paragraph(ArticleText.Concat(listOf(p("hi "), bold(p("there"))))),
                ArticleBlock.Paragraph(ArticleText.Concat(listOf(ArticleText.Url(p("site"), "https://t.me"), p(" "), ArticleText.CustomEmoji(5, "\u2B50")))),
            ),
        )
        val text = article.asFormattedText()
        assertEquals("hi there\n\nsite \u2B50", text?.text)
        assertEquals(
            listOf(TextEntity(3, 5, EntityType.Bold), TextEntity(10, 4, EntityType.TextUrl("https://t.me")), TextEntity(15, 1, EntityType.CustomEmoji(5))),
            text?.entities,
        )
        assertNull(ArticleMessage(listOf(ArticleBlock.Heading(1, p("title")))).asFormattedText())
        assertNull(ArticleMessage(listOf(ArticleBlock.Paragraph(ArticleText.Styled(ArticleStyle.Marked, p("x"))))).asFormattedText())
        assertNull(ArticleMessage(listOf(ArticleBlock.Paragraph(ArticleText.Math("x")))).asFormattedText())
        assertNull(ArticleMessage(listOf(ArticleBlock.Divider)).asFormattedText())
    }

    @Test
    fun entitiesBecomeNestedRichText() {
        val text = FormattedText(
            "bold italic link plain",
            listOf(
                TextEntity(0, 11, EntityType.Bold),
                TextEntity(5, 11, EntityType.Italic),
                TextEntity(12, 4, EntityType.TextUrl("https://t.me")),
                TextEntity(17, 5, EntityType.Hashtag),
            ),
        )
        val rich = text.toArticleText()
        assertEquals(
            ArticleText.Concat(
                listOf(
                    bold(ArticleText.Concat(listOf(p("bold "), ArticleText.Styled(ArticleStyle.Italic, p("italic"))))),
                    ArticleText.Styled(ArticleStyle.Italic, ArticleText.Concat(listOf(p(" "), ArticleText.Url(p("link"), "https://t.me")))),
                    p(" plain"),
                ),
            ),
            rich,
        )
        assertEquals("bold italic link plain", rich.flatten().text)
        assertEquals(p("plain"), FormattedText("plain").toArticleText())
        assertEquals(ArticleText.Empty, FormattedText("").toArticleText())
    }

    @Test
    fun rowsBecomeBlocksAndBack() {
        val rows = listOf(
            ArticleRow(ArticleRowKind.Heading1, FormattedText("Title")),
            ArticleRow(ArticleRowKind.Paragraph, FormattedText("some bold", listOf(TextEntity(5, 4, EntityType.Bold)))),
            ArticleRow(ArticleRowKind.Paragraph, FormattedText("  ")),
            ArticleRow(ArticleRowKind.Check, FormattedText("todo")),
            ArticleRow(ArticleRowKind.Check, FormattedText("done"), checked = true),
            ArticleRow(ArticleRowKind.Bullet, FormattedText("a")),
            ArticleRow(ArticleRowKind.Numbered, FormattedText("one")),
            ArticleRow(ArticleRowKind.Numbered, FormattedText("two")),
            ArticleRow(ArticleRowKind.Quote, FormattedText("said")),
            ArticleRow(ArticleRowKind.Code, FormattedText("val x = 1\n"), language = "kotlin"),
            ArticleRow(ArticleRowKind.Divider),
            ArticleRow(ArticleRowKind.Math, FormattedText("E = mc^2")),
        )
        val article = articleOfRows(rows)
        assertEquals(
            listOf(
                ArticleBlock.Heading(1, p("Title")),
                ArticleBlock.Paragraph(ArticleText.Concat(listOf(p("some "), bold(p("bold"))))),
                ArticleBlock.Items(listOf(ArticleListItem(listOf(ArticleBlock.Paragraph(p("todo"))), checkbox = true), ArticleListItem(listOf(ArticleBlock.Paragraph(p("done"))), checkbox = true, checked = true))),
                ArticleBlock.Items(listOf(ArticleListItem(listOf(ArticleBlock.Paragraph(p("a")))))),
                ArticleBlock.Items(listOf(ArticleListItem(listOf(ArticleBlock.Paragraph(p("one")))), ArticleListItem(listOf(ArticleBlock.Paragraph(p("two"))))), ordered = true),
                ArticleBlock.Quote(p("said")),
                ArticleBlock.Preformatted(p("val x = 1"), "kotlin"),
                ArticleBlock.Divider,
                ArticleBlock.Math("E = mc^2"),
            ),
            article.blocks,
        )
        val back = article.toRows()
        assertEquals(rows.filter { it.text.text.isNotBlank() || it.kind == ArticleRowKind.Divider }.map { it.copy(text = it.text.trimmed(), id = 0) }.map { it.kind to it.text.text }, back.map { it.kind to it.text.text })
        assertEquals(back.indices.toList(), back.map { it.id })
        assertEquals(article, articleOfRows(back))
        val map = ArticleBlock.Map(52.5, 13.4, 15, 600, 300)
        assertEquals(listOf(ArticleRow(ArticleRowKind.Block, block = map)), ArticleMessage(listOf(map)).toRows())
        assertEquals(ArticleMessage(listOf(map)), articleOfRows(listOf(ArticleRow(ArticleRowKind.Block, block = map))))
    }

    @Test
    fun mediaRowsBecomeMediaBlocks() {
        val caption = FormattedText("a bold cat", listOf(TextEntity(2, 4, EntityType.Bold)))
        val uploading = ArticleUpload(1, ArticleMediaKind.Photo, "cat.jpg", uploaded = 10, total = 100)
        val rows = listOf(
            ArticleRow(ArticleRowKind.Media, caption, upload = uploading, id = 0),
            ArticleRow(ArticleRowKind.Media, upload = ArticleUpload(2, ArticleMediaKind.Video, "clip.mp4"), id = 1),
            ArticleRow(ArticleRowKind.Media, upload = ArticleUpload(3, ArticleMediaKind.Audio, "song.mp3"), id = 2),
            ArticleRow(ArticleRowKind.Media, upload = ArticleUpload(4, ArticleMediaKind.File, "report.pdf"), id = 3),
        )
        val pending = articleOfRows(rows)
        assertEquals(
            listOf(
                ArticleBlock.Photo(null, ArticleCaption(ArticleText.Concat(listOf(p("a "), bold(p("bold")), p(" cat"))))),
                ArticleBlock.Video(null),
                ArticleBlock.Audio(null),
                ArticleBlock.Document(null),
            ),
            pending.blocks,
        )
        assertEquals(4, pending.counts().media)
        assertTrue(uploadsPending(rows, emptyMap()))
        val uploads = rows.map { it.upload!! }.associate { it.id to it.copy(media = media("doc${it.id}")) }
        assertEquals(false, uploadsPending(rows, uploads))
        assertEquals(listOf("doc1", "doc2", "doc3", "doc4"), articleOfRows(rows, uploads).blocks.map { (it as? ArticleBlock.Photo)?.media?.key ?: (it as? ArticleBlock.Video)?.media?.key ?: (it as? ArticleBlock.Audio)?.media?.key ?: (it as ArticleBlock.Document).media?.key })
        assertTrue(uploadsPending(rows, uploads + (1L to uploading.copy(failed = true))))
        val photo = ArticleBlock.Photo(media("photo1"), ArticleCaption(p("old"), p("by me")), spoiler = true)
        val row = ArticleMessage(listOf(photo)).toRows().single()
        assertEquals(ArticleRow(ArticleRowKind.Media, FormattedText("old"), block = photo), row)
        assertEquals(listOf(photo.copy(caption = ArticleCaption(p("new"), p("by me")))), articleOfRows(listOf(row.copy(text = FormattedText("new")))).blocks)
        val missing = ArticleBlock.Video(null)
        assertEquals(ArticleRowKind.Block, ArticleMessage(listOf(missing)).toRows().single().kind)
    }

    @Test
    fun composerTextBecomesRows() {
        val text = FormattedText(
            "first line\nsame paragraph\n\nsecond bold\ncode here\nquoted",
            listOf(TextEntity(34, 4, EntityType.Bold), TextEntity(39, 9, EntityType.Pre("kotlin")), TextEntity(49, 6, EntityType.Blockquote())),
        )
        val rows = text.toRows()
        assertEquals(
            listOf(
                ArticleRowKind.Paragraph to "first line\nsame paragraph",
                ArticleRowKind.Paragraph to "second bold",
                ArticleRowKind.Code to "code here",
                ArticleRowKind.Quote to "quoted",
            ),
            rows.map { it.kind to it.text.text },
        )
        assertEquals(listOf(TextEntity(7, 4, EntityType.Bold)), rows[1].text.entities)
        assertEquals("kotlin", rows[2].language)
        assertEquals(listOf(ArticleRow(ArticleRowKind.Paragraph)), FormattedText("").toRows())
    }

    @Test
    fun tableRowsBecomeTablesAndBack() {
        val styled = FormattedText("Name bold", listOf(TextEntity(5, 4, EntityType.Bold)))
        val row = newTableRow(3, styled).let { it.copy(cells = it.cells.mapIndexed { line, cells -> if (line == 1) listOf(FormattedText(" Alice "), FormattedText("")) else cells }) }
        val table = articleOfRows(listOf(row)).blocks.single() as ArticleBlock.Table
        assertEquals(
            listOf(
                listOf(ArticleCell(ArticleText.Concat(listOf(p("Name "), bold(p("bold")))), header = true), ArticleCell(null, header = true)),
                listOf(ArticleCell(p("Alice")), ArticleCell(null)),
            ),
            table.rows,
        )
        assertEquals(true to false, table.bordered to table.striped)
        assertEquals(ArticleCounts(length = 14, blocks = 3, depth = 3, tableColumns = 2), ArticleMessage(listOf(table)).counts())
        val plain = articleOfRows(listOf(row.copy(header = false, block = table.copy(bordered = false, striped = true)))).blocks.single() as ArticleBlock.Table
        assertEquals(listOf(false, false), plain.rows.map { it[0].header })
        assertEquals(false to true, plain.bordered to plain.striped)
        assertEquals(emptyList(), articleOfRows(listOf(newTableRow(0))).blocks)
        val back = ArticleMessage(listOf(table)).toRows().single()
        assertEquals(ArticleRowKind.Table, back.kind)
        assertEquals(listOf(listOf(styled, FormattedText("")), listOf(FormattedText("Alice"), FormattedText(""))), back.cells)
        assertEquals(true, back.header)
        assertEquals(table, articleOfRows(listOf(back)).blocks.single())
        val spanned = table.copy(rows = listOf(listOf(ArticleCell(p("wide"), colspan = 2))))
        val aligned = table.copy(rows = listOf(listOf(ArticleCell(p("right"), align = ArticleAlign.End))))
        val side = table.copy(rows = listOf(listOf(ArticleCell(p("a")), ArticleCell(p("b"), header = true))))
        val math = table.copy(rows = listOf(listOf(ArticleCell(ArticleText.Math("x")))))
        listOf(spanned, aligned, side, math).forEach { kept ->
            assertEquals(listOf(ArticleRow(ArticleRowKind.Block, block = kept)), ArticleMessage(listOf(kept)).toRows())
        }
    }

    @Test
    fun commandsTurnRows() {
        assertEquals(ArticleCommand(ArticleRowKind.Heading1, 4), articleCommandOf("/h1 Title"))
        assertEquals(ArticleCommand(ArticleRowKind.Bullet, 6), articleCommandOf("/list "))
        assertEquals(ArticleCommand(ArticleRowKind.Quote, 7), articleCommandOf("/quote "))
        assertEquals(ArticleCommand(ArticleRowKind.Code, 6), articleCommandOf("/code "))
        assertEquals(ArticleCommand(ArticleRowKind.Math, 6), articleCommandOf("/math "))
        assertEquals(ArticleCommand(ArticleRowKind.Table, 7), articleCommandOf("/table "))
        assertEquals(ArticleCommand(ArticleRowKind.Media, 7, ArticleMediaKind.Photo), articleCommandOf("/image "))
        assertEquals(ArticleCommand(ArticleRowKind.Media, 7, ArticleMediaKind.Video), articleCommandOf("/video "))
        assertEquals(ArticleCommand(ArticleRowKind.Media, 7, ArticleMediaKind.Audio), articleCommandOf("/audio "))
        assertEquals(ArticleCommand(ArticleRowKind.Media, 6, ArticleMediaKind.File), articleCommandOf("/file "))
        assertEquals(ArticleCommand(ArticleRowKind.Divider, 3), articleCommandOf("---"))
        assertEquals(ArticleCommand(ArticleRowKind.Check, 3), articleCommandOf("[] "))
        assertNull(articleCommandOf("/h1"))
        assertNull(articleCommandOf("/unknown x"))
        assertNull(articleCommandOf("plain text"))
    }

    @Test
    fun aChecklistItemToggles() {
        val todo = ArticleListItem(listOf(ArticleBlock.Paragraph(p("todo"))), checkbox = true)
        val same = ArticleListItem(listOf(ArticleBlock.Paragraph(p("todo"))), checkbox = true)
        val article = ArticleMessage(listOf(ArticleBlock.Items(listOf(todo, same))))
        val toggled = article.toggled(todo)
        val items = (toggled.blocks.single() as ArticleBlock.Items).items
        assertEquals(listOf(true, false), items.map { it.checked })
        assertTrue(toggled.toggled((toggled.blocks.single() as ArticleBlock.Items).items[0]) == article)
    }
}
