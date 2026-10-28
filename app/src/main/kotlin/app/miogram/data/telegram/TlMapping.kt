package app.miogram.data.telegram

import cn.ellerotta.core.model.ActivityKind
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.Preview
import cn.ellerotta.core.model.ServiceKind
import cn.ellerotta.ktproto.api.types.SendMessageChooseStickerAction
import cn.ellerotta.ktproto.api.types.SendMessageGamePlayAction
import cn.ellerotta.ktproto.api.types.SendMessageRecordAudioAction
import cn.ellerotta.ktproto.api.types.SendMessageRecordRoundAction
import cn.ellerotta.ktproto.api.types.SendMessageRecordVideoAction
import cn.ellerotta.ktproto.api.types.SendMessageUploadAudioAction
import cn.ellerotta.ktproto.api.types.SendMessageUploadDocumentAction
import cn.ellerotta.ktproto.api.types.SendMessageUploadPhotoAction
import cn.ellerotta.ktproto.api.types.SendMessageUploadRoundAction
import cn.ellerotta.ktproto.api.types.SendMessageUploadVideoAction
import cn.ellerotta.ktproto.api.types.TypeSendMessageAction
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.ChannelForbidden
import cn.ellerotta.ktproto.api.types.Chat
import cn.ellerotta.ktproto.api.types.ChatEmpty
import cn.ellerotta.ktproto.api.types.ChatForbidden
import cn.ellerotta.ktproto.api.types.Community
import cn.ellerotta.ktproto.api.types.CommunityForbidden
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.DocumentAttributeAnimated
import cn.ellerotta.ktproto.api.types.DocumentAttributeAudio
import cn.ellerotta.ktproto.api.types.DocumentAttributeCustomEmoji
import cn.ellerotta.ktproto.api.types.DocumentAttributeFilename
import cn.ellerotta.ktproto.api.types.DocumentAttributeSticker
import cn.ellerotta.ktproto.api.types.DocumentAttributeVideo
import cn.ellerotta.ktproto.api.types.InputPeerChannel
import cn.ellerotta.ktproto.api.types.InputPeerChat
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageActionChannelCreate
import cn.ellerotta.ktproto.api.types.MessageActionChatAddUser
import cn.ellerotta.ktproto.api.types.MessageActionChatCreate
import cn.ellerotta.ktproto.api.types.MessageActionChatDeleteUser
import cn.ellerotta.ktproto.api.types.MessageActionChatEditPhoto
import cn.ellerotta.ktproto.api.types.MessageActionChatEditTitle
import cn.ellerotta.ktproto.api.types.MessageActionChatJoinedByLink
import cn.ellerotta.ktproto.api.types.MessageActionChatJoinedByRequest
import cn.ellerotta.ktproto.api.types.MessageActionPhoneCall
import cn.ellerotta.ktproto.api.types.MessageActionPinMessage
import cn.ellerotta.ktproto.api.types.MessageActionSetChatWallPaper
import cn.ellerotta.ktproto.api.types.MessageActionStarGift
import cn.ellerotta.ktproto.api.types.MessageActionStarGiftUnique
import cn.ellerotta.ktproto.api.types.MessageEntitySpoiler
import cn.ellerotta.ktproto.api.types.MessageMediaContact
import cn.ellerotta.ktproto.api.types.MessageMediaDocument
import cn.ellerotta.ktproto.api.types.MessageMediaGeo
import cn.ellerotta.ktproto.api.types.MessageMediaGeoLive
import cn.ellerotta.ktproto.api.types.MessageMediaPhoto
import cn.ellerotta.ktproto.api.types.MessageMediaPoll
import cn.ellerotta.ktproto.api.types.MessageMediaVenue
import cn.ellerotta.ktproto.api.types.MessageActionGeoProximityReached
import cn.ellerotta.ktproto.api.types.MessageMediaWebPage
import cn.ellerotta.ktproto.api.types.MessageService
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.TypeChat
import cn.ellerotta.ktproto.api.types.TypeInputPeer
import cn.ellerotta.ktproto.api.types.TypeMessage
import cn.ellerotta.ktproto.api.types.TypeMessageMedia
import cn.ellerotta.ktproto.api.types.TypePeer
import cn.ellerotta.ktproto.api.types.TypeUpdates
import cn.ellerotta.ktproto.api.types.TypeUserStatus
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.UserStatusHidden
import cn.ellerotta.ktproto.api.types.UserStatusLastMonth
import cn.ellerotta.ktproto.api.types.UserStatusLastWeek
import cn.ellerotta.ktproto.api.types.UserStatusOffline
import cn.ellerotta.ktproto.api.types.UserStatusOnline
import cn.ellerotta.ktproto.api.types.UserStatusRecently
import cn.ellerotta.ktproto.api.types.messages.ChatInviteJoinResultOk
import cn.ellerotta.ktproto.api.types.messages.ChatInviteJoinResultWebView
import cn.ellerotta.ktproto.api.types.messages.TypeChatInviteJoinResult
import cn.ellerotta.ktproto.client.PushNotification
import cn.ellerotta.ktproto.client.decodeWaveform

