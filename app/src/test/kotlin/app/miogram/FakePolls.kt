package app.miogram

import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.messages.GetPollVotes
import cn.ellerotta.ktproto.api.functions.messages.SendVote
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageMediaPoll
import cn.ellerotta.ktproto.api.types.MessagePeerVote
import cn.ellerotta.ktproto.api.types.MessagePeerVoteMultiple
import cn.ellerotta.ktproto.api.types.Poll
import cn.ellerotta.ktproto.api.types.PollAnswer
import cn.ellerotta.ktproto.api.types.PollAnswerVoters
import cn.ellerotta.ktproto.api.types.PollResults
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.TextWithEntities
import cn.ellerotta.ktproto.api.types.UpdateMessagePoll
import cn.ellerotta.ktproto.api.types.UpdateShort
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.messages.VotesList
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FakePolls(private val fake: FakeTelegram) {
    val voteCalls: MutableList<SendVote> = CopyOnWriteArrayList()
    val votesCalls: MutableList<GetPollVotes> = CopyOnWriteArrayList()

    private class Kept(val messageId: Int, val poll: Poll, val correct: Int?, val solution: String?, val votes: MutableMap<Long, List<Int>>)

    private val polls = ConcurrentHashMap<Long, Kept>()
    private var nextPollId = 7_000L

    private val voters get() = listOf(fake.alice, fake.bob)

    fun addPoll(
        question: String,
        answers: List<String>,
        quiz: Boolean = false,
        correct: Int? = null,
        solution: String? = null,
        multiple: Boolean = false,
        public: Boolean = false,
        closeDate: Int? = null,
        othersVoted: List<Int> = emptyList(),
    ): Int {
        val pollId = nextPollId++
        val poll = Poll(
            id = pollId, quiz = quiz, multipleChoice = multiple, publicVoters = public, closeDate = closeDate,
            question = TextWithEntities(question, emptyList()),
            answers = answers.mapIndexed { index, text -> PollAnswer(TextWithEntities(text, emptyList()), "$index".toByteArray()) },
            hash = pollId,
        )
        val votes = ConcurrentHashMap<Long, List<Int>>()
        othersVoted.forEach { answer -> voters.forEach { votes[it.id] = listOf(answer) } }
        lateinit var kept: Kept
        val id = fake.addSaved { id ->
            kept = Kept(id, poll, correct, solution, votes)
            message(kept)
        }
        polls[pollId] = kept
        return id
    }

    suspend fun close(id: Int) {
        val old = keptAt(id)
        val closed = Poll(
            id = old.poll.id, closed = true, quiz = old.poll.quiz, multipleChoice = old.poll.multipleChoice, publicVoters = old.poll.publicVoters,
            question = old.poll.question, answers = old.poll.answers, hash = old.poll.hash + 1,
        )
        val kept = Kept(old.messageId, closed, old.correct, old.solution, old.votes)
        polls[closed.id] = kept
        fake.replaceSaved(message(kept))
        fake.cluster.updates.push(UpdateShort(UpdateMessagePoll(pollId = closed.id, poll = closed, results = results(kept, forSelf = true)), NOW))
    }

    suspend fun bobVotes(id: Int, answer: Int) {
        val kept = keptAt(id)
        kept.votes[fake.bob.id] = listOf(answer)
        fake.replaceSaved(message(kept))
        fake.cluster.updates.push(UpdateShort(UpdateMessagePoll(pollId = kept.poll.id, results = results(kept, forSelf = false)), NOW))
    }

    private fun keptAt(id: Int) = polls.values.first { it.messageId == id }

    private fun message(kept: Kept) = Message(
        id = kept.messageId, fromId = PeerUser(fake.alice.id), peerId = PeerUser(fake.selfId), date = NOW, message = "",
        media = MessageMediaPoll(kept.poll, results(kept, forSelf = true)),
    )

    private fun results(kept: Kept, forSelf: Boolean): PollResults {
        val own = kept.votes[fake.selfId].orEmpty()
        val revealed = forSelf && (own.isNotEmpty() || kept.poll.closed)
        val answers = kept.poll.answers.indices.map { index ->
            PollAnswerVoters(
                chosen = forSelf && index in own,
                correct = revealed && kept.correct == index,
                option = "$index".toByteArray(),
                voters = kept.votes.values.count { index in it },
                recentVoters = emptyList(),
            )
        }
        val recent = if (kept.poll.publicVoters) kept.votes.keys.filter { it != fake.selfId }.sorted().map { PeerUser(it) } else null
        return PollResults(
            min = !forSelf,
            results = answers,
            totalVoters = kept.votes.size,
            recentVoters = recent,
            solution = kept.solution?.takeIf { revealed },
            solutionEntities = kept.solution?.takeIf { revealed }?.let { emptyList() },
        )
    }

    private val users: List<User> get() = listOf(User(self = true, id = fake.selfId, accessHash = 1, firstName = "Test", lastName = "User"), fake.alice, fake.bob)

    fun register(server: FakeTelegramServer) {
        server.rpc.on(SendVote.CONSTRUCTOR_ID) { request ->
            val call = SendVote.decode(request)
            voteCalls += call
            val kept = polls.values.firstOrNull { it.messageId == call.msgId } ?: return@on FakeAnswer.Error(400, "MESSAGE_ID_INVALID")
            if (kept.poll.closed) return@on FakeAnswer.Error(400, "MESSAGE_POLL_CLOSED")
            val chosen = call.options.map { String(it).toInt() }
            if (chosen.size > 1 && !kept.poll.multipleChoice) return@on FakeAnswer.Error(400, "OPTIONS_TOO_MUCH")
            if (kept.poll.quiz && chosen.isEmpty()) return@on FakeAnswer.Error(400, "REVOTE_NOT_ALLOWED")
            if (chosen.isEmpty()) kept.votes -= fake.selfId else kept.votes[fake.selfId] = chosen
            fake.replaceSaved(message(kept))
            val update = UpdateMessagePoll(peer = PeerUser(fake.selfId), msgId = kept.messageId, pollId = kept.poll.id, results = results(kept, forSelf = true))
            FakeAnswer.Result(Updates(listOf(update), users, emptyList(), NOW, 0))
        }
        server.rpc.on(GetPollVotes.CONSTRUCTOR_ID) { request ->
            val call = GetPollVotes.decode(request)
            votesCalls += call
            val kept = polls.values.firstOrNull { it.messageId == call.id } ?: return@on FakeAnswer.Error(400, "MESSAGE_ID_INVALID")
            if (!kept.poll.publicVoters) return@on FakeAnswer.Error(403, "BROADCAST_FORBIDDEN")
            val wanted = call.option?.let { String(it).toInt() }
            val all = kept.votes.entries.filter { wanted == null || wanted in it.value }.sortedBy { it.key }
            val start = call.offset?.toInt() ?: 0
            val page = all.drop(start).take(call.limit)
            val votes = page.map { (voter, answers) ->
                if (kept.poll.multipleChoice) MessagePeerVoteMultiple(PeerUser(voter), answers.map { "$it".toByteArray() }, NOW) else MessagePeerVote(PeerUser(voter), "${answers.single()}".toByteArray(), NOW)
            }
            val next = (start + page.size).takeIf { it < all.size }?.toString()
            FakeAnswer.Result(VotesList(count = all.size, votes = votes, chats = emptyList(), users = users, nextOffset = next))
        }
    }
}
