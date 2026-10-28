package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import cn.ellerotta.core.model.CodeRequest
import cn.ellerotta.core.model.CodeType
import cn.ellerotta.core.ui.components.auth.CodeEntryScreen
import cn.ellerotta.core.ui.theme.MioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CodeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun request(type: CodeType = CodeType.App, next: CodeType? = CodeType.Sms, timeout: Int? = 90, sentAgo: Long = 0) =
        CodeRequest("79991234567", "hash", 5, 2, type, next, timeout, System.currentTimeMillis() - sentAgo)

    private fun typed(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))

    @Test
    fun fullCodeSubmitsOnceAndAWrongOneClears() {
        val codes = mutableListOf<String>()
        var wrong by mutableIntStateOf(0)
        compose.setContent {
            MioTheme {
                CodeEntryScreen(request(), testMode = false, busy = false, wrongCount = wrong, accepted = false, onBack = {}, onCode = { codes += it }, onResend = {})
            }
        }
        compose.onNodeWithText("Check your Telegram messages").assertIsDisplayed()
        compose.onNodeWithTag("code").performTextInput("1234")
        assertEquals(emptyList(), codes)
        compose.onNodeWithTag("code").performTextInput("5x9")
        compose.onNodeWithTag("code").assert(typed("12345"))
        compose.waitForIdle()
        assertEquals(listOf("12345"), codes)
        wrong = 1
        compose.onNodeWithTag("wrongCode").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("code").assert(typed(""))
    }

    @Test
    fun resendCountsDownThenOffersTheNextWay() {
        var resends = 0
        compose.setContent {
            MioTheme {
                CodeEntryScreen(request(CodeType.Sms, CodeType.Call, timeout = 65), testMode = true, busy = false, wrongCount = 0, accepted = false, onBack = {}, onCode = {}, onResend = { resends++ })
            }
        }
        compose.onNodeWithText("We've sent an SMS with an activation code to +7 999 123-45-67.").assertIsDisplayed()
        compose.onNodeWithText("We can call you with the code in 1:05").assertIsDisplayed()
        compose.onNodeWithTag("testCode").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Test code: 22222").assertIsDisplayed()
    }

    @Test
    fun expiredTimerShowsTheResendButton() {
        var resends = 0
        compose.setContent {
            MioTheme {
                CodeEntryScreen(request(CodeType.App, CodeType.Sms, timeout = 30, sentAgo = 60_000), testMode = false, busy = false, wrongCount = 0, accepted = false, onBack = {}, onCode = {}, onResend = { resends++ })
            }
        }
        compose.onNodeWithTag("resend").performScrollTo().performClick()
        assertEquals(1, resends)
    }

    @Test
    fun aMinuteWithoutTypingAsksAboutTheCodeAndWrongNumberGoesBack() {
        var backs = 0
        compose.setContent {
            MioTheme {
                CodeEntryScreen(request(), testMode = false, busy = false, wrongCount = 0, accepted = false, onBack = { backs++ }, onCode = {}, onResend = {})
            }
        }
        compose.onNodeWithTag("wrongNumber").performScrollTo().performClick()
        assertEquals(1, backs)
        compose.onNodeWithTag("codeNotReceived").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(62_000)
        compose.waitForIdle()
        compose.onNodeWithText("Can't find the code?").assertExists()
        compose.onNodeWithTag("changeNumber").performClick()
        assertEquals(2, backs)
        compose.onNodeWithTag("codeNotReceived").assertDoesNotExist()
    }
}
