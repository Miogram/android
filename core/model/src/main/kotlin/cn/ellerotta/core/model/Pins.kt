package cn.ellerotta.core.model

data class PinnedMessages(val messages: List<MessageItem> = emptyList(), val current: Int = 0, val hiddenUpTo: Int = 0) {
    val shown: MessageItem? get() = if (hidden) null else messages.getOrNull(current.coerceIn(0, (messages.size - 1).coerceAtLeast(0)))

    val number: Int get() = messages.size - current.coerceIn(0, (messages.size - 1).coerceAtLeast(0))

    val index: Int get() = (number - 1).coerceAtLeast(0)

    val hidden: Boolean get() = messages.isNotEmpty() && messages.first().id <= hiddenUpTo

    val ids: Set<Int> get() = messages.mapTo(HashSet()) { it.id }

    fun next(): PinnedMessages = if (messages.size < 2) this else copy(current = (current + 1) % messages.size)

    fun showing(id: Int): PinnedMessages = messages.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { copy(current = it) } ?: this

    fun hide(): PinnedMessages = copy(hiddenUpTo = messages.firstOrNull()?.id ?: hiddenUpTo)

    fun without(ids: Collection<Int>): PinnedMessages {
        val kept = messages.filter { it.id !in ids }
        val shownId = messages.getOrNull(current)?.id
        val index = kept.indexOfFirst { it.id == shownId }.takeIf { it >= 0 } ?: current.coerceAtMost((kept.size - 1).coerceAtLeast(0))
        return PinnedMessages(kept, index, hiddenUpTo)
    }

    fun reloaded(list: List<MessageItem>): PinnedMessages {
        val newer = (list.firstOrNull()?.id ?: 0) > (messages.firstOrNull()?.id ?: 0)
        val shownId = messages.getOrNull(current)?.id
        val index = if (newer) 0 else list.indexOfFirst { it.id == shownId }.takeIf { it >= 0 } ?: 0
        return PinnedMessages(list, index, hiddenUpTo)
    }
}
