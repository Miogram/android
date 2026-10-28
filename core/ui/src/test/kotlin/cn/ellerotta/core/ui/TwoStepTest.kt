package cn.ellerotta.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import cn.ellerotta.core.model.TwoStepGoal
import cn.ellerotta.core.model.TwoStepState
import cn.ellerotta.core.model.TwoStepStatus
import cn.ellerotta.core.model.TwoStepStep
import cn.ellerotta.core.ui.components.profile.TwoStepActions
import cn.ellerotta.core.ui.components.profile.TwoStepScreen
import cn.ellerotta.core.ui.theme.MioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TwoStepTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun anAccountWithoutAPasswordIsOfferedOne() {
        var goal: TwoStepGoal? = null
        compose.setContent { MioTheme { TwoStepScreen(TwoStepState(TwoStepStatus(enabled = false)), busy = false, TwoStepActions(onStart = { goal = it })) } }
        compose.onNodeWithTag("twoStepSet").performClick()
        assertEquals(TwoStepGoal.Create, goal)
    }

    @Test
    fun aPasswordShowsItsHintAndOffersChanges() {
        var goal: TwoStepGoal? = null
        compose.setContent { MioTheme { TwoStepScreen(TwoStepState(TwoStepStatus(enabled = true, hint = "a fish", hasRecovery = true)), busy = false, TwoStepActions(onStart = { goal = it })) } }
        compose.onNodeWithTag("twoStepHint").assertIsDisplayed()
        compose.onNodeWithTag("twoStepChange").performScrollTo().performClick()
        assertEquals(TwoStepGoal.Change, goal)
    }

    @Test
    fun aNewPasswordGoesOnOnlyWhenTypedTheSameTwice() {
        var sent: String? = null
        compose.setContent { MioTheme { TwoStepScreen(TwoStepState(TwoStepStatus(), step = TwoStepStep.NewPassword), busy = false, TwoStepActions(onNewPassword = { sent = it })) } }
        compose.onNodeWithTag("twoStepNew").performTextInput("secret")
        compose.onNodeWithTag("twoStepRepeat").performTextInput("secreT")
        compose.onNodeWithTag("twoStepContinue").assertIsNotEnabled()
        compose.onNodeWithTag("twoStepRepeatError", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("twoStepRepeat").performTextInput("x")
        compose.onNodeWithTag("twoStepNew").performTextInput("x")
        compose.onNodeWithTag("twoStepContinue").assertIsNotEnabled()
        assertEquals(null, sent)
    }

    @Test
    fun theForgottenPasswordLinkNeedsARecoveryEmail() {
        var forgot = false
        compose.setContent {
            MioTheme {
                TwoStepScreen(TwoStepState(TwoStepStatus(enabled = true, hasRecovery = true), step = TwoStepStep.Current(TwoStepGoal.Change)), busy = false, TwoStepActions(onForgot = { forgot = true }))
            }
        }
        compose.onNodeWithTag("twoStepContinue").assertIsNotEnabled()
        compose.onNodeWithTag("twoStepPassword").performTextInput("guess")
        compose.onNodeWithTag("twoStepContinue").assertIsEnabled()
        compose.onNodeWithTag("twoStepForgot").performClick()
        assertEquals(true, forgot)
    }
}
