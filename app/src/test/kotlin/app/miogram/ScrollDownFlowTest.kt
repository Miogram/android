package app.miogram

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
class ScrollDownFlowTest {
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
    fun aPinFurtherBackAndDownAgain() {
        fake.addSavedMessages(200)
        fake.pins.pinned += 11
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("message_209")
        compose.onNodeWithTag("scrollDown").assertDoesNotExist()

        compose.waitForTag("pinnedBar")
        compose.onNodeWithTag("pinnedBar").performClick()
        compose.waitUntilIdle { fake.historyCalls.any { it.first == 0L && it.second == 12 && it.third == -25 } }
        compose.waitForTag("highlight_11")

        val newest = fake.historyCalls.count { it == Triple(0L, 0, 0) }
        compose.waitForTag("scrollDown")
        compose.onNodeWithTag("scrollDown").performClick()
        compose.waitUntilIdle { fake.historyCalls.count { it == Triple(0L, 0, 0) } > newest }
        compose.waitForTag("message_209")
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("scrollDown")).fetchSemanticsNodes().isEmpty() }
        assertTrue(compose.onAllNodes(hasTestTag("message_11")).fetchSemanticsNodes().isEmpty())
    }
}
