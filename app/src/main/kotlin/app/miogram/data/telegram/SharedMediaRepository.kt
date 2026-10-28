package app.miogram.data.telegram

import cn.ellerotta.core.model.SharedMedia
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.SharedTab
import cn.ellerotta.ktproto.api.functions.messages.GetCommonChats
import cn.ellerotta.ktproto.api.functions.messages.GetSearchCounters
import cn.ellerotta.ktproto.api.functions.messages.Search
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.Chat
import cn.ellerotta.ktproto.api.types.InputMessagesFilterDocument
import cn.ellerotta.ktproto.api.types.InputMessagesFilterGif
import cn.ellerotta.ktproto.api.types.InputMessagesFilterMusic
import cn.ellerotta.ktproto.api.types.InputMessagesFilterPhotoVideo
import cn.ellerotta.ktproto.api.types.InputMessagesFilterRoundVoice
import cn.ellerotta.ktproto.api.types.InputMessagesFilterUrl
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.TypeMessagesFilter
import cn.ellerotta.ktproto.api.types.messages.ChannelMessages
import cn.ellerotta.ktproto.api.types.messages.Messages
import cn.ellerotta.ktproto.api.types.messages.MessagesSlice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedMediaRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    fun stored(dialogKey: String): SharedMedia? = telegram.memory["shared:$dialogKey"]

    fun remember(dialogKey: String, shared: SharedMedia) {
        telegram.memory["shared:$dialogKey"] = shared
    }

    suspend fun counts(dialogKey: String): Map<SharedTab, Int> {
        val tabs = MESSAGE_TABS
        val counters = telegram.client().invoke(GetSearchCounters(peer = registry.peer(dialogKey), filters = tabs.map { it.filter() }))
        val counts = counters.associate { it.filter to it.count }
        return tabs.mapNotNull { tab -> counts[tab.filter()]?.takeIf { it > 0 }?.let { tab to it } }.toMap()
    }

    suspend fun page(dialogKey: String, tab: SharedTab, offsetId: Int = 0, limit: Int = tab.pageSize): List<MessageItem> {
        val search = Search(
            peer = registry.peer(dialogKey),
            q = "",
            filter = tab.filter(),
            minDate = 0,
            maxDate = 0,
            offsetId = offsetId,
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

    suspend fun common(userKey: String, maxId: Long = 0, limit: Int = SharedTab.Groups.pageSize): List<DialogItem> {
        val chats = when (val result = telegram.client().invoke(GetCommonChats(userId = registry.user(userKey), maxId = maxId, limit = limit))) {
            is cn.ellerotta.ktproto.api.types.messages.Chats -> result.chats
            is cn.ellerotta.ktproto.api.types.messages.ChatsSlice -> result.chats
        }
        registry.rememberChats(chats)
        val now = System.currentTimeMillis() / 1000
        return chats.mapNotNull { chat ->
            when (chat) {
                is Channel -> registry.dialogOf(PeerChannel(chat.id), emptyMap(), mapOf(chat.id to chat), now)
                is Chat -> registry.dialogOf(PeerChat(chat.id), emptyMap(), mapOf(chat.id to chat), now)
                else -> null
            }
        }
    }

    private fun SharedTab.filter(): TypeMessagesFilter = when (this) {
        SharedTab.Media -> InputMessagesFilterPhotoVideo
        SharedTab.Files -> InputMessagesFilterDocument
        SharedTab.Links -> InputMessagesFilterUrl
        SharedTab.Music -> InputMessagesFilterMusic
        SharedTab.Voice -> InputMessagesFilterRoundVoice
        SharedTab.Gifs -> InputMessagesFilterGif
        SharedTab.Groups -> error("the chats in common are not messages")
    }

    private companion object {
        val MESSAGE_TABS = SharedTab.entries.filter { it != SharedTab.Groups }
    }
}
