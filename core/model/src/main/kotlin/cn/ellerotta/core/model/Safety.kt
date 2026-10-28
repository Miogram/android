package cn.ellerotta.core.model

data class BlockedUser(
    val key: String,
    val name: String,
    val avatarKey: String? = null,
    val username: String? = null,
    val date: Int = 0,
)

data class BlockedList(val users: List<BlockedUser> = emptyList(), val total: Int = 0, val read: Int = 0, val loaded: Boolean = false) {
    val more: Boolean get() = read < total

    fun plus(page: BlockedList) = BlockedList((users + page.users).distinctBy { it.key }, page.total, read + page.read, loaded = true)

    fun without(key: String) = if (users.none { it.key == key }) this else copy(users = users.filter { it.key != key }, total = (total - 1).coerceAtLeast(0), read = (read - 1).coerceAtLeast(0))
}

sealed interface ReportTarget {
    data class Peer(val key: String) : ReportTarget

    data class Messages(val key: String, val ids: List<Int>) : ReportTarget

    data class Story(val key: String, val id: Int) : ReportTarget
}

enum class PeerReportReason { Spam, Violence, Pornography, ChildAbuse, IllegalDrugs, PersonalDetails, Fake, Other }

data class ReportOption(val id: String, val text: String? = null, val reason: PeerReportReason? = null)

sealed interface ReportStep {
    data class Choose(val title: String?, val options: List<ReportOption>) : ReportStep

    data class Comment(val optional: Boolean, val option: String) : ReportStep

    data object Done : ReportStep
}

data class ReportState(val target: ReportTarget, val step: ReportStep? = null, val sending: Boolean = false)

data class PeerBar(val reportSpam: Boolean = false, val addContact: Boolean = false, val block: Boolean = false) {
    val shown: Boolean get() = reportSpam || addContact || block
}
