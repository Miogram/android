package app.miogram.data.telegram

import cn.ellerotta.core.model.BotButton
import cn.ellerotta.core.model.BotButtonStyle
import cn.ellerotta.core.model.BotButtonMark
import cn.ellerotta.core.model.InlineBot
import cn.ellerotta.core.model.KeyboardChange
import cn.ellerotta.core.model.ReplyKeyboard
import cn.ellerotta.core.model.InlineResult
import cn.ellerotta.core.model.InlineResultKind
import cn.ellerotta.core.model.InlineResults
import cn.ellerotta.core.model.InlineSwitchPm
import cn.ellerotta.core.model.InlineSwitchWebView
import cn.ellerotta.core.model.AttachMenuBot
import cn.ellerotta.core.model.AttachPeerType
import cn.ellerotta.ktproto.api.types.AttachMenuPeerTypeBotPM
import cn.ellerotta.ktproto.api.types.AttachMenuPeerTypeBroadcast
import cn.ellerotta.ktproto.api.types.AttachMenuPeerTypeChat
import cn.ellerotta.ktproto.api.types.AttachMenuPeerTypePM
import cn.ellerotta.ktproto.api.types.AttachMenuPeerTypeSameBotPM
import cn.ellerotta.ktproto.api.types.AttachMenuBot as TlAttachMenuBot
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.ktproto.api.types.BotInlineMediaResult
import cn.ellerotta.ktproto.api.types.BotInlineResult
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.DocumentAttributeImageSize
import cn.ellerotta.ktproto.api.types.DocumentAttributeVideo
import cn.ellerotta.ktproto.api.types.Photo
import cn.ellerotta.ktproto.api.types.TypeBotInlineResult
import cn.ellerotta.ktproto.api.types.TypeDocumentAttribute
import cn.ellerotta.ktproto.api.types.TypeWebDocument
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.WebDocument
import cn.ellerotta.ktproto.api.types.WebDocumentNoProxy
import cn.ellerotta.ktproto.api.types.ButtonTypeDefault
import cn.ellerotta.ktproto.api.types.ButtonTypeRequestGeoLocation
import cn.ellerotta.ktproto.api.types.ButtonTypeRequestPeer
import cn.ellerotta.ktproto.api.types.ButtonTypeRequestPhone
import cn.ellerotta.ktproto.api.types.ButtonTypeRequestPoll
import cn.ellerotta.ktproto.api.types.ButtonTypeSimpleWebView
import cn.ellerotta.ktproto.api.types.InlineButtonTypeBuy
import cn.ellerotta.ktproto.api.types.InlineButtonTypeCallback
import cn.ellerotta.ktproto.api.types.InlineButtonTypeCopy
import cn.ellerotta.ktproto.api.types.InlineButtonTypeDisabled
import cn.ellerotta.ktproto.api.types.InlineButtonTypeGame
import cn.ellerotta.ktproto.api.types.InlineButtonTypeSwitchInline
import cn.ellerotta.ktproto.api.types.InlineButtonTypeUrl
import cn.ellerotta.ktproto.api.types.InlineButtonTypeUrlAuth
import cn.ellerotta.ktproto.api.types.InlineButtonTypeUserProfile
import cn.ellerotta.ktproto.api.types.InlineButtonTypeWebView
import cn.ellerotta.ktproto.api.types.InputButtonTypeRequestPeer
import cn.ellerotta.ktproto.api.types.InputInlineButtonTypeUrlAuth
import cn.ellerotta.ktproto.api.types.InputInlineButtonTypeUserProfile
import cn.ellerotta.ktproto.api.types.KeyboardButton
import cn.ellerotta.ktproto.api.types.KeyboardButtonStyle
import cn.ellerotta.ktproto.api.types.RichButtonStyle
import cn.ellerotta.ktproto.api.types.TypeInlineButtonType
import cn.ellerotta.ktproto.api.types.KeyboardInlineButton
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.ReplyInlineMarkup
import cn.ellerotta.ktproto.api.types.ReplyKeyboardForceReply
import cn.ellerotta.ktproto.api.types.ReplyKeyboardHide
import cn.ellerotta.ktproto.api.types.ReplyKeyboardMarkup
import cn.ellerotta.ktproto.api.types.messages.BotResults

internal fun PeerRegistry.inlineBotOf(user: User, username: String? = usernameOf(user)): InlineBot? {
    if (!user.bot || username == null) return null
    val placeholder = user.botInlinePlaceholder ?: return null
    val key = "u${user.id}"
    return InlineBot(key, username, nameOf(user), placeholder, avatarKey(avatarOf(key, user)))
}

