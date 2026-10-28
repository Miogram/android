package cn.ellerotta.core.model

data class FolderLimits(val folders: Int = DEFAULT_FOLDERS, val chats: Int = DEFAULT_CHATS, val premium: Boolean = false) {
    fun canAddFolder(count: Int): Boolean = count < folders

    companion object {
        const val DEFAULT_FOLDERS = 10
        const val PREMIUM_FOLDERS = 20
        const val DEFAULT_CHATS = 100
        const val PREMIUM_CHATS = 200
    }
}

data class SuggestedFolder(val folder: ChatFolder, val description: String)

val List<ChatFolder>.real: List<ChatFolder> get() = filter { !it.isAll }

fun ChatFolder.savable(): Boolean = title.isNotBlank() && (explicit.isNotEmpty() || hasRules)

fun ChatFolder.includingOnly(keys: Collection<String>): ChatFolder {
    val kept = keys.toSet()
    return copy(pinned = pinned.filter { it in kept }, include = keys.filter { it !in pinned }.toCollection(LinkedHashSet()), exclude = exclude - kept)
}

fun ChatFolder.excluding(keys: Collection<String>): ChatFolder {
    val dropped = keys.toSet()
    return copy(exclude = keys.toCollection(LinkedHashSet()), include = include - dropped, pinned = pinned.filter { it !in dropped })
}

val ChatFolder.named: Int get() = explicit.size

fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

val FolderEmojis: List<String> = listOf("📁", "💬", "👤", "👥", "📢", "🤖", "⭐", "🔥", "🏠", "💼", "🎮", "🎵", "📚", "🚀", "🛒", "🔒")