class JoinBotCheckException : IllegalStateException("a bot checks who joins this chat")

internal fun joinedUpdates(result: TypeChatInviteJoinResult): TypeUpdates = when (result) {
    is ChatInviteJoinResultOk -> result.updates
    is ChatInviteJoinResultWebView -> throw JoinBotCheckException()
}

internal fun peerKey(peer: TypePeer?): String = when (peer) {
    is PeerUser -> "u${peer.userId}"
    is PeerChat -> "c${peer.chatId}"
    is PeerChannel -> "ch${peer.channelId}"
    null -> ""
}

internal fun peerOfKey(key: String): TypePeer? = when {
    key.startsWith("ch") -> key.substring(2).toLongOrNull()?.let(::PeerChannel)
    key.startsWith("c") -> key.substring(1).toLongOrNull()?.let(::PeerChat)
    key.startsWith("u") -> key.substring(1).toLongOrNull()?.let(::PeerUser)
    else -> null
}

internal fun inputPeerKey(peer: TypeInputPeer, selfId: Long?): String? = when (peer) {
    is InputPeerUser -> "u${peer.userId}"
    is InputPeerSelf -> selfId?.let { "u$it" }
    is InputPeerChat -> "c${peer.chatId}"
    is InputPeerChannel -> "ch${peer.channelId}"
    else -> null
}

internal fun peerOf(message: TypeMessage): TypePeer? = when (message) {
    is Message -> message.peerId
    is MessageService -> message.peerId
    else -> null
}

internal fun idOf(message: TypeMessage): Int = when (message) {
    is Message -> message.id
    is MessageService -> message.id
    else -> 0
}

internal fun dateOf(message: TypeMessage?): Int = when (message) {
    is Message -> message.date
    is MessageService -> message.date
    else -> 0
}

internal fun chatId(chat: TypeChat): Long = when (chat) {
    is Chat -> chat.id
    is Channel -> chat.id
    is ChatForbidden -> chat.id
    is ChannelForbidden -> chat.id
    is ChatEmpty -> chat.id
    is Community -> chat.id
    is CommunityForbidden -> chat.id
}

internal fun titleOf(chat: TypeChat): String = when (chat) {
    is Chat -> chat.title
    is Channel -> chat.title
    is ChatForbidden -> chat.title
    is ChannelForbidden -> chat.title
    is ChatEmpty -> "chat ${chat.id}"
    is Community -> chat.title
    is CommunityForbidden -> chat.title
}

internal fun nameOf(user: User): String =
    listOfNotNull(user.firstName, user.lastName).joinToString(" ").ifBlank { user.username ?: "user ${user.id}" }

internal fun isOnline(user: User, nowSeconds: Long): Boolean = (user.status as? UserStatusOnline)?.let { it.expires > nowSeconds } ?: false

internal fun mediaKindOf(media: TypeMessageMedia?): MediaKind? = when (media) {
    null -> null
    is MessageMediaPhoto -> MediaKind.Photo
    is MessageMediaGeo, is MessageMediaGeoLive, is MessageMediaVenue -> MediaKind.Location
    is MessageMediaContact -> MediaKind.Contact
    is MessageMediaPoll -> MediaKind.Poll
    is MessageMediaWebPage -> MediaKind.Link
    is MessageMediaDocument -> documentKind(media.document as? Document, media)
    else -> MediaKind.Other
}

internal fun documentKind(doc: Document?, media: MessageMediaDocument? = null): MediaKind {
    val attributes = doc?.attributes.orEmpty()
    val video = attributes.filterIsInstance<DocumentAttributeVideo>().firstOrNull()
    val audio = attributes.filterIsInstance<DocumentAttributeAudio>().firstOrNull()
    return when {
        attributes.any { it is DocumentAttributeSticker || it is DocumentAttributeCustomEmoji } -> MediaKind.Sticker
        attributes.any { it is DocumentAttributeAnimated } -> MediaKind.Gif
        media?.round == true || video?.roundMessage == true -> MediaKind.Round
        media?.voice == true || audio?.voice == true -> MediaKind.Voice
        audio != null -> MediaKind.Music
        video != null || media?.video == true -> MediaKind.Video
        else -> MediaKind.File
    }
}

internal fun stickerEmoji(media: TypeMessageMedia?): String? =
    ((media as? MessageMediaDocument)?.document as? Document)?.let(::stickerAlt)

