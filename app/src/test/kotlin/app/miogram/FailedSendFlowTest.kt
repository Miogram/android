package app.miogram

import android.app.Activity
import android.content.Intent
import android.net.Uri
import java.io.File
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlin.test.assertEquals
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
class FailedSendFlowTest {
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
    fun aFailedSendIsKeptAndSentAgainWithATap() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("messageInput")

        fake.failSends = 1
        compose.onNodeWithTag("messageInput").performTextInput("not yet")
        compose.onNodeWithTag("send").performClick()
        val failed = "failed_${Int.MAX_VALUE}"
        compose.waitForTag(failed, unmerged = true)
        assertEquals(0, fake.failSends)

        compose.onNodeWithTag("message_${Int.MAX_VALUE}").performClick()
        compose.waitUntilIdle { fake.lastSendMessage?.message == "not yet" }
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag(failed), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        compose.waitUntilIdle { compose.onAllNodes(hasText("not yet"), useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
    }

    @Test
    fun aFailedFileStaysAndIsUploadedAgainWithATap() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("messageInput")

        val bytes = Random(5).nextBytes(20_000)
        val file = File(dir, "notes.bin").apply { writeBytes(bytes) }
        fake.failMediaSends = 1
        compose.onNodeWithTag("attach").performClick()
        compose.onNodeWithTag("attachFile").performClick()
        scenario!!.onActivity { activity ->
            val shadow = shadowOf(activity)
            val request = shadow.nextStartedActivityForResult
            shadow.receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        }
        compose.waitForTag("attachmentSend")
        compose.onNodeWithTag("attachmentSend").performClick()
        val failed = "failed_${Int.MAX_VALUE}"
        compose.waitForTag(failed, unmerged = true)
        assertEquals(1, fake.sendMediaCalls)

        compose.onNodeWithTag("message_${Int.MAX_VALUE}").performClick()
        compose.waitUntilIdle { fake.lastSendMedia != null }
        assertEquals(2, fake.sendMediaCalls)
        assertContentEquals(bytes, fake.lastUpload)
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag(failed), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aRetryOfASendThatReachedTelegramIsNotDoubled() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("messageInput")

        fake.loseSendAnswers = 1
        compose.onNodeWithTag("messageInput").performTextInput("only once")
        compose.onNodeWithTag("send").performClick()
        val failed = "failed_${Int.MAX_VALUE}"
        compose.waitForTag(failed, unmerged = true)

        compose.onNodeWithTag("message_${Int.MAX_VALUE}").performClick()
        compose.waitUntilIdle { fake.sendMessageCalls == 2 }
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag(failed), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        assertEquals(1, fake.savedTexts("only once"))
        compose.waitUntilIdle { compose.onAllNodes(hasText("only once"), useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
    }
}
