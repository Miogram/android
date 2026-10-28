package cn.ellerotta.core.ui.components.chat

import cn.ellerotta.core.model.ArticleAlign
import cn.ellerotta.core.model.ArticleBlock
import cn.ellerotta.core.model.ArticleButton
import cn.ellerotta.core.model.ArticleCaption
import cn.ellerotta.core.model.ArticleCell
import cn.ellerotta.core.model.ArticleListItem
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.ArticleStyle
import cn.ellerotta.core.model.ArticleText
import cn.ellerotta.core.model.BotButton
import cn.ellerotta.core.model.BotButtonMark
import cn.ellerotta.core.model.BotButtonStyle
import cn.ellerotta.core.model.CallEnd
import cn.ellerotta.core.model.CallInfo
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.EPHEMERAL_IDS
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.EphemeralInfo
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.ForwardInfo
import cn.ellerotta.core.model.GiftAction
import cn.ellerotta.core.model.LinkPreview
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.MessageTranslation
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Poll
import cn.ellerotta.core.model.PollAnswer
import cn.ellerotta.core.model.PollResults
import cn.ellerotta.core.model.PollVoters
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.ReactionCount
import cn.ellerotta.core.model.Reactor
import cn.ellerotta.core.model.RepliesInfo
import cn.ellerotta.core.model.ReplyKeyboard
import cn.ellerotta.core.model.ServiceKind
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.model.VoicePlayback
import cn.ellerotta.core.ui.components.common.previewPhoto
import kotlin.math.sin

internal object ChatPreviewData {
    const val NOW = 1_792_328_400
    private const val DAY = 86_400

    val single = BubblePosition(first = true, last = true)
    val runFirst = BubblePosition(first = true, last = false)
    val runMiddle = BubblePosition(first = false, last = false)
    val runLast = BubblePosition(first = false, last = true)

    val alice = Reactor(11, "Alice Moreau", "alice")
    val bob = Reactor(12, "Bob Lindqvist", "bob")
    val carol = Reactor(13, "Carol Okafor", "carol")

    val avatars: Map<String, ByteArray> by lazy {
        mapOf(
            "alice" to previewPhoto(start = 0xFFF2A65A.toInt(), end = 0xFFD9480F.toInt()),
            "bob" to previewPhoto(start = 0xFF63C7B2.toInt(), end = 0xFF1C7C54.toInt()),
            "carol" to previewPhoto(start = 0xFFB197FC.toInt(), end = 0xFF5F3DC4.toInt()),
            "team" to previewPhoto(start = 0xFF74C0FC.toInt(), end = 0xFF1864AB.toInt()),
            "news" to previewPhoto(start = 0xFFFFD43B.toInt(), end = 0xFFE8590C.toInt()),
        )
    }

    fun formatted(vararg parts: Pair<String, EntityType?>): FormattedText {
        val text = StringBuilder()
        val entities = mutableListOf<TextEntity>()
        for ((part, type) in parts) {
            if (type != null) entities += TextEntity(text.length, part.length, type)
            text.append(part)
        }
        return FormattedText(text.toString(), entities)
    }

    private fun MessageItem.withText(text: FormattedText): MessageItem = copy(text = text.text, entities = text.entities)

    private fun dated(from: Int, messages: List<MessageItem>): List<MessageItem> = messages.mapIndexed { index, message -> message.copy(date = from + index * 90) }

    val photo: MediaItem by lazy {
        MediaItem("photo1", "photo1.jpg", 182_000, 2, isPhoto = true, kind = MediaKind.Photo, thumb = previewPhoto(32, 24), width = 1280, height = 960)
    }

    val video: MediaItem by lazy {
        MediaItem(
            "video1", "video1.mp4", 4_200_000, 2, isPhoto = false, kind = MediaKind.Video,
            thumb = previewPhoto(32, 18, start = 0xFF4DABF7.toInt(), end = 0xFF0B7285.toInt()), width = 1280, height = 720, duration = 42, mimeType = "video/mp4",
        )
    }

    private val albumColors = listOf(
        0xFFFFA94D.toInt() to 0xFFE03131.toInt(),
        0xFF69DB7C.toInt() to 0xFF2B8A3E.toInt(),
        0xFF74C0FC.toInt() to 0xFF5F3DC4.toInt(),
        0xFFFFE066.toInt() to 0xFFF08C00.toInt(),
    )

