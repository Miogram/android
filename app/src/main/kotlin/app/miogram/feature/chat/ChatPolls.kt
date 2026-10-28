package app.miogram.feature.chat

import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.PollsRepository
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PollVotesState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class ChatPolls(
    private val dialogKey: String,
    private val repository: PollsRepository,
    private val messages: MutableStateFlow<List<MessageItem>>,
    private val work: (suspend () -> Unit) -> Job,
    private val shown: (List<String?>) -> Unit,
) {
    private val votesState = MutableStateFlow<PollVotesState?>(null)

    val votes: StateFlow<PollVotesState?> = votesState.asStateFlow()

    fun vote(id: Int, options: List<String>) {
        work { repository.vote(dialogKey, id, options) }
    }

    fun apply(event: ChatEvent.PollChanged): List<MessageItem> {
        if (event.key != null && event.key != dialogKey) return emptyList()
        val changed = ArrayList<MessageItem>()
        messages.update { list ->
            list.map { message ->
                val poll = message.poll?.takeIf { it.id == event.pollId || (event.id != null && message.id == event.id) } ?: return@map message
                val base = event.poll?.copy(results = poll.results) ?: poll
                message.copy(poll = base.withResults(event.results)).also(changed::add)
            }
        }
        votesState.update { state -> state?.let { open -> changed.firstOrNull { it.id == open.messageId }?.poll?.let { open.copy(poll = it) } ?: open } }
        return changed
    }

    fun openVotes(id: Int) {
        val poll = messages.value.firstOrNull { it.id == id }?.poll?.takeIf { it.publicVoters } ?: return
        votesState.value = PollVotesState(id, poll)
        load(null)
    }

    fun moreVotes() {
        val state = votesState.value ?: return
        val next = state.next ?: return
        if (state.loading) return
        votesState.value = state.copy(loading = true)
        load(next)
    }

    fun closeVotes() {
        votesState.value = null
    }

    private fun load(offset: String?) {
        val id = votesState.value?.messageId ?: return
        val job = work {
            val page = repository.votes(dialogKey, id, offset = offset)
            shown(page.votes.map { it.voter.avatarKey })
            votesState.update { state ->
                state?.takeIf { it.messageId == id }?.let { it.copy(votes = it.votes + page.votes, count = page.count, next = page.next, loading = false) } ?: state
            }
        }
        job.invokeOnCompletion { votesState.update { state -> state?.takeIf { it.messageId == id && it.loading }?.copy(loading = false) ?: state } }
    }
}
