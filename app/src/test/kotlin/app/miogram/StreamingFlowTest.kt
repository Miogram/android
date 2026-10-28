package app.miogram

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import app.miogram.links.LinkRouter
import cn.ellerotta.ktproto.api.types.SendMessageStopDraftAction
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import javax.inject.Inject
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
class StreamingFlowTest {
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

    @Inject
    lateinit var router: LinkRouter

    @After
    fun tearDown() {
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    private fun stopped(randomId: Long) = fake.typingCalls.any { (it.action as? SendMessageStopDraftAction)?.randomId == randomId }

    private fun gone(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()

    @Test
    fun aStreamedReplyGrowsStopsAndMakesWayForTheBotsMessage() {
        hilt.inject()
        compose.logIn()
        compose.runOnUiThread { router.submit("tg://resolve?domain=helperbot") }
        compose.waitForText("Pick an action")
        val bot = fake.helperBot.id

        runBlocking { fake.streams.text(bot, 11, "Let me think", canStop = true) }
        compose.waitForText("Let me think")
        compose.waitForTag("stopDraft")
        assertTrue(compose.onAllNodes(hasText("typing", substring = true)).fetchSemanticsNodes().isEmpty())

        runBlocking { fake.streams.text(bot, 11, "Let me think about it", canStop = true) }
        compose.waitForText("Let me think about it")
        compose.onNodeWithTag("message_-1").assertExists()
        assertTrue(compose.onAllNodes(hasText("Let me think")).fetchSemanticsNodes().isEmpty())

        compose.onNodeWithTag("stopDraft").performClick()
        compose.waitUntilIdle { stopped(11) }
        compose.waitUntilIdle { gone("message_-1") && gone("stopDraft") }

        runBlocking { fake.streams.text(bot, 12, "A partial answer", canStop = true, keepOnStop = true) }
        compose.waitForText("A partial answer")
        compose.waitForTag("stopDraft")
        compose.onNodeWithTag("stopDraft").performClick()
        compose.waitUntilIdle { stopped(12) && gone("stopDraft") }
        compose.onNodeWithTag("message_-2").assertExists()

        val final = runBlocking { fake.streams.botMessage(bot, FakeBots.QUESTION_ID + 1, "A partial answer, done") }
        compose.waitForText("A partial answer, done")
        compose.waitUntilIdle { gone("message_-2") }
        compose.onNodeWithTag("message_${final.id}").assertExists()
        assertTrue(compose.onAllNodes(hasText("A partial answer")).fetchSemanticsNodes().isEmpty())
    }
}
