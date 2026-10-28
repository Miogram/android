package app.miogram.data.telegram

import cn.ellerotta.core.model.EmojiCategoryGroup
import cn.ellerotta.core.model.FeaturedSet
import cn.ellerotta.core.model.FeaturedSets
import cn.ellerotta.core.model.GifPage
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.StickerSetDetail
import cn.ellerotta.core.model.StickerSetInfo
import cn.ellerotta.ktproto.api.functions.channels.GetMessages as ChannelsGetMessages
import cn.ellerotta.ktproto.api.functions.contacts.ResolveUsername
import cn.ellerotta.ktproto.api.functions.messages.GetAllStickers
import cn.ellerotta.ktproto.api.functions.messages.GetCustomEmojiDocuments
import cn.ellerotta.ktproto.api.functions.messages.GetEmojiGroups
import cn.ellerotta.ktproto.api.functions.messages.GetEmojiStickerGroups
import cn.ellerotta.ktproto.api.functions.messages.GetEmojiStickers
import cn.ellerotta.ktproto.api.functions.messages.GetFavedStickers
import cn.ellerotta.ktproto.api.functions.messages.GetFeaturedEmojiStickers
import cn.ellerotta.ktproto.api.functions.messages.GetFeaturedStickers
import cn.ellerotta.ktproto.api.functions.messages.GetInlineBotResults
import cn.ellerotta.ktproto.api.functions.messages.GetMessages
import cn.ellerotta.ktproto.api.functions.messages.GetRichMessage
import cn.ellerotta.ktproto.api.functions.messages.GetRecentStickers
import cn.ellerotta.ktproto.api.functions.messages.GetSavedGifs
import cn.ellerotta.ktproto.api.functions.messages.GetStickerSet
import cn.ellerotta.ktproto.api.functions.messages.GetStickers
import cn.ellerotta.ktproto.api.functions.messages.SearchStickers
import cn.ellerotta.ktproto.api.types.BotInlineMediaResult
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.EmojiGroup
import cn.ellerotta.ktproto.api.types.EmojiGroupGreeting
import cn.ellerotta.ktproto.api.types.EmojiGroupPremium
import cn.ellerotta.ktproto.api.types.InputPeerEmpty
import cn.ellerotta.ktproto.api.types.InputMessageID
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.InputStickerSetID
import cn.ellerotta.ktproto.api.types.InputStickerSetThumb
import cn.ellerotta.ktproto.api.types.PhotoSize
import cn.ellerotta.ktproto.api.types.StickerSet
import cn.ellerotta.ktproto.api.types.StickerSetCovered
import cn.ellerotta.ktproto.api.types.StickerSetFullCovered
import cn.ellerotta.ktproto.api.types.StickerSetMultiCovered
import cn.ellerotta.ktproto.api.types.StickerSetNoCovered
import cn.ellerotta.ktproto.api.types.TypeDocument
import cn.ellerotta.ktproto.api.types.TypeInputStickerSet
import cn.ellerotta.ktproto.api.types.TypeInputUser
import cn.ellerotta.ktproto.api.types.messages.TypeAllStickers
import cn.ellerotta.ktproto.api.types.messages.TypeFavedStickers
import cn.ellerotta.ktproto.api.types.messages.TypeRecentStickers
import cn.ellerotta.ktproto.api.types.messages.TypeSavedGifs
import cn.ellerotta.ktproto.api.types.messages.AllStickers
import cn.ellerotta.ktproto.api.types.messages.ChannelMessages
import cn.ellerotta.ktproto.api.types.messages.EmojiGroups
import cn.ellerotta.ktproto.api.types.messages.FavedStickers
import cn.ellerotta.ktproto.api.types.messages.FeaturedStickers
import cn.ellerotta.ktproto.api.types.messages.FoundStickers
import cn.ellerotta.ktproto.api.types.messages.Messages
import cn.ellerotta.ktproto.api.types.messages.MessagesSlice
import cn.ellerotta.ktproto.api.types.messages.RecentStickers
import cn.ellerotta.ktproto.api.types.messages.SavedGifs
import cn.ellerotta.ktproto.api.types.messages.StickerSet as MessagesStickerSet
import cn.ellerotta.ktproto.api.types.messages.Stickers
import cn.ellerotta.ktproto.tl.decode
import kotlinx.coroutines.CancellationException

