package cn.ellerotta.core.model

data class InlineBot(val key: String, val username: String, val name: String, val placeholder: String, val avatarKey: String? = null)

enum class InlineResultKind { Article, Photo, Gif, Video, Sticker, Audio, Voice, File }

data class InlineResult(
    val queryId: Long,
    val id: String,
    val kind: InlineResultKind,
    val title: String? = null,
    val description: String? = null,
    val thumb: MediaItem? = null,
    val width: Int = 0,
    val height: Int = 0,
)

data class InlineSwitchPm(val text: String, val startParameter: String)

data class InlineSwitchWebView(val text: String, val url: String)

data class InlineResults(
    val results: List<InlineResult>,
    val nextOffset: String? = null,
    val gallery: Boolean = false,
    val switchPm: InlineSwitchPm? = null,
    val switchWebView: InlineSwitchWebView? = null,
    val cacheTime: Int = 0,
)

data class InlineState(
    val bot: InlineBot? = null,
    val query: String = "",
    val results: InlineResults? = null,
    val more: Boolean = false,
    val loading: Boolean = false,
)

data class InlineQuery(val username: String, val query: String)

fun inlineQueryOf(text: String): InlineQuery? {
    val match = INLINE_QUERY.matchEntire(text) ?: return null
    return InlineQuery(match.groupValues[1], match.groupValues[2])
}

private val INLINE_QUERY = Regex("@([a-zA-Z][a-zA-Z0-9_]{2,31}) (.*)", RegexOption.DOT_MATCHES_ALL)

enum class BotButtonStyle { Default, Primary, Danger, Success, Link }

enum class BotButtonMark { Link, Share, Copy, Profile, WebApp, Payment }

sealed interface BotButton {
    val text: String
    val style: BotButtonStyle
    val icon: Long?

    data class Text(override val text: String, override val style: BotButtonStyle = BotButtonStyle.Default, override val icon: Long? = null) : BotButton

    data class Url(override val text: String, val url: String, override val style: BotButtonStyle = BotButtonStyle.Default, override val icon: Long? = null) : BotButton

    class Callback(override val text: String, val data: ByteArray, override val style: BotButtonStyle = BotButtonStyle.Default, override val icon: Long? = null) : BotButton {
        override fun equals(other: Any?) = other is Callback && other.text == text && other.data.contentEquals(data) && other.style == style && other.icon == icon

        override fun hashCode() = 31 * (31 * (31 * text.hashCode() + data.contentHashCode()) + style.hashCode()) + icon.hashCode()

        override fun toString() = "Callback($text)"
    }

    data class SwitchInline(
        override val text: String,
        val bot: String?,
        val query: String,
        val samePeer: Boolean,
        override val style: BotButtonStyle = BotButtonStyle.Default,
        override val icon: Long? = null,
    ) : BotButton

    data class Copy(override val text: String, val copyText: String, override val style: BotButtonStyle = BotButtonStyle.Default, override val icon: Long? = null) : BotButton

    data class UserProfile(override val text: String, val userId: Long, override val style: BotButtonStyle = BotButtonStyle.Default, override val icon: Long? = null) : BotButton

    data class LoginUrl(override val text: String, val url: String, val buttonId: Int, override val style: BotButtonStyle = BotButtonStyle.Default, override val icon: Long? = null) : BotButton

    data class WebApp(
        override val text: String,
        val url: String,
        val simple: Boolean = false,
        override val style: BotButtonStyle = BotButtonStyle.Default,
        override val icon: Long? = null,
    ) : BotButton

    data class Disabled(override val text: String, override val style: BotButtonStyle = BotButtonStyle.Default, override val icon: Long? = null) : BotButton

    data class Unsupported(
        override val text: String,
        override val style: BotButtonStyle = BotButtonStyle.Default,
        override val icon: Long? = null,
        val mark: BotButtonMark? = null,
    ) : BotButton
}

val BotButton.mark: BotButtonMark?
    get() = when (this) {
        is BotButton.Url, is BotButton.LoginUrl -> BotButtonMark.Link
        is BotButton.SwitchInline -> BotButtonMark.Share
        is BotButton.Copy -> BotButtonMark.Copy
        is BotButton.UserProfile -> BotButtonMark.Profile
        is BotButton.WebApp -> BotButtonMark.WebApp
        is BotButton.Unsupported -> mark
        is BotButton.Text, is BotButton.Callback, is BotButton.Disabled -> null
    }

data class ReplyKeyboard(
    val rows: List<List<BotButton>>,
    val resize: Boolean = false,
    val singleUse: Boolean = false,
    val persistent: Boolean = false,
    val placeholder: String? = null,
    val forceReply: Boolean = false,
)

sealed interface KeyboardChange {
    data class Show(val keyboard: ReplyKeyboard) : KeyboardChange

    data object Hide : KeyboardChange

