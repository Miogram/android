package cn.ellerotta.core.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.PollDraft
import cn.ellerotta.core.model.PollLimits
import cn.ellerotta.core.model.PollOptionDraft
import cn.ellerotta.core.ui.components.attach.PollComposerScreen
import cn.ellerotta.core.ui.theme.MioTheme
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PollComposerTest {
    @get:Rule
    val compose = createComposeRule()

    private var draft by mutableStateOf(PollDraft())
    private var sends = 0
    private var backs = 0

    private fun show(initial: PollDraft = PollDraft(), dark: Boolean = false) {
        draft = initial
        compose.setContent {
            MioTheme(darkTheme = dark, useDynamicColor = false) {
                PollComposerScreen(draft, busy = false, onChange = { draft = it }, onSend = { sends++ }, onBack = { backs++ })
            }
        }
    }

    private fun scrollTo(tag: String) = compose.onNodeWithTag("pollList").performScrollToNode(hasTestTag(tag))

    @Test
    fun theSendButtonAppearsWithAQuestionAndTwoOptions() {
        show()
        assertTrue(compose.onAllNodes(hasTestTag("pollSend")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("pollQuestion").performTextInput("Tea or coffee?")
        compose.onNodeWithTag("pollText_0").performTextInput("Tea")
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("pollSend")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("pollText_1").performTextInput("Coffee")
        compose.onNodeWithTag("pollSend").performClick()
        assertEquals(1, sends)
        assertEquals(listOf("Tea", "Coffee"), draft.options.map { it.text })
    }

    @Test
    fun optionsAreAddedMovedAndRemovedUpToTheirLimits() {
        show(PollDraft(question = "q", options = listOf(PollOptionDraft(0, "a"), PollOptionDraft(1, "b")), nextId = 2))
        compose.onNodeWithTag("pollRemove_0").assertIsNotEnabled()
        compose.onNodeWithTag("pollUp_0").assertIsNotEnabled()
        compose.onNodeWithTag("pollDown_1").assertIsNotEnabled()
        compose.onNodeWithTag("pollAdd").performClick()
        compose.waitForIdle()
        assertEquals(3, draft.options.size)
        compose.onNodeWithTag("pollDown_0").performClick()
        assertEquals(listOf("b", "a", ""), draft.options.map { it.text })
        compose.onNodeWithTag("pollRemove_0").performClick()
        assertEquals(listOf("b", ""), draft.options.map { it.text })
        repeat(PollLimits.MAX_OPTIONS) { draft = draft.addOption() }
        compose.waitForIdle()
        compose.onNodeWithTag("pollAdd").assertIsNotEnabled()
        assertEquals(PollLimits.MAX_OPTIONS, draft.options.size)
    }

    @Test
    fun aQuizShowsRadiosAndTheExplanationAndLosesMultipleAnswers() {
        show(PollDraft(question = "q", options = listOf(PollOptionDraft(0, "a"), PollOptionDraft(1, "b")), nextId = 2, multiple = true))
        assertTrue(compose.onAllNodes(hasTestTag("pollCorrect_0")).fetchSemanticsNodes().isEmpty())
        scrollTo("pollQuiz")
        compose.onNodeWithTag("pollQuiz").performClick()
        compose.waitForIdle()
        assertTrue(draft.quiz)
        assertFalse(draft.multiple)
        assertTrue(compose.onAllNodes(hasTestTag("pollSend")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("pollCorrect_1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("pollCorrect_1").assertIsSelected()
        compose.onNodeWithTag("pollSend").assertIsEnabled()
        scrollTo("pollExplanation")
        compose.onNodeWithTag("pollExplanation").performTextInput("Because b")
        assertEquals("Because b", draft.explanation)
    }

    @Test
    fun onlyGroupsOfferAnonymousVoting() {
        show(PollDraft(kind = PeerKind.Channel))
        assertTrue(compose.onAllNodes(hasTestTag("pollAnonymous")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aGroupsVotesCanBeMadePublic() {
        show(PollDraft(kind = PeerKind.Group))
        scrollTo("pollAnonymous")
        compose.onNodeWithTag("pollAnonymous").performClick()
        assertTrue(draft.publicVoters)
    }

    @Test
    fun leavingAsksOnlyWhenSomethingWasWritten() {
        show()
        compose.onNodeWithContentDescription("back").performClick()
        assertEquals(1, backs)
        compose.onNodeWithTag("pollQuestion").performTextInput("Half written")
        compose.onNodeWithContentDescription("back").performClick()
        compose.onNodeWithTag("pollDiscard").assertExists()
        assertEquals(1, backs)
        compose.onNodeWithTag("pollDiscard").performClick()
        assertEquals(2, backs)
    }

    private val quiz = PollDraft(
        question = "What is the capital of Australia?",
        options = listOf(PollOptionDraft(0, "Sydney"), PollOptionDraft(1, "Canberra"), PollOptionDraft(2, "Melbourne")),
        quiz = true, correct = 1, explanation = "Canberra was built as a compromise.", nextId = 3,
    )

    @Test
    fun quizScreenshotLight() {
        show(quiz)
        compose.waitForIdle()
        screenshot("poll_quiz", dark = false)
    }

    @Test
    fun quizScreenshotDark() {
        show(quiz, dark = true)
        compose.waitForIdle()
        screenshot("poll_quiz", dark = true)
    }

    private fun screenshot(name: String, dark: Boolean) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue(image.width > 0)
        System.getenv("MIOGRAM_SCREENSHOTS")?.let { dir ->
            File(dir).mkdirs()
            File(dir, "${name}_${if (dark) "dark" else "light"}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
