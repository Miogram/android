package cn.ellerotta.core.model

data class ContactItem(
    val key: String,
    val userId: Long,
    val name: String,
    val presence: Presence = Presence.LongAgo,
    val avatarKey: String? = null,
    val phone: String? = null,
    val username: String? = null,
    val mutual: Boolean = false,
    val contact: Boolean = true,
) {
    fun matches(query: String): Boolean {
        val q = query.trim().removePrefix("@")
        if (q.isEmpty()) return true
        val number = q.filterNot { it == '+' || it == ' ' || it == '-' }
        return name.startsWith(q, ignoreCase = true) ||
            name.split(' ').any { it.startsWith(q, ignoreCase = true) } ||
            username?.startsWith(q, ignoreCase = true) == true ||
            (number.length >= 3 && number.all { it.isDigit() } && phone?.contains(number) == true)
    }
}

fun List<ContactItem>.sortedByPresence(): List<ContactItem> =
    sortedWith(compareByDescending<ContactItem> { presenceRank(it.presence) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

fun presenceRank(presence: Presence): Long = when (presence) {
    Presence.Online -> Long.MAX_VALUE
    is Presence.Offline -> presence.wasOnline.toLong()
    Presence.Recently -> -100
    Presence.LastWeek -> -101
    Presence.LastMonth -> -102
    Presence.Bot -> -104
    else -> -103
}
