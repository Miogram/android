package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import cn.ellerotta.core.ui.components.auth.PasswordScreen
import cn.ellerotta.core.ui.components.auth.SignUpScreen
import cn.ellerotta.core.ui.components.common.initials
import cn.ellerotta.core.ui.theme.MioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PasswordTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun wrongPasswordIsCleared() {
        val sent = mutableListOf<String>()
        var wrong by mutableIntStateOf(0)
        compose.setContent { MioTheme { PasswordScreen("my cat", busy = false, wrongCount = wrong, onBack = {}, onCheckPassword = { sent += it }) } }
        compose.onNodeWithTag("passwordHint", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("checkPassword").performClick()
        compose.onNodeWithTag("password").performTextInput("hunter2")
        compose.onNodeWithTag("checkPassword").performClick()
        assertEquals(listOf("hunter2"), sent)
        wrong = 1
        compose.onNodeWithTag("wrongPassword", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("checkPassword").performClick()
        assertEquals(1, sent.size)
    }

    @Test
    fun signUpNeedsAFirstName() {
        var names: Pair<String, String>? = null
        compose.setContent { MioTheme { SignUpScreen(busy = false, onBack = {}, onSignUp = { first, last -> names = first to last }) } }
        compose.onNodeWithTag("signUp").performClick()
        assertEquals(null, names)
        compose.onNodeWithTag("firstName").performTextInput(" Ada ")
        compose.onNodeWithTag("lastName").performTextInput("Lovelace")
        compose.onNodeWithText("AL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("signUp").performScrollTo().performClick()
        assertEquals("Ada" to "Lovelace", names)
        assertEquals("AL", initials("ada  lovelace"))
        assertEquals("C", initials("cat"))
    }
}
