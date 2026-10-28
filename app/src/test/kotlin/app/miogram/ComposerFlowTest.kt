package app.miogram

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.InputMediaUploadedDocument
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class ComposerFlowTest {
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

    private fun openSaved() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("messageInput")
    }

    private fun file(name: String, size: Int, seed: Int): File = File(dir, name).apply { writeBytes(Random(seed).nextBytes(size)) }

    private fun pickFiles(vararg files: File) {
        compose.onNodeWithTag("attach").performClick()
        compose.onNodeWithTag("attachFile").performClick()
        scenario!!.onActivity { activity ->
            val shadow = shadowOf(activity)
            val request = shadow.nextStartedActivityForResult
            val uris = files.map(Uri::fromFile)
            val result = Intent().apply { clipData = ClipData.newRawUri("files", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } } }
            shadow.receiveResult(request.intent, Activity.RESULT_OK, result)
        }
        compose.waitForTag("attachmentSheet")
    }

    private fun reply(id: Int) {
        compose.waitForTag("message_$id")
        compose.onNodeWithTag("message_$id").performTouchInput { longClick() }
        compose.waitForTag("actionReply")
        compose.onNodeWithTag("actionReply").performClick()
        compose.waitForTag("composerStrip")
    }

    private fun gone(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()

    private val scheduledOrFailed = SemanticsMatcher("a failed mark") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("failed_") == true }

    @Test
    fun filesPickedTogetherGoOutAsOneAlbumWithTheCaptionAnsweringAMessage() {
        fake.addSavedMessages(2)
        openSaved()
        reply(11)

        pickFiles(file("one.bin", 3_000, 1), file("two.bin", 2_000, 2))
        compose.onNodeWithTag("attachmentTitle").assertIsDisplayed()
        compose.onNodeWithTag("attachmentCaption").performTextInput("two parts")
        compose.onNodeWithTag("attachmentSend").performClick()

        compose.waitUntilIdle { fake.sending.albums.isNotEmpty() }
        val album = fake.sending.albums.single()
        assertEquals(2, album.multiMedia.size)
        assertEquals(listOf("two parts", ""), album.multiMedia.map { it.message })
        assertEquals(2, album.multiMedia.map { it.randomId }.toSet().size)
        assertEquals(11, assertIs<InputReplyToMessage>(album.replyTo).replyToMsgId)
        assertFalse(album.silent)
        assertNull(album.scheduleDate)
        compose.waitUntilIdle { gone("attachmentSheet") && gone("composerStrip") }
        compose.waitUntilIdle { compose.onAllNodes(hasText("two parts"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntilIdle { gone("failed_${Int.MAX_VALUE}") }
    }

    @Test
    fun aSingleFileKeepsItsCaptionAndAFileTogglesBetweenFileAndMedia() {
        openSaved()
        pickFiles(file("notes.bin", 3_000, 3))
        compose.onNodeWithTag("attachmentCaption").performTextInput("the notes")
        compose.onNodeWithTag("attachmentSend").performClick()
        compose.waitUntilIdle { fake.lastSendMedia != null }
        val first = assertIs<InputMediaUploadedDocument>(assertNotNull(fake.lastSendMedia).media)
        assertTrue(first.forceFile)
        assertEquals("the notes", fake.lastSendMedia?.message)
        assertNull(fake.lastSendMedia?.replyTo)

        pickFiles(file("more.bin", 2_000, 4))
        compose.onNodeWithTag("attachmentAsFile").performClick()
        compose.onNodeWithTag("attachmentSend").performClick()
        compose.waitUntilIdle { fake.sendMediaCalls == 2 }
        assertFalse(assertIs<InputMediaUploadedDocument>(assertNotNull(fake.lastSendMedia).media).forceFile)
        assertEquals("", fake.lastSendMedia?.message)
    }

    @Test
    fun aFileTakenOutOfTheSheetIsNotSentAndTheLastOneClosesIt() {
        openSaved()
        pickFiles(file("one.bin", 3_000, 5), file("two.bin", 2_000, 6))
        compose.onNodeWithTag("attachmentRemove_0").performClick()
        compose.waitUntilIdle { gone("attachmentTile_0") }
        compose.onNodeWithTag("attachmentSend").performClick()
        compose.waitUntilIdle { fake.lastSendMedia != null }
        assertTrue(fake.sending.albums.isEmpty())
        assertEquals(1, fake.sendMediaCalls)
        assertContentEquals(File(dir, "two.bin").readBytes(), fake.lastUpload)

        pickFiles(file("three.bin", 1_000, 7))
        compose.onNodeWithTag("attachmentRemove_2").performClick()
        compose.waitUntilIdle { gone("attachmentSheet") }
        assertEquals(1, fake.sendMediaCalls)
    }

    @Test
    fun aFailedAlbumStaysMarkedAndAllOfItIsSentAgainWithATap() {
        openSaved()
        fake.sending.failAlbums = 1
        pickFiles(file("one.bin", 3_000, 8), file("two.bin", 2_000, 9))
        compose.onNodeWithTag("attachmentSend").performClick()
        compose.waitUntilIdle { compose.onAllNodes(scheduledOrFailed, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(fake.sending.albums.isEmpty())

        compose.onAllNodes(SemanticsMatcher("an album bubble") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("message_") == true })[0].performClick()
        compose.waitUntilIdle { fake.sending.albums.isNotEmpty() }
        assertEquals(2, fake.sending.albums.single().multiMedia.size)
        compose.waitUntilIdle { compose.onAllNodes(scheduledOrFailed, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aLongPressOnSendSendsWithoutSound() {
        openSaved()
        compose.onNodeWithTag("messageInput").performTextInput("psst")
        compose.onNodeWithTag("send").performTouchInput { longClick() }
        compose.waitForTag("sendSilent")
        assertTrue(gone("sendWhenOnline"))
        compose.onNodeWithTag("sendSilent").performClick()
        compose.waitUntilIdle { fake.lastSendMessage?.message == "psst" }
        assertTrue(assertNotNull(fake.lastSendMessage).silent)
        assertNull(fake.lastSendMessage?.scheduleDate)
        compose.waitUntilIdle { compose.onAllNodes(hasText("psst"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aScheduledMessageWaitsUnderTheBarWhereItCanBeSentNowEditedMovedAndDeleted() {
        openSaved()
        compose.onNodeWithTag("messageInput").performTextInput("remind me")
        compose.onNodeWithTag("send").performTouchInput { longClick() }
        compose.waitForTag("sendSchedule")
        compose.onNodeWithTag("sendSchedule").performClick()
        compose.waitForTag("scheduleDateOk")
        compose.onNodeWithTag("scheduleDateOk").performClick()
        compose.waitForTag("scheduleTimeOk")
        compose.onNodeWithTag("scheduleTimeOk").performClick()

        compose.waitUntilIdle { fake.lastSendMessage?.message == "remind me" }
        val date = assertNotNull(fake.lastSendMessage?.scheduleDate)
        assertTrue(date > System.currentTimeMillis() / 1000 + 3_000)
        compose.waitForTag("scheduledBar")
        compose.waitUntilIdle { compose.onAllNodes(hasText("remind me"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        assertEquals(listOf("remind me"), fake.sending.scheduled.map { it.message })

        compose.onNodeWithTag("scheduledBar").performClick()
        compose.waitForTag("scheduled_10")
        compose.onNodeWithTag("scheduledDate_10", useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithTag("scheduled_10").performClick()
        compose.onNodeWithTag("scheduledEdit").performClick()
        compose.waitForTag("scheduledEditField")
        compose.onNodeWithTag("scheduledEditField").performTextInput(" at six")
        compose.onNodeWithTag("scheduledEditSave").performClick()
        compose.waitUntilIdle { fake.sending.scheduled.single().message == "remind me at six" }
        assertEquals(date, fake.sending.scheduled.single().date)

        compose.onNodeWithTag("scheduled_10").performClick()
        compose.onNodeWithTag("scheduledReschedule").performClick()
        compose.waitForTag("scheduleDateOk")
        compose.onNodeWithTag("scheduleDateOk").performClick()
        compose.waitForTag("scheduleTimeOk")
        compose.onNodeWithTag("scheduleTimeOk").performClick()
        compose.waitUntilIdle { fake.editCalls.any { it.scheduleDate != null } }
        assertEquals(date / 60 * 60, fake.sending.scheduled.single().date)

        compose.onNodeWithTag("scheduled_10").performClick()
        compose.onNodeWithTag("scheduledSendNow").performClick()
        compose.waitUntilIdle { fake.sending.sentNow == listOf(10) }
        compose.waitUntilIdle { gone("scheduled_10") }
        compose.waitForTag("scheduledEmpty")
        compose.onNodeWithTag("back").performClick()
        compose.waitUntilIdle { gone("scheduledBar") }
    }

    @Test
    fun aScheduledMessageCanBeDeletedAfterAsking() {
        openSaved()
        runBlocking { fake.sending.scheduleText("never mind", (System.currentTimeMillis() / 1000).toInt() + 3_600) }
        compose.waitForTag("scheduledBar")
        compose.onNodeWithTag("scheduledBar").performClick()
        compose.waitForTag("scheduled_10")
        compose.onNodeWithTag("scheduled_10").performClick()
        compose.onNodeWithTag("scheduledDelete").performClick()
        compose.waitForTag("confirmDeleteScheduled")
        assertTrue(fake.sending.deleted.isEmpty())
        compose.onNodeWithTag("confirmDeleteScheduled").performClick()
        compose.waitUntilIdle { fake.sending.deleted == listOf(10) }
        compose.waitForTag("scheduledEmpty")
    }

    @Test
    fun theBarFollowsTheScheduledMessageUpdates() {
        val date = (System.currentTimeMillis() / 1000).toInt() + 3_600
        fake.sending.hold("planned elsewhere", date)
        openSaved()
        compose.waitUntilIdle { compose.onAllNodes(hasText("1 scheduled message")).fetchSemanticsNodes().isNotEmpty() }

        runBlocking { fake.sending.scheduleText("planned too", date + 60) }
        compose.waitUntilIdle { compose.onAllNodes(hasText("2 scheduled messages")).fetchSemanticsNodes().isNotEmpty() }

        runBlocking { fake.sending.pushDeleted(fake.sending.scheduled.map { it.id }) }
        compose.waitUntilIdle { gone("scheduledBar") }
    }
}
