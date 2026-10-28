package cn.ellerotta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PollsTest {
    private fun poll(counts: List<Int>, total: Int = counts.sum(), multiple: Boolean = false, chosen: Int? = null) = Poll(
        id = 1,
        question = FormattedText("which?"),
        answers = counts.indices.map { PollAnswer("$it", FormattedText("answer $it")) },
        multipleChoice = multiple,
        results = PollResults(counts.mapIndexed { index, count -> PollVoters("$index", count, chosen = index == chosen) }, total),
    )

    @Test
    fun singleChoicePercentsAddUpToAHundred() {
        assertEquals(listOf(33, 33, 34).sorted(), poll(listOf(1, 1, 1)).percents().sorted())
        assertEquals(100, poll(listOf(1, 1, 1)).percents().sum())
        assertEquals(listOf(67, 33, 0), poll(listOf(2, 1, 0)).percents())
        assertEquals(listOf(0, 0), poll(listOf(0, 0)).percents())
    }

    @Test
    fun multipleChoicePercentsAreOfAllVoters() {
        assertEquals(listOf(100, 67), poll(listOf(3, 2), total = 3, multiple = true).percents())
    }

    @Test
    fun resultsShowAfterVotingOrClosing() {
        val open = poll(listOf(1, 0))
        assertFalse(open.showsResults(now = 100))
        assertTrue(open.canVote(now = 100))
        val voted = poll(listOf(1, 0), chosen = 0)
        assertTrue(voted.showsResults(now = 100))
        assertTrue(voted.canRetract(now = 100))
        assertFalse(voted.copy(quiz = true).canRetract(now = 100))
        val ending = open.copy(closeDate = 90)
        assertTrue(ending.isClosed(now = 100))
        assertTrue(ending.showsResults(now = 100))
        assertFalse(ending.canVote(now = 100))
        assertFalse(voted.copy(hideResultsUntilClose = true).showsResults(now = 100))
    }

    @Test
    fun minResultsKeepTheUsersChoice() {
        val voted = poll(listOf(1, 0), chosen = 0)
        val newer = PollResults(listOf(PollVoters("0", 2), PollVoters("1", 1)), totalVoters = 3, min = true)
        val merged = voted.withResults(newer)
        assertEquals(listOf(true, false), merged.results.answers.map { it.chosen })
        assertEquals(3, merged.results.totalVoters)
        assertFalse(voted.withResults(newer.copy(min = false)).voted)
    }

    @Test
    fun pollTextsAskForTheirCustomEmoji() {
        val emoji = TextEntity(0, 2, EntityType.CustomEmoji(42))
        val poll = poll(listOf(0)).copy(question = FormattedText("\uD83D\uDE00?", listOf(emoji)))
        assertEquals(setOf(42L), customEmojiIds(listOf(MessageItem(1, "", out = false, date = 0, poll = poll))))
    }
}
