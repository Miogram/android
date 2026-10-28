package app.miogram.data.telegram

import cn.ellerotta.core.model.ChatReactions
import cn.ellerotta.core.model.PeerReaction
import cn.ellerotta.core.model.PeerReactions
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.ReactionArt
import cn.ellerotta.ktproto.api.functions.channels.GetFullChannel
import cn.ellerotta.ktproto.api.functions.messages.GetFullChat
import cn.ellerotta.ktproto.api.functions.messages.GetMessageReactionsList
import cn.ellerotta.ktproto.api.functions.messages.GetUnreadReactions
import cn.ellerotta.ktproto.api.functions.messages.ReadReactions
import cn.ellerotta.ktproto.api.functions.messages.GetRecentReactions
import cn.ellerotta.ktproto.api.functions.messages.GetTopReactions
import cn.ellerotta.ktproto.api.functions.messages.SendReaction
import cn.ellerotta.ktproto.api.functions.users.GetUsers
import cn.ellerotta.ktproto.api.types.ChannelFull
import cn.ellerotta.ktproto.api.types.ChatFull
import cn.ellerotta.ktproto.api.types.InputUserSelf
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.messages.Reactions
import cn.ellerotta.ktproto.api.types.messages.TypeReactions
import cn.ellerotta.ktproto.tl.TlFunction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ReactionsRepository @Inject constructor(private val telegram: TelegramClientHolder, private val appConfig: AppConfigRepository) {
    private val registry get() = telegram.peers
    private val lock = Mutex()
    private var availableHash = 0
    private var available: List<ReactionArt> = emptyList()
    private val lists = HashMap<String, Pair<Long, List<Reaction>>>()

    suspend fun catalog(): List<ReactionArt> = lock.withLock {
        telegram.availableReactions(availableHash)?.let { (hash, list) ->
            availableHash = hash
            available = list
        }
        available
    }

    suspend fun top(): List<Reaction> = cachedList("top") { hash -> GetTopReactions(LIST_LIMIT, hash) }

    suspend fun recent(): List<Reaction> = cachedList("recent") { hash -> GetRecentReactions(LIST_LIMIT, hash) }

    private suspend fun cachedList(name: String, call: (Long) -> TlFunction<TypeReactions>): List<Reaction> {
        val hash = lock.withLock { lists[name]?.first ?: 0L }
        val result = telegram.client().invoke(call(hash))
        return lock.withLock {
            if (result is Reactions) lists[name] = result.hash to result.reactions.mapNotNull(::reactionOf)
            lists[name]?.second.orEmpty()
        }
    }

    suspend fun premium(): Boolean = telegram.selfPremium()

    suspend fun maxOwn(premium: Boolean): Int = appConfig.config().maxOwnReactions(premium)

    suspend fun uniqueMax(): Int = appConfig.config().uniqueReactions

    suspend fun chatReactions(dialogKey: String): ChatReactions = when (val peer = peerOfKey(dialogKey)) {
        is PeerChannel -> {
            val full = telegram.client().invoke(GetFullChannel(registry.channel(dialogKey))).fullChat as? ChannelFull
            chatReactionsOf(full?.availableReactions, full?.reactionsLimit, full?.paidReactionsAvailable == true)
        }
        is PeerChat -> {
            val full = telegram.client().invoke(GetFullChat(peer.chatId)).fullChat as? ChatFull
            chatReactionsOf(full?.availableReactions, full?.reactionsLimit)
        }
        else -> ChatReactions(custom = true)
    }

    suspend fun defaultReaction(): Reaction? {
        telegram.connect()
        return telegram.defaultReaction?.let(::reactionOf)
    }

    suspend fun react(dialogKey: String, messageId: Int, reactions: List<Reaction>, addToRecent: Boolean = false, big: Boolean = false) {
        telegram.client().invoke(
            SendReaction(
                big = big,
                addToRecent = addToRecent,
                peer = registry.peer(dialogKey),
                msgId = messageId,
                reaction = reactions.map(::inputReactionOf).ifEmpty { null },
            ),
        )
    }

    suspend fun reactors(dialogKey: String, messageId: Int, offset: String? = null, limit: Int = REACTORS_PAGE): PeerReactions {
        val list = telegram.client().invoke(GetMessageReactionsList(peer = registry.peer(dialogKey), id = messageId, offset = offset, limit = limit))
        registry.rememberUsers(list.users)
        registry.rememberChats(list.chats)
        val reactions = list.reactions.mapNotNull { peer ->
            val reaction = reactionOf(peer.reaction) ?: return@mapNotNull null
            PeerReaction(registry.reactorOf(peer.peerId) ?: return@mapNotNull null, peerKey(peer.peerId), reaction, peer.date)
        }
        return PeerReactions(reactions, list.count, list.nextOffset?.takeIf { it.isNotEmpty() })
    }

    suspend fun unread(dialogKey: String, after: Int = 0): List<Int> {
        val result = telegram.client().invoke(GetUnreadReactions(peer = registry.peer(dialogKey), offsetId = 0, addOffset = 0, limit = UNREAD_PAGE, maxId = 0, minId = after))
        return messagesOf(result).mapNotNull { (it as? Message)?.id }.filter { it > after }.sorted()
    }

    suspend fun readAll(dialogKey: String) {
        val peer = registry.peer(dialogKey)
        do {
            val affected = telegram.client().invoke(ReadReactions(peer))
        } while (affected.offset > 0)
    }

    private companion object {
        const val LIST_LIMIT = 50

        const val REACTORS_PAGE = 50

        const val UNREAD_PAGE = 100
    }
}

internal suspend fun TelegramClientHolder.selfPremium(): Boolean =
    client().invoke(GetUsers(listOf(InputUserSelf))).filterIsInstance<User>().firstOrNull()?.premium == true