    private fun albumPhoto(index: Int, width: Int, height: Int): MediaItem {
        val (start, end) = albumColors[index]
        return MediaItem("album$index", "album$index.jpg", 150_000, 2, isPhoto = true, kind = MediaKind.Photo, thumb = previewPhoto(width / 40, height / 40, start, end), width = width, height = height)
    }

    val stillSticker = MediaItem("sticker1", "sticker1.webp", 24_000, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "😺", width = 512, height = 512)

    val outlineSticker = MediaItem(
        "sticker2", "sticker2.tgs", 30_000, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "🐶", animated = true,
        outline = "M256,40L472,256L256,472L40,256z", width = 512, height = 512,
    )

    val emojiSticker = MediaItem("sticker3", "sticker3.tgs", 30_000, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "🦊", animated = true, width = 512, height = 512)

    val stickers: Map<String, ByteArray> by lazy {
        mapOf(stillSticker.key to previewPhoto(128, start = 0xFFFFD8A8.toInt(), end = 0xFFFF8787.toInt()))
    }

    val waveform: List<Int> = List(48) { i -> ((sin(i * 0.45) + 1) * 11 + (i % 5) * 1.6).toInt().coerceIn(0, 31) }

    val voice = MediaItem("voice1", "voice1.ogg", 18_000, 2, isPhoto = false, kind = MediaKind.Voice, duration = 14, waveform = waveform, mimeType = "audio/ogg")
    val ownVoice = voice.copy(key = "voice2", fileName = "voice2.ogg", duration = 37, waveform = waveform.reversed())

    val playing = VoicePlayback(ownVoice.key, playing = true, positionMs = 18_000, durationMs = 37_000)

    val round: MediaItem by lazy {
        MediaItem(
            "round1", "round1.mp4", 900_000, 2, isPhoto = false, kind = MediaKind.Round,
            thumb = previewPhoto(32, start = 0xFF8CE99A.toInt(), end = 0xFF1971C2.toInt()), width = 384, height = 384, duration = 9,
        )
    }

    val song: MediaItem by lazy {
        MediaItem(
            "song1", "song1.mp3", 7_800_000, 2, isPhoto = false, kind = MediaKind.Music,
            thumb = previewPhoto(48, start = 0xFF845EF7.toInt(), end = 0xFFF06595.toInt()), duration = 214, title = "Night Drive", performer = "The Lanterns", mimeType = "audio/mpeg",
        )
    }

    val file = MediaItem("file1", "quarterly-report.pdf", 2_400_000, 2, isPhoto = false, kind = MediaKind.File, mimeType = "application/pdf")

    val textIn = MessageItem(1, "Are we still on for lunch tomorrow?", out = false, date = NOW - 3_000)
    val textOutRead = MessageItem(2, "Yes, 12:30 at the usual place", out = true, date = NOW - 2_900, read = true)
    val textOutUnread = MessageItem(3, "I'll book a table for four", out = true, date = NOW - 2_880)
    val textEdited = MessageItem(4, "Make it one o'clock, the meeting runs late", out = false, date = NOW - 2_700, edited = true)

    val longText = MessageItem(5, "", out = false, date = NOW - 2_600).withText(
        formatted(
            "Release notes" to EntityType.Bold,
            " for this week: the new player is " to null,
            "ready" to EntityType.Italic,
            " and the old one goes away. Details on " to null,
            "the blog" to EntityType.TextUrl("https://example.com/blog"),
            ", questions to " to null,
            "@miogram" to EntityType.Mention,
            " " to null,
            "#release" to EntityType.Hashtag,
            ". The date is " to null,
            "Friday" to EntityType.Spoiler,
            ", build with " to null,
            "assembleDebug" to EntityType.Code,
            ".\n" to null,
            "./gradlew :app:assembleSandboxDebug" to EntityType.Pre("shell"),
            "\n" to null,
            "Small steps, every week." to EntityType.Blockquote(),
        ),
    )

    val emojiOne = MessageItem(6, "👍", out = true, date = NOW - 2_500, read = true)
    val emojiTwo = MessageItem(7, "🎉🎉", out = false, date = NOW - 2_490)
    val emojiThree = MessageItem(8, "😂😂😂", out = true, date = NOW - 2_480, read = true)

