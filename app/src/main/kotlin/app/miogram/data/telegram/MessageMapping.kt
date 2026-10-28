package app.miogram.data.telegram

import cn.ellerotta.core.model.Draft
import cn.ellerotta.core.model.Venue
import cn.ellerotta.core.model.ProximityAlert
import cn.ellerotta.core.model.ContactCard
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.LiveInfo
import cn.ellerotta.core.model.LivePeriods
import cn.ellerotta.core.model.EPHEMERAL_IDS
import cn.ellerotta.core.model.EphemeralInfo
import cn.ellerotta.ktproto.api.types.EphemeralMessage
import cn.ellerotta.core.model.ForwardInfo
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.LinkPreview
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MediaSize
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.model.normalizeEntities
import cn.ellerotta.core.model.Reactor
import cn.ellerotta.core.model.CallEnd
import cn.ellerotta.core.model.CallInfo
import cn.ellerotta.core.model.RepliesInfo
import cn.ellerotta.ktproto.api.types.MessageReplies
import cn.ellerotta.core.model.stickerOutline
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.DocumentAttributeSticker
import cn.ellerotta.ktproto.api.types.InputDocument
import cn.ellerotta.ktproto.api.types.InputDocumentFileLocation
import cn.ellerotta.ktproto.api.types.InputPhoto
import cn.ellerotta.ktproto.api.types.InputPhotoFileLocation
import cn.ellerotta.ktproto.api.types.InputStickerSetEmpty
import cn.ellerotta.ktproto.api.types.DraftMessage
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import cn.ellerotta.ktproto.api.types.TypeDraftMessage
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.InputMessageEntityMentionName
import cn.ellerotta.ktproto.api.types.InputUser
import cn.ellerotta.ktproto.api.types.MessageEntityBankCard
import cn.ellerotta.ktproto.api.types.MessageEntityBlockquote
import cn.ellerotta.ktproto.api.types.MessageEntityBold
import cn.ellerotta.ktproto.api.types.MessageEntityBotCommand
import cn.ellerotta.ktproto.api.types.MessageEntityCashtag
import cn.ellerotta.ktproto.api.types.MessageEntityCode
import cn.ellerotta.ktproto.api.types.MessageEntityCustomEmoji
import cn.ellerotta.ktproto.api.types.MessageEntityEmail
import cn.ellerotta.ktproto.api.types.MessageEntityFormattedDate
import cn.ellerotta.ktproto.api.types.MessageEntityHashtag
import cn.ellerotta.ktproto.api.types.MessageEntityItalic
import cn.ellerotta.ktproto.api.types.MessageEntityMention
import cn.ellerotta.ktproto.api.types.MessageEntityMentionName
import cn.ellerotta.ktproto.api.types.MessageEntityPhone
import cn.ellerotta.ktproto.api.types.MessageEntityPre
import cn.ellerotta.ktproto.api.types.MessageEntitySpoiler
import cn.ellerotta.ktproto.api.types.MessageEntityStrike
import cn.ellerotta.ktproto.api.types.MessageEntityUnderline
import cn.ellerotta.ktproto.api.types.MessageEntityTextUrl
import cn.ellerotta.ktproto.api.types.MessageEntityUrl
import cn.ellerotta.ktproto.api.types.MessageMediaDocument
import cn.ellerotta.ktproto.api.types.MessageMediaPhoto
import cn.ellerotta.ktproto.api.types.MessageMediaPoll
import cn.ellerotta.ktproto.api.types.MessageMediaContact
import cn.ellerotta.ktproto.api.types.MessageMediaGeo
import cn.ellerotta.ktproto.api.types.MessageMediaGeoLive
import cn.ellerotta.ktproto.api.types.MessageMediaVenue
import cn.ellerotta.ktproto.api.types.GeoPoint
import cn.ellerotta.ktproto.api.types.TypeGeoPoint
import cn.ellerotta.ktproto.api.types.TypeMessageMedia
import cn.ellerotta.ktproto.api.types.MessageMediaWebPage
import cn.ellerotta.ktproto.api.types.MessageFwdHeader
import cn.ellerotta.ktproto.api.types.MessageReplyHeader
import cn.ellerotta.ktproto.api.types.MessageActionTopicCreate
import cn.ellerotta.ktproto.api.types.MessageActionPhoneCall
import cn.ellerotta.ktproto.api.types.PhoneCallDiscardReasonBusy
import cn.ellerotta.ktproto.api.types.PhoneCallDiscardReasonDisconnect
import cn.ellerotta.ktproto.api.types.PhoneCallDiscardReasonHangup
import cn.ellerotta.ktproto.api.types.PhoneCallDiscardReasonMissed
import cn.ellerotta.ktproto.api.types.TypeMessageAction
import cn.ellerotta.ktproto.api.types.TypeMessageReplyHeader
import cn.ellerotta.ktproto.api.types.MessageActionGeoProximityReached
import cn.ellerotta.ktproto.api.types.MessageService
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.Photo
import cn.ellerotta.ktproto.api.types.PhotoCachedSize
import cn.ellerotta.ktproto.api.types.PhotoPathSize
import cn.ellerotta.ktproto.api.types.PhotoSize
import cn.ellerotta.ktproto.api.types.PhotoSizeProgressive
import cn.ellerotta.ktproto.api.types.PhotoStrippedSize
import cn.ellerotta.ktproto.api.types.TypeMessage
import cn.ellerotta.ktproto.api.types.TypePeer
import cn.ellerotta.ktproto.api.types.TypeMessageEntity
import cn.ellerotta.ktproto.api.types.TypePhotoSize
import cn.ellerotta.ktproto.api.types.WebPage
import cn.ellerotta.ktproto.client.expandStrippedThumbnail

