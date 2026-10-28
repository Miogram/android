package app.miogram

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performTextReplacement
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.Username
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.io.File
import java.nio.file.Files
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class ProfileEditFlowTest {
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

    private fun pause() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))

    private fun scrollTo(tag: String) = compose.onNodeWithTag("editProfile").performScrollToNode(hasTestTag(tag))

    private fun type(tag: String, text: String) {
        scrollTo(tag)
        compose.onNodeWithTag(tag).performTextReplacement(text)
    }

    private fun openEditor() {
        compose.logIn()
        compose.waitForTag("openSettings")
        compose.onNodeWithTag("openSettings").performClick()
        compose.waitForTag("editOwnProfile")
        compose.onNodeWithTag("editOwnProfile").performClick()
        compose.waitForTag("editFirstName")
    }

    @Test
    fun nameBioAndUsernameAreSavedFromTheSettingsCard() {
        openEditor()
        compose.onNodeWithTag("editSave").assertIsNotEnabled()
        type("editFirstName", "Ann")
        type("editAbout", "likes tea")
        compose.onNodeWithTag("editSave").assertIsEnabled()

        type("editUsername", "tester_n")
        type("editUsername", "tester_new")
        compose.onNodeWithTag("editUsernameVerdict", useUnmergedTree = true).assertTextEquals("Checking…")
        compose.onNodeWithTag("editSave").assertIsNotEnabled()
        pause()
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("editUsernameVerdict", useUnmergedTree = true).assertTextEquals("@tester_new is available") }.isSuccess }
        assertEquals(listOf("tester_new"), fake.profileEdit.checks)
        compose.onNodeWithTag("editSave").assertIsEnabled()

        type("editUsername", "taken_name")
        pause()
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("editUsernameVerdict", useUnmergedTree = true).assertTextEquals("This username is already taken") }.isSuccess }
        compose.onNodeWithTag("editSave").assertIsNotEnabled()

        type("editUsername", "abc")
        compose.onNodeWithTag("editUsernameVerdict", useUnmergedTree = true).assertTextEquals("A username has at least 5 characters")
        assertEquals(listOf("tester_new", "taken_name"), fake.profileEdit.checks)

        type("editUsername", "tester_new")
        pause()
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("editSave").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("editSave").performClick()

        compose.waitUntilIdle { fake.profileEdit.usernameUpdates.isNotEmpty() }
        compose.waitForTag("openOwnProfile")
        compose.waitForText("Ann User")
        compose.waitUntilIdle { compose.onAllNodes(hasText("@tester_new", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        val call = fake.profileEdit.updates.single()
        assertEquals(Triple<String?, String?, String?>("Ann", "User", "likes tea"), Triple(call.firstName, call.lastName, call.about))
        assertEquals(listOf("tester_new"), fake.profileEdit.usernameUpdates)
        assertEquals(0, compose.onAllNodesWithTag("editProfile").fetchSemanticsNodes().size)
    }

    @Test
    fun collectibleUsernamesBirthdayAndChannelFromTheOwnProfile() {
        fake.profileEdit.collectibles = listOf(Username(active = true, username = "frag_one"), Username(active = true, username = "frag_two"), Username(active = false, username = "frag_off"))
        fake.profileEdit.admined = listOf(fake.news(), FakeProfileEdit.myNotes())
        compose.logIn()
        compose.waitForTag("openSettings")
        compose.onNodeWithTag("openSettings").performClick()
        compose.waitForTag("openOwnProfile")
        compose.onNodeWithTag("openOwnProfile").performClick()
        compose.waitForTag("profileEdit")
        compose.onNodeWithTag("profileEdit").performClick()
        compose.waitForTag("editFirstName")

        compose.onNodeWithTag("editProfile").performScrollToNode(hasTestTag("editUsernames"))
        compose.onNodeWithTag("usernameSwitch_frag_off").performClick()
        compose.waitUntilIdle { fake.profileEdit.toggles.isNotEmpty() }
        compose.onNodeWithTag("usernameDown_frag_one").performClick()
        compose.waitUntilIdle { fake.profileEdit.reorders.isNotEmpty() }
        assertEquals(listOf("frag_off" to true), fake.profileEdit.toggles.map { it.username to it.active })
        assertEquals(listOf(listOf("tester", "frag_two", "frag_one", "frag_off")), fake.profileEdit.reorders)
        compose.onNodeWithTag("editSave").assertIsNotEnabled()

        compose.onNodeWithTag("editProfile").performScrollToNode(hasTestTag("editBirthday"))
        compose.onNodeWithTag("editBirthdayRemove").performClick()
        compose.onNodeWithTag("editProfile").performScrollToNode(hasTestTag("editChannel"))
        compose.onNodeWithTag("editChannel").performClick()
        compose.waitForTag("channel_ch${FakeTelegram.NEWS_ID}")
        compose.onNodeWithTag("channel_ch${FakeTelegram.NEWS_ID}").performClick()
        compose.onNodeWithTag("editSave").assertIsEnabled()
        compose.onNodeWithTag("editSave").performClick()
        compose.waitUntilIdle { fake.profileEdit.channelUpdates.isNotEmpty() }
        assertNull(fake.profileEdit.birthdayUpdates.single())
        assertEquals(listOf<Long?>(FakeTelegram.NEWS_ID), fake.profileEdit.channelUpdates)
        compose.waitForTag("peerProfile")
        compose.waitForText("Kotlin News")
    }

    @Test
    fun leavingWithUnsavedChangesAsksFirst() {
        openEditor()
        compose.onNodeWithTag("editFirstName").performTextReplacement("Anna")
        compose.onNodeWithTag("back").performClick()
        compose.waitForText("Discard your changes?")
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("editSave").assertIsEnabled()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("confirmDiscard").performClick()
        compose.waitForTag("openOwnProfile")
        assertEquals(emptyList(), fake.profileEdit.updates)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aNewPhotoIsCroppedAndUploadedAndTheOldOnesAreManaged() {
        fake.profileEdit.seedPhotos(1)
        openEditor()
        compose.onNodeWithTag("editPhoto").performClick()
        compose.onNodeWithTag("editPhotoView").performClick()
        compose.waitForTag("viewerMenu")

        compose.onNodeWithTag("viewerMenu").performClick()
        compose.onNodeWithTag("viewerAddGallery").performClick()
        val bitmap = Bitmap.createBitmap(3000, 1500, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff4f9de0.toInt()) }
        val file = File(dir, "wide.jpg").apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        val shadow = shadowOf(compose.activity)
        shadow.receiveResult(shadow.nextStartedActivityForResult.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))

        compose.waitForTag("cropArea")
        compose.waitForTag("cropApply")
        compose.onNodeWithTag("cropApply").performScrollTo().performClick()
        compose.waitUntilIdle { fake.profileEdit.uploads.isNotEmpty() }
        val sent = BitmapFactory.decodeByteArray(fake.profileEdit.uploads.single(), 0, fake.profileEdit.uploads.single().size)
        assertEquals(1280 to 1280, sent.width to sent.height)

        compose.waitForTag("viewerMenu")
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("viewerPosition", useUnmergedTree = true).assertTextEquals("1 of 2") }.isSuccess }
        assertEquals(listOf(FakeProfileEdit.PHOTO_ID + 1, FakeProfileEdit.PHOTO_ID), fake.profileEdit.photos().map { (it as cn.ellerotta.ktproto.api.types.Photo).id })

        compose.onNodeWithTag("viewer").performTouchInput { swipeLeft() }
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("viewerPosition", useUnmergedTree = true).assertTextEquals("2 of 2") }.isSuccess }
        compose.onNodeWithTag("viewerMenu").performClick()
        compose.onNodeWithTag("viewerSetMain").performClick()
        compose.waitUntilIdle { (fake.profileEdit.photos().first() as cn.ellerotta.ktproto.api.types.Photo).id == FakeProfileEdit.PHOTO_ID }

        compose.onNodeWithTag("viewerMenu").performClick()
        compose.onNodeWithTag("viewerDelete").performClick()
        compose.onNodeWithTag("confirmDeletePhoto").assertExists()
        assertEquals(2, fake.profileEdit.photos().size)
        compose.onNodeWithTag("confirmDeletePhoto").performClick()
        compose.waitUntilIdle { fake.profileEdit.photos().size == 1 }
    }
}
