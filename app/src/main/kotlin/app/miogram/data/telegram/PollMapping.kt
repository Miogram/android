package app.miogram.data.telegram

import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.Poll
import cn.ellerotta.core.model.PollAnswer
import cn.ellerotta.core.model.PollResults
import cn.ellerotta.core.model.PollVote
import cn.ellerotta.core.model.PollVoters
import cn.ellerotta.ktproto.api.types.MessagePeerVote
import cn.ellerotta.ktproto.api.types.MessagePeerVoteInputOption
import cn.ellerotta.ktproto.api.types.MessagePeerVoteMultiple
import cn.ellerotta.ktproto.api.types.TextWithEntities
import cn.ellerotta.ktproto.api.types.TypeMessagePeerVote
import cn.ellerotta.ktproto.api.types.Poll as TlPoll
import cn.ellerotta.ktproto.api.types.PollAnswer as TlPollAnswer
import cn.ellerotta.ktproto.api.types.PollResults as TlPollResults

internal fun PeerRegistry.pollOf(poll: TlPoll, results: TlPollResults): Poll = Poll(
    id = poll.id,
    question = formattedOf(poll.question),
    answers = poll.answers.filterIsInstance<TlPollAnswer>().map { PollAnswer(optionText(it.option), formattedOf(it.text)) },
    closed = poll.closed,
    publicVoters = poll.publicVoters,
    multipleChoice = poll.multipleChoice,
    quiz = poll.quiz,
    revotingDisabled = poll.revotingDisabled,
    hideResultsUntilClose = poll.hideResultsUntilClose,
    closeDate = poll.closeDate,
    results = pollResultsOf(results),
)

internal fun PeerRegistry.pollResultsOf(results: TlPollResults): PollResults = PollResults(
    answers = results.results.orEmpty().map { PollVoters(optionText(it.option), it.voters ?: 0, it.chosen, it.correct) },
    totalVoters = results.totalVoters ?: 0,
    recentVoters = results.recentVoters.orEmpty().mapNotNull(::reactorOf),
    solution = results.solution?.let { FormattedText(it, entitiesOf(it, results.solutionEntities.orEmpty())) },
    min = results.min,
)

internal fun PeerRegistry.pollVoteOf(vote: TypeMessagePeerVote): PollVote? = when (vote) {
    is MessagePeerVote -> reactorOf(vote.peer)?.let { PollVote(it, listOf(optionText(vote.option)), vote.date) }
    is MessagePeerVoteMultiple -> reactorOf(vote.peer)?.let { PollVote(it, vote.options.map(::optionText), vote.date) }
    is MessagePeerVoteInputOption -> reactorOf(vote.peer)?.let { PollVote(it, emptyList(), vote.date) }
}

internal fun optionText(option: ByteArray): String = String(option, Charsets.ISO_8859_1)

internal fun optionBytes(option: String): ByteArray = option.toByteArray(Charsets.ISO_8859_1)

private fun formattedOf(text: TextWithEntities) = FormattedText(text.text, entitiesOf(text.text, text.entities))
