package app.miogram

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class SessionEndedFlowTest {
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

    @Before
    fun workManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        scenario?.close()
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    private fun logIn() {
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
    }

    @Test
    fun aSessionEndedElsewhereReturnsToTheWelcomeScreen() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        logIn()
        compose.waitForText("Alice Example")

        runBlocking { fake.revokeSession() }
        compose.waitForTag("start")
        compose.waitForText("You were logged out: this session was ended on another device")
        assertFalse(telegram.isAuthorized)
        assertTrue(compose.onAllNodes(hasTestTag("openChats")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("ok").performClick()

        logIn()
        compose.waitForText("Alice Example")
        compose.onNodeWithTag("openSettings").performClick()
        compose.waitForTag("settings")
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("logOut"))
        compose.onNodeWithTag("logOut").performClick()
        compose.waitForTag("confirmLogOut")
        compose.onNodeWithTag("confirmLogOut").performClick()
        compose.waitForTag("start")
        compose.waitUntilIdle { true }
        assertTrue(compose.onAllNodes(hasTestTag("error")).fetchSemanticsNodes().isEmpty())
    }
}