internal fun PeerRegistry.itemOf(m: TypeMessage, dialogKey: String): MessageItem? {
    val group = !dialogKey.startsWith("u") && dialogKey != SELF_KEY
    return when (m) {
        is Message -> {
            val from = (m.fromId as? PeerUser)?.userId
            val origin = MessageOrigin(dialogKey, m.id)
            val rich = m.richMessage
            MessageItem(
                id = m.id,
                text = if (rich == null) m.message else "",
                out = m.out,
                date = m.date,
                media = if (rich == null) mediaOf(m, origin) else null,
                senderName = if (group && !m.out) from?.let { userNames[it] } else null,
                senderId = from ?: 0,
                senderRank = if (group && !m.out) m.fromRank?.ifBlank { null } else null,
                senderEmojiStatus = if (group && !m.out) from?.let { userEmojiStatuses[it] } else null,
                read = m.out && m.id <= (readOutbox[dialogKey] ?: 0),
                replyTo = replyOf(m.replyTo),
                topicId = topicOf(m.replyTo),
                edited = m.editDate != null && !m.editHide,
                link = if (rich == null) linkOf(m, origin) else null,
                entities = if (rich == null) entitiesOf(m.message, m.entities.orEmpty()) else emptyList(),
                reactions = reactionsOf(m.reactions),
                reactorsListed = m.reactions?.canSeeList == true,
                forward = forwardOf(m.fwdFrom),
                viaBot = m.viaBotId?.let { usernames[it] },
                buttons = buttonsOf(m),
                keyboard = keyboardChangeOf(m),
                article = rich?.let { articleOf(it, this, RichMessageOrigin(dialogKey, m.id), botOf(m)) },
                threadId = threadOf(m.replyTo),
                replies = repliesOf(m.replies),
                poll = (m.media as? MessageMediaPoll)?.let { pollOf(it.poll, it.results) },
                place = placeOf(m.media, m.date, m.editDate)?.let { senderOf(it, m) },
                contact = contactOf(m.media),
                mediaUnread = m.mediaUnread,
                groupedId = m.groupedId,
                senderAvatar = if (group && !m.out) m.fromId?.let { peerAvatars[peerKey(it)] } else null,
                senderKey = if (group && !m.out) m.fromId?.let(::peerKey) else null,
            )
        }
        is MessageService -> previewOf(m, null, this).let { preview -> MessageItem(
            id = m.id,
            text = "",
            out = m.out,
            date = m.date,
            senderName = (m.fromId as? PeerUser)?.userId?.let { userNames[it] },
            service = preview.service,
            added = preview.added,
            gift = giftActionOf(m.action, MessageOrigin(dialogKey, m.id)),
            topicId = topicIdOf(m),
            call = callOf(m.action),
            wallpaper = wallpaperActionOf(m.action),
            proximity = proximityOf(m.action),
        ) }
        else -> null
    }
}

