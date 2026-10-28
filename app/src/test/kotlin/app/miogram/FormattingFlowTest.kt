package app.miogram

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ActivityScenario
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.MessageEntityBold
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
class FormattingFlowTest {
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("poc").toFile()
    private var scenario: ActivityScenario<MainActivity>? = null

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @BindValue
    @JvmField
    val server = ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test")

    @BindValue
    @JvmField
    val telegram = TelegramClientHolder(dir) { id, hash, logger -> fake.config(id, hash, logger) }

    @After
    fun tearDown() {
        scenario?.close()
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    @Test
    fun aSelectionIsSentBold() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForTag("start")
        compose.onNodeWithTag("start").performClick()
        compose.waitForTag("phone")
        compose.onNodeWithTag("phone").performTextClearance()
        compose.onNodeWithTag("phone").performTextInput("6627777")
        compose.onNodeWithTag("sendCode").performClick()
        compose.waitForTag("confirmPhone")
        compose.onNodeWithTag("confirmPhone").performClick()
        compose.waitForTag("code")
        compose.onNodeWithTag("code").performTextInput("22222")
        compose.waitForTag("openChats")
        compose.openSavedMessages(fake.selfId)

        compose.waitForTag("messageInput")
        compose.onNodeWithTag("messageInput").performTextInput("hello bold world")
        compose.onNodeWithTag("messageInput").performTextInputSelection(TextRange(6, 10))
        compose.waitForTag("formatBold")
        compose.onNodeWithTag("formatBold").performClick()
        compose.onNodeWithTag("send").performClick()
        compose.waitUntilIdle { fake.lastSendMessage != null }
        val call = assertNotNull(fake.lastSendMessage)
        assertEquals("hello bold world", call.message)
        val bold = assertIs<MessageEntityBold>(assertNotNull(call.entities).single())
        assertEquals(6 to 4, bold.offset to bold.length)
        compose.waitUntilIdle {
            compose.onAllNodes(hasText("hello bold world"), useUnmergedTree = true).fetchSemanticsNodes().any { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.spanStyles?.any { it.item.fontWeight == FontWeight.Bold && it.start == 6 && it.end == 10 } == true
            }
        }
    }
}