internal fun PeerRegistry.inlineResultsOf(results: BotResults): InlineResults = InlineResults(
    results = results.results.map { inlineResultOf(it, results.queryId) },
    nextOffset = results.nextOffset?.ifEmpty { null },
    gallery = results.gallery,
    switchPm = results.switchPm?.let { InlineSwitchPm(it.text, it.startParam) },
    switchWebView = results.switchWebview?.let { InlineSwitchWebView(it.text, it.url) },
    cacheTime = results.cacheTime,
)

internal fun attachMenuBotOf(bot: TlAttachMenuBot): AttachMenuBot = AttachMenuBot(
    key = "u${bot.botId}",
    shortName = bot.shortName,
    peerTypes = bot.peerTypes.orEmpty().mapTo(HashSet()) { type ->
        when (type) {
            AttachMenuPeerTypeSameBotPM -> AttachPeerType.SameBot
            AttachMenuPeerTypeBotPM -> AttachPeerType.Bots
            AttachMenuPeerTypePM -> AttachPeerType.Users
            AttachMenuPeerTypeChat -> AttachPeerType.Groups
            AttachMenuPeerTypeBroadcast -> AttachPeerType.Channels
        }
    },
    attachMenu = bot.showInAttachMenu,
    sideMenu = bot.showInSideMenu,
    inactive = bot.inactive,
    requestWriteAccess = bot.requestWriteAccess,
    disclaimer = bot.sideMenuDisclaimerNeeded,
)

private fun PeerRegistry.inlineResultOf(result: TypeBotInlineResult, queryId: Long): InlineResult = when (result) {
    is BotInlineResult -> {
        val kind = kindOf(result.type)
        val document = result.thumb ?: result.content?.takeIf { kind == InlineResultKind.Photo }
        val (width, height) = result.content?.let { sizeOf(attributesOf(it)) } ?: (0 to 0)
        InlineResult(queryId, result.id, kind, result.title, result.description, document?.let(::webItem), width, height)
    }
    is BotInlineMediaResult -> {
        val photo = result.photo as? Photo
        val document = result.document as? Document
        val kind = kindOf(result.type)
        when {
            photo != null -> {
                val item = photoItem(photo, null)
                InlineResult(queryId, result.id, kind, result.title, result.description, item?.let(::smallThumb), item?.width ?: 0, item?.height ?: 0)
            }
            document != null -> {
                val item = documentOf(null, document, null)
                val sticker = item.kind == MediaKind.Sticker
                val (width, height) = sizeOf(document.attributes)
                InlineResult(queryId, result.id, if (sticker) InlineResultKind.Sticker else kind, result.title, result.description, if (sticker) item else smallThumb(item), width, height)
            }
            else -> InlineResult(queryId, result.id, kind, result.title, result.description)
        }
    }
}

private fun smallThumb(item: MediaItem): MediaItem? {
    val sizes = item.sizes.sortedBy { it.width }
    val size = sizes.firstOrNull { it.width >= THUMB_MIN } ?: sizes.lastOrNull() ?: return null
    return MediaItem(key = "${item.key}@${size.type}", fileName = "${item.key}_${size.type}.jpg", size = size.bytes, dcId = item.dcId, isPhoto = true, kind = MediaKind.Photo, thumb = item.thumb, width = size.width, height = size.height)
}

private fun PeerRegistry.webItem(document: TypeWebDocument): MediaItem {
    val (url, hash, mimeType) = when (document) {
        is WebDocument -> Triple(document.url, document.accessHash, document.mimeType)
        is WebDocumentNoProxy -> Triple(document.url, null, document.mimeType)
    }
    val key = "web${url.hashCode().toUInt()}_${url.length}"
    webFiles[key] = WebFile(url, hash)
    val (width, height) = sizeOf(attributesOf(document))
    return MediaItem(key = key, fileName = key, size = null, dcId = 0, isPhoto = true, kind = MediaKind.Photo, width = width, height = height, mimeType = mimeType)
}

private fun attributesOf(document: TypeWebDocument): List<TypeDocumentAttribute> = when (document) {
    is WebDocument -> document.attributes
    is WebDocumentNoProxy -> document.attributes
}

private fun sizeOf(attributes: List<TypeDocumentAttribute>): Pair<Int, Int> =
    attributes.firstNotNullOfOrNull { attribute ->
        when (attribute) {
            is DocumentAttributeImageSize -> attribute.w to attribute.h
            is DocumentAttributeVideo -> attribute.w to attribute.h
            else -> null
        }
    } ?: (0 to 0)

private fun kindOf(type: String): InlineResultKind = when (type) {
    "photo" -> InlineResultKind.Photo
    "gif", "mpeg4_gif" -> InlineResultKind.Gif
    "video" -> InlineResultKind.Video
    "sticker" -> InlineResultKind.Sticker
    "audio" -> InlineResultKind.Audio
    "voice" -> InlineResultKind.Voice
    "file", "document" -> InlineResultKind.File
    else -> InlineResultKind.Article
}

