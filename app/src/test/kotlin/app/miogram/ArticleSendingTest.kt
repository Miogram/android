package app.miogram

import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.ArticleBlock
import cn.ellerotta.core.model.ArticleCell
import cn.ellerotta.core.model.ArticleGate
import cn.ellerotta.core.model.ArticleLimits
import cn.ellerotta.core.model.ArticleListItem
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.ArticlePosting
import cn.ellerotta.core.model.ArticleText
import cn.ellerotta.core.model.visualMedia
import cn.ellerotta.ktproto.api.types.InputPhoto
import cn.ellerotta.ktproto.api.types.InputRichMessage
import cn.ellerotta.ktproto.session.RpcException
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class ArticleSendingTest {
    private fun session(block: suspend (FakeTelegram, TestRepositories) -> Unit): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val repos = TestRepositories(Files.createTempDirectory("poc").toFile(), fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("9996624242"), "22222")).profile.id
                block(fake, repos)
            }
        }
    }

    @Test
    fun gateReadsPostingLimitsAndPremium() = session { fake, repos ->
        assertEquals(ArticleGate(ArticlePosting.Enabled, premium = false, ArticleLimits()), repos.articles.gate())
    }

    @Test
    fun gateOfAPremiumAccountUnderOtherLimits() = session { fake, repos ->
        fake.articles.posting = "premium"
        fake.articles.premium = true
        fake.articles.limits += mapOf("rich_message_max_blocks" to 10, "rich_message_max_table_cols" to 4)
        val gate = repos.articles.gate()
        assertEquals(ArticleGate(ArticlePosting.Premium, premium = true, ArticleLimits(blocks = 10, tableColumns = 4)), gate)
        assertEquals(true, gate.allowed)
    }

    private fun p(text: String) = ArticleText.Plain(text)

    private val plan = ArticleMessage(
        listOf(
            ArticleBlock.Heading(1, p("Plan")),
            ArticleBlock.Items(listOf(ArticleListItem(listOf(ArticleBlock.Paragraph(p("buy milk"))), checkbox = true))),
        ),
    )

    @Test
    fun richMessagesAreSentEditedAndKeptAsDrafts() = session { fake, repos ->
        repos.dialogs.dialogs()
        repos.articles.send("self", plan)
        val sent = assertNotNull(fake.lastSendMessage)
        assertEquals(Triple("", true, true), Triple(sent.message, sent.noWebpage, sent.clearDraft))
        val id = fake.lastSentId
        assertEquals(plan, repos.messages.history("self").single { it.id == id }.article)

        val checked = ArticleMessage(listOf(plan.blocks[0], ArticleBlock.Items(listOf(ArticleListItem(listOf(ArticleBlock.Paragraph(p("buy milk"))), checkbox = true, checked = true)))))
        repos.articles.edit("self", id, checked)
        assertEquals(id, fake.editCalls.single().id)
        assertEquals(checked, repos.messages.history("self").single { it.id == id }.article)

        repos.articles.saveDraft("u501", plan, replyTo = 1)
        val draft = fake.saveDraftCalls.last()
        assertEquals("", draft.message)
        assertIs<InputRichMessage>(draft.richMessage)
        val listed = repos.dialogs.dialogs().dialogs.first { it.key == "u501" }.draft
        assertEquals(plan, listed?.article)
        assertEquals(1, listed?.replyTo)
    }

    @Test
    fun aReceivedArticleGoesOutAgainWithItsMedia() = session { fake, repos ->
        repos.dialogs.dialogs()
        val id = fake.articles.addArticle()
        val original = assertNotNull(repos.messages.history("self").single { it.id == id }.article)
        repos.articles.send("self", original)
        val again = assertNotNull(repos.messages.history("self").single { it.id == fake.lastSentId }.article)
        assertEquals(original.visualMedia().map { it.key }, again.visualMedia().map { it.key })
        assertEquals(original.blocks.size - 1, again.blocks.size)
        val rich = assertIs<InputRichMessage>(fake.lastSendMessage!!.richMessage)
        assertEquals(listOf(FakeTelegram.PHOTO_ID), rich.photos!!.map { (it as InputPhoto).id })
    }

    @Test
    fun theServerRefusesWhatItsLimitsAndPostingDoNotAllow() = session { fake, repos ->
        repos.dialogs.dialogs()
        fake.articles.limits["rich_message_max_blocks"] = 2
        assertEquals("RICH_MESSAGE_BLOCKS_TOO_MANY", assertFailsWith<RpcException> { repos.articles.send("self", plan) }.errorMessage)
        fake.articles.limits.clear()
        fake.articles.limits["rich_message_max_table_cols"] = 1
        val wide = ArticleMessage(listOf(ArticleBlock.Table(p("t"), listOf(listOf(ArticleCell(p("a")), ArticleCell(p("b")))))))
        assertEquals("RICH_MESSAGE_TABLE_COLS_TOO_MANY", assertFailsWith<RpcException> { repos.articles.send("self", wide) }.errorMessage)
        fake.articles.limits.clear()
        fake.articles.posting = "premium"
        assertEquals("PREMIUM_ACCOUNT_REQUIRED", assertFailsWith<RpcException> { repos.articles.send("self", plan) }.errorMessage)
        fake.articles.premium = true
        repos.articles.send("self", plan)
    }
}
