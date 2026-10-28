package cn.ellerotta.core.model

data class Poll(
    val id: Long,
    val question: FormattedText,
    val answers: List<PollAnswer>,
    val closed: Boolean = false,
    val publicVoters: Boolean = false,
    val multipleChoice: Boolean = false,
    val quiz: Boolean = false,
    val revotingDisabled: Boolean = false,
    val hideResultsUntilClose: Boolean = false,
    val closeDate: Int? = null,
    val results: PollResults = PollResults(),
) {
    val voted: Boolean get() = results.answers.any { it.chosen }

    fun isClosed(now: Int): Boolean = closed || (closeDate != null && closeDate <= now)

    fun showsResults(now: Int): Boolean = if (hideResultsUntilClose) isClosed(now) else voted || isClosed(now)

    fun canVote(now: Int): Boolean = !voted && !isClosed(now)

    fun canRetract(now: Int): Boolean = voted && !quiz && !revotingDisabled && !isClosed(now)

    fun votersOf(option: String): PollVoters? = results.answers.firstOrNull { it.option == option }

    fun percents(): List<Int> {
        val total = results.totalVoters
        val counts = answers.map { votersOf(it.option)?.voters ?: 0 }
        if (total <= 0) return counts.map { 0 }
        if (multipleChoice) return counts.map { (it * 200 + total) / (total * 2) }
        val sum = counts.sum().takeIf { it > 0 } ?: return counts.map { 0 }
        val floors = counts.map { it * 100 / sum }
        var left = 100 - floors.sum()
        val byRemainder = counts.indices.sortedByDescending { (counts[it] * 100) % sum }
        val result = floors.toMutableList()
        for (index in byRemainder) {
            if (left <= 0) break
            if (counts[index] == 0) continue
            result[index]++
            left--
        }
        return result
    }

    fun withResults(newer: PollResults): Poll {
        if (!newer.min) return copy(results = newer)
        val known = results.answers.associateBy { it.option }
        val answers = newer.answers.map { voters ->
            val old = known[voters.option] ?: return@map voters
            voters.copy(chosen = old.chosen, correct = voters.correct || old.correct)
        }
        return copy(results = newer.copy(answers = answers, solution = newer.solution ?: results.solution))
    }
}

data class PollAnswer(val option: String, val text: FormattedText)

data class PollVoters(val option: String, val voters: Int, val chosen: Boolean = false, val correct: Boolean = false)

data class PollResults(
    val answers: List<PollVoters> = emptyList(),
    val totalVoters: Int = 0,
    val recentVoters: List<Reactor> = emptyList(),
    val solution: FormattedText? = null,
    val min: Boolean = false,
)

data class PollVote(val voter: Reactor, val options: List<String>, val date: Int)

data class PollVotes(val votes: List<PollVote>, val count: Int, val next: String? = null)

data class PollVotesState(
    val messageId: Int,
    val poll: Poll,
    val votes: List<PollVote> = emptyList(),
    val count: Int = 0,
    val next: String? = null,
    val loading: Boolean = true,
)