internal suspend fun TelegramClientHolder.refetch(origin: FileOrigin) {
    when (origin) {
        is MessageOrigin -> refetchMessage(origin)
        is RichMessageOrigin -> richMessage(origin.dialogKey, origin.id)
        is StickerSetOrigin -> stickerSet(origin.set)
        RecentStickersOrigin -> recentStickers(0)
        FavedStickersOrigin -> favedStickers(0)
        is EmojiStickersOrigin -> stickersFor(origin.emoji)
        is GiftsOrigin -> savedGifts(origin.dialogKey, "", GIFTS_PAGE)
        is ProfilePhotosOrigin -> profilePhotos(origin.dialogKey)
        is ProfileMusicOrigin -> profileMusic(origin.dialogKey)
        is CustomEmojiOrigin -> customEmojiDocuments(listOf(origin.documentId), fresh = true)
        AvailableReactionsOrigin -> availableReactions(0)
        SavedGifsOrigin -> savedGifs(0)
        is GifSearchOrigin -> foundGifs(origin.query, origin.offset)
        is StickerSearchOrigin -> foundStickers(origin.query, origin.languages, origin.emoticon, origin.emojis)
        is StoryOrigin -> storiesById(origin.peerKey, listOf(origin.storyId))
    }
}

internal suspend fun TelegramClientHolder.customEmojiDocuments(ids: Collection<Long>, fresh: Boolean = false): Map<Long, MediaItem> {
    val wanted = ids.distinct()
    val known = if (fresh) emptyMap() else wanted.mapNotNull { id -> peers.customEmoji[id]?.let { id to it } }.toMap()
    val stored = cache?.takeIf { !fresh && wanted.any { it !in known } }?.customEmoji(wanted.filter { it !in known }).orEmpty()
        .associate { doc -> doc.id to peers.documentOf(null, doc, CustomEmojiOrigin(doc.id)).also { peers.customEmoji[doc.id] = it } }
    val fetched = wanted.filter { it !in known && it !in stored }.chunked(CUSTOM_EMOJI_BATCH).flatMap { chunk ->
        val documents = client().invoke(GetCustomEmojiDocuments(chunk)).filterIsInstance<Document>()
        cache?.putCustomEmoji(documents)
        documents.map { doc -> doc.id to peers.documentOf(null, doc, CustomEmojiOrigin(doc.id)).also { peers.customEmoji[doc.id] = it } }
    }.toMap()
    return known + stored + fetched
}

internal const val CUSTOM_EMOJI_BATCH = 200

internal suspend fun TelegramClientHolder.refreshReference(mediaKey: String): Boolean {
    val origin = peers.origins[mediaKey.substringBefore('@')] ?: return false
    refetch(origin)
    log("file reference of $mediaKey refreshed")
    return true
}

private suspend fun TelegramClientHolder.refetchMessage(origin: MessageOrigin) {
    val ids = listOf(InputMessageID(origin.id))
    val result = if (peerOfKey(origin.dialogKey) is PeerChannel) {
        client().invoke(ChannelsGetMessages(peers.channel(origin.dialogKey), ids))
    } else {
        client().invoke(GetMessages(ids))
    }
    val messages = when (result) {
        is Messages -> result.messages
        is MessagesSlice -> result.messages
        is ChannelMessages -> result.messages
        else -> emptyList()
    }
    messages.forEach { peers.itemOf(it, origin.dialogKey) }
}

internal suspend fun TelegramClientHolder.richMessage(dialogKey: String, id: Int): MessageItem? {
    val result = client().invoke(GetRichMessage(peers.peer(dialogKey), id))
    val (messages, users) = when (result) {
        is Messages -> result.messages to result.users
        is MessagesSlice -> result.messages to result.users
        is ChannelMessages -> result.messages to result.users
        else -> return null
    }
    peers.rememberUsers(users)
    return messages.firstNotNullOfOrNull { message -> peers.itemOf(message, dialogKey)?.takeIf { it.article != null } }
}

internal suspend fun TelegramClientHolder.stickerSet(input: TypeInputStickerSet): StickerSetDetail? = stickerSetChanged(input, 0)?.detail

internal class FetchedSet(val detail: StickerSetDetail, val hash: Int)

internal suspend fun TelegramClientHolder.stickerSetChanged(input: TypeInputStickerSet, hash: Int): FetchedSet? {
    val result = client().invoke(GetStickerSet(input, hash)) as? MessagesStickerSet ?: return null
    val origin = StickerSetOrigin(InputStickerSetID(result.set.id, result.set.accessHash))
    val stickers = stickersOf(result.documents, origin)
    return FetchedSet(StickerSetDetail(setInfos(listOf(result.set), stickers).single(), stickers), result.set.hash)
}

internal suspend fun TelegramClientHolder.allStickers(emoji: Boolean, hash: Long): Pair<Long, List<StickerSetInfo>>? {
    val answer = if (emoji) client().invoke(GetEmojiStickers(hash)) else client().invoke(GetAllStickers(hash))
    val result = answer as? AllStickers ?: return null
    cache?.putStickerList(if (emoji) StickerLists.EMOJI_SETS else StickerLists.SETS, result)
    return result.hash to setInfos(result.sets)
}

