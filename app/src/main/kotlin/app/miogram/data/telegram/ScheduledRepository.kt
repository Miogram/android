package app.miogram.data.telegram

import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.ktproto.api.functions.messages.DeleteScheduledMessages
import cn.ellerotta.ktproto.api.functions.messages.EditMessage
import cn.ellerotta.ktproto.api.functions.messages.GetScheduledHistory
import cn.ellerotta.ktproto.api.functions.messages.SendScheduledMessages
import cn.ellerotta.ktproto.api.types.messages.ChannelMessages
import cn.ellerotta.ktproto.api.types.messages.Messages
import cn.ellerotta.ktproto.api.types.messages.MessagesSlice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduledRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    suspend fun list(dialogKey: String): List<MessageItem> {
        val result = telegram.client().invoke(GetScheduledHistory(registry.peer(dialogKey), hash = 0))
        val (messages, users, chats) = when (result) {
            is Messages -> Triple(result.messages, result.users, result.chats)
            is MessagesSlice -> Triple(result.messages, result.users, result.chats)
            is ChannelMessages -> Triple(result.messages, result.users, result.chats)
            else -> return emptyList()
        }
        registry.rememberUsers(users)
        registry.rememberChats(chats)
        return messages.mapNotNull { registry.itemOf(it, dialogKey) }.sortedWith(compareBy({ it.date }, { it.id })).also { telegram.memory["scheduled:$dialogKey"] = it }
    }

    fun stored(dialogKey: String): List<MessageItem>? = telegram.memory["scheduled:$dialogKey"]

    fun remember(dialogKey: String, messages: List<MessageItem>) {
        telegram.memory["scheduled:$dialogKey"] = messages
    }

    suspend fun sendNow(dialogKey: String, ids: List<Int>) {
        telegram.client().invoke(SendScheduledMessages(registry.peer(dialogKey), ids))
    }

    suspend fun delete(dialogKey: String, ids: List<Int>) {
        telegram.client().invoke(DeleteScheduledMessages(registry.peer(dialogKey), ids))
    }

    suspend fun reschedule(dialogKey: String, id: Int, date: Int) {
        telegram.client().invoke(EditMessage(peer = registry.peer(dialogKey), id = id, scheduleDate = date))
    }

    suspend fun edit(dialogKey: String, id: Int, text: String, entities: List<TextEntity> = emptyList()) {
        telegram.client().invoke(EditMessage(peer = registry.peer(dialogKey), id = id, message = text, entities = registry.inputEntitiesOf(entities)))
    }
}