    val photoCaption: MessageItem by lazy { MessageItem(10, "The view from the top", out = false, date = NOW - 2_400, media = photo) }
    val photoAlone: MessageItem by lazy { MessageItem(11, "", out = true, date = NOW - 2_390, media = photo.copy(key = "photo2"), read = true) }
    val videoMessage: MessageItem by lazy { MessageItem(12, "", out = false, date = NOW - 2_380, media = video) }

    val album: List<MessageItem> by lazy {
        listOf(1600 to 1200, 1200 to 1600, 1200 to 1200, 1600 to 900).mapIndexed { index, (width, height) ->
            MessageItem(
                20 + index, if (index == 0) "Weekend at the lake" else "", out = true, date = NOW - 2_300,
                media = albumPhoto(index, width, height), groupedId = 77L, read = true,
            )
        }
    }

    val stillStickerMessage = MessageItem(30, "", out = false, date = NOW - 2_200, media = stillSticker)
    val outlineStickerMessage = MessageItem(31, "", out = true, date = NOW - 2_190, media = outlineSticker, read = true)
    val emojiStickerMessage = MessageItem(32, "", out = false, date = NOW - 2_180, media = emojiSticker)

    val voiceUnread = MessageItem(40, "", out = false, date = NOW - 2_100, media = voice, mediaUnread = true)
    val voiceOut = MessageItem(41, "", out = true, date = NOW - 2_090, media = ownVoice, read = true)
    val roundMessage: MessageItem by lazy { MessageItem(42, "", out = false, date = NOW - 2_080, media = round, mediaUnread = true) }
    val songMessage: MessageItem by lazy { MessageItem(43, "", out = false, date = NOW - 2_070, media = song) }
    val fileMessage = MessageItem(44, "Numbers for the meeting", out = true, date = NOW - 2_060, media = file, read = true)

    private val answers = listOf(
        PollAnswer("0", FormattedText("The office")),
        PollAnswer("1", FormattedText("The park by the river")),
        PollAnswer("2", FormattedText("Online")),
    )

    val pollOpen = MessageItem(
        50, "", out = false, date = NOW - 2_000, senderName = "Alice Moreau", senderId = alice.peerId, senderAvatar = alice.avatarKey,
        poll = Poll(1, FormattedText("Where do we meet on Friday?"), answers, publicVoters = true),
    )

    val pollVoted = MessageItem(
        51, "", out = true, date = NOW - 1_990, read = true,
        poll = Poll(
            2, FormattedText("Where do we meet on Friday?"), answers, publicVoters = true,
            results = PollResults(listOf(PollVoters("0", 5, chosen = true), PollVoters("1", 3), PollVoters("2", 1)), totalVoters = 9, recentVoters = listOf(alice, bob, carol)),
        ),
    )

    private val quizAnswers = listOf(
        PollAnswer("0", FormattedText("Mercury")),
        PollAnswer("1", FormattedText("Venus")),
        PollAnswer("2", FormattedText("Mars")),
    )

    val quizOpen = MessageItem(52, "", out = false, date = NOW - 1_980, poll = Poll(3, FormattedText("Which planet is the hottest?"), quizAnswers, quiz = true))

    val quizAnswered = MessageItem(
        53, "", out = false, date = NOW - 1_970,
        poll = Poll(
            4, FormattedText("Which planet is the hottest?"), quizAnswers, quiz = true,
            results = PollResults(
                listOf(PollVoters("0", 6, chosen = true), PollVoters("1", 9, correct = true), PollVoters("2", 2)),
                totalVoters = 17,
                solution = FormattedText("Venus: its thick air keeps the heat in."),
            ),
        ),
    )

    val callIncoming = MessageItem(60, "", out = false, date = NOW - 1_900, service = ServiceKind.Call, call = CallInfo(end = CallEnd.Hangup, duration = 312))
    val callOutgoing = MessageItem(61, "", out = true, date = NOW - 1_890, service = ServiceKind.Call, call = CallInfo(end = CallEnd.Hangup, duration = 45))
    val callMissed = MessageItem(62, "", out = false, date = NOW - 1_880, service = ServiceKind.Call, call = CallInfo(end = CallEnd.Missed))
    val callVideo = MessageItem(63, "", out = true, date = NOW - 1_870, service = ServiceKind.Call, call = CallInfo(video = true, end = CallEnd.Hangup, duration = 1_260))
    val callCancelled = MessageItem(64, "", out = true, date = NOW - 1_860, service = ServiceKind.Call, call = CallInfo(video = true, end = CallEnd.Busy))