internal fun PeerRegistry.proximityOf(action: TypeMessageAction): ProximityAlert? {
    val reached = action as? MessageActionGeoProximityReached ?: return null
    val me = selfId()
    fun name(peer: TypePeer): String = when (peer) {
        is PeerUser -> if (peer.userId == me) "" else userNames[peer.userId].orEmpty()
        else -> peerNames[peerKey(peer)].orEmpty()
    }
    return ProximityAlert(name(reached.fromId), name(reached.toId), reached.distance)
}

internal fun callOf(action: TypeMessageAction): CallInfo? = (action as? MessageActionPhoneCall)?.let { call ->
    CallInfo(
        video = call.video,
        end = when (call.reason) {
            null -> null
            PhoneCallDiscardReasonMissed -> CallEnd.Missed
            PhoneCallDiscardReasonBusy -> CallEnd.Busy
            PhoneCallDiscardReasonDisconnect -> CallEnd.Disconnect
            PhoneCallDiscardReasonHangup -> CallEnd.Hangup
            else -> CallEnd.Other
        },
        duration = call.duration ?: 0,
    )
}

internal fun topicIdOf(m: TypeMessage): Int? = when (m) {
    is Message -> topicOf(m.replyTo)
    is MessageService -> if (m.action is MessageActionTopicCreate) m.id else topicOf(m.replyTo)
    else -> null
}

internal fun topicOf(header: TypeMessageReplyHeader?): Int? {
    val reply = header as? MessageReplyHeader ?: return null
    return if (reply.forumTopic) reply.replyToTopId ?: reply.replyToMsgId else null
}

private fun replyOf(header: TypeMessageReplyHeader?): Int? {
    val reply = header as? MessageReplyHeader ?: return null
    if (reply.forumTopic && reply.replyToTopId == null) return null
    return if (reply.replyToEphemeral) reply.replyToMsgId?.let { EPHEMERAL_IDS + it } else reply.replyToMsgId
}

internal fun threadOf(header: TypeMessageReplyHeader?): Int? {
    val reply = header as? MessageReplyHeader ?: return null
    if (reply.forumTopic || reply.replyToEphemeral || reply.replyToPeerId != null) return null
    return reply.replyToTopId ?: reply.replyToMsgId
}

internal fun PeerRegistry.repliesOf(replies: MessageReplies?): RepliesInfo? {
    if (replies == null || (!replies.comments && replies.replies == 0)) return null
    val recent = replies.recentRepliers.orEmpty().mapNotNull(::reactorOf)
    return RepliesInfo(
        count = replies.replies,
        comments = replies.comments,
        recent = recent,
        discussionKey = replies.channelId?.let { "ch$it" },
        maxId = replies.maxId,
        readMaxId = replies.readMaxId,
    )
}

internal fun PeerRegistry.reactorOf(peer: TypePeer): Reactor? {
    val (key, id) = when (peer) {
        is PeerUser -> "u${peer.userId}" to peer.userId
        is PeerChannel -> "ch${peer.channelId}" to peer.channelId
        is PeerChat -> "c${peer.chatId}" to peer.chatId
    }
    return Reactor(id, peerNames[key] ?: return null, peerAvatars[key])
}

internal fun PeerRegistry.ephemeralOf(e: EphemeralMessage, selfId: Long?): Pair<String, MessageItem>? {
    val sender = (e.fromId as? PeerUser)?.userId ?: return null
    val out = e.out || sender == selfId
    val bot = if (out) e.receiverId else sender
    val key = e.peerId?.let(::peerKey) ?: "u$bot"
    val header = e.replyTo as? MessageReplyHeader
    val replyTo = if (e.topMsgId != null && header != null) MessageReplyHeader(forumTopic = true, replyToEphemeral = header.replyToEphemeral, replyToMsgId = header.replyToMsgId, replyToTopId = e.topMsgId) else header
    val message = Message(
        out = out,
        noforwards = e.noforwards,
        invertMedia = e.invertMedia,
        id = e.id,
        fromId = e.fromId,
        peerId = e.peerId ?: PeerUser(bot),
        replyTo = replyTo ?: e.topMsgId?.let { MessageReplyHeader(forumTopic = true, replyToMsgId = it) },
        date = e.date,
        message = e.message,
        media = e.media.takeIf { e.richMessage == null },
        replyMarkup = e.replyMarkup,
        entities = e.entities,
        richMessage = e.richMessage,
    )
    val item = itemOf(message, key) ?: return null
    return key to item.copy(
        id = EPHEMERAL_IDS + e.id,
        read = false,
        reactions = emptyList(),
        ephemeral = EphemeralInfo(e.id, "u$bot", usernames[bot], e.welcomeTemplate),
    )
}