internal suspend fun TelegramClientHolder.storedAllStickers(emoji: Boolean): Pair<Long, List<StickerSetInfo>>? =
    (cache?.stickerList(if (emoji) StickerLists.EMOJI_SETS else StickerLists.SETS) { TypeAllStickers.decode(it) } as? AllStickers)?.let { it.hash to setInfos(it.sets) }

internal object StickerLists {
    const val SETS = "sets"
    const val EMOJI_SETS = "emoji_sets"
    const val RECENT = "recent"
    const val FAVED = "faved"
    const val GIFS = "gifs"
}

internal suspend fun TelegramClientHolder.recentStickers(hash: Long): Pair<Long, List<MediaItem>>? =
    when (val result = client().invoke(GetRecentStickers(hash = hash))) {
        is RecentStickers -> {
            cache?.putStickerList(StickerLists.RECENT, result)
            result.hash to stickersOf(result.stickers, RecentStickersOrigin)
        }
        else -> null
    }

internal suspend fun TelegramClientHolder.storedRecentStickers(): Pair<Long, List<MediaItem>>? =
    (cache?.stickerList(StickerLists.RECENT) { TypeRecentStickers.decode(it) } as? RecentStickers)?.let { it.hash to stickersOf(it.stickers, RecentStickersOrigin) }

internal suspend fun TelegramClientHolder.favedStickers(hash: Long): Pair<Long, List<MediaItem>>? =
    when (val result = client().invoke(GetFavedStickers(hash))) {
        is FavedStickers -> {
            cache?.putStickerList(StickerLists.FAVED, result)
            result.hash to stickersOf(result.stickers, FavedStickersOrigin)
        }
        else -> null
    }

internal suspend fun TelegramClientHolder.storedFavedStickers(): Pair<Long, List<MediaItem>>? =
    (cache?.stickerList(StickerLists.FAVED) { TypeFavedStickers.decode(it) } as? FavedStickers)?.let { it.hash to stickersOf(it.stickers, FavedStickersOrigin) }

internal suspend fun TelegramClientHolder.stickersFor(emoji: String): List<MediaItem> =
    when (val result = client().invoke(GetStickers(emoji, 0))) {
        is Stickers -> stickersOf(result.stickers, EmojiStickersOrigin(emoji))
        else -> emptyList()
    }

internal suspend fun TelegramClientHolder.savedGifs(hash: Long): Pair<Long, List<MediaItem>>? =
    when (val result = client().invoke(GetSavedGifs(hash))) {
        is SavedGifs -> {
            cache?.putStickerList(StickerLists.GIFS, result)
            result.hash to stickersOf(result.gifs, SavedGifsOrigin)
        }
        else -> null
    }

internal suspend fun TelegramClientHolder.storedSavedGifs(): Pair<Long, List<MediaItem>>? =
    (cache?.stickerList(StickerLists.GIFS) { TypeSavedGifs.decode(it) } as? SavedGifs)?.let { it.hash to stickersOf(it.gifs, SavedGifsOrigin) }

internal suspend fun TelegramClientHolder.foundGifs(query: String, offset: String): GifPage {
    val answer = client().invoke(GetInlineBotResults(bot = gifBot(), peer = InputPeerEmpty, query = query, offset = offset))
    val origin = GifSearchOrigin(query, offset)
    val gifs = answer.results.filterIsInstance<BotInlineMediaResult>().mapNotNull { it.document as? Document }.map { peers.documentOf(null, it, origin) }
    return GifPage(gifs, answer.nextOffset?.ifEmpty { null })
}

private suspend fun TelegramClientHolder.gifBot(): TypeInputUser {
    val username = gifSearchUsername?.ifEmpty { null } ?: DEFAULT_GIF_BOT
    peers.botIds[username]?.let { id -> peers.inputUser(id)?.let { return it } }
    val resolved = client().invoke(ResolveUsername(username))
    peers.rememberUsers(resolved.users)
    val id = (resolved.peer as? PeerUser)?.userId ?: error("@$username is not a bot")
    peers.botIds[username] = id
    return peers.inputUser(id) ?: error("no access hash for @$username")
}

private const val DEFAULT_GIF_BOT = "gif"

internal suspend fun TelegramClientHolder.foundStickers(query: String, languages: List<String>, emoticon: String = "", emojis: Boolean = false): List<MediaItem> {
    val call = SearchStickers(emojis = emojis, q = query, emoticon = emoticon, langCode = languages, offset = 0, limit = STICKER_SEARCH_LIMIT, hash = 0)
    val result = client().invoke(call) as? FoundStickers ?: return emptyList()
    val origin = StickerSearchOrigin(query, languages, emoticon, emojis)
    if (!emojis) return stickersOf(result.stickers, origin)
    return result.stickers.filterIsInstance<Document>().map { doc -> peers.documentOf(null, doc, origin).also { peers.customEmoji[doc.id] = it } }
}

