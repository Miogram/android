package app.miogram

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.SendMessageCancelAction
import cn.ellerotta.ktproto.api.types.SendMessageTypingAction
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlin.test.assertEquals
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
class TypingFlowTest {
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
    fun typingClearingAndSendingReachTheServer() {
        compose.logIn()
        compose.onNode(hasText("Alice Example")).performClick()
        compose.waitForTag("messageInput")

        compose.onNodeWithTag("messageInput").performTextInput("hi")
        compose.waitUntilIdle { fake.typingCalls.size == 1 }
        compose.onNodeWithTag("messageInput").performTextClearance()
        compose.waitUntilIdle { fake.typingCalls.size == 2 }
        compose.onNodeWithTag("messageInput").performTextInput("hello")
        compose.onNodeWithTag("send").performClick()
        compose.waitUntilIdle { fake.typingCalls.size == 4 && fake.lastSendMessage != null }

        assertEquals(listOf(SendMessageTypingAction, SendMessageCancelAction, SendMessageTypingAction, SendMessageCancelAction), fake.typingCalls.map { it.action })
        assertEquals(setOf(501L), fake.typingCalls.map { (it.peer as InputPeerUser).userId }.toSet())
    }
}
