package cn.ellerotta.core.ui.components.chat

import cn.ellerotta.core.model.ArticleGate
import cn.ellerotta.core.model.ArticleMediaKind
import cn.ellerotta.core.model.ArticlePosting
import cn.ellerotta.core.model.ArticleRow
import cn.ellerotta.core.model.ArticleRowKind
import cn.ellerotta.core.model.ArticleUpload
import cn.ellerotta.core.model.ChatReactionState
import cn.ellerotta.core.model.ChatSearchState
import cn.ellerotta.core.model.ChatStickerState
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.ForwardInfo
import cn.ellerotta.core.model.ForwardPreview
import cn.ellerotta.core.model.GifPanel
import cn.ellerotta.core.model.InlineBot
import cn.ellerotta.core.model.InlineResult
import cn.ellerotta.core.model.InlineResultKind
import cn.ellerotta.core.model.InlineResults
import cn.ellerotta.core.model.InlineSwitchPm
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.PickedEmoji
import cn.ellerotta.core.model.PinnedMessages
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.StickerPanel
import cn.ellerotta.core.model.StickerSetDetail
import cn.ellerotta.core.model.StickerSetInfo
import cn.ellerotta.core.model.StoryRing
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.model.Wallpaper
import cn.ellerotta.core.model.WallpaperKind
import cn.ellerotta.core.model.newTableRow
import cn.ellerotta.core.ui.components.common.previewPhoto

internal object ChatChromePreviewData {
    const val NOW = 1_760_000_000

    private const val BLUE = 0xFF5B8DEF.toInt()
    private const val PINK = 0xFFE86A9A.toInt()
    private const val GREEN = 0xFF4CAF7A.toInt()
    private const val AMBER = 0xFFF2B33D.toInt()
    private const val VIOLET = 0xFF8E6BE8.toInt()

    val incoming = MessageItem(
        id = 101,
        text = "Are we still meeting at the station at six?",
        out = false,
        date = NOW - 600,
        senderName = "Anna Petrova",
        senderId = 11,
    )

    val outgoing = MessageItem(
        id = 102,
        text = "Yes, I'll bring the tickets and the map",
        out = true,
        date = NOW - 540,
        read = true,
        entities = listOf(TextEntity(20, 7, EntityType.Bold)),
    )

    fun photo(key: String, start: Int = BLUE, end: Int = PINK) = MediaItem(
        key = key,
        fileName = "$key.jpg",
        size = 120_000,
        dcId = 2,
        isPhoto = true,
        kind = MediaKind.Photo,
        thumb = previewPhoto(48, 36, start, end),
        width = 1280,
        height = 960,
    )

    val photoMessage = MessageItem(id = 103, text = "The view from the bridge", out = false, date = NOW - 3_600, senderName = "Anna Petrova", senderId = 11, senderAvatar = "avatar_anna", media = photo("photo1"))

    val pinnedMessages = listOf(
        MessageItem(id = 120, text = "Meeting moved to Thursday, 10:00", out = false, date = NOW - 7_200, senderName = "Mark Lee", senderId = 12, senderAvatar = "avatar_mark"),
        photoMessage.copy(id = 110, date = NOW - 86_400),
        MessageItem(id = 100, text = "Welcome! Please read the rules before posting", out = true, date = NOW - 172_800),
    )

    val pinnedOne = PinnedMessages(listOf(pinnedMessages.first()))

    val pinnedSeveral = PinnedMessages(pinnedMessages, current = 1)

    val avatars: Map<String, ByteArray> = mapOf(
        "avatar_anna" to previewPhoto(start = PINK, end = AMBER),
        "avatar_group" to previewPhoto(start = GREEN, end = BLUE),
        "avatar_mark" to previewPhoto(start = VIOLET, end = PINK),
        "avatar_bot" to previewPhoto(start = VIOLET, end = BLUE),
    )

    val user = DialogItem(
        key = "u11",
        title = "Anna Petrova",
        avatarKey = "avatar_anna",
        peerId = 11,
        kind = PeerKind.User,
        online = true,
        story = StoryRing.Unread,
    )

    val group = DialogItem(
        key = "c21",
        title = "Weekend hiking club",
        avatarKey = "avatar_group",
        peerId = 21,
        kind = PeerKind.Group,
        typing = "Mark",
    )

    val forwardTargets = listOf(
        DialogItem(key = "u0", title = "Saved Messages", isSaved = true, kind = PeerKind.Saved),
        user.copy(story = StoryRing.None),
        group.copy(typing = null),
        DialogItem(key = "ch31", title = "City news", kind = PeerKind.Channel, peerId = 31),
        DialogItem(key = "u13", title = "Mark Lee", peerId = 13),
    )

