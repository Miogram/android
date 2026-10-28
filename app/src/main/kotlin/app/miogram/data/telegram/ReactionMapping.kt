package app.miogram.data.telegram

import cn.ellerotta.core.model.ChatReactions
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.ReactionArt
import cn.ellerotta.core.model.ReactionCount
import cn.ellerotta.core.model.Reactor
import cn.ellerotta.ktproto.api.functions.messages.GetAvailableReactions
import cn.ellerotta.ktproto.api.types.AvailableReaction
import cn.ellerotta.ktproto.api.types.ChatReactionsAll
import cn.ellerotta.ktproto.api.types.ChatReactionsNone
import cn.ellerotta.ktproto.api.types.ChatReactionsSome
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.MessageReactions
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.ReactionCustomEmoji
import cn.ellerotta.ktproto.api.types.ReactionEmoji
import cn.ellerotta.ktproto.api.types.ReactionEmpty
import cn.ellerotta.ktproto.api.types.ReactionPaid
import cn.ellerotta.ktproto.api.types.TypeChatReactions
import cn.ellerotta.ktproto.api.types.TypeDocument
import cn.ellerotta.ktproto.api.types.TypeReaction
import cn.ellerotta.ktproto.api.types.messages.AvailableReactions

internal fun reactionOf(reaction: TypeReaction): Reaction? = when (reaction) {
    is ReactionEmoji -> Reaction.Emoji(reaction.emoticon)
    is ReactionCustomEmoji -> Reaction.CustomEmoji(reaction.documentId)
    ReactionPaid -> Reaction.Paid
    ReactionEmpty -> null
}

internal fun inputReactionOf(reaction: Reaction): TypeReaction = when (reaction) {
    is Reaction.Emoji -> ReactionEmoji(reaction.emoji)
    is Reaction.CustomEmoji -> ReactionCustomEmoji(reaction.documentId)
    Reaction.Paid -> ReactionPaid
}

internal fun PeerRegistry.reactionsOf(reactions: MessageReactions?): List<ReactionCount> {
    if (reactions == null) return emptyList()
    val recent = reactions.recentReactions.orEmpty()
    return reactions.results.mapNotNull { result ->
        val reaction = reactionOf(result.reaction) ?: return@mapNotNull null
        val reactors = recent.filter { reactionOf(it.reaction) == reaction }.mapNotNull { peer ->
            val (key, id) = when (val p = peer.peerId) {
                is PeerUser -> "u${p.userId}" to p.userId
                is PeerChannel -> "ch${p.channelId}" to p.channelId
                is PeerChat -> "c${p.chatId}" to p.chatId
            }
            Reactor(id, peerNames[key] ?: return@mapNotNull null, peerAvatars[key])
        }
        ReactionCount(reaction, result.count, chosen = result.chosenOrder != null, recent = reactors, order = result.chosenOrder)
    }
}

internal fun chatReactionsOf(reactions: TypeChatReactions?, limit: Int? = null, paid: Boolean = false): ChatReactions = when (reactions) {
    is ChatReactionsAll -> ChatReactions(all = true, custom = reactions.allowCustom, limit = limit, paid = paid)
    is ChatReactionsSome -> ChatReactions(all = false, some = reactions.reactions.mapNotNull(::reactionOf), limit = limit, paid = paid)
    ChatReactionsNone, null -> ChatReactions.None.copy(paid = paid)
}

internal suspend fun TelegramClientHolder.availableReactions(hash: Int): Pair<Int, List<ReactionArt>>? {
    val result = client().invoke(GetAvailableReactions(hash)) as? AvailableReactions ?: return null
    return result.hash to result.reactions.filter { !it.inactive }.map { peers.artOf(it) }
}

private fun PeerRegistry.artOf(reaction: AvailableReaction): ReactionArt {
    fun item(doc: TypeDocument?) = (doc as? Document)?.let { documentOf(null, it, AvailableReactionsOrigin) }
    return ReactionArt(
        reaction = Reaction.Emoji(reaction.reaction),
        title = reaction.title,
        premium = reaction.premium,
        still = item(reaction.staticIcon),
        appear = item(reaction.appearAnimation),
        select = item(reaction.selectAnimation),
        center = item(reaction.centerIcon),
        around = item(reaction.aroundAnimation),
        activate = item(reaction.activateAnimation),
        effect = item(reaction.effectAnimation),
    )
}