internal fun stickerAlt(doc: Document): String? = doc.attributes.firstNotNullOfOrNull { attribute ->
    when (attribute) {
        is DocumentAttributeSticker -> attribute.alt
        is DocumentAttributeCustomEmoji -> attribute.alt
        else -> null
    }
}

internal fun documentItem(media: MessageMediaDocument?, doc: Document): MediaItem {
    val kind = documentKind(doc, media)
    val audio = doc.attributes.filterIsInstance<DocumentAttributeAudio>().firstOrNull()
    val video = doc.attributes.filterIsInstance<DocumentAttributeVideo>().firstOrNull()
    val voice = kind == MediaKind.Voice
    val name = doc.attributes.filterIsInstance<DocumentAttributeFilename>().firstOrNull()?.fileName
        ?: if (voice) "voice_${doc.id}.ogg" else "file_${doc.id}" + extensionOf(doc.mimeType)
    val music = kind == MediaKind.Music
    val duration = when {
        voice || music -> audio?.duration ?: 0
        video != null && kind != MediaKind.Sticker -> kotlin.math.round(video.duration).toInt()
        else -> 0
    }
    return MediaItem(
        key = "doc${doc.id}",
        fileName = name.replace('/', '_'),
        size = doc.size,
        dcId = doc.dcId,
        isPhoto = false,
        kind = kind,
        emoji = if (kind == MediaKind.Sticker) stickerAlt(doc) else null,
        animated = doc.mimeType == "application/x-tgsticker",
        width = video?.w ?: 0,
        height = video?.h ?: 0,
        duration = duration,
        waveform = if (voice) audio?.waveform?.let { decodeWaveform(it).toList() }.orEmpty() else emptyList(),
        mimeType = doc.mimeType,
        textColor = doc.attributes.any { it is DocumentAttributeCustomEmoji && it.textColor },
        title = if (music) audio?.title else null,
        performer = if (music) audio?.performer else null,
        documentId = doc.id,
    )
}

private fun extensionOf(mimeType: String): String = when (mimeType) {
    "video/mp4" -> ".mp4"
    "video/webm" -> ".webm"
    "image/webp" -> ".webp"
    "audio/mpeg" -> ".mp3"
    "audio/mp4", "audio/x-m4a" -> ".m4a"
    "audio/ogg" -> ".ogg"
    "audio/flac" -> ".flac"
    "application/x-tgsticker" -> ".tgs"
    else -> ""
}

internal fun previewOf(message: TypeMessage?, sender: String?, peers: PeerRegistry? = null): Preview = when (message) {
    is Message -> message.richMessage?.let { Preview(sender = sender, article = articleOf(it, null, null, null)) } ?: run {
        val media = mediaKindOf(message.media)
        val text = message.message.ifBlank { if (media == MediaKind.Sticker) stickerEmoji(message.media).orEmpty() else "" }
        val spoilers = message.entities.orEmpty().filterIsInstance<MessageEntitySpoiler>().map { it.offset until it.offset + it.length }
        Preview(text = text, sender = sender, media = media.takeIf { it != MediaKind.Link || message.message.isBlank() }, hidden = spoilers)
    }
    is MessageService -> {
        val added = addedOf(message, peers)
        Preview(
            sender = sender,
            added = added.orEmpty(),
            service = when (message.action) {
                is MessageActionChatCreate, is MessageActionChannelCreate -> ServiceKind.Created
                is MessageActionChatAddUser -> if (added == null) ServiceKind.Joined else ServiceKind.Added
                is MessageActionChatJoinedByLink, is MessageActionChatJoinedByRequest -> ServiceKind.Joined
                is MessageActionChatDeleteUser -> ServiceKind.Left
                is MessageActionPinMessage -> ServiceKind.Pinned
                is MessageActionChatEditTitle -> ServiceKind.TitleChanged
                is MessageActionChatEditPhoto -> ServiceKind.PhotoChanged
                is MessageActionPhoneCall -> ServiceKind.Call
                is MessageActionStarGift, is MessageActionStarGiftUnique -> ServiceKind.Gift
                is MessageActionSetChatWallPaper -> ServiceKind.Wallpaper
                is MessageActionGeoProximityReached -> ServiceKind.Proximity
                else -> ServiceKind.Other
            },
        )
    }
    else -> Preview()
}

internal fun addedOf(message: MessageService, peers: PeerRegistry?): List<String>? {
    val action = message.action as? MessageActionChatAddUser ?: return null
    val from = (message.fromId as? PeerUser)?.userId
    if (from != null && action.users.singleOrNull() == from) return null
    val me = peers?.selfId()
    return action.users.mapNotNull { if (it == me) "" else peers?.userNames?.get(it) }
}