    val giftIn = MessageItem(
        65, "", out = false, date = NOW - 1_800, senderName = "Alice", service = ServiceKind.Gift,
        gift = GiftAction(MediaItem("gift1", "gift1.tgs", 30_000, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "🎁", animated = true), stars = 50, message = "Happy birthday!"),
    )
    val giftOut = MessageItem(
        66, "", out = true, date = NOW - 1_790, service = ServiceKind.Gift,
        gift = GiftAction(MediaItem("gift2", "gift2.tgs", 30_000, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "🧸", animated = true), stars = 250),
    )

    val joined = MessageItem(67, "", out = false, date = NOW - 1_700, senderName = "Bob", service = ServiceKind.Joined)
    val added = MessageItem(71, "", out = false, date = NOW - 1_695, senderName = "Bob", service = ServiceKind.Added, added = listOf("Carol", ""))
    val created = MessageItem(68, "", out = true, date = NOW - DAY, service = ServiceKind.Created)
    val pinned = MessageItem(69, "", out = false, date = NOW - 1_690, senderName = "Carol", service = ServiceKind.Pinned)
    val titleChanged = MessageItem(70, "", out = false, date = NOW - 1_680, senderName = "Alice", service = ServiceKind.TitleChanged)

    val linkMessage: MessageItem by lazy {
        MessageItem(
            80, "Have you seen this? https://example.com/trails", out = false, date = NOW - 1_600,
            entities = listOf(TextEntity(20, 26, EntityType.Url)),
            link = LinkPreview(
                "https://example.com/trails", siteName = "Example Outdoors", title = "Ten trails for a short autumn day",
                description = "Easy walks under three hours, with the best views at the end.",
                photo = MediaItem("link1", "link1.jpg", 40_000, 2, isPhoto = true, kind = MediaKind.Photo, thumb = previewPhoto(32, start = 0xFFC0EB75.toInt(), end = 0xFF2F9E44.toInt()), width = 640, height = 640),
            ),
        )
    }

    val replyOut = MessageItem(81, "Sounds great, see you there", out = true, date = NOW - 1_500, replyTo = textIn.id, read = true)
    val forwarded = MessageItem(82, "The station reopens on Monday after the renovation.", out = false, date = NOW - 1_490, forward = ForwardInfo("City News", "https://t.me/citynews/120"))
    val viaBot = MessageItem(83, "Sunny, 18° in the afternoon", out = true, date = NOW - 1_480, viaBot = "weather", read = true)

    val reacted = MessageItem(
        90, "Photos from the trip are up!", out = false, date = NOW - 1_400,
        reactions = listOf(
            ReactionCount(Reaction.Emoji("❤️"), 3, chosen = true, recent = listOf(alice, bob, carol)),
            ReactionCount(Reaction.Emoji("🔥"), 12),
            ReactionCount(Reaction.Emoji("👍"), 1, recent = listOf(bob)),
            ReactionCount(Reaction.Paid, 5),
        ),
    )

    val reactedOwn = MessageItem(
        91, "Thanks everyone", out = true, date = NOW - 1_390, read = true,
        reactions = listOf(ReactionCount(Reaction.Emoji("😂"), 2, recent = listOf(alice, carol)), ReactionCount(Reaction.Emoji("🎉"), 1, chosen = true)),
    )

    val failed = MessageItem(92, "This one did not go through", out = true, date = NOW - 1_300, pending = true, failed = true)
    val pending = MessageItem(93, "On my way", out = true, date = NOW - 1_290, pending = true)

    val keyboardMessage = MessageItem(
        100, "Your order #1042 is ready. What next?", out = false, date = NOW - 1_200,
        buttons = listOf(
            listOf(BotButton.Callback("Confirm", byteArrayOf(1), BotButtonStyle.Primary), BotButton.Callback("Cancel", byteArrayOf(2), BotButtonStyle.Danger)),
            listOf(BotButton.Url("Track", "https://example.com/track", BotButtonStyle.Link), BotButton.Copy("Copy code", "X7K2")),
            listOf(BotButton.Callback("Rate", byteArrayOf(3), BotButtonStyle.Success), BotButton.Disabled("Sold out")),
            listOf(BotButton.SwitchInline("Share", "shopbot", "1042", samePeer = false), BotButton.Unsupported("Pay", mark = BotButtonMark.Payment)),
        ),
    )

