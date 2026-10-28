package app.miogram.data.telegram

import cn.ellerotta.core.model.PollVotes
import cn.ellerotta.ktproto.api.functions.messages.GetPollVotes
import cn.ellerotta.ktproto.api.functions.messages.SendVote
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PollsRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    suspend fun vote(dialogKey: String, id: Int, options: List<String>) {
        telegram.client().invoke(SendVote(registry.peer(dialogKey), id, options.map(::optionBytes)))
    }

    suspend fun votes(dialogKey: String, id: Int, option: String? = null, offset: String? = null, limit: Int = 50): PollVotes {
        val list = telegram.client().invoke(GetPollVotes(registry.peer(dialogKey), id, option?.let(::optionBytes), offset, limit))
        registry.rememberUsers(list.users)
        registry.rememberChats(list.chats)
        return PollVotes(list.votes.mapNotNull { registry.pollVoteOf(it) }, list.count, list.nextOffset?.takeIf { it.isNotEmpty() })
    }
}