internal fun PeerRegistry.forwardOf(header: MessageFwdHeader?): ForwardInfo? {
    if (header == null) return null
    return when (val from = header.fromId) {
        is PeerChannel -> {
            val key = "ch${from.channelId}"
            val post = header.channelPost?.let { "/$it" }.orEmpty()
            val link = channelUsernames[key]?.let { "https://t.me/$it$post" } ?: "https://t.me/c/${from.channelId}$post"
            ForwardInfo(peerNames[key] ?: header.postAuthor.orEmpty(), link)
        }
        is PeerUser -> ForwardInfo(peerNames["u${from.userId}"] ?: header.fromName.orEmpty(), "tg://user?id=${from.userId}")
        is PeerChat -> ForwardInfo(peerNames["c${from.chatId}"] ?: header.fromName.orEmpty())
        null -> ForwardInfo(header.fromName.orEmpty())
    }
}

internal fun savedDraftOf(draft: TypeDraftMessage?, registry: PeerRegistry? = null): Draft? {
    val saved = draft as? DraftMessage ?: return null
    val article = saved.richMessage?.let { articleOf(it, registry, null, null) }
    if (saved.message.isBlank() && article == null) return null
    return Draft(FormattedText(saved.message, entitiesOf(saved.message, saved.entities.orEmpty())), (saved.replyTo as? InputReplyToMessage)?.replyToMsgId, article)
}

internal fun entitiesOf(text: String, entities: List<TypeMessageEntity>): List<TextEntity> =
    normalizeEntities(text, entities.mapNotNull(::entityOf))

private fun entityOf(entity: TypeMessageEntity): TextEntity? = when (entity) {
    is MessageEntityBold -> TextEntity(entity.offset, entity.length, EntityType.Bold)
    is MessageEntityItalic -> TextEntity(entity.offset, entity.length, EntityType.Italic)
    is MessageEntityUnderline -> TextEntity(entity.offset, entity.length, EntityType.Underline)
    is MessageEntityStrike -> TextEntity(entity.offset, entity.length, EntityType.Strike)
    is MessageEntitySpoiler -> TextEntity(entity.offset, entity.length, EntityType.Spoiler)
    is MessageEntityCode -> TextEntity(entity.offset, entity.length, EntityType.Code)
    is MessageEntityPre -> TextEntity(entity.offset, entity.length, EntityType.Pre(entity.language))
    is MessageEntityBlockquote -> TextEntity(entity.offset, entity.length, EntityType.Blockquote(entity.collapsed))
    is MessageEntityUrl -> TextEntity(entity.offset, entity.length, EntityType.Url)
    is MessageEntityTextUrl -> TextEntity(entity.offset, entity.length, EntityType.TextUrl(entity.url))
    is MessageEntityMention -> TextEntity(entity.offset, entity.length, EntityType.Mention)
    is MessageEntityMentionName -> TextEntity(entity.offset, entity.length, EntityType.MentionName(entity.userId))
    is InputMessageEntityMentionName -> (entity.userId as? InputUser)?.let { TextEntity(entity.offset, entity.length, EntityType.MentionName(it.userId)) }
    is MessageEntityHashtag -> TextEntity(entity.offset, entity.length, EntityType.Hashtag)
    is MessageEntityCashtag -> TextEntity(entity.offset, entity.length, EntityType.Cashtag)
    is MessageEntityBotCommand -> TextEntity(entity.offset, entity.length, EntityType.BotCommand)
    is MessageEntityEmail -> TextEntity(entity.offset, entity.length, EntityType.Email)
    is MessageEntityPhone -> TextEntity(entity.offset, entity.length, EntityType.Phone)
    is MessageEntityBankCard -> TextEntity(entity.offset, entity.length, EntityType.BankCard)
    is MessageEntityCustomEmoji -> TextEntity(entity.offset, entity.length, EntityType.CustomEmoji(entity.documentId))
    is MessageEntityFormattedDate -> TextEntity(
        entity.offset,
        entity.length,
        EntityType.FormattedDate(entity.date, entity.relative, entity.shortTime, entity.longTime, entity.shortDate, entity.longDate, entity.dayOfWeek),
    )
    else -> null
}

