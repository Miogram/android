package app.miogram

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.DraftMessage
import cn.ellerotta.ktproto.api.types.MessageEntityBold
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlin.test.assertEquals
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
class DraftsFlowTest {
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

    private fun previewShows(text: String) = compose.waitUntilIdle {
        compose.onAllNodes(hasTestTag("preview_u501") and hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun fieldText(text: String) = SemanticsMatcher("field text $text") { it.config.getOrNull(SemanticsProperties.EditableText)?.text == text }

    @Test
    fun aDraftIsRestoredOnOpenAndSavedOnLeaving() {
        fake.drafts[501] = DraftMessage(message = "see you soon", entities = listOf(MessageEntityBold(8, 4)), date = FakeTelegram.NOW)
        compose.logIn()
        previewShows("see you soon")
        compose.onNode(hasText("Alice Example")).performClick()

        compose.waitUntilIdle { compose.onAllNodes(fieldText("see you soon")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag("messageInput").performTextClearance()
        compose.onNodeWithTag("messageInput").performTextInput("new plan")
        compose.onNodeWithTag("back").performClick()
        compose.waitUntilIdle { fake.drafts[501]?.message == "new plan" }
        assertNull(fake.saveDraftCalls.last().entities)
        previewShows("new plan")

        compose.onNode(hasText("Alice Example")).performClick()
        compose.waitUntilIdle { compose.onAllNodes(fieldText("new plan")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("send").performClick()
        compose.waitUntilIdle { fake.lastSendMessage != null }
        assertTrue(fake.lastSendMessage!!.clearDraft)
        assertNull(fake.drafts[501])
    }

    @Test
    fun draftsFromAnotherDeviceShowLive() {
        compose.logIn()

        runBlocking { fake.pushDraft(501, "from the laptop") }
        previewShows("from the laptop")

        compose.onNode(hasText("Alice Example")).performClick()
        compose.waitUntilIdle { compose.onAllNodes(fieldText("from the laptop")).fetchSemanticsNodes().isNotEmpty() }
        runBlocking { fake.pushDraft(501, "changed on the laptop") }
        compose.waitUntilIdle { compose.onAllNodes(fieldText("changed on the laptop")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag("messageInput").performTextInput("!")
        runBlocking { fake.pushDraft(501, "third version") }
        val settled = System.currentTimeMillis() + 2_000
        compose.waitUntilIdle { System.currentTimeMillis() > settled }
        assertEquals(1, compose.onAllNodes(fieldText("changed on the laptop!")).fetchSemanticsNodes().size)
    }
}
