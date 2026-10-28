package app.miogram

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.KtprotoVersion
import cn.ellerotta.ktproto.api.LAYER
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import kotlin.test.assertTrue

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class LoginFlowTest {
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
    fun loginChatAndLog() {
        compose.waitForTag("start")
        compose.onNodeWithTag("start").performClick()
        compose.waitForTag("phone")
        compose.onNodeWithTag("phone").performTextClearance()
        compose.onNodeWithTag("phone").performTextInput("6627777")
        compose.onNodeWithTag("sendCode").performClick()
        compose.waitForTag("confirmPhone")
        compose.onNodeWithTag("confirmPhone").performClick()
        compose.waitForTag("code")
        compose.onNodeWithTag("code").performTextInput("12345")
        compose.waitForTag("wrongCode")
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("code") and editableText("")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("code").performTextInput("22222")
        compose.waitForText("Alice Example")
        compose.onNodeWithText("Alice Example").assertIsDisplayed()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("messageInput")
        compose.onNodeWithTag("messageInput").performTextInput("sent from compose")
        compose.onNodeWithTag("send").performClick()
        compose.waitForText("sent from compose")
        compose.onNodeWithTag("back").performClick()
        compose.waitForTag("openChats")
        compose.onNodeWithTag("openChats").performTouchInput { longClick() }
        compose.waitForText("Bob")
        compose.onNodeWithTag("back").performClick()
        compose.waitForTag("folder_2")
        compose.onNodeWithTag("folder_2").performClick()
        compose.waitForText("hi from bob")
        compose.onNodeWithTag("openSettings").performClick()
        compose.waitForTag("openOwnProfile")
        val library = "ktproto ${KtprotoVersion.NAME} · layer $LAYER"
        compose.onNodeWithTag("settings").performScrollToNode(hasText(library))
        compose.onNodeWithText(library).assertIsDisplayed()
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("openSessions"))
        compose.onNodeWithTag("openSessions").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForText("This device")
        compose.onNodeWithTag("sessions").performScrollToNode(hasText("iPhone 17"))
        compose.onNodeWithTag("back").performClick()
        compose.waitForTag("openChats")
        compose.onNodeWithTag("openChats").performClick()
        compose.waitForTag("folder_0")
        compose.onNodeWithTag("folder_0").performClick()
        compose.waitForText("Alice Example")
        compose.onNodeWithText("Alice Example").performClick()
        compose.waitForTag("title")
        compose.onNodeWithTag("title").performClick()
        compose.waitForTag("personalChannel")
        compose.onNodeWithTag("peerProfile").performScrollToNode(hasText("likes compose"))
        compose.waitForText("likes compose")
        assertTrue(telegram.log.value.any { it.text.contains("InitConnection") })
    }
}