internal fun PeerRegistry.inputEntitiesOf(entities: List<TextEntity>): List<TypeMessageEntity> = entities.mapNotNull { entity ->
    val offset = entity.offset
    val length = entity.length
    when (val type = entity.type) {
        EntityType.Bold -> MessageEntityBold(offset, length)
        EntityType.Italic -> MessageEntityItalic(offset, length)
        EntityType.Underline -> MessageEntityUnderline(offset, length)
        EntityType.Strike -> MessageEntityStrike(offset, length)
        EntityType.Spoiler -> MessageEntitySpoiler(offset, length)
        EntityType.Code -> MessageEntityCode(offset, length)
        is EntityType.Pre -> MessageEntityPre(offset, length, type.language)
        is EntityType.Blockquote -> MessageEntityBlockquote(collapsed = type.collapsed, offset = offset, length = length)
        EntityType.Url -> MessageEntityUrl(offset, length)
        is EntityType.TextUrl -> MessageEntityTextUrl(offset, length, type.url)
        EntityType.Mention -> MessageEntityMention(offset, length)
        is EntityType.MentionName -> inputUser(type.userId)?.let { InputMessageEntityMentionName(offset, length, it) }
        EntityType.Hashtag -> MessageEntityHashtag(offset, length)
        EntityType.Cashtag -> MessageEntityCashtag(offset, length)
        EntityType.BotCommand -> MessageEntityBotCommand(offset, length)
        EntityType.Email -> MessageEntityEmail(offset, length)
        EntityType.Phone -> MessageEntityPhone(offset, length)
        EntityType.BankCard -> MessageEntityBankCard(offset, length)
        is EntityType.CustomEmoji -> MessageEntityCustomEmoji(offset, length, type.documentId)
        is EntityType.FormattedDate -> MessageEntityFormattedDate(
            relative = type.relative, shortTime = type.shortTime, longTime = type.longTime, shortDate = type.shortDate,
            longDate = type.longDate, dayOfWeek = type.dayOfWeek, offset = offset, length = length, date = type.date,
        )
    }
}

internal fun keyOf(m: TypeMessage): String? {
    val peer = when (m) {
        is Message -> m.peerId
        is MessageService -> m.peerId
        else -> return null
    }
    return peerKey(peer)
}

private fun PeerRegistry.mediaOf(message: Message, origin: MessageOrigin): MediaItem? = when (val media = message.media) {
    is MessageMediaPhoto -> (media.photo as? Photo)?.let { photoItem(it, origin) }?.let { if (media.spoiler) it.copy(spoiler = true) else it }
    is MessageMediaDocument -> (media.document as? Document)?.let { doc -> documentOf(media, doc, origin).let { if (media.spoiler) it.copy(spoiler = true) else it } }
    else -> null
}

private fun PeerRegistry.linkOf(message: Message, origin: MessageOrigin): LinkPreview? {
    val page = (message.media as? MessageMediaWebPage)?.webpage as? WebPage ?: return null
    return LinkPreview(
        url = page.url,
        siteName = page.siteName,
        title = page.title,
        description = page.description,
        photo = (page.photo as? Photo)?.let { photoItem(it, origin) },
    )
}

internal fun PeerRegistry.photoItem(photo: Photo, origin: FileOrigin?): MediaItem? {
    val sizes = sizesOf(photo.sizes)
    val largest = sizes.maxByOrNull { it.width * it.height } ?: return null
    val key = "photo${photo.id}"
    sizes.forEach { size -> locations["$key@${size.type}"] = InputPhotoFileLocation(photo.id, photo.accessHash, photo.fileReference, size.type) }
    locations[key] = InputPhotoFileLocation(photo.id, photo.accessHash, photo.fileReference, largest.type)
    inputPhotos[key] = InputPhoto(photo.id, photo.accessHash, photo.fileReference)
    origin?.let { origins[key] = it }
    return MediaItem(
        key = key,
        fileName = "photo_${photo.id}_${largest.type}.jpg",
        size = largest.bytes,
        dcId = photo.dcId,
        isPhoto = true,
        kind = MediaKind.Photo,
        thumb = thumbOf(photo.sizes),
        width = largest.width,
        height = largest.height,
        sizes = sizes,
        mimeType = "image/jpeg",
    )
}