    val forward = ForwardPreview(listOf(incoming), fromTitle = "Weekend hiking club")

    val forwardSeveral = ForwardPreview(listOf(incoming, outgoing), fromTitle = "Weekend hiking club")

    val forwardInfo = ForwardInfo("City news", link = "https://t.me/example/1")

    val reactions = ChatReactionState(
        allowed = listOf("👍", "❤️", "🔥", "🎉", "😁", "😮", "😢", "🙏", "👏", "🤔", "👀", "💯").map { Reaction.Emoji(it) },
        default = Reaction.Emoji("👍"),
    )

    val chosen: Set<Reaction> = setOf(Reaction.Emoji("🔥"))

    val messageActions = listOf(
        MessageAction.Reply, MessageAction.Copy, MessageAction.Edit, MessageAction.Delete,
        MessageAction.Forward, MessageAction.Pin, MessageAction.Translate, MessageAction.Select,
    )

    val incomingActions = listOf(MessageAction.Reply, MessageAction.Copy, MessageAction.Forward, MessageAction.Pin, MessageAction.Select)

    fun sticker(key: String, emoji: String) =
        MediaItem(key = key, fileName = "$key.webp", size = 20_000, dcId = 2, isPhoto = false, kind = MediaKind.Sticker, emoji = emoji, width = 512, height = 512, mimeType = "image/webp")

    val stickerList = listOf(
        sticker("st1", "😀"),
        sticker("st2", "🐱"),
        sticker("st3", "🎉"),
        sticker("st4", "❤️"),
        sticker("st5", "👍"),
        sticker("st6", "😴"),
    )

    private val stickerColors = listOf(BLUE to PINK, GREEN to BLUE, AMBER to PINK, PINK to VIOLET, VIOLET to GREEN, AMBER to BLUE)

    private fun customEmoji(index: Int, emoji: String) = MediaItem(
        key = "ce$index",
        fileName = "ce$index.webp",
        size = 4_000,
        dcId = 2,
        isPhoto = false,
        kind = MediaKind.Sticker,
        emoji = emoji,
        width = 100,
        height = 100,
        mimeType = "image/webp",
        documentId = 9_000L + index,
    )

    val emojiSetStickers = listOf("🙂", "🌟", "🍀", "🚀", "🎈", "🌈", "☕", "🎵").mapIndexed { index, emoji -> customEmoji(index + 1, emoji) }

    val stickerSet = StickerSetInfo(key = "set1", title = "Happy friends", shortName = "HappyFriends", count = stickerList.size, installed = true)

    val emojiSet = StickerSetInfo(key = "set2", title = "Little things", shortName = "LittleThings", count = emojiSetStickers.size, installed = true)

    val stickerSetDetail = StickerSetDetail(stickerSet.copy(installed = false), stickerList)

    val gifs = (1..6).map { index ->
        val (start, end) = stickerColors[index - 1]
        MediaItem(key = "gif$index", fileName = "gif$index.mp4", size = 300_000, dcId = 2, isPhoto = false, kind = MediaKind.Gif, thumb = previewPhoto(40, 30, start, end), width = 320, height = 240, duration = 3, mimeType = "video/mp4")
    }

    val stickerFiles: Map<String, ByteArray> = buildMap {
        stickerList.forEachIndexed { index, sticker ->
            val (start, end) = stickerColors[index]
            put(sticker.key, previewPhoto(96, 96, start, end))
        }
        emojiSetStickers.forEachIndexed { index, emoji ->
            val (start, end) = stickerColors[index % stickerColors.size]
            put(emoji.key, previewPhoto(48, 48, start, end))
        }
    }

    val customEmojiById: Map<Long, MediaItem> = emojiSetStickers.associateBy { it.documentId }

    val pinnedSticker = PinnedMessages(listOf(MessageItem(id = 130, text = "", out = false, date = NOW - 600, senderName = "Mark Lee", senderId = 12, media = stickerList[4])) + pinnedMessages)

    val stickerState = ChatStickerState(
        panel = StickerPanel(recent = stickerList.take(3), faved = stickerList.takeLast(2), sets = listOf(stickerSet)),
        setStickers = mapOf(stickerSet.key to stickerList, emojiSet.key to emojiSetStickers),
        emojiSets = listOf(emojiSet),
        recentEmoji = listOf(PickedEmoji("😂"), PickedEmoji("👍"), PickedEmoji("🙂", emojiSetStickers.first().documentId), PickedEmoji("❤️")),
        gifs = GifPanel(saved = gifs),
    )

