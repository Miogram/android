package app.miogram

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
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
class MediaFlowTest {
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

    @Test
    fun tappingAPhotoOpensTheViewerWhichPages() {
        fake.addPhotoMessage()
        fake.addVideoMessage()
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

        val photoKey = "photo${FakeTelegram.PHOTO_ID}"
        compose.waitForTag("photo_$photoKey")
        compose.waitUntilIdle { fake.photoTypes.isNotEmpty() }
        assertTrue("w" !in fake.photoTypes)
        compose.onNodeWithTag("photo_$photoKey").performClick()

        compose.waitForTag("viewerPosition")
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("viewerPosition").assertTextEquals("1 of 2") }.isSuccess }
        compose.waitUntilIdle { "w" in fake.photoTypes }
        compose.onNodeWithTag("viewer").performTouchInput { swipeLeft() }
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("viewerPosition").assertTextEquals("2 of 2") }.isSuccess }
        compose.waitForTag("video")
        compose.onNodeWithTag("viewerBack").performClick()
        compose.waitForTag("photo_$photoKey")
    }
}
