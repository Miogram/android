package app.miogram

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.client.UPLOAD_PART_SIZE
import cn.ellerotta.ktproto.testing.FakeAnswer
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class OutgoingQueueFlowTest {
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
    fun leavingTheChatMidUploadStillDelivers() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("messageInput")

        val bytes = Random(9).nextBytes(UPLOAD_PART_SIZE * 2 + 1_000)
        val file = File(dir, "report.bin").apply { writeBytes(bytes) }
        fake.cluster.uploadPartErrors[1] = ConcurrentLinkedQueue(listOf(FakeAnswer.Error(420, "FLOOD_PREMIUM_WAIT_6")))
        compose.onNodeWithTag("attach").performClick()
        compose.onNodeWithTag("attachFile").performClick()
        scenario!!.onActivity { activity ->
            val shadow = shadowOf(activity)
            val request = shadow.nextStartedActivityForResult
            shadow.receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        }
        compose.waitForTag("attachmentSend")
        compose.onNodeWithTag("attachmentSend").performClick()
        compose.waitForTag("message_${Int.MAX_VALUE}")

        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForTag("sending_u${fake.selfId}", unmerged = true)
        assertEquals(0, fake.sendMediaCalls)

        compose.waitUntilIdle { fake.lastSendMedia != null }
        assertContentEquals(bytes, fake.lastUpload)
        assertEquals(1, fake.sendMediaCalls)
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("sending_u${fake.selfId}"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }
}