private const val THUMB_MIN = 160

internal fun PeerRegistry.buttonsOf(m: Message): List<List<BotButton>> {
    val markup = m.replyMarkup as? ReplyInlineMarkup ?: return emptyList()
    val bot = botOf(m)
    return markup.rows.map { row -> row.buttons.map { inlineButtonOf(it, bot) } }
}

internal fun keyboardChangeOf(m: Message): KeyboardChange? = when (val markup = m.replyMarkup) {
    is ReplyKeyboardMarkup -> KeyboardChange.Show(
        ReplyKeyboard(markup.rows.map { row -> row.buttons.map(::replyButtonOf) }, markup.resize, markup.singleUse, markup.persistent, markup.placeholder, markup.forceReply),
    ).takeIf { !markup.selective || m.mentioned }
    is ReplyKeyboardHide -> KeyboardChange.Hide.takeIf { !markup.selective || m.mentioned }
    is ReplyKeyboardForceReply -> KeyboardChange.ForceReply(markup.placeholder).takeIf { !markup.selective || m.mentioned }
    is ReplyInlineMarkup -> KeyboardChange.ForceReply().takeIf { markup.forceReply }
    else -> null
}

internal fun PeerRegistry.botOf(m: Message): String? {
    val id = m.viaBotId ?: (m.fromId as? PeerUser)?.userId ?: (m.peerId as? PeerUser)?.userId ?: return null
    return usernames[id]
}

private fun inlineButtonOf(button: KeyboardInlineButton, bot: String?): BotButton = buttonOf(button.text, button.type, styleOf(button.style), bot, button.style?.icon)

internal fun buttonOf(text: String, type: TypeInlineButtonType, style: BotButtonStyle, bot: String?, icon: Long? = null): BotButton = when (type) {
    is InlineButtonTypeUrl -> BotButton.Url(text, type.url, style, icon)
    is InlineButtonTypeCallback -> if (type.requiresPassword) BotButton.Unsupported(text, style, icon) else BotButton.Callback(text, type.data, style, icon)
    is InlineButtonTypeSwitchInline -> BotButton.SwitchInline(text, bot, type.query, type.samePeer, style, icon)
    is InlineButtonTypeCopy -> BotButton.Copy(text, type.copyText, style, icon)
    is InlineButtonTypeUserProfile -> BotButton.UserProfile(text, type.userId, style, icon)
    is InlineButtonTypeDisabled -> BotButton.Disabled(text, style, icon)
    is InlineButtonTypeGame -> BotButton.Unsupported(text, style, icon)
    is InlineButtonTypeBuy -> BotButton.Unsupported(text, style, icon, BotButtonMark.Payment)
    is InlineButtonTypeUrlAuth -> BotButton.LoginUrl(text, type.url, type.buttonId, style, icon)
    is InlineButtonTypeWebView -> BotButton.WebApp(text, type.url, simple = false, style = style, icon = icon)
    is InputInlineButtonTypeUrlAuth -> BotButton.Unsupported(text, style, icon, BotButtonMark.Link)
    is InputInlineButtonTypeUserProfile -> BotButton.Unsupported(text, style, icon)
}

private fun replyButtonOf(button: KeyboardButton): BotButton {
    val style = styleOf(button.style)
    val icon = button.style?.icon
    return when (button.type) {
        is ButtonTypeDefault -> BotButton.Text(button.text, style, icon)
        is ButtonTypeSimpleWebView -> BotButton.WebApp(button.text, (button.type as ButtonTypeSimpleWebView).url, simple = true, style = style, icon = icon)
        is ButtonTypeRequestPhone, is ButtonTypeRequestGeoLocation, is ButtonTypeRequestPoll,
        is ButtonTypeRequestPeer, is InputButtonTypeRequestPeer -> BotButton.Unsupported(button.text, style, icon)
    }
}

private fun styleOf(style: KeyboardButtonStyle?): BotButtonStyle = when {
    style == null -> BotButtonStyle.Default
    style.bgDanger -> BotButtonStyle.Danger
    style.bgSuccess -> BotButtonStyle.Success
    style.bgPrimary -> BotButtonStyle.Primary
    else -> BotButtonStyle.Default
}

internal fun richStyleOf(style: RichButtonStyle?): BotButtonStyle = when {
    style == null -> BotButtonStyle.Default
    style.link -> BotButtonStyle.Link
    style.bgDanger -> BotButtonStyle.Danger
    style.bgSuccess -> BotButtonStyle.Success
    style.bgPrimary -> BotButtonStyle.Primary
    else -> BotButtonStyle.Default
}