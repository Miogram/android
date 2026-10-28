package cn.ellerotta.core.model

data class TypedMention(val start: Int, val end: Int, val query: String)

fun mentionAt(text: String, cursor: Int): TypedMention? {
    if (cursor !in 1..text.length) return null
    var from = cursor
    while (from > 0 && text[from - 1].nameChar) from--
    val start = from - 1
    if (start < 0 || text[start] != '@' || cursor - from > MENTION_MAX) return null
    if (start > 0 && !text[start - 1].isWhitespace()) return null
    var end = cursor
    while (end < text.length && text[end].nameChar) end++
    return TypedMention(start, end, text.substring(from, cursor))
}

private val Char.nameChar: Boolean get() = this == '_' || isLetterOrDigit()

private const val MENTION_MAX = 32

fun mentionMatches(member: ChatMember, query: String): Boolean {
    if (query.isEmpty()) return true
    if (member.username?.startsWith(query, ignoreCase = true) == true) return true
    return member.name.split(' ').any { it.startsWith(query, ignoreCase = true) }
}

fun mentionSuggestions(members: List<ChatMember>, query: String, speakers: List<String>, limit: Int = MENTIONS_SHOWN): List<ChatMember> {
    val rank = speakers.withIndex().associate { (index, key) -> key to index }
    return members.asSequence()
        .filter { !it.self && mentionMatches(it, query) }
        .distinctBy { it.userKey }
        .sortedBy { rank[it.userKey] ?: Int.MAX_VALUE }
        .take(limit)
        .toList()
}

const val MENTIONS_SHOWN = 50

fun FormattedText.withMention(typed: TypedMention, member: ChatMember): Pair<FormattedText, Int> {
    val username = member.username
    val piece = if (username != null) "@$username" else member.firstName?.ifBlank { null } ?: member.name
    val spaced = typed.end < text.length && text[typed.end].isWhitespace()
    val replaced = replaced(typed.start, typed.end, if (spaced) piece else "$piece ")
    val userId = member.userKey.removePrefix("u").toLongOrNull()
    val placed = if (username == null && userId != null) {
        FormattedText(replaced.text, normalizeEntities(replaced.text, replaced.entities + TextEntity(typed.start, piece.length, EntityType.MentionName(userId))))
    } else {
        replaced
    }
    return placed to typed.start + piece.length + 1
}
