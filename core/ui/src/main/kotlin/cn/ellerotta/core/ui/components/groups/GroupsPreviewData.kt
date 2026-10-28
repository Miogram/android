package cn.ellerotta.core.ui.components.groups

import cn.ellerotta.core.model.ChatMember
import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.model.ForumTopic
import cn.ellerotta.core.model.InviteLink
import cn.ellerotta.core.model.InviteLinks
import cn.ellerotta.core.model.JoinRequest
import cn.ellerotta.core.model.MemberRole
import cn.ellerotta.core.model.MissingInvitee
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.Preview
import cn.ellerotta.core.ui.components.common.previewPhoto

internal object GroupsPreviewData {
    const val NOW = 1_790_000_000

    const val TITLE = "Film Photography Club"

    val avatars: Map<String, ByteArray> = mapOf(
        "photo_alex" to previewPhoto(start = 0xFF5B8DEF.toInt(), end = 0xFF7ED67E.toInt()),
        "photo_maria" to previewPhoto(start = 0xFFE86A9A.toInt(), end = 0xFFFFB85C.toInt()),
        "photo_nina" to previewPhoto(start = 0xFF8C86FF.toInt(), end = 0xFF3FD8D8.toInt()),
        "photo_omar" to previewPhoto(start = 0xFFFF885E.toInt(), end = 0xFF8C86FF.toInt()),
    )

    val members = listOf(
        ChatMember("u1001", "Alex Morgan", "photo_alex", role = MemberRole.Owner, presence = Presence.Online, self = true),
        ChatMember("u2002", "Maria Lopez", "photo_maria", role = MemberRole.Admin, rank = "Moderator", presence = Presence.Offline(NOW - 1500)),
        ChatMember("u3003", "Sam Lee", role = MemberRole.Admin, presence = Presence.Recently),
        ChatMember("u4004", "Nina Park", "photo_nina", presence = Presence.Offline(NOW - 30 * 3600)),
        ChatMember("u5005", "Omar Haddad", "photo_omar", presence = Presence.LastWeek),
        ChatMember("u6006", "Scan Helper", role = MemberRole.Member, presence = Presence.Bot, bot = true),
        ChatMember("u7007", "Lena Fischer with a rather long display name", presence = Presence.LongAgo),
    )

    val inviteLinks = InviteLinks(
        primary = InviteLink("https://t.me/+AbCdEf123456", primary = true, date = NOW - 86_400 * 200, usage = 342),
        others = listOf(
            InviteLink("https://t.me/+Newsletter42", title = "Newsletter", date = NOW - 86_400 * 3, expireDate = NOW + 86_400 * 7, usageLimit = 100, usage = 12),
            InviteLink("https://t.me/+ApprovalOnly7", title = "Meetup flyer", requestNeeded = true, date = NOW - 86_400, usage = 4, requested = 3),
            InviteLink("https://t.me/+QwErTy987654", date = NOW - 3600, usage = 0),
        ),
        revoked = listOf(
            InviteLink("https://t.me/+OldPoster2025", title = "Old poster", revoked = true, date = NOW - 86_400 * 400, usage = 230),
            InviteLink("https://t.me/+Expired00001", revoked = true, date = NOW - 86_400 * 30, expireDate = NOW - 86_400 * 2, usage = 7),
        ),
    )

    val joinRequests = listOf(
        JoinRequest("u8008", "Nina Park", "photo_nina", date = NOW - 3600, about = "Shooting on a rangefinder since 2019, mostly street"),
        JoinRequest("u9009", "Omar Haddad", "photo_omar", date = NOW - 86_400),
        JoinRequest("u1010", "Lena Fischer", date = NOW - 86_400 * 4, about = "Found you through the meetup flyer"),
    )

    val topics = listOf(
        ForumTopic(1, "General", date = NOW - 600, preview = Preview(text = "Welcome, introduce yourself!", sender = "Alex")),
        ForumTopic(12, "Announcements", iconColor = 0xFFD67E, unread = 3, pinned = true, date = NOW - 3600, preview = Preview(text = "Next photo walk is on Saturday", sender = "Maria")),
        ForumTopic(15, "Darkroom", iconColor = 0xCB86DB, unread = 18, mentions = 2, date = NOW - 7200, preview = Preview(text = "Which developer do you use for HP5?", sender = "Nina")),
        ForumTopic(21, "Gear swap", iconColor = 0x8EEE98, date = NOW - 86_400 * 2, preview = Preview(text = "Selling a 50 mm lens, barely used", sender = "Omar")),
        ForumTopic(30, "Contest 2025", iconColor = 0xFB6F5F, closed = true, date = NOW - 86_400 * 60, preview = Preview(text = "Thanks everyone, see you next year", sender = "Alex")),
    )

    val pickable = listOf(
        ContactItem("u1001", 1001, "Alex Morgan", Presence.Online, "photo_alex", mutual = true),
        ContactItem("u2002", 2002, "Maria Lopez", Presence.Offline(NOW - 1500), "photo_maria"),
        ContactItem("u4004", 4004, "Nina Park", Presence.Offline(NOW - 30 * 3600), "photo_nina"),
        ContactItem("u5005", 5005, "Omar Haddad", Presence.LastWeek, "photo_omar"),
        ContactItem("u7007", 7007, "Lena Fischer with a rather long display name", Presence.LongAgo),
    )

    val missing = listOf(MissingInvitee("u4004", "Nina Park"), MissingInvitee("u5005", "Omar Haddad", premiumWouldAllow = true))
}
