package cn.ellerotta.core.model

enum class PeerKind { User, Bot, Group, Channel, Saved }

enum class MediaKind { Photo, Video, Round, Voice, Music, Gif, Sticker, File, Location, Contact, Poll, Link, Other }

enum class ServiceKind { Created, Joined, Added, Left, Pinned, TitleChanged, PhotoChanged, Call, Gift, Wallpaper, Proximity, Other }

enum class ActivityKind { Typing, RecordVoice, UploadPhoto, UploadVideo, UploadVoice, UploadFile, RecordRound, UploadRound, ChooseSticker, PlayGame }

data class Preview(
    val text: String = "",
    val sender: String? = null,
    val media: MediaKind? = null,
    val service: ServiceKind? = null,
    val added: List<String> = emptyList(),
    val draft: Boolean = false,
    val article: ArticleMessage? = null,
    val hidden: List<IntRange> = emptyList(),
)

fun FormattedText.spoilers(): List<IntRange> = entities.filter { it.type == EntityType.Spoiler }.map { it.offset until it.offset + it.length }

const val NOTIFICATIONS_KEY = "u777000"

private val LOGIN_CODE = Regex("""(?<!\d)\d{5,6}(?!\d)""")

fun loginCodes(text: String): List<IntRange> = LOGIN_CODE.findAll(text).map { it.range }.toList()

data class Draft(val text: FormattedText, val replyTo: Int? = null, val article: ArticleMessage? = null) {
    val isEmpty: Boolean get() = text.text.isBlank() && article == null

    companion object {
        val EMPTY = Draft(FormattedText(""))
    }
}

data class DialogItem(
    val key: String,
    val title: String,
    val preview: Preview = Preview(),
    val unread: Int = 0,
    val avatarKey: String? = null,
    val isSaved: Boolean = false,
    val peerId: Long = 0,
    val kind: PeerKind = PeerKind.User,
    val date: Int = 0,
    val mentions: Int = 0,
    val reactions: Int = 0,
    val unreadMark: Boolean = false,
    val muted: Boolean = false,
    val followsDefault: Boolean = false,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val verified: Boolean = false,
    val premium: Boolean = false,
    val online: Boolean = false,
    val presence: Presence? = null,
    val contact: Boolean = false,
    val phone: String? = null,
    val support: Boolean = false,
    val outgoing: Boolean = false,
    val read: Boolean = false,
    val sending: Boolean = false,
    val sendFailed: Boolean = false,
    val typing: String? = null,
    val activity: ActivityKind = ActivityKind.Typing,
    val typingMore: List<String> = emptyList(),
    val draft: Draft? = null,
    val forum: Boolean = false,
    val topMessage: Int = 0,
    val story: StoryRing = StoryRing.None,
    val emojiStatus: Long? = null,
    val scam: Boolean = false,
    val fake: Boolean = false,
    val members: Int = 0,
    val membersOnline: Int? = null,
    val botUsers: Int = 0,
    val owner: Boolean = false,
) {
    val hasUnread: Boolean get() = unread > 0 || unreadMark

    val phoneTitle: String? get() = phone?.takeIf { kind == PeerKind.User && !isSaved && !contact && !support && it.isNotEmpty() }

    val listPreview: Preview get() = draft?.let { Preview(text = it.text.text, draft = true, article = it.article, hidden = it.text.spoilers()) }
        ?: if (key == NOTIFICATIONS_KEY) preview.copy(hidden = preview.hidden + loginCodes(preview.text)) else preview

    val removals: List<ChatRemoval>
        get() = when {
            isSaved -> listOf(ChatRemoval.Clear)
            kind == PeerKind.User || kind == PeerKind.Bot -> listOf(ChatRemoval.Clear, ChatRemoval.Delete)
            kind == PeerKind.Channel -> listOfNotNull(ChatRemoval.Delete, if (owner) ChatRemoval.DeleteForEveryone else null)
            else -> listOfNotNull(if (forum) null else ChatRemoval.Clear, ChatRemoval.Delete, if (owner) ChatRemoval.DeleteForEveryone else null)
        }

    fun revokable(removal: ChatRemoval): Boolean = kind == PeerKind.User && !isSaved && removal != ChatRemoval.DeleteForEveryone
}

enum class ChatRemoval { Clear, Delete, DeleteForEveryone }

data class PendingRemoval(val dialog: DialogItem, val removal: ChatRemoval, val revoke: Boolean = false)

data class ChatFolder(
    val id: Int,
    val title: String,
    val emoticon: String? = null,
    val pinned: List<String> = emptyList(),
    val include: Set<String> = emptySet(),
    val exclude: Set<String> = emptySet(),
    val contacts: Boolean = false,
    val nonContacts: Boolean = false,
    val groups: Boolean = false,
    val broadcasts: Boolean = false,
    val bots: Boolean = false,
    val excludeMuted: Boolean = false,
    val excludeRead: Boolean = false,
    val excludeArchived: Boolean = false,
    val shared: Boolean = false,
) {
    val isAll: Boolean get() = id == ALL

    val hasRules: Boolean get() = contacts || nonContacts || groups || broadcasts || bots

    val explicit: List<String> get() = (pinned + include).distinct()

    fun matches(dialog: DialogItem): Boolean {
        if (isAll) return !dialog.archived
        if (dialog.key in exclude) return false
        if (dialog.key in include || dialog.key in pinned) return true
        if (excludeMuted && dialog.muted) return false
        if (excludeRead && !dialog.hasUnread) return false
        if (excludeArchived && dialog.archived) return false
        return when (dialog.kind) {
            PeerKind.User -> if (dialog.contact) contacts else nonContacts
            PeerKind.Saved -> false
            PeerKind.Bot -> bots
            PeerKind.Group -> groups
            PeerKind.Channel -> broadcasts
        }
    }

    fun pick(dialogs: List<DialogItem>): List<DialogItem> {
        val matching = dialogs.distinctBy { it.key }.filter(::matches)
        val byKey = matching.associateBy { it.key }
        val head = if (isAll) matching.filter { it.pinned } else pinned.distinct().mapNotNull(byKey::get)
        val headKeys = head.map { it.key }.toSet()
        return head + matching.filter { it.key !in headKeys }.sortedByDescending { it.date }
    }

    companion object {
        const val ALL = 0

        const val NEW = -1

        fun all(title: String): ChatFolder = ChatFolder(ALL, title)
    }
}

data class FolderTab(val folder: ChatFolder, val dialogs: List<DialogItem>, val loading: Boolean = false) {
    val unreadChats: Int get() = dialogs.count { it.hasUnread }
    val mutedOnly: Boolean get() = dialogs.filter { it.hasUnread }.all { it.muted }
}

fun DialogItem.withOutgoing(last: OutgoingLast?): DialogItem =
    if (last == null || last.date < date) this else copy(preview = last.preview, date = last.date, outgoing = true, read = false, sending = !last.failed, sendFailed = last.failed)