private const val STICKER_SEARCH_LIMIT = 100

internal suspend fun TelegramClientHolder.featuredSets(emoji: Boolean, hash: Long): Pair<Long, FeaturedSets>? {
    val result = client().invoke(if (emoji) GetFeaturedEmojiStickers(hash) else GetFeaturedStickers(hash)) as? FeaturedStickers ?: return null
    val covered = result.sets.map { covered ->
        when (covered) {
            is StickerSetCovered -> covered.set to listOf(covered.cover)
            is StickerSetMultiCovered -> covered.set to covered.covers
            is StickerSetFullCovered -> covered.set to covered.documents
            is StickerSetNoCovered -> covered.set to emptyList()
        }
    }
    val sets = covered.map { (set, documents) ->
        val origin = StickerSetOrigin(InputStickerSetID(set.id, set.accessHash))
        set to documents.filterIsInstance<Document>().map { doc -> peers.documentOf(null, doc, origin).also { if (emoji) peers.customEmoji[doc.id] = it } }
    }
    val infos = setInfos(sets.map { it.first }, sets.flatMap { it.second })
    val featured = infos.zip(sets) { info, (_, covers) -> FeaturedSet(info, covers) }
    return result.hash to FeaturedSets(featured, result.unread.mapTo(LinkedHashSet()) { "set$it" })
}

internal suspend fun TelegramClientHolder.emojiGroups(stickers: Boolean, hash: Int): Pair<Int, List<EmojiCategoryGroup>>? {
    val result = client().invoke(if (stickers) GetEmojiStickerGroups(hash) else GetEmojiGroups(hash)) as? EmojiGroups ?: return null
    val groups = result.groups.mapNotNull { group ->
        when (group) {
            is EmojiGroup -> EmojiCategoryGroup(group.title, group.iconEmojiId, emoticons = group.emoticons)
            is EmojiGroupGreeting -> EmojiCategoryGroup(group.title, group.iconEmojiId, emoticons = group.emoticons, greeting = true)
            is EmojiGroupPremium -> null
        }
    }
    return result.hash to groups
}

private fun TelegramClientHolder.stickersOf(documents: List<TypeDocument>, origin: FileOrigin): List<MediaItem> =
    documents.filterIsInstance<Document>().map { peers.documentOf(null, it, origin) }

internal suspend fun TelegramClientHolder.setInfos(sets: List<StickerSet>, stickers: List<MediaItem> = emptyList()): List<StickerSetInfo> {
    val inHand = stickers.mapTo(HashSet()) { it.key }
    val missing = sets.mapNotNull { it.thumbDocumentId }.filter { "doc$it" !in inHand && !peers.customEmoji.containsKey(it) }
    if (missing.isNotEmpty()) {
        try {
            customEmojiDocuments(missing)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("set thumbnails $missing: ${e.message}")
        }
    }
    return sets.map { peers.setInfo(it, stickers) }
}

internal fun PeerRegistry.setInfo(set: StickerSet, stickers: List<MediaItem>): StickerSetInfo {
    val key = "set${set.id}"
    val input = InputStickerSetID(set.id, set.accessHash)
    stickerSets[key] = input
    val document = set.thumbDocumentId?.let { id -> stickers.firstOrNull { it.key == "doc$id" } ?: customEmoji[id] }
    val thumb = document ?: setThumb(key, input, set) ?: stickers.firstOrNull()
    return StickerSetInfo(key, set.title, set.shortName, set.count, installed = set.installedDate != null && !set.archived, thumb = thumb)
}

private fun PeerRegistry.setThumb(key: String, input: InputStickerSetID, set: StickerSet): MediaItem? {
    val version = set.thumbVersion ?: return null
    val thumbs = set.thumbs.orEmpty()
    val sizes = thumbs.filterIsInstance<PhotoSize>()
    val size = SET_THUMB_TYPES.firstNotNullOfOrNull { type -> sizes.firstOrNull { it.type == type } } ?: sizes.firstOrNull() ?: return null
    val thumbKey = "${key}thumb$version"
    locations[thumbKey] = InputStickerSetThumb(input, version)
    set.thumbDcId?.let { fileDcs[thumbKey] = it }
    val (extension, mimeType) = when (size.type) {
        "a" -> "tgs" to "application/x-tgsticker"
        "v" -> "webm" to "video/webm"
        else -> "webp" to "image/webp"
    }
    return MediaItem(
        thumbKey, "$thumbKey.$extension", size.size.toLong(), set.thumbDcId ?: 0, isPhoto = false, kind = MediaKind.Sticker,
        animated = size.type == "a", width = size.w, height = size.h, mimeType = mimeType, outline = outlineOf(thumbs),
    )
}

private val SET_THUMB_TYPES = listOf("a", "v", "s")
