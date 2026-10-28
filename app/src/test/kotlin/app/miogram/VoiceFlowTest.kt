package app.miogram

import android.Manifest
import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.data.VoiceClip
import app.miogram.data.VoiceRecorder
import app.miogram.data.reduceWaveform
import app.miogram.di.TelegramModule
import app.miogram.di.VoiceModule
import cn.ellerotta.ktproto.api.types.DocumentAttributeAudio
import cn.ellerotta.ktproto.api.types.InputMediaUploadedDocument
import cn.ellerotta.ktproto.client.decodeWaveform
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import androidx.compose.ui.test.longClick
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import androidx.compose.ui.test.hasTestTag
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(TelegramModule::class, VoiceModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class VoiceFlowTest {
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("poc").toFile()
    private val clip = kotlin.random.Random(3).nextBytes(30_000)
    private val fakeRecorder = FakeRecorder(dir, clip)

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

    @BindValue
    @JvmField
    val recorder: VoiceRecorder = fakeRecorder

    @After
    fun tearDown() {
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    @Test
    fun holdingTheMicrophoneSendsAVoiceMessage() {
        openSavedWithMicrophone()

        compose.onNodeWithTag("mic").performClick()
        assertEquals(0, fakeRecorder.starts)
        assertEquals(0, fakeRecorder.cancels)

        compose.onNodeWithTag("mic").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            up()
        }
        compose.waitUntilIdle { fake.lastSendMedia != null }
        val media = assertIs<InputMediaUploadedDocument>(assertNotNull(fake.lastSendMedia).media)
        assertEquals("audio/ogg", media.mimeType)
        val audio = media.attributes.filterIsInstance<DocumentAttributeAudio>().single()
        assertTrue(audio.voice)
        assertEquals(2, audio.duration)
        val expected = reduceWaveform(fakeRecorder.returned)
        assertEquals(100, expected.size)
        assertEquals(expected, decodeWaveform(assertNotNull(audio.waveform)).toList())
        assertContentEquals(clip, fake.lastUpload)
        val sent = SemanticsMatcher("a sent voice message") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("voice_doc") == true }
        compose.waitUntilIdle { compose.onAllNodes(sent, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("voiceTime", useUnmergedTree = true).assertTextEquals("0:02")
    }

    @Test
    fun aFailedVoiceMessageStaysAndIsSentAgainWithATap() {
        openSavedWithMicrophone()
        fake.failMediaSends = 1
        compose.onNodeWithTag("mic").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            up()
        }
        val failed = "failed_${Int.MAX_VALUE}"
        compose.waitForTag(failed, unmerged = true)
        assertEquals(1, fake.sendMediaCalls)
        assertNull(fake.lastSendMedia)

        compose.onNodeWithTag("message_${Int.MAX_VALUE}").performClick()
        compose.waitUntilIdle { fake.lastSendMedia != null }
        assertEquals(2, fake.sendMediaCalls)
        assertContentEquals(clip, fake.lastUpload)
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag(failed), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aVoiceMessageAnswersTheRepliedMessage() {
        fake.addSavedMessages(1)
        openSavedWithMicrophone()
        compose.waitForTag("message_10")
        compose.onNodeWithTag("message_10").performTouchInput { longClick() }
        compose.waitForTag("actionReply")
        compose.onNodeWithTag("actionReply").performClick()
        compose.waitForTag("composerStrip")

        compose.onNodeWithTag("mic").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            up()
        }
        compose.waitUntilIdle { fake.lastSendMedia != null }
        assertEquals(10, assertIs<InputReplyToMessage>(assertNotNull(fake.lastSendMedia).replyTo).replyToMsgId)
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("composerStrip")).fetchSemanticsNodes().isEmpty() }
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("replyQuote"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openSavedWithMicrophone() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.RECORD_AUDIO)
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
        compose.waitForTag("mic")
    }
}

private class FakeRecorder(private val dir: File, private val bytes: ByteArray) : VoiceRecorder {
    val returned: MutableList<Int> = CopyOnWriteArrayList()
    var cancels = 0
    var starts = 0
    private var recording = false

    override fun start() {
        starts++
        returned.clear()
        recording = true
    }

    override fun maxAmplitude(): Int {
        if (!recording) return 0
        val amplitude = 1_000 * (returned.size + 1)
        returned += amplitude
        return amplitude
    }

    override fun stop(): VoiceClip? {
        if (!recording) return null
        recording = false
        return VoiceClip(File(dir, "clip.ogg").apply { writeBytes(bytes) }, 2_300)
    }

    override fun cancel() {
        recording = false
        cancels++
    }
}
