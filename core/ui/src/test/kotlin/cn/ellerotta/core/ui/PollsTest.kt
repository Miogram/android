package cn.ellerotta.core.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.Poll
import cn.ellerotta.core.model.PollAnswer
import cn.ellerotta.core.model.PollResults
import cn.ellerotta.core.model.PollVote
import cn.ellerotta.core.model.PollVoters
import cn.ellerotta.core.model.PollVotesState
import cn.ellerotta.core.model.Reactor
import cn.ellerotta.core.ui.components.chat.ChatPollActions
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp")
class PollsTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()
    private val votes = mutableListOf<Pair<Int, List<String>>>()
    private val shown = mutableListOf<Int>()
    private val actions = ChatPollActions(onVote = { id, options -> votes += id to options }, onShowVotes = { shown += it }, onMoreVotes = {}, onCloseVotes = {})

    private fun poll(id: Long, answers: List<String>, counts: List<Int> = answers.map { 0 }, chosen: Set<Int> = emptySet(), total: Int = counts.sum()) = Poll(
        id = id,
        question = FormattedText("question $id"),
        answers = answers.mapIndexed { index, text -> PollAnswer("$index", FormattedText(text)) },
        results = PollResults(counts.mapIndexed { index, count -> PollVoters("$index", count, chosen = index in chosen) }, total),
    )

    private fun show(vararg polls: Poll, votesState: PollVotesState? = null) {
        val messages = polls.mapIndexed { index, poll -> MessageItem(index + 1, "", out = false, date = now - 100 + index, poll = poll) }
        compose.setContent {
            MioTheme { ChatScreen(title = "Polls", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, polls = actions, pollVotes = votesState) }
        }
    }

    @Test
    fun aTapVotesAndSeveralAnswersWaitForVote() {
        show(
            poll(1, listOf("tabs", "spaces")),
            poll(2, listOf("kotlin", "java", "rust")).copy(multipleChoice = true),
        )
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("message_1"))
        compose.onNodeWithText("question 1").assertExists()
        compose.onNodeWithTag("pollAnswer_1_1").performClick()
        assertEquals(listOf(1 to listOf("1")), votes)
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("message_2"))
        compose.onNodeWithTag("pollVote_2").assertIsNotEnabled()
        compose.onNodeWithTag("pollAnswer_2_0").performClick()
        compose.onNodeWithTag("pollAnswer_2_2").performClick()
        compose.onNodeWithTag("pollVote_2").assertIsEnabled().performClick()
        assertEquals(2 to listOf("0", "2"), votes.last())
    }

    @Test
    fun aPollWithACloseDateCountsDown() {
        compose.mainClock.autoAdvance = false
        show(poll(1, listOf("tabs", "spaces")).copy(closeDate = now + 125))
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Anonymous poll").assertExists()
        compose.onNodeWithTag("pollEndsIn_1", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("ends in 2:0", substring = true).assertExists()
    }

    @Test
    fun resultsShowPercentagesTheChoiceAndRetract() {
        val voters = listOf(Reactor(7, "Alice"), Reactor(8, "Bob"))
        show(poll(1, listOf("tabs", "spaces"), counts = listOf(1, 2), chosen = setOf(0)).let { it.copy(publicVoters = true, results = it.results.copy(recentVoters = voters)) })
        compose.onNodeWithText("Public poll").assertExists()
        compose.onNodeWithText("33%").assertExists()
        compose.onNodeWithText("67%").assertExists()
        compose.onNodeWithTag("pollChosen_1_0", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("3 votes").assertExists()
        compose.onNodeWithTag("pollRetract_1").performClick()
        assertEquals(listOf(1 to emptyList<String>()), votes)
        compose.onNodeWithTag("pollVotes_1").performClick()
        assertEquals(listOf(1), shown)
    }

    @Test
    fun aQuizShowsRightWrongAndTheSolution() {
        val quiz = poll(1, listOf("3", "4"), counts = listOf(1, 0), chosen = setOf(0)).let { poll ->
            poll.copy(
                quiz = true,
                results = poll.results.copy(answers = listOf(PollVoters("0", 1, chosen = true), PollVoters("1", 0, correct = true)), solution = FormattedText("basic arithmetic")),
            )
        }
        show(quiz, poll(2, listOf("a", "b"), counts = listOf(0, 0)).copy(closed = true))
        compose.onNodeWithTag("pollWrong_1_0", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("pollCorrect_1_1", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("basic arithmetic").assertExists()
        compose.onNodeWithTag("pollRetract_1").assertDoesNotExist()
        compose.onNodeWithText("Final results").assertExists()
        compose.onNodeWithText("No votes yet").assertExists()
        compose.onNodeWithTag("pollRetract_2").assertDoesNotExist()
    }

    @Test
    fun theVotesSheetListsVotersAndTheirAnswers() {
        val poll = poll(1, listOf("tabs", "spaces"), counts = listOf(1, 1), chosen = setOf(0)).copy(publicVoters = true)
        show(poll, votesState = PollVotesState(1, poll, listOf(PollVote(Reactor(7, "Alice"), listOf("1"), now)), count = 2, loading = false))
        compose.onNodeWithTag("pollVotesList").assertExists()
        compose.onNode(hasText("Alice") and hasAnyAncestor(hasTestTag("pollVotesList"))).assertExists()
        compose.onNode(hasText("spaces") and hasAnyAncestor(hasTestTag("pollVotesList"))).assertExists()
    }
}
