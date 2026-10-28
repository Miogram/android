package cn.ellerotta.core.ui.components.chats

import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.Draft
import cn.ellerotta.core.model.FolderTab
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageHit
import cn.ellerotta.core.model.NOTIFICATIONS_KEY
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.PeerStories
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.Preview
import cn.ellerotta.core.model.ProfileInfo
import cn.ellerotta.core.model.ServiceKind
import cn.ellerotta.core.model.StoryItem
import cn.ellerotta.core.model.StoryRing
import cn.ellerotta.core.ui.components.appbar.TopBarStories
import cn.ellerotta.core.ui.components.common.previewPhoto

internal object ChatsPreviewData {
    const val NOW = 1_792_065_600_000L
    private val nowSeconds = (NOW / 1000).toInt()

    val me = ProfileInfo(id = 1, name = "Alex Morgan", phone = "447700900123", username = "alexm", avatarKey = "photo_me")

    val avatars: Map<String, ByteArray> = mapOf(
        "photo_me" to previewPhoto(start = 0xFF5B8DEF.toInt(), end = 0xFF7E57C2.toInt()),
        "photo_alice" to previewPhoto(start = 0xFFFFB74D.toInt(), end = 0xFFE86A9A.toInt()),
        "photo_news" to previewPhoto(start = 0xFF26A69A.toInt(), end = 0xFF1E88E5.toInt()),
        "photo_mom" to previewPhoto(start = 0xFFAED581.toInt(), end = 0xFF43A047.toInt()),
    )

    val alice = DialogItem(
        key = "u101",
        title = "Alice Johnson",
        preview = Preview(text = "Are we still on for lunch tomorrow? I found a new place near the river"),
        unread = 3,
        avatarKey = "photo_alice",
        peerId = 101,
        date = nowSeconds - 120,
        online = true,
        presence = Presence.Online,
        contact = true,
        story = StoryRing.Unread,
    )

    val group = DialogItem(
        key = "c202",
        title = "Design team",
        preview = Preview(text = "Pushed the new icons, have a look", sender = "Mark"),
        unread = 12,
        mentions = 1,
        peerId = 202,
        kind = PeerKind.Group,
        date = nowSeconds - 600,
        typing = "Mark",
    )

    val channel = DialogItem(
        key = "ch303",
        title = "Daily news",
        preview = Preview(text = "Morning briefing: weather, markets and what to watch today", media = MediaKind.Photo),
        unread = 1240,
        avatarKey = "photo_news",
        peerId = 303,
        kind = PeerKind.Channel,
        date = nowSeconds - 3_600,
        muted = true,
        verified = true,
        story = StoryRing.Read,
    )

    val pinned = DialogItem(
        key = "u404",
        title = "Mom",
        preview = Preview(text = "Call me when you get home"),
        avatarKey = "photo_mom",
        peerId = 404,
        date = nowSeconds - 86_400,
        pinned = true,
        contact = true,
        outgoing = true,
        read = true,
        presence = Presence.Recently,
        story = StoryRing.CloseFriends,
    )

    val draft = DialogItem(
        key = "u505",
        title = "Bob Carter",
        preview = Preview(text = "Sounds good"),
        peerId = 505,
        date = nowSeconds - 7_200,
        contact = true,
        premium = true,
        presence = Presence.Offline(nowSeconds - 1_800),
        draft = Draft(FormattedText("I'll bring the slides and the")),
    )

    val saved = DialogItem(
        key = "u1",
        title = "Alex Morgan",
        preview = Preview(text = "Packing list: passport, charger, umbrella"),
        isSaved = true,
        peerId = 1,
        kind = PeerKind.Saved,
        date = nowSeconds - 172_800,
        pinned = true,
    )

    val sent = DialogItem(
        key = "u606",
        title = "Kate Wilson",
        preview = Preview(media = MediaKind.Voice),
        peerId = 606,
        date = nowSeconds - 259_200,
        contact = true,
        outgoing = true,
        unreadMark = true,
        presence = Presence.LastWeek,
    )

    val notifications = DialogItem(
        key = NOTIFICATIONS_KEY,
        title = "Telegram",
        preview = Preview(text = "Login code: 52814. Do not give this code to anyone"),
        unread = 1,
        peerId = 777000,
        date = nowSeconds - 345_600,
        verified = true,
    )

    val joined = DialogItem(
        key = "c707",
        title = "Book club",
        preview = Preview(service = ServiceKind.Joined, sender = "Leo"),
        peerId = 707,
        kind = PeerKind.Group,
        date = nowSeconds - 604_800,
    )

    val dialogs = listOf(saved, pinned, alice, group, channel, draft, sent, notifications, joined)

    val archived = listOf(
        DialogItem(
            key = "c808",
            title = "Old project",
            preview = Preview(text = "Final invoice attached", sender = "Nina", media = MediaKind.File),
            unread = 2,
            peerId = 808,
            kind = PeerKind.Group,
            date = nowSeconds - 1_209_600,
            archived = true,
        ),
        DialogItem(
            key = "ch909",
            title = "Deals and offers",
            preview = Preview(text = "Weekend sale starts now"),
            peerId = 909,
            kind = PeerKind.Channel,
            date = nowSeconds - 2_419_200,
            muted = true,
            archived = true,
            story = StoryRing.Unread,
        ),
    )

