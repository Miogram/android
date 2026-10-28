package app.miogram

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.core.ui.R
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlin.test.assertEquals
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
class ArticlesFlowTest {
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

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun exists(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aRichMessageShowsItsBlocksInsteadOfItsText() {
        val id = fake.articles.addArticle()
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("article_$id", unmerged = true)

        compose.onNodeWithTag("heading1", useUnmergedTree = true).assertExists()
        assertTrue(exists("Release notes"))
        assertTrue(exists("plain bold italic under strike fixed sub sup marked link"))
        listOf("Heading six", "fun main() {}", "footer", "thinking", "lettered", "quoted", "inside", "pulled", "More", "E = mc^2").forEach { assertTrue(exists(it), it) }
        compose.onNodeWithTag("articleUnsupported", useUnmergedTree = true).assertExists()
        assertTrue(!exists("plain fallback"))
    }

    @Test
    fun richMediaOpenTheViewerAndButtonsTakeTheKeyboardPath() {
        val id = fake.articles.addArticle()
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("article_$id", unmerged = true)

        tap("inlineButton_Ping")
        compose.waitUntilIdle { fake.bots.callbackCalls.any { it.msgId == id } }
        assertEquals("ping", fake.bots.callbackCalls.last { it.msgId == id }.data?.decodeToString())

        tap("articleButton_Site")
        compose.waitForTag("openLinkDialog")
        compose.onNodeWithText(compose.activity.getString(R.string.common_cancel)).performClick()
        compose.waitUntilIdle { compose.onAllNodesWithTag("openLinkDialog").fetchSemanticsNodes().isEmpty() }

        tap("articleMedia_0")
        compose.waitUntilIdle { compose.onAllNodesWithTag("mediaSpoiler", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        tap("articleMedia_0")
        compose.waitForTag("viewer")
        compose.waitForText("1 of 5")
        assertTrue(id in fake.articles.richCalls)
    }

    @Test
    fun aPartShowsTheRestWithGetRichMessage() {
        val id = fake.articles.addPart()
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("showMore_$id", unmerged = true)
        assertTrue(exists("Heading two"))
        assertTrue(!exists("Heading four"))

        fake.articles.richDelay = 10_000
        tap("showMore_$id")
        compose.waitForTag("showMoreProgress", unmerged = true)
        compose.waitUntilIdle { fake.articles.richCalls.size == 1 }
        tap("showMore_$id")
        compose.waitUntilIdle { compose.onAllNodesWithTag("showMoreProgress", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        assertEquals(listOf(id), fake.articles.richCalls.toList())
        assertTrue(!exists("Heading four"))

        fake.articles.richDelay = 0
        tap("showMore_$id")
        compose.waitUntilIdle { exists("Heading four") }
        compose.waitUntilIdle { compose.onAllNodesWithTag("showMore_$id", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        assertEquals(listOf(id, id), fake.articles.richCalls.toList())
        compose.onNodeWithTag("articleUnsupported", useUnmergedTree = true).assertExists()
    }
}