internal fun PeerRegistry.documentOf(media: MessageMediaDocument?, doc: Document, origin: FileOrigin?): MediaItem {
    val key = "doc${doc.id}"
    locations[key] = InputDocumentFileLocation(doc.id, doc.accessHash, doc.fileReference, "")
    inputDocuments[key] = InputDocument(doc.id, doc.accessHash, doc.fileReference)
    doc.attributes.filterIsInstance<DocumentAttributeSticker>().firstOrNull()?.stickerset?.takeIf { it != InputStickerSetEmpty }?.let { stickerSetOf[key] = it }
    val thumbs = doc.thumbs.orEmpty()
    val sizes = sizesOf(thumbs)
    sizes.forEach { size -> locations["$key@${size.type}"] = InputDocumentFileLocation(doc.id, doc.accessHash, doc.fileReference, size.type) }
    origin?.let { origins[key] = it }
    return documentItem(media, doc).copy(thumb = thumbOf(thumbs), sizes = sizes, outline = outlineOf(thumbs))
}

private fun sizesOf(sizes: List<TypePhotoSize>): List<MediaSize> = sizes.mapNotNull { size ->
    when (size) {
        is PhotoSize -> MediaSize(size.type, size.w, size.h, size.size.toLong())
        is PhotoSizeProgressive -> MediaSize(size.type, size.w, size.h, (size.sizes.maxOrNull() ?: 0).toLong())
        else -> null
    }
}

internal fun outlineOf(sizes: List<TypePhotoSize>): String? =
    sizes.filterIsInstance<PhotoPathSize>().firstOrNull { it.type == "j" }?.bytes?.takeIf { it.isNotEmpty() }?.let(::stickerOutline)

private fun thumbOf(sizes: List<TypePhotoSize>): ByteArray? =
    sizes.filterIsInstance<PhotoStrippedSize>().firstOrNull()?.bytes?.let(::expandStrippedThumbnail)
        ?: sizes.filterIsInstance<PhotoCachedSize>().firstOrNull()?.bytes

internal fun placeOf(media: TypeMessageMedia?, date: Int, editDate: Int? = null): GeoPlace? {
    fun point(geo: TypeGeoPoint) = geo as? GeoPoint
    return when (media) {
        is MessageMediaGeo -> point(media.geo)?.let { GeoPlace(it.lat, it.long, accessHash = it.accessHash, accuracy = it.accuracyRadius) }
        is MessageMediaVenue -> point(media.geo)?.let {
            val venue = media.venueId.takeIf { id -> id.isNotBlank() }?.let { id -> Venue(media.provider, id, media.venueType) }
            GeoPlace(it.lat, it.long, media.title.ifBlank { null }, media.address.ifBlank { null }, venue = venue, accessHash = it.accessHash, accuracy = it.accuracyRadius)
        }
        is MessageMediaGeoLive -> {
            val geo = point(media.geo)
            val expires = if (media.period >= LivePeriods.FOREVER) null else date + media.period
            GeoPlace(
                geo?.lat ?: 0.0, geo?.long ?: 0.0,
                live = LiveInfo(media.period, media.heading, media.proximityNotificationRadius, expires, stopped = geo == null, updated = editDate ?: date),
                accessHash = geo?.accessHash, accuracy = geo?.accuracyRadius,
            )
        }
        else -> null
    }
}

private fun PeerRegistry.senderOf(place: GeoPlace, m: Message): GeoPlace {
    val live = place.live ?: return place
    val sender = m.fromId ?: if (m.out) selfId()?.let(::PeerUser) else m.peerId
    val key = sender?.let(::peerKey) ?: return place
    val name = peerNames[key] ?: (sender as? PeerUser)?.userId?.let { userNames[it] }
    return place.copy(live = live.copy(senderKey = key, senderName = name, senderAvatar = peerAvatars[key]))
}

internal fun contactOf(media: TypeMessageMedia?): ContactCard? =
    (media as? MessageMediaContact)?.let { ContactCard(it.firstName, it.lastName, it.phoneNumber, it.userId, it.vcard) }