    val replyKeyboard = ReplyKeyboard(
        rows = listOf(
            listOf(BotButton.Text("Yes"), BotButton.Text("No")),
            listOf(BotButton.Text("Show the menu", BotButtonStyle.Primary)),
        ),
        resize = true,
    )

    private val articlePhoto: MediaItem by lazy {
        MediaItem(
            "article1", "article1.jpg", 120_000, 2, isPhoto = true, kind = MediaKind.Photo,
            thumb = previewPhoto(40, 24, 0xFF66D9E8.toInt(), 0xFF364FC7.toInt()), width = 1600, height = 960,
        )
    }

    val article = ArticleMessage(
        listOf(
            ArticleBlock.Heading(1, ArticleText.Plain("Weekly update")),
            ArticleBlock.Paragraph(
                ArticleText.Concat(
                    listOf(
                        ArticleText.Plain("The new "),
                        ArticleText.Styled(ArticleStyle.Bold, ArticleText.Plain("music player")),
                        ArticleText.Plain(" is out. Read about it on "),
                        ArticleText.Url(ArticleText.Plain("the blog"), "https://example.com/blog"),
                        ArticleText.Plain("."),
                    ),
                ),
            ),
            ArticleBlock.Items(
                listOf(
                    ArticleListItem(listOf(ArticleBlock.Paragraph(ArticleText.Plain("Queue from the chat"))), checkbox = true, checked = true),
                    ArticleListItem(listOf(ArticleBlock.Paragraph(ArticleText.Plain("Lock screen controls"))), checkbox = true),
                ),
            ),
            ArticleBlock.Quote(ArticleText.Plain("Small steps, every week."), author = ArticleText.Plain("The team")),
            ArticleBlock.Preformatted(ArticleText.Plain("./gradlew assembleDebug"), "shell"),
            ArticleBlock.Table(
                ArticleText.Plain("Downloads"),
                listOf(
                    listOf(ArticleCell(ArticleText.Plain("Device"), header = true), ArticleCell(ArticleText.Plain("Count"), header = true, align = ArticleAlign.End)),
                    listOf(ArticleCell(ArticleText.Plain("Phone")), ArticleCell(ArticleText.Plain("1 204"), align = ArticleAlign.End)),
                    listOf(ArticleCell(ArticleText.Plain("Tablet")), ArticleCell(ArticleText.Plain("312"), align = ArticleAlign.End)),
                ),
                bordered = true,
                striped = true,
            ),
        ),
    )

    val articleMedia = ArticleMessage(
        listOf(
            ArticleBlock.Photo(articlePhoto, ArticleCaption(ArticleText.Plain("The player on a phone"))),
            ArticleBlock.Collage(listOf(ArticleBlock.Photo(albumPhoto(0, 1600, 1200)), ArticleBlock.Photo(albumPhoto(1, 1200, 1600)))),
            ArticleBlock.Document(file, ArticleCaption(ArticleText.Plain("The full numbers"))),
            ArticleBlock.Divider,
            ArticleBlock.Buttons(listOf(ArticleButton(ArticleText.Plain("Read more"), BotButton.Url("Read more", "https://example.com/blog"))), ArticleAlign.Center),
            ArticleBlock.Footer(ArticleText.Plain("Sent every Friday")),
        ),
    )

    val articleMessage = MessageItem(101, "", out = false, date = NOW - 1_100, article = article)
    val articleMediaMessage = MessageItem(104, "", out = false, date = NOW - 1_090, article = articleMedia)

    val translated = MessageItem(
        102, "Wir sehen uns morgen um neun am Bahnhof.", out = false, date = NOW - 1_000,
        translation = MessageTranslation("en", FormattedText("See you tomorrow at nine at the station.")),
    )

    val streaming = MessageItem(
        103, "Here is a plan for the trip: leave at eight, stop for", out = false, date = NOW - 60, streaming = true,
    )

