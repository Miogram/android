package cn.ellerotta.core.model

enum class SearchFilter { All, Media, Links, Files, Music, Voice }

data class SearchCursor(val rate: Int, val peerKey: String, val messageId: Int)

data class MessageHit(val dialog: DialogItem, val messageId: Int) {
    val key: String get() = "${dialog.key}_$messageId"
}

data class MessageHits(val hits: List<MessageHit>, val next: SearchCursor? = null)

const val RECENT_SEARCHES = 10

fun List<String>.withRecentSearch(query: String): List<String> {
    val text = query.trim()
    if (text.isEmpty()) return this
    return (listOf(text) + filter { !it.equals(text, ignoreCase = true) }).take(RECENT_SEARCHES)
}
