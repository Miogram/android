package cn.ellerotta.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import cn.ellerotta.core.model.ConfigSummary
import cn.ellerotta.core.ui.components.auth.ServerSettings
import cn.ellerotta.core.ui.components.auth.StartScreen
import cn.ellerotta.core.ui.components.auth.UserResolutionFailedDialog
import cn.ellerotta.core.ui.theme.MioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun startScreenStartsAndOpensTheServerSheet() {
        var starts = 0
        var sheets = 0
        compose.setContent {
            MioTheme {
                StartScreen(testMode = true, busy = false, onStartAuth = { starts++ }, serverSheet = { sheets++ })
            }
        }
        compose.onNodeWithTag("testServers").assertIsDisplayed()
        compose.onNodeWithTag("start").performClick()
        assertEquals(1, starts)
        compose.onNodeWithTag("server").performClick()
        compose.waitForIdle()
        assertEquals(1, sheets)
    }

    @Test
    fun serverSettingsForwardInput() {
        var credentials = "" to ""
        var logs = 0
        val config = ConfigSummary(thisDc = 2, testMode = true, dcs = listOf("dc2 149.154.167.40:443"))
        compose.setContent {
            MioTheme {
                ServerSettings("1", "h", testMode = true, config = config, version = "0.1.0-test (1)", onCredentials = { id, hash -> credentials = id to hash }, onOpenLog = { logs++ })
            }
        }
        compose.onNodeWithTag("apiId").performTextInput("2x")
        assertEquals("21" to "h", credentials)
        compose.onNodeWithText("Telegram test servers").assertIsDisplayed()
        compose.onNodeWithText("dc2 149.154.167.40:443").assertIsDisplayed()
        compose.onNodeWithTag("openLog").performScrollTo().performClick()
        assertEquals(1, logs)
    }

    @Test
    @Config(qualifiers = "ru")
    fun russianStrings() {
        compose.setContent {
            MioTheme {
                ServerSettings("", "", testMode = false, config = null, version = "0.1.0-test (1)", onCredentials = { _, _ -> }, onOpenLog = {})
            }
        }
        compose.onNodeWithText("Рабочие серверы Telegram").assertIsDisplayed()
        compose.onNodeWithText("версия 0.1.0-test (1)").assertIsDisplayed()
    }

    @Test
    fun aLoginThatCannotBeOpenedOffersRetryAndClose() {
        var retries = 0
        var closes = 0
        compose.setContent { MioTheme { UserResolutionFailedDialog("disk full", onRetry = { retries++ }, onCloseApp = { closes++ }) } }
        compose.onNodeWithText("Couldn't load your account").assertExists()
        compose.onNodeWithText("disk full").assertExists()
        compose.onNodeWithTag("retryRestore").performClick()
        compose.onNodeWithTag("closeApp").performClick()
        assertEquals(1 to 1, retries to closes)
    }
}
