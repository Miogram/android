package app.miogram

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.InputRichMessage
import cn.ellerotta.ktproto.api.types.PageBlockHeading1
import cn.ellerotta.ktproto.api.types.PageBlockList
import cn.ellerotta.ktproto.api.types.PageBlockParagraph
import cn.ellerotta.ktproto.api.types.PageListItemText
import cn.ellerotta.ktproto.api.types.RichMessage
import cn.ellerotta.ktproto.api.types.TextPlain
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class ArticleComposeFlowTest {
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

    @After
    fun tearDown() {
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextInput(text)
    }

    private fun addRow() {
        compose.onNodeWithTag("articleAddEnd").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun exists(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun gone(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()

    private fun items(rich: InputRichMessage) = rich.blocks.filterIsInstance<PageBlockList>().single().items.map { it as PageListItemText }

    @Test
    fun richMessagesAreSentEditedAndChecked() {
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("articleOpen")

        compose.onNodeWithTag("messageInput").performTextInput("just words")
        compose.onNodeWithTag("articleOpen").performClick()
        compose.onNodeWithTag("articleSend").performClick()
        compose.waitUntilIdle { fake.lastSendMessage?.message == "just words" }
        assertNull(fake.lastSendMessage!!.richMessage)

        compose.onNodeWithTag("messageInput").performTextInput("intro")
        compose.onNodeWithTag("articleOpen").performClick()
        addRow()
        type("articleText_1", "/h1 Plan")
        addRow()
        type("articleText_2", "[] buy milk")
        compose.onNodeWithTag("articleSend").performClick()
        compose.waitUntilIdle { fake.lastSendMessage?.richMessage != null }
        val sent = fake.lastSendMessage!!
        assertEquals("" to true, sent.message to sent.noWebpage)
        val rich = assertIs<InputRichMessage>(sent.richMessage)
        assertEquals(listOf("intro"), rich.blocks.filterIsInstance<PageBlockParagraph>().map { (it.text as TextPlain).text })
        assertEquals("Plan", (rich.blocks.filterIsInstance<PageBlockHeading1>().single().text as TextPlain).text)
        assertEquals(listOf(true to false), items(rich).map { it.checkbox to it.checked })
        val id = fake.lastSentId
        compose.waitForTag("article_$id", unmerged = true)
        compose.waitUntilIdle { exists("buy milk") }

        compose.onNodeWithTag("checkboxOpen", useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilIdle { fake.editCalls.isNotEmpty() }
        val toggle = fake.editCalls.single()
        assertEquals(id, toggle.id)
        assertEquals(listOf(true to true), items(assertIs<InputRichMessage>(toggle.richMessage)).map { it.checkbox to it.checked })
        compose.waitForTag("checkboxChecked", unmerged = true)

        compose.onNodeWithTag("message_$id").performTouchInput { longClick(Offset(centerX, height - 4f)) }
        compose.onNodeWithTag("actionEdit").performScrollTo().performClick()
        compose.waitForTag("articleEditor")
        type("articleText_0", " and more")
        compose.onNodeWithTag("articleSend").performClick()
        compose.waitUntilIdle { fake.editCalls.size == 2 }
        val edit = assertIs<InputRichMessage>(fake.editCalls.last().richMessage)
        assertEquals("intro and more", ((edit.blocks.first() as PageBlockParagraph).text as TextPlain).text)
        assertEquals(listOf(true to true), items(edit).map { it.checkbox to it.checked })
        compose.waitUntilIdle { exists("intro and more") }
    }

    @Test
    fun richDraftsAreKeptDeletedAndRestored() {
        compose.logIn()
        compose.onNode(hasText("Alice Example")).performClick()
        compose.waitForTag("articleOpen")

        compose.onNodeWithTag("messageInput").performTextInput("draft text")
        compose.onNodeWithTag("articleOpen").performClick()
        addRow()
        type("articleText_1", "/h1 Title")
        compose.onNodeWithTag("articleClose").performClick()
        compose.waitUntilIdle { fake.drafts[501]?.richMessage != null }
        val saved = fake.saveDraftCalls.last { it.richMessage != null }
        assertEquals("", saved.message)
        assertEquals(2, assertIs<InputRichMessage>(saved.richMessage).blocks.size)
        compose.waitForTag("articleDraft", unmerged = true)
        assertTrue(exists("Title"))

        compose.onNodeWithTag("articleDraftDelete").performClick()
        compose.waitUntilIdle { fake.drafts[501] == null }
        assertNull(fake.saveDraftCalls.last().richMessage)
        assertTrue(gone("articleDraft"))

        val elsewhere = RichMessage(blocks = listOf(PageBlockHeading1(TextPlain("From the laptop")), PageBlockParagraph(TextPlain("written there"))), photos = emptyList(), documents = emptyList())
        runBlocking { fake.pushRichDraft(501, elsewhere) }
        compose.waitForTag("articleDraft", unmerged = true)
        compose.waitUntilIdle { exists("From the laptop") }
        compose.onNodeWithTag("articleDraft", useUnmergedTree = true).performClick()
        compose.waitForTag("articleEditor")
        compose.onNodeWithTag("articleSend").performClick()
        compose.waitUntilIdle { fake.lastSendMessage?.richMessage != null }
        assertTrue(fake.lastSendMessage!!.clearDraft)
        assertNull(fake.drafts[501])
    }
}