    private val personal = ChatFolder(id = 2, title = "Personal", contacts = true, nonContacts = true)
    private val work = ChatFolder(id = 3, title = "Work", groups = true)
    private val channels = ChatFolder(id = 4, title = "Channels", broadcasts = true)

    val tabs: List<FolderTab> = listOf(ChatFolder.all("All"), personal, work, channels).map { FolderTab(it, it.pick(dialogs)) }

    val singleTab: List<FolderTab> = listOf(FolderTab(ChatFolder.all("All"), dialogs))

    val emptyTabs: List<FolderTab> = listOf(FolderTab(ChatFolder.all("All"), emptyList()))

    private fun story(id: Int, start: Int, end: Int, closeFriends: Boolean = false) = StoryItem(
        id = id,
        date = nowSeconds - 3_600 * id,
        expireDate = nowSeconds + 80_000,
        media = MediaItem(
            key = "story_$id",
            fileName = "story_$id.jpg",
            size = null,
            dcId = 2,
            isPhoto = true,
            kind = MediaKind.Photo,
            thumb = previewPhoto(width = 45, height = 70, start = start, end = end),
        ),
        closeFriends = closeFriends,
    )

    val storyPeers = listOf(
        PeerStories(key = alice.key, name = alice.title, avatarKey = "photo_alice", peerId = alice.peerId, stories = listOf(story(1, 0xFFFFB74D.toInt(), 0xFFE53935.toInt()))),
        PeerStories(key = pinned.key, name = pinned.title, avatarKey = "photo_mom", peerId = pinned.peerId, stories = listOf(story(2, 0xFF81C784.toInt(), 0xFF00897B.toInt(), closeFriends = true))),
        PeerStories(key = "u808", name = "Leo Park", peerId = 808, stories = listOf(StoryItem(id = 3, date = nowSeconds - 900, expireDate = nowSeconds + 80_000))),
        PeerStories(key = channel.key, name = channel.title, avatarKey = "photo_news", peerId = channel.peerId, stories = listOf(story(4, 0xFF4FC3F7.toInt(), 0xFF3949AB.toInt())), maxReadId = 4),
        PeerStories(key = sent.key, name = sent.title, peerId = sent.peerId, stories = listOf(story(5, 0xFFBA68C8.toInt(), 0xFF5E35B1.toInt())), maxReadId = 5),
    )

    val myStories = PeerStories(key = me.key(), name = me.name, avatarKey = me.avatarKey, peerId = me.id, stories = listOf(story(6, 0xFF5B8DEF.toInt(), 0xFFE86A9A.toInt())), maxReadId = 6, self = true)

    val myStoriesEmpty = PeerStories(key = me.key(), name = me.name, avatarKey = me.avatarKey, peerId = me.id, self = true)

    val hiddenStories = listOf(
        PeerStories(key = "ch909", name = "Deals and offers", peerId = 909, stories = listOf(story(7, 0xFFFFD54F.toInt(), 0xFFF4511E.toInt())), hidden = true),
        PeerStories(key = "u810", name = "Nina Brooks", peerId = 810, stories = listOf(story(8, 0xFF90A4AE.toInt(), 0xFF455A64.toInt())), maxReadId = 8, hidden = true),
    )

    fun topBarStories(self: PeerStories? = myStories, peers: List<PeerStories> = storyPeers, canPost: Boolean = true) = TopBarStories(
        peers = peers,
        self = self,
        avatars = avatars,
        onExpand = {},
        onOpenStories = {},
        onAddStory = if (canPost) ({}) else null,
        onHideStories = {},
    )

    val hits: List<MessageHit> = listOf(
        MessageHit(alice.copy(preview = Preview(text = "Can you send me the new design before lunch?"), date = nowSeconds - 300, unread = 0, story = StoryRing.None), 41),
        MessageHit(group.copy(preview = Preview(text = "Design review moved to 3 pm", sender = "Mark"), date = nowSeconds - 5_400, unread = 0, mentions = 0, typing = null), 977),
        MessageHit(channel.copy(preview = Preview(text = "Design week: five long articles worth reading"), date = nowSeconds - 90_000, unread = 0, story = StoryRing.None), 2_310),
    )

    val linkHits: List<MessageHit> = listOf(
        MessageHit(alice.copy(preview = Preview(text = "The place near the river: https://example.com/riverside"), date = nowSeconds - 1_500, unread = 0, story = StoryRing.None), 52),
        MessageHit(group.copy(preview = Preview(text = "Icon sources: https://example.com/icons", sender = "Mark"), date = nowSeconds - 20_000, unread = 0, mentions = 0, typing = null), 940),
    )

    val recent = listOf("lunch", "#kotlin", "invoice")

    val topPeers = listOf(alice.copy(story = StoryRing.None), pinned.copy(story = StoryRing.None), draft)

    private fun ProfileInfo.key() = "u$id"
}