    data class ForceReply(val placeholder: String? = null) : KeyboardChange
}

data class CallbackAnswer(val message: String? = null, val alert: Boolean = false, val url: String? = null)

data class LoginRequest(val domain: String, val bot: String, val writeAccess: Boolean = false, val phone: Boolean = false)

sealed interface LoginAnswer {
    data class Ask(val request: LoginRequest) : LoginAnswer

    data class Open(val url: String) : LoginAnswer

    data object Plain : LoginAnswer
}

data class LoginState(val request: LoginRequest? = null, val url: String? = null, val confirmed: Boolean = true)

data class OpenWebApp(
    val bot: String,
    val title: String,
    val url: String,
    val queryId: Long? = null,
    val sendsData: Boolean = false,
    val buttonText: String = "",
    val inlineBot: String? = null,
    val mode: WebAppMode = WebAppMode.Default,
)

data class BotMenuButton(val text: String, val url: String)

enum class AttachPeerType { SameBot, Bots, Users, Groups, Channels }

data class AttachMenuBot(
    val key: String,
    val shortName: String,
    val peerTypes: Set<AttachPeerType> = emptySet(),
    val attachMenu: Boolean = false,
    val sideMenu: Boolean = false,
    val inactive: Boolean = false,
    val requestWriteAccess: Boolean = false,
    val disclaimer: Boolean = false,
) {
    fun allowedIn(dialogKey: String, kind: PeerKind): Boolean = when (kind) {
        PeerKind.Bot -> (if (dialogKey == key) AttachPeerType.SameBot else AttachPeerType.Bots) in peerTypes
        PeerKind.User -> AttachPeerType.Users in peerTypes
        PeerKind.Group -> AttachPeerType.Groups in peerTypes
        PeerKind.Channel -> AttachPeerType.Channels in peerTypes
        PeerKind.Saved -> false
    }

    val consent: WebAppConsent?
        get() = when {
            inactive -> WebAppConsent(shortName, add = true, writeAccess = requestWriteAccess, terms = disclaimer)
            disclaimer -> WebAppConsent(shortName, terms = true)
            else -> null
        }
}

data class BotAppInfo(
    val bot: String,
    val shortName: String,
    val title: String,
    val description: String = "",
    val inactive: Boolean = false,
    val requestWriteAccess: Boolean = false,
) {
    val consent: WebAppConsent?
        get() = if (inactive || requestWriteAccess) WebAppConsent(title, writeAccess = requestWriteAccess) else null
}

data class WebAppConsent(val name: String, val add: Boolean = false, val writeAccess: Boolean = false, val terms: Boolean = false) {
    fun writeAllowed(ticked: Boolean): Boolean = if (writeAccess) ticked else add
}

enum class WebAppMode {
    Default, Compact, Fullscreen;

    companion object {
        fun of(mode: String?): WebAppMode = when (mode) {
            "compact" -> Compact
            "fullscreen" -> Fullscreen
            else -> Default
        }
    }
}

fun keyboardMessage(messages: List<MessageItem>): MessageItem? = messages.lastOrNull { !it.out && !it.pending && it.keyboard != null }

fun isTelegramUrl(url: String): Boolean {
    val trimmed = url.trim().lowercase()
    if (trimmed.startsWith("tg:")) return true
    val host = trimmed.removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore('?').substringBefore('#')
    return host in TELEGRAM_HOSTS || host.endsWith(".t.me")
}

private val TELEGRAM_HOSTS = setOf("t.me", "telegram.me", "telegram.dog", "www.t.me", "www.telegram.me", "www.telegram.dog")

data class BotCommand(val command: String, val description: String, val bot: String? = null, val ephemeral: Boolean = false, val botKey: String? = null) {
    val text: String get() = if (bot == null) "/$command" else "/$command@$bot"
}

fun ephemeralCommandOf(text: String, commands: List<BotCommand>): BotCommand? {
    val call = COMMAND_CALL.matchAt(text, 0) ?: return null
    val name = call.groupValues[1]
    val bot = call.groupValues[2].ifEmpty { null }
    val bots = commands.filter { it.command.equals(name, ignoreCase = true) }
    val command = if (bot == null) bots.firstOrNull() else bots.firstOrNull { it.bot.equals(bot, ignoreCase = true) } ?: bots.firstOrNull { it.bot == null }
    return command?.takeIf { it.ephemeral }
}

fun commandsFor(text: String, commands: List<BotCommand>): List<BotCommand> {
    val prefix = COMMAND_PREFIX.matchEntire(text)?.groupValues?.get(1) ?: return emptyList()
    return commands.filter { it.command.startsWith(prefix, ignoreCase = true) }
}

private val COMMAND_PREFIX = Regex("/([a-zA-Z0-9_]*)")

private val COMMAND_CALL = Regex("/([a-zA-Z0-9_]+)(?:@([a-zA-Z0-9_]+))?(?=\\s|$)")
