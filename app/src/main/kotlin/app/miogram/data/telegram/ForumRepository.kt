package app.miogram.data.telegram

import cn.ellerotta.core.model.ForumTopic
import cn.ellerotta.core.model.ForumTopicsPage
import cn.ellerotta.ktproto.api.functions.messages.CreateForumTopic
import cn.ellerotta.ktproto.api.functions.messages.GetForumTopics
import cn.ellerotta.ktproto.api.functions.messages.ReadDiscussion
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageActionTopicCreate
import cn.ellerotta.ktproto.api.types.MessageService
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.TypeMessage
import cn.ellerotta.ktproto.api.types.TypeUpdates
import cn.ellerotta.ktproto.api.types.UpdateNewChannelMessage
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.UpdatesCombined
import cn.ellerotta.ktproto.api.types.messages.ForumTopics
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import cn.ellerotta.ktproto.api.types.ForumTopic as TlForumTopic

@Singleton
class ForumRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    suspend fun topics(dialogKey: String, after: ForumTopic? = null, limit: Int = 30): ForumTopicsPage {
        val result = telegram.client().invoke(
            GetForumTopics(
                peer = registry.peer(dialogKey),
                offsetDate = after?.date ?: 0,
                offsetId = after?.topMessage ?: 0,
                offsetTopic = after?.id ?: 0,
                limit = limit,
            ),
        )
        val topics = topicsOf(result)
        return ForumTopicsPage(topics, maxOf(result.count, topics.size)).also { if (after == null) telegram.memory["topics:$dialogKey"] = it }
    }

    fun stored(dialogKey: String): ForumTopicsPage? = telegram.memory["topics:$dialogKey"]

    suspend fun create(dialogKey: String, title: String, iconColor: Int): ForumTopic {
        val name = title.trim()
        val updates = telegram.client().invoke(CreateForumTopic(peer = registry.peer(dialogKey), title = name, iconColor = iconColor, randomId = Random.nextLong()))
        val created = messagesOf(updates).filterIsInstance<MessageService>().firstOrNull { it.action is MessageActionTopicCreate }
            ?: error("telegram did not say which topic it created")
        return ForumTopic(id = created.id, title = name, iconColor = iconColor, topMessage = created.id, date = created.date)
    }

    suspend fun read(dialogKey: String, topicId: Int, maxId: Int) {
        telegram.client().invoke(ReadDiscussion(registry.peer(dialogKey), topicId, maxId))
    }

    private fun topicsOf(result: ForumTopics): List<ForumTopic> {
        registry.rememberUsers(result.users)
        registry.rememberChats(result.chats)
        val messages = result.messages.associateBy(::idOf)
        return result.topics.filterIsInstance<TlForumTopic>().map { topic ->
            val top = messages[topic.topMessage]
            ForumTopic(
                id = topic.id,
                title = topic.title,
                iconColor = topic.iconColor,
                iconEmojiId = topic.iconEmojiId?.takeIf { it != 0L },
                unread = topic.unreadCount,
                mentions = topic.unreadMentionsCount,
                pinned = topic.pinned,
                closed = topic.closed,
                hidden = topic.hidden,
                topMessage = topic.topMessage,
                date = top?.let(::dateOf) ?: topic.date,
                preview = previewOf(top, senderOf(top), registry),
            )
        }
    }

    private fun senderOf(message: TypeMessage?): String? = when {
        message !is Message -> null
        message.out -> ""
        else -> (message.fromId as? PeerUser)?.let { registry.userNames[it.userId] }
    }
}

private fun messagesOf(updates: TypeUpdates): List<TypeMessage> {
    val list = when (updates) {
        is Updates -> updates.updates
        is UpdatesCombined -> updates.updates
        else -> emptyList()
    }
    return list.filterIsInstance<UpdateNewChannelMessage>().map { it.message }
}
