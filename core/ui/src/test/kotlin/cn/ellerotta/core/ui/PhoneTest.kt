package cn.ellerotta.core.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import cn.ellerotta.core.ui.components.auth.Country
import cn.ellerotta.core.ui.components.auth.PhoneNumbers
import cn.ellerotta.core.ui.components.auth.PhoneScreen
import cn.ellerotta.core.ui.theme.MioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhoneTest {
    @get:Rule
    val compose = createComposeRule()

    private val numbers = PhoneNumbers.get(ApplicationProvider.getApplicationContext())

    private fun editable(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))

    @Test
    fun codesSplitAndFormat() {
        assertEquals("7" to "9991234567", numbers.splitCode("79991234567"))
        assertEquals("44" to "20", numbers.splitCode("4420"))
        assertEquals("351" to "", numbers.splitCode("351"))
        assertEquals("999" to "66", numbers.splitCode("99966"))
        assertEquals("999 123-45-67", numbers.format("7", "9991234567"))
        assertEquals("650-253-0000", numbers.format("1", "6502530000"))
        assertEquals("66 2 7777", numbers.format("999", "6627777"))
        assertEquals("+7 999 123-45-67", numbers.international("7", "9991234567"))
        assertTrue(numbers.isPossible("7", "9991234567"))
        assertFalse(numbers.isPossible("7", "99912"))
        assertTrue(numbers.isPossible("999", "6627777"))
        assertFalse(numbers.isPossible("999", "1234567"))
        assertEquals("000 000-00-00", numbers.mask("7"))
        assertNull(numbers.country(0, Locale.ENGLISH, "test"))
        assertEquals("RU", numbers.country(7, Locale.ENGLISH, "test")?.region)
        assertEquals("🇷🇺", Country("RU", 7, "Russia").flag)
        assertTrue(numbers.countries(Locale.ENGLISH).size > 200)
    }

    @Test
    fun typingACodeJumpsToTheNumberAndConfirms() {
        var sent: String? = null
        compose.setContent { MioTheme { PhoneScreen(testMode = false, busy = false, onBack = {}, onSendCode = { sent = it }) } }
        compose.onNodeWithTag("countryCode").performTextClearance()
        compose.onNodeWithTag("countryCode").performTextInput("79")
        compose.onNodeWithText("Russia").assertIsDisplayed()
        compose.onNodeWithTag("countryCode").assert(editable("7"))
        compose.onNodeWithTag("phone").assert(editable("9"))
        compose.onNodeWithTag("phone").performTextInput("991234567")
        compose.onNodeWithTag("phone").assert(editable("999 123-45-67"))
        compose.onNodeWithTag("sendCode").performClick()
        compose.onNodeWithTag("confirmNumber").assertTextEquals("+7 999 123-45-67")
        compose.onNodeWithTag("confirmPhone").performClick()
        assertEquals("79991234567", sent)
    }

    @Test
    fun incompleteNumbersAreNotSent() {
        compose.setContent { MioTheme { PhoneScreen(testMode = true, busy = false, onBack = {}, onSendCode = { error("sent") }) } }
        compose.onNodeWithText("Test number").assertIsDisplayed()
        compose.onNodeWithTag("phone").assert(editable("66"))
        compose.onNodeWithTag("sendCode").performClick()
        compose.onNodeWithTag("testNumber").performScrollTo().performClick()
        compose.onNodeWithTag("sendCode").performClick()
        compose.onNodeWithTag("editPhone").assertIsDisplayed()
    }
}