internal fun presenceOf(status: TypeUserStatus?, nowSeconds: Long): Presence = when (status) {
    is UserStatusOnline -> if (status.expires > nowSeconds) Presence.Online else Presence.Offline(status.expires)
    is UserStatusOffline -> Presence.Offline(status.wasOnline)
    is UserStatusRecently -> Presence.Recently
    is UserStatusHidden -> Presence.Recently
    is UserStatusLastWeek -> Presence.LastWeek
    is UserStatusLastMonth -> Presence.LastMonth
    else -> Presence.LongAgo
}

internal fun activityOf(action: TypeSendMessageAction): ActivityKind = when (action) {
    SendMessageRecordAudioAction -> ActivityKind.RecordVoice
    is SendMessageUploadAudioAction -> ActivityKind.UploadVoice
    is SendMessageUploadPhotoAction -> ActivityKind.UploadPhoto
    is SendMessageUploadVideoAction -> ActivityKind.UploadVideo
    is SendMessageUploadDocumentAction -> ActivityKind.UploadFile
    SendMessageRecordVideoAction, SendMessageRecordRoundAction -> ActivityKind.RecordRound
    is SendMessageUploadRoundAction -> ActivityKind.UploadRound
    SendMessageChooseStickerAction -> ActivityKind.ChooseSticker
    SendMessageGamePlayAction -> ActivityKind.PlayGame
    else -> ActivityKind.Typing
}

internal fun colorIndex(color: Int?, id: Long): Int = ((color?.toLong() ?: id) % 7).toInt().let { if (it < 0) it + 7 else it }

internal fun pushMessageOf(push: PushNotification): PushMessage {
    val key = push.channelId?.let { "ch$it" } ?: push.chatId?.let { "c$it" } ?: push.fromId?.let { "u$it" }
    val type = push.locKey
    val (kind, rest) = when {
        type.startsWith("CHANNEL_MESSAGE_") -> PeerKind.Channel to type.removePrefix("CHANNEL_MESSAGE_")
        type.startsWith("CHAT_MESSAGE_") -> PeerKind.Group to type.removePrefix("CHAT_MESSAGE_")
        type.startsWith("MESSAGE_") && type !in SERVICE_PUSHES -> PeerKind.User to type.removePrefix("MESSAGE_")
        else -> null to ""
    }
    val args = push.locArgs
    val textAt = if (kind == PeerKind.Group) 2 else 1
    val action = when {
        type == "SESSION_REVOKE" -> PushAction.Revoke
        type == "DC_UPDATE" -> PushAction.DcUpdate
        key == null -> PushAction.Ignore
        type == "MESSAGE_DELETED" -> PushAction.Delete
        type == "READ_HISTORY" -> PushAction.Read
        kind != null -> PushAction.Show
        else -> PushAction.Ignore
    }
    return PushMessage(
        action = action,
        dialogKey = key,
        kind = kind,
        title = args.getOrNull(if (kind == PeerKind.Group) 1 else 0).orEmpty(),
        sender = if (kind == PeerKind.Group) args.getOrNull(0) else null,
        text = if (rest == "TEXT") args.getOrNull(textAt).orEmpty() else "",
        media = if (kind == null || rest == "TEXT") null else pushMedia(rest),
        messageId = push.msgId,
        messageIds = push.messages,
        maxId = push.maxId,
        silent = push.silent,
        senderKey = if (kind == PeerKind.Group) push.chatFromId?.let { "u$it" } else key,
        mention = push.mention,
        userId = push.userId,
        dc = (push.custom["dc"] as? Number)?.toInt() ?: (push.custom["dc"] as? String)?.trim()?.toIntOrNull(),
        address = push.custom["addr"] as? String,
    )
}

private val SERVICE_PUSHES = setOf("MESSAGE_DELETED", "MESSAGE_MUTED", "MESSAGE_ANNOUNCEMENT")

private fun pushMedia(type: String): MediaKind = when (type) {
    "PHOTO", "PHOTOS", "PHOTO_SECRET" -> MediaKind.Photo
    "VIDEO", "VIDEOS", "VIDEO_SECRET" -> MediaKind.Video
    "ROUND" -> MediaKind.Round
    "AUDIO" -> MediaKind.Voice
    "PLAYLIST" -> MediaKind.Music
    "DOC", "DOCS" -> MediaKind.File
    "STICKER" -> MediaKind.Sticker
    "GIF" -> MediaKind.Gif
    "GEO", "GEOLIVE" -> MediaKind.Location
    "CONTACT" -> MediaKind.Contact
    "POLL", "QUIZ" -> MediaKind.Poll
    else -> MediaKind.Other
}
