package cn.ellerotta.core.model

sealed interface Reaction {
    data class Emoji(val emoji: String) : Reaction

    data class CustomEmoji(val documentId: Long) : Reaction

    data object Paid : Reaction
}

data class Reactor(val peerId: Long, val name: String, val avatarKey: String? = null)

data class ReactionCount(val reaction: Reaction, val count: Int, val chosen: Boolean = false, val recent: List<Reactor> = emptyList(), val order: Int? = null) {
    val showsReactors: Boolean get() = count in 1..MAX_REACTOR_AVATARS && recent.size >= count
}

data class PeerReaction(val reactor: Reactor, val key: String, val reaction: Reaction, val date: Int)

data class PeerReactions(val list: List<PeerReaction>, val count: Int, val next: String?)

data class ReactorsState(val messageId: Int, val total: Int, val list: List<PeerReaction> = emptyList(), val count: Int = 0, val next: String? = null, val loading: Boolean = true)

const val MAX_REACTOR_AVATARS = 3

data class ChatReactions(val all: Boolean = true, val custom: Boolean = false, val some: List<Reaction> = emptyList(), val limit: Int? = null, val paid: Boolean = false) {
    fun allowed(available: List<Reaction>): List<Reaction> = if (all) available else some

    fun allows(reaction: Reaction, premium: Boolean): Boolean = when (reaction) {
        Reaction.Paid -> false
        is Reaction.CustomEmoji -> premium && if (all) custom else reaction in some
        is Reaction.Emoji -> all || reaction in some
    }

    companion object {
        val None = ChatReactions(all = false)
    }
}

data class ReactionArt(
    val reaction: Reaction.Emoji,
    val title: String,
    val premium: Boolean = false,
    val still: MediaItem? = null,
    val appear: MediaItem? = null,
    val select: MediaItem? = null,
    val center: MediaItem? = null,
    val around: MediaItem? = null,
    val activate: MediaItem? = null,
    val effect: MediaItem? = null,
)

data class ChatReactionState(
    val allowed: List<Reaction> = emptyList(),
    val default: Reaction? = null,
    val art: Map<Reaction, ReactionArt> = emptyMap(),
    val recent: List<Reaction> = emptyList(),
    val custom: Boolean = false,
    val premium: Boolean = false,
    val maxOwn: Int = 1,
    val limit: Int? = null,
) {
    fun offeredFor(current: List<ReactionCount>): ChatReactionState {
        val present = current.filter { it.count > 0 }.map { it.reaction }.toSet()
        if (limit == null || present.size < limit) return this
        return copy(allowed = allowed.filter { it in present })
    }
}

fun exceedsReactionLimit(before: List<ReactionCount>, after: List<ReactionCount>, limit: Int?): Boolean {
    if (limit == null) return false
    val kinds = after.count { it.count > 0 }
    return kinds > limit && kinds > before.count { it.count > 0 }
}

fun pickerReactions(top: List<Reaction>, available: List<ReactionArt>, chat: ChatReactions, premium: Boolean): List<Reaction> {
    val usable = available.filter { premium || !it.premium }.map { it.reaction }
    val known = usable.toSet()
    fun ok(reaction: Reaction) = chat.allows(reaction, premium) && (reaction !is Reaction.Emoji || reaction in known)
    val own = if (chat.all) emptyList() else chat.some.filterIsInstance<Reaction.CustomEmoji>()
    return (top + usable + own).filter(::ok).distinct()
}

fun ownReactionsAfter(current: List<ReactionCount>, reaction: Reaction, max: Int = 1): List<Reaction> {
    val own = current.filter { it.chosen && it.reaction != Reaction.Paid }.sortedBy { it.order ?: Int.MAX_VALUE }.map { it.reaction }
    return when {
        reaction in own -> own - reaction
        max <= 1 -> listOf(reaction)
        else -> (own + reaction).takeLast(max)
    }
}

fun withOwnReactions(current: List<ReactionCount>, own: List<Reaction>): List<ReactionCount> {
    val changed = current.mapNotNull { count ->
        if (count.reaction == Reaction.Paid) return@mapNotNull count
        val mine = count.reaction in own
        val order = own.indexOf(count.reaction).takeIf { it >= 0 }
        when {
            mine == count.chosen -> if (mine) count.copy(order = order) else count
            mine -> count.copy(count = count.count + 1, chosen = true, order = order)
            count.count <= 1 -> null
            else -> count.copy(count = count.count - 1, chosen = false, order = null)
        }
    }
    val added = own.filter { reaction -> changed.none { it.reaction == reaction } }.map { ReactionCount(it, 1, chosen = true, order = own.indexOf(it)) }
    return changed + added
}

fun reactionsUpdated(previous: List<ReactionCount>, fresh: List<ReactionCount>, min: Boolean): List<ReactionCount> {
    if (!min) return fresh
    val chosen = previous.filter { it.chosen }.associate { it.reaction to it.order }
    return fresh.map { if (it.reaction in chosen) it.copy(chosen = true, order = chosen[it.reaction]) else it.copy(chosen = false, order = null) }
}
