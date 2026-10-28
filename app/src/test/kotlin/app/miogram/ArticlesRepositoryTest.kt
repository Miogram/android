package app.miogram

import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.ArticleAlign
import cn.ellerotta.core.model.ArticleBlock
import cn.ellerotta.core.model.ArticleButton
import cn.ellerotta.core.model.ArticleCaption
import cn.ellerotta.core.model.ArticleCell
import cn.ellerotta.core.model.ArticleListItem
import cn.ellerotta.core.model.ArticleStyle
import cn.ellerotta.core.model.ArticleText
import cn.ellerotta.core.model.ArticleVerticalAlign
import cn.ellerotta.core.model.BotButton
import cn.ellerotta.core.model.BotButtonStyle
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.flatten
import cn.ellerotta.core.model.visualMedia
import cn.ellerotta.core.model.walk
import cn.ellerotta.core.model.walkText
import cn.ellerotta.ktproto.testing.FakeCluster
import cn.ellerotta.core.model.mediaLike
import cn.ellerotta.core.model.preview
import cn.ellerotta.ktproto.api.types.DraftMessage
import cn.ellerotta.ktproto.api.types.PageBlockMath
import cn.ellerotta.ktproto.api.types.PageBlockParagraph
import cn.ellerotta.ktproto.api.types.RichMessage
import cn.ellerotta.ktproto.api.types.TextPlain
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class ArticlesRepositoryTest {
    @Test
    fun everyBlockAndInlineKindMaps(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("9996624242"), "22222")).profile.id
                repos.dialogs.dialogs()

                val id = fake.articles.addArticle()
                val message = repos.messages.history("self").single { it.id == id }
                assertEquals("", message.text)
                assertNull(message.media)
                assertEquals(emptyList(), message.entities)
                val article = assertNotNull(message.article)
                assertEquals(false to false, article.part to article.rtl)
                assertEquals(expectedBlocks(), article.blocks.map(::stripped))

                val media = buildList { article.blocks.forEach { top -> top.walk(onBlock = { block -> add(mediaOf(block)) }) } }.filterNotNull()
                assertEquals(
                    listOf(
                        "photo${FakeTelegram.PHOTO_ID}" to MediaKind.Photo,
                        "doc${FakeTelegram.VIDEO_ID}" to MediaKind.Video,
                        "photo${FakeTelegram.PHOTO_ID}" to MediaKind.Photo,
                        "doc${FakeTelegram.VIDEO_ID}" to MediaKind.Video,
                        "photo${FakeTelegram.PHOTO_ID}" to MediaKind.Photo,
                        "doc${FakeArticles.SONG_ID}" to MediaKind.Music,
                        "doc${FakeCluster.FILE_ID}" to MediaKind.File,
                    ),
                    media.map { it.key to it.kind },
                )
                assertEquals(5, article.visualMedia().size)
                val image = (article.blocks[1] as ArticleBlock.Paragraph).text.let { text ->
                    buildList { text.walkText { if (it is ArticleText.Image) add(it) } }.single()
                }
                assertEquals("doc${FakeArticles.IMAGE_ID}", image.media?.key)
                assertEquals(FakeArticles.INLINE, (article.blocks[1] as ArticleBlock.Paragraph).text.flatten().text)

                val partId = fake.articles.addPart()
                val part = assertNotNull(repos.messages.history("self").single { it.id == partId }.article)
                assertEquals(true to FakeArticles.PART_BLOCKS, part.part to part.blocks.size)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun expiredMediaAreFetchedAgainWithGetRichMessage(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("9996624242"), "22222")).profile.id
                repos.dialogs.dialogs()

                val id = fake.articles.addArticle()
                fake.expireVideoReference()
                val article = assertNotNull(repos.messages.history("self").single { it.id == id }.article)
                val video = assertNotNull((article.blocks.first { it is ArticleBlock.Video } as ArticleBlock.Video).media)
                val done = repos.media.download(video).filterIsInstance<DownloadState.Done>().first()
                assertContentEquals(fake.cluster.fileBytes, done.file.readBytes())
                assertEquals(listOf(id), fake.articles.richCalls.toList())
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun previewsOfRichMessagesAndRichDrafts(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("9996624242"), "22222")).profile.id

                fake.articles.addSingle(PageBlockMath("x^2"))
                fake.drafts[fake.alice.id] = DraftMessage(
                    message = "",
                    date = FakeTelegram.NOW + 60,
                    richMessage = RichMessage(blocks = listOf(PageBlockParagraph(TextPlain("rich draft"))), photos = emptyList(), documents = emptyList()),
                )
                val dialogs = repos.dialogs.dialogs().dialogs
                val saved = dialogs.first { it.isSaved }.preview
                val article = assertNotNull(saved.article)
                assertEquals(listOf(ArticleBlock.Math("x^2")), article.blocks)
                assertEquals(true to "Formula", article.mediaLike to article.preview({ it.name }))
                assertEquals("" to null, saved.text to saved.media)

                val alice = dialogs.first { it.key == "u${fake.alice.id}" }
                val draft = assertNotNull(alice.draft)
                assertEquals("rich draft", draft.article?.preview({ it.name }))
                assertEquals(true to "rich draft", alice.listPreview.draft to alice.listPreview.article?.preview({ it.name }))
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    private fun mediaOf(block: ArticleBlock) = when (block) {
        is ArticleBlock.Photo -> block.media
        is ArticleBlock.Video -> block.media
        is ArticleBlock.Audio -> block.media
        is ArticleBlock.Document -> block.media
        else -> null
    }

    private fun stripped(block: ArticleBlock): ArticleBlock = when (block) {
        is ArticleBlock.Paragraph -> block.copy(text = stripped(block.text))
        is ArticleBlock.Items -> block.copy(items = block.items.map { item -> item.copy(blocks = item.blocks.map(::stripped)) })
        is ArticleBlock.Photo -> block.copy(media = null)
        is ArticleBlock.Video -> block.copy(media = null)
        is ArticleBlock.Audio -> block.copy(media = null)
        is ArticleBlock.Document -> block.copy(media = null)
        is ArticleBlock.Collage -> block.copy(items = block.items.map(::stripped))
        is ArticleBlock.Slideshow -> block.copy(items = block.items.map(::stripped))
        else -> block
    }

    private fun stripped(text: ArticleText): ArticleText = when (text) {
        is ArticleText.Concat -> ArticleText.Concat(text.parts.map(::stripped))
        is ArticleText.Image -> text.copy(media = null)
        else -> text
    }

    private fun p(text: String) = ArticleText.Plain(text)

    private fun styled(style: ArticleStyle, text: String) = ArticleText.Styled(style, p(text))

    private fun paragraph(text: String) = ArticleBlock.Paragraph(p(text))

    private fun caption(text: String, credit: ArticleText = ArticleText.Empty) = ArticleCaption(p(text), credit)

    private fun expectedInline(): ArticleText {
        val pieces = listOf(
            styled(ArticleStyle.Bold, "bold"),
            styled(ArticleStyle.Italic, "italic"),
            styled(ArticleStyle.Underline, "under"),
            styled(ArticleStyle.Strike, "strike"),
            styled(ArticleStyle.Fixed, "fixed"),
            styled(ArticleStyle.Subscript, "sub"),
            styled(ArticleStyle.Superscript, "sup"),
            styled(ArticleStyle.Marked, "marked"),
            ArticleText.Url(p("link"), "https://core.telegram.org/api/instant-view"),
            ArticleText.Email(p("mail"), "a@b.co"),
            ArticleText.Phone(p("call"), "+1 650 253 0000"),
            ArticleText.Url(p("example.com"), "example.com", auto = true),
            ArticleText.Email(p("c@d.co"), "c@d.co", auto = true),
            ArticleText.Phone(p("+16502530000"), "+16502530000", auto = true),
            ArticleText.Mention(p("@carol_writer")),
            ArticleText.MentionName(p("Alice"), 501),
            ArticleText.Hashtag(p("#kotlin")),
            ArticleText.Cashtag(p("\$TON")),
            ArticleText.BotCommand(p("/start")),
            ArticleText.BankCard(p("4111111111111111")),
            styled(ArticleStyle.Spoiler, "secret"),
            ArticleText.CustomEmoji(FakeTelegram.EMOJI_STAR, "\u2B50"),
            ArticleText.Math("x^2"),
            ArticleText.Date(p("tomorrow"), EntityType.FormattedDate(FakeTelegram.NOW + 86_400, relative = true)),
            ArticleText.Diff(p("new"), p("old")),
            ArticleText.Diff(ArticleText.Empty, p("gone")),
            ArticleText.Diff(p("added"), ArticleText.Empty),
            ArticleText.Button(p("Ping"), BotButton.Callback("Ping", "ping".encodeToByteArray(), BotButtonStyle.Primary)),
            ArticleText.Anchor(p("a footnote"), "Note"),
            ArticleText.Url(p("see note"), "#note"),
        )
        return ArticleText.Concat(
            listOf(p("plain ")) + pieces.flatMap { listOf(it, p(" ")) } +
                listOf(ArticleText.Anchor(ArticleText.Empty, "here"), ArticleText.Image(FakeArticles.IMAGE_ID, 16, 16)),
        )
    }

    private fun expectedBlocks(): List<ArticleBlock> = listOf(
        ArticleBlock.Heading(1, p("Release notes")),
        ArticleBlock.Paragraph(expectedInline()),
        ArticleBlock.Heading(2, p("Heading two")),
        ArticleBlock.Heading(3, p("Heading three")),
        ArticleBlock.Heading(4, p("Heading four")),
        ArticleBlock.Heading(5, p("Heading five")),
        ArticleBlock.Heading(6, p("Heading six")),
        ArticleBlock.Preformatted(p("fun main() {}"), "kotlin"),
        ArticleBlock.Footer(p("footer")),
        ArticleBlock.Thinking(p("thinking")),
        ArticleBlock.Items(
            listOf(
                ArticleListItem(listOf(paragraph("first"))),
                ArticleListItem(listOf(paragraph("todo")), checkbox = true),
                ArticleListItem(listOf(paragraph("done")), checkbox = true, checked = true),
                ArticleListItem(listOf(paragraph("nested"), ArticleBlock.Items(listOf(ArticleListItem(listOf(paragraph("deeper"))))))),
            ),
        ),
        ArticleBlock.Items(
            listOf(
                ArticleListItem(listOf(paragraph("lettered")), num = "a"),
                ArticleListItem(listOf(paragraph("valued")), value = 7),
                ArticleListItem(listOf(paragraph("counted"))),
            ),
            ordered = true,
            start = 3,
            reversed = true,
        ),
        ArticleBlock.Quote(p("quoted"), p("Alice"), collapsed = true),
        ArticleBlock.QuoteBlocks(listOf(paragraph("inside")), p("Bob")),
        ArticleBlock.Pullquote(p("pulled"), p("Carol")),
        ArticleBlock.Details(p("More"), listOf(paragraph("hidden")), open = false),
        ArticleBlock.Table(
            title = p("Scores"),
            rows = listOf(
                listOf(ArticleCell(p("Name"), header = true), ArticleCell(p("Points"), header = true, align = ArticleAlign.Center)),
                listOf(ArticleCell(p("Alice")), ArticleCell(p("3"), align = ArticleAlign.End, valign = ArticleVerticalAlign.Middle)),
                listOf(ArticleCell(p("Bob"), valign = ArticleVerticalAlign.Bottom, colspan = 2)),
                listOf(ArticleCell(p("Tall"), rowspan = 2), ArticleCell(null)),
            ),
            bordered = true,
            striped = true,
            compact = true,
        ),
        ArticleBlock.Math("E = mc^2"),
        ArticleBlock.Divider,
        ArticleBlock.Photo(null, caption("A photo", p("by me")), spoiler = true),
        ArticleBlock.Video(null, caption("A clip"), loop = true),
        ArticleBlock.Collage(listOf(ArticleBlock.Photo(null, caption("")), ArticleBlock.Video(null, caption(""))), caption("Side by side")),
        ArticleBlock.Slideshow(listOf(ArticleBlock.Photo(null, caption("")), ArticleBlock.Photo(null, caption(""))), caption("")),
        ArticleBlock.Audio(null, caption("")),
        ArticleBlock.Document(null, caption("The file")),
        ArticleBlock.Map(52.5, 13.4, 15, 600, 300, caption("Berlin")),
        ArticleBlock.Buttons(
            listOf(
                ArticleButton(p("Site"), BotButton.Url("Site", "https://core.telegram.org")),
                ArticleButton(p("Copy"), BotButton.Copy("Copy", "copied text", BotButtonStyle.Link)),
                ArticleButton(p("Alice"), BotButton.UserProfile("Alice", 501, BotButtonStyle.Success)),
                ArticleButton(p("Search"), BotButton.SwitchInline("Search", null, "cats", samePeer = true)),
                ArticleButton(p("Off"), BotButton.Disabled("Off", BotButtonStyle.Danger)),
            ),
            ArticleAlign.Center,
        ),
        ArticleBlock.Anchor("end"),
        ArticleBlock.Unsupported,
        paragraph("covered"),
    )
}