    val stickerActions = ChatStickerActions(
        onOpenPanel = {},
        onOpenTab = {},
        onLoad = {},
        onSend = {},
        onDraft = {},
        onShowSet = {},
        onInstall = { _, _ -> },
        onDismissSet = {},
    )

    val viewerItems = listOf(
        photoMessage,
        photoMessage.copy(id = 104, media = photo("photo2", GREEN, AMBER)),
        photoMessage.copy(id = 105, media = photo("photo3", VIOLET, BLUE)),
    )

    val search = ChatSearchState(
        query = "station",
        results = listOf(
            incoming,
            MessageItem(id = 90, text = "The station cafe opens at seven, let's meet there", out = true, date = NOW - 90_000),
            MessageItem(id = 80, text = "Which station is closer to the trail?", out = false, date = NOW - 400_000, senderName = "Mark Lee", senderId = 12),
        ),
        total = 3,
    )

    val inlineBots = listOf(
        InlineBot(key = "u41", username = "gif", name = "GIF search", placeholder = "Search GIFs", avatarKey = "avatar_bot"),
        InlineBot(key = "u42", username = "wiki", name = "Encyclopedia", placeholder = "Search articles"),
    )

    private fun thumb(key: String, start: Int, end: Int) =
        MediaItem(key = key, fileName = "$key.jpg", size = 8_000, dcId = 2, isPhoto = true, kind = MediaKind.Photo, thumb = previewPhoto(48, 48, start, end), width = 90, height = 90)

    val inlineArticles = InlineResults(
        results = listOf(
            InlineResult(1, "a1", InlineResultKind.Article, title = "Mountain trails", description = "Routes, difficulty and the best season for each one", thumb = thumb("in1", GREEN, BLUE)),
            InlineResult(1, "a2", InlineResultKind.Article, title = "Station history", description = "How the old railway station was built and restored"),
            InlineResult(1, "a3", InlineResultKind.Article, title = "Packing list", description = "What to take on a day hike", thumb = thumb("in3", AMBER, PINK)),
        ),
        switchPm = InlineSwitchPm("Open settings", "settings"),
    )

    val inlineGallery = InlineResults(
        results = (1..5).map { index ->
            val (start, end) = stickerColors[index - 1]
            InlineResult(2, "g$index", InlineResultKind.Photo, title = "Photo $index", thumb = thumb("ig$index", start, end), width = 320, height = 240)
        },
        gallery = true,
    )

    val articleGate = ArticleGate(ArticlePosting.Enabled)

    val articleUpload = ArticleUpload(id = 7, kind = ArticleMediaKind.Photo, name = "bridge.jpg", uploaded = 40_000, total = 120_000)

    val articleRows = listOf(
        ArticleRow(ArticleRowKind.Heading1, FormattedText("Weekend trip"), id = 0),
        ArticleRow(ArticleRowKind.Paragraph, FormattedText("We leave early on Saturday and come back on Sunday evening", listOf(TextEntity(9, 5, EntityType.Bold))), id = 1),
        ArticleRow(ArticleRowKind.Check, FormattedText("Tickets"), checked = true, id = 2),
        ArticleRow(ArticleRowKind.Check, FormattedText("Map and water"), id = 3),
        ArticleRow(ArticleRowKind.Quote, FormattedText("The best views are after the second bridge"), id = 4),
        ArticleRow(ArticleRowKind.Code, FormattedText("meet = \"06:00\""), language = "kotlin", id = 5),
        newTableRow(6).copy(cells = listOf(listOf(FormattedText("Day"), FormattedText("Distance")), listOf(FormattedText("Saturday"), FormattedText("14 km")))),
        ArticleRow(ArticleRowKind.Media, FormattedText("The bridge"), id = 8, upload = articleUpload),
        ArticleRow(ArticleRowKind.Divider, id = 9),
        ArticleRow(ArticleRowKind.Paragraph, FormattedText(""), id = 10),
    )

    val wallpaperSolid = Wallpaper(key = "wp1", kind = WallpaperKind.Fill, colors = listOf(0xDCE8C8))

    val wallpaperGradient = Wallpaper(key = "wp2", kind = WallpaperKind.Fill, colors = listOf(0x7BA7E0, 0xE7A6C8), rotation = 45)

    val wallpaperFreeform = Wallpaper(key = "wp3", kind = WallpaperKind.Fill, colors = listOf(0xDBDDBB, 0x6BA587, 0xD5D88D, 0x88B884))
}
