package app.miogram.data.telegram

import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.ktproto.api.functions.messages.Search
import cn.ellerotta.ktproto.api.functions.messages.UnpinAllMessages
import cn.ellerotta.ktproto.api.functions.messages.UpdatePinnedMessage
import cn.ellerotta.ktproto.api.types.InputMessagesFilterPinned
import cn.ellerotta.ktproto.api.types.messages.ChannelMessages
import cn.ellerotta.ktproto.api.types.messages.Messages
import cn.ellerotta.ktproto.api.types.messages.MessagesSlice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinsRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    suspend fun pinned(dialogKey: String, topicId: Int? = null, limit: Int = PINNED_LIMIT): List<MessageItem> {
        val search = Search(
            peer = registry.peer(dialogKey),
            q = "",
            topMsgId = topicId,
            filter = InputMessagesFilterPinned,
            minDate = 0,
            maxDate = 0,
            offsetId = 0,
            addOffset = 0,
            limit = limit,
            maxId = 0,
            minId = 0,
            hash = 0,
        )
        val (messages, users, chats) = when (val result = telegram.client().invoke(search)) {
            is Messages -> Triple(result.messages, result.users, result.chats)
            is MessagesSlice -> Triple(result.messages, result.users, result.chats)
            is ChannelMessages -> Triple(result.messages, result.users, result.chats)
            else -> return emptyList()
        }
        registry.rememberUsers(users)
        registry.rememberChats(chats)
        return messages.mapNotNull { registry.itemOf(it, dialogKey) }.sortedByDescending { it.id }
    }

    suspend fun pin(dialogKey: String, id: Int, silent: Boolean = false, forBoth: Boolean = true) {
        telegram.client().invoke(UpdatePinnedMessage(silent = silent, pmOneside = !forBoth, peer = registry.peer(dialogKey), id = id))
    }

    suspend fun unpin(dialogKey: String, id: Int) {
        telegram.client().invoke(UpdatePinnedMessage(unpin = true, peer = registry.peer(dialogKey), id = id))
    }

    suspend fun unpinAll(dialogKey: String, topicId: Int? = null) {
        val peer = registry.peer(dialogKey)
        do {
            val affected = telegram.client().invoke(UnpinAllMessages(peer = peer, topMsgId = topicId))
        } while (affected.offset > 0)
    }

    suspend fun hiddenUpTo(dialogKey: String, topicId: Int? = null): Int = telegram.cache?.pinsHiddenUpTo(dialogKey, topicId ?: 0) ?: 0

    suspend fun hide(dialogKey: String, topicId: Int?, upTo: Int) {
        telegram.cache?.putPinsHiddenUpTo(dialogKey, topicId ?: 0, upTo)
    }

    private companion object {
        const val PINNED_LIMIT = 100
    }
}