    val ephemeralIn = MessageItem(
        EPHEMERAL_IDS + 1, "Your reminder is set for 9:00.", out = false, date = NOW - 50,
        ephemeral = EphemeralInfo(1, "u500", "remindbot"),
    )
    val ephemeralOut = MessageItem(
        EPHEMERAL_IDS + 2, "/remind 9:00 call the bank", out = true, date = NOW - 55,
        ephemeral = EphemeralInfo(2, "u500", "remindbot"),
    )

    val withReplies = MessageItem(
        110, "Who brings the snacks on Friday?", out = false, date = NOW - 900, senderName = "Carol Okafor", senderId = carol.peerId,
        replies = RepliesInfo(4, recent = listOf(alice, bob), maxId = 120, readMaxId = 115),
    )

    val post = MessageItem(
        111, "The autumn timetable starts on Monday: trains every 15 minutes in the morning.", out = false, date = NOW - 800,
        replies = RepliesInfo(27, comments = true, recent = listOf(alice, bob, carol)),
    )

    private fun member(id: Int, text: String, who: Reactor, date: Int, rank: String? = null) =
        MessageItem(id, text, out = false, date = date, senderName = who.name, senderId = who.peerId, senderAvatar = who.avatarKey, senderRank = rank)

    val groupRun: List<Pair<MessageItem, BubblePosition>> = listOf(
        member(120, "Morning! Did anyone see the forecast?", alice, NOW - 700, rank = "organizer") to runFirst,
        member(121, "Rain until noon, then sun", alice, NOW - 690, rank = "organizer") to runMiddle,
        member(122, "So the hike is on 🥾", alice, NOW - 680, rank = "organizer") to runLast,
        member(123, "Count me in", bob, NOW - 600).copy(reactions = listOf(ReactionCount(Reaction.Emoji("👍"), 2))) to single,
        MessageItem(124, "Me too", out = true, date = NOW - 500, read = true) to runFirst,
        MessageItem(125, "I'll bring the map", out = true, date = NOW - 490, read = true, reactions = listOf(ReactionCount(Reaction.Emoji("🎉"), 1, chosen = true))) to runLast,
    )

    val privateDialog = DialogItem("u11", "Alice Moreau", avatarKey = "alice", peerId = alice.peerId, kind = PeerKind.User, online = true)

    val privateChat: List<MessageItem> by lazy {
        listOf(
            created,
            MessageItem(200, "Hi! Back from the trip?", out = false, date = NOW - DAY + 60),
            MessageItem(201, "Yes, last night", out = true, date = NOW - DAY + 120, read = true),
            callMissed.copy(id = 202, date = NOW - DAY + 600),
            callOutgoing.copy(id = 203, date = NOW - DAY + 700),
        ) + dated(
            NOW - 2_000,
            listOf(textIn, textOutRead, replyOut, photoCaption) + album + listOf(
                voiceUnread,
                stillStickerMessage,
                reacted,
                linkMessage,
                emojiOne,
                failed,
                pending,
            ),
        )
    }

    val groupDialog = DialogItem("c5", "Weekend hikers", avatarKey = "team", kind = PeerKind.Group, typing = "Bob")

    val groupChat: List<MessageItem> by lazy {
        dated(
            NOW - 2_000,
            listOf(joined) + groupRun.map { it.first } + listOf(
                pinned,
                pollOpen,
                member(130, "Who brings the snacks?", carol, 0).copy(replyTo = 122),
                MessageItem(131, "I will", out = true, date = 0, replyTo = 130, read = true),
                member(132, "🎉", bob, 0),
            ),
        )
    }

    val channelDialog = DialogItem("ch9", "City News", avatarKey = "news", kind = PeerKind.Channel, verified = true)

    val channelPosts: List<MessageItem> by lazy {
        listOf(
            MessageItem(300, "Good morning! Today: road works on the bridge until 18:00.", out = false, date = NOW - 5_000, replies = RepliesInfo(8, comments = true, recent = listOf(bob))),
            MessageItem(301, "The view from the new tower", out = false, date = NOW - 4_000, media = photo, replies = RepliesInfo(42, comments = true, recent = listOf(alice, bob, carol))),
            post,
            MessageItem(
                302, "", out = false, date = NOW - 600,
                poll = Poll(9, FormattedText("Do you take the train to work?"), listOf(PollAnswer("0", FormattedText("Every day")), PollAnswer("1", FormattedText("Sometimes")), PollAnswer("2", FormattedText("Never")))),
                replies = RepliesInfo(0, comments = true),
            ),
        )
    }
}
