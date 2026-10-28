package cn.ellerotta.core.model

enum class Membership {
    Member,

    CanJoin,

    RequestToJoin,

    RequestSent,

    Removed,
}

data class ChatInfo(
    val key: String,
    val membership: Membership = Membership.Member,
    val basicGroup: Boolean = false,
    val creator: Boolean = false,
    val admin: Boolean = false,
    val canInvite: Boolean = false,
    val canBan: Boolean = false,
    val canManageTopics: Boolean = false,
    val canPin: Boolean = false,
    val canChangeInfo: Boolean = false,
)

data class MemberCounts(val members: Int?, val online: Int?)

data class InviteLink(
    val link: String,
    val title: String? = null,
    val primary: Boolean = false,
    val revoked: Boolean = false,
    val requestNeeded: Boolean = false,
    val date: Int = 0,
    val expireDate: Int? = null,
    val usageLimit: Int? = null,
    val usage: Int = 0,
    val requested: Int = 0,
)

data class InviteLinks(val primary: InviteLink?, val others: List<InviteLink> = emptyList(), val revoked: List<InviteLink> = emptyList())

data class NewInviteLink(val title: String = "", val expiresIn: Int? = null, val usageLimit: Int? = null, val requestNeeded: Boolean = false)

data class JoinRequest(val userKey: String, val name: String, val avatarKey: String? = null, val date: Int = 0, val about: String? = null)

data class JoinRequestsPage(val requests: List<JoinRequest>, val total: Int)

enum class MemberRole { Member, Admin, Owner }

data class ChatMember(
    val userKey: String,
    val name: String,
    val avatarKey: String? = null,
    val role: MemberRole = MemberRole.Member,
    val rank: String? = null,
    val presence: Presence = Presence.Unknown,
    val bot: Boolean = false,
    val self: Boolean = false,
    val username: String? = null,
    val firstName: String? = null,
)

data class MembersPage(val members: List<ChatMember>, val total: Int)

data class ForumTopic(
    val id: Int,
    val title: String,
    val iconColor: Int = DEFAULT_TOPIC_COLOR,
    val iconEmojiId: Long? = null,
    val unread: Int = 0,
    val mentions: Int = 0,
    val pinned: Boolean = false,
    val closed: Boolean = false,
    val hidden: Boolean = false,
    val topMessage: Int = 0,
    val date: Int = 0,
    val preview: Preview = Preview(),
) {
    val general: Boolean get() = id == GENERAL_TOPIC

    companion object {
        const val GENERAL_TOPIC = 1

        const val DEFAULT_TOPIC_COLOR = 0x6FB9F0

        val COLORS = listOf(0x6FB9F0, 0xFFD67E, 0xCB86DB, 0x8EEE98, 0xFF93B2, 0xFB6F5F)
    }
}

data class ForumTopicsPage(val topics: List<ForumTopic>, val total: Int)
