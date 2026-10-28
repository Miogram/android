package cn.ellerotta.core.ui

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import cn.ellerotta.core.model.VoiceRecording
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceKeyboardTest {
    @get:Rule
    val compose = createComposeRule()

    private var view: View? = null
    private var recording by mutableStateOf<VoiceRecording?>(null)
    private val stops = mutableListOf<Boolean>()

    private fun dispatchKeyboard(height: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, height))
            .setVisible(WindowInsetsCompat.Type.ime(), height > 0)
            .build()
        ViewCompat.dispatchApplyWindowInsets(view!!, insets)
    }

    private fun keyboard(height: Int) {
        compose.runOnUiThread { dispatchKeyboard(height) }
        compose.waitForIdle()
    }

    @Before
    fun screen() {
        compose.setContent {
            view = LocalView.current
            MioTheme {
                ChatScreen(
                    title = "Saved", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    recording = recording,
                    onVoiceStart = {
                        recording = VoiceRecording(7_000, 0.5f)
                        dispatchKeyboard(0)
                    },
                    onVoiceStop = {
                        stops += it
                        recording = null
                    },
                )
            }
        }
        compose.onNodeWithTag("messageInput").performClick()
        val before = micTop()
        keyboard(KEYBOARD)
        assertEquals(before - KEYBOARD, micTop(), "the keyboard lifts the composer")
    }

    private fun micTop() = compose.onNodeWithTag("mic").fetchSemanticsNode().positionInRoot.y

    @Test
    fun theKeyboardGoingWithTheStartLeavesAHeldRecordingToTheRelease() {
        compose.onNodeWithTag("mic").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("mic").performTouchInput { moveBy(Offset(1f, 0f)) }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithTag("recording").assertIsDisplayed()
        compose.onNodeWithTag("discardVoice").assertDoesNotExist()
        assertEquals(emptyList(), stops)
        compose.onNodeWithTag("mic").performTouchInput {
            advanceEventTime(1_500)
            up()
        }
        assertEquals(listOf(true), stops)
    }

    @Test
    fun theKeyboardHidingWhileHeldLeavesTheRecordingToTheRelease() {
        compose.onNodeWithTag("mic").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("recording").assertIsDisplayed()
        keyboard(0)
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("mic").performTouchInput { moveBy(Offset(1f, 0f)) }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithTag("recording").assertIsDisplayed()
        compose.onNodeWithTag("discardVoice").assertDoesNotExist()
        assertEquals(emptyList(), stops)
        compose.onNodeWithTag("mic").performTouchInput {
            advanceEventTime(1_500)
            up()
        }
        assertEquals(listOf(true), stops)
    }

    @Test
    fun aLockedRecordingSurvivesTheKeyboardGoing() {
        compose.onNodeWithTag("mic").performTouchInput {
            down(center)
            advanceEventTime(300)
            moveBy(Offset(0f, -100.dp.toPx()))
            up()
        }
        keyboard(0)
        compose.onNodeWithTag("discardVoice").assertIsDisplayed()
        assertEquals(emptyList(), stops)
        compose.onNodeWithTag("sendVoice").performClick()
        assertEquals(listOf(true), stops)
    }

    private companion object {
        const val KEYBOARD = 200
    }
}
