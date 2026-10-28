package app.miogram.data.telegram

import cn.ellerotta.core.model.ChatStickerState
import cn.ellerotta.core.model.EmojiCategoryGroup
import cn.ellerotta.core.model.FeaturedSets
import cn.ellerotta.core.model.GifPage
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.StickerPanel
import cn.ellerotta.core.model.StickerSetDetail
import cn.ellerotta.core.model.StickerSetInfo
import cn.ellerotta.core.model.orderedBy
import cn.ellerotta.ktproto.api.functions.messages.GetEmojiKeywords
import cn.ellerotta.ktproto.api.functions.messages.InstallStickerSet
import cn.ellerotta.ktproto.api.functions.messages.FaveSticker
import cn.ellerotta.ktproto.api.functions.messages.ReadFeaturedStickers
import cn.ellerotta.ktproto.api.functions.messages.ReorderStickerSets
import cn.ellerotta.ktproto.api.functions.messages.SaveGif
import cn.ellerotta.ktproto.api.functions.messages.SearchCustomEmoji
import cn.ellerotta.ktproto.api.functions.messages.UninstallStickerSet
import cn.ellerotta.ktproto.api.types.EmojiKeyword
import cn.ellerotta.ktproto.api.types.EmojiList
import cn.ellerotta.ktproto.api.types.InputStickerSetID
import cn.ellerotta.ktproto.api.types.TypeInputDocument
import cn.ellerotta.ktproto.session.RpcException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class StickersRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    fun storedPanels(): ChatStickerState? = telegram.memory["panels"]

    fun rememberPanels(panels: ChatStickerState) {
        telegram.memory["panels"] = panels
    }

    private val registry get() = telegram.peers
    private val lock = Mutex()
    private var setsHash = 0L
    private var sets: List<StickerSetInfo> = emptyList()
    private var recentHash = 0L
    private var recent: List<MediaItem> = emptyList()
    private var favedHash = 0L
    private var faved: List<MediaItem> = emptyList()
    private val details = HashMap<String, StickerSetDetail>()
    private val setHashes = HashMap<String, Int>()
    private val restored = HashSet<String>()
    private var emojiSetsHash = 0L
    private var emojiSets: List<StickerSetInfo> = emptyList()
    private val keywords = HashMap<String, Map<String, List<String>>>()
    private var gifsHash = 0L
    private var gifs: List<MediaItem> = emptyList()
    private var featuredHash = 0L
    private var featured = FeaturedSets()
    private var featuredEmojiHash = 0L
    private var featuredEmoji = FeaturedSets()
    private var emojiGroupsHash = 0
    private var emojiGroups: List<EmojiCategoryGroup> = emptyList()
    private var stickerGroupsHash = 0
    private var stickerGroups: List<EmojiCategoryGroup> = emptyList()

    private suspend fun restore(name: String, load: suspend () -> Unit) {
        if (restored.add(name)) load()
    }

    suspend fun panel(): StickerPanel = lock.withLock {
        restore(StickerLists.SETS) { telegram.storedAllStickers(emoji = false)?.let { (hash, list) -> setsHash = hash; sets = list } }
        restore(StickerLists.RECENT) { telegram.storedRecentStickers()?.let { (hash, list) -> recentHash = hash; recent = list } }
        restore(StickerLists.FAVED) { telegram.storedFavedStickers()?.let { (hash, list) -> favedHash = hash; faved = list } }
        coroutineScope {
            val all = async { telegram.allStickers(emoji = false, hash = setsHash) }
            val recentNow = async { telegram.recentStickers(recentHash) }
            val favedNow = async { telegram.favedStickers(favedHash) }
            all.await()?.let { (hash, list) ->
                setsHash = hash
                sets = list
            }
            recentNow.await()?.let { (hash, list) ->
                recentHash = hash
                recent = list
            }
            favedNow.await()?.let { (hash, list) ->
                favedHash = hash
                faved = list
            }
        }
        StickerPanel(recent = recent, faved = faved, sets = sets)
    }

    suspend fun set(setKey: String): StickerSetDetail {
        lock.withLock { details[setKey] }?.let { return it }
        val input = registry.stickerSets[setKey] ?: error("unknown set $setKey")
        val fetched = telegram.stickerSetChanged(input, 0) ?: error("set $setKey not found")
        return remember(fetched)
    }

    suspend fun setOf(mediaKey: String): StickerSetDetail? {
        val input = registry.stickerSetOf[mediaKey] ?: return null
        val key = (input as? InputStickerSetID)?.let { "set${it.id}" }
        val known = key?.let { lock.withLock { details[it]?.let { detail -> detail to setHashes[it] } } }
        val fetched = telegram.stickerSetChanged(input, known?.second ?: 0)
        if (fetched != null) return remember(fetched)
        return known?.first?.let { lock.withLock { it.copy(info = it.info.copy(installed = installedNow(it.info.key, it.info.installed))) } }
    }

    private suspend fun remember(fetched: FetchedSet): StickerSetDetail = lock.withLock {
        val key = fetched.detail.info.key
        details[key] = fetched.detail
        setHashes[key] = fetched.hash
        fetched.detail
    }

    private fun installedNow(key: String, was: Boolean): Boolean =
        if (setsHash != 0L && emojiSetsHash != 0L) (sets + emojiSets).any { it.key == key && it.installed } else was

    suspend fun forEmoji(emoji: String): List<MediaItem> = telegram.stickersFor(emoji)

    suspend fun customEmoji(ids: Collection<Long>): Map<Long, MediaItem> = telegram.customEmojiDocuments(ids)

    suspend fun emojiSets(): List<StickerSetInfo> = lock.withLock {
        restore(StickerLists.EMOJI_SETS) { telegram.storedAllStickers(emoji = true)?.let { (hash, list) -> emojiSetsHash = hash; emojiSets = list } }
        telegram.allStickers(emoji = true, hash = emojiSetsHash)?.let { (hash, list) ->
            emojiSetsHash = hash
            emojiSets = list
        }
        emojiSets
    }

    suspend fun searchEmoji(query: String, languages: List<String>): List<String> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val keywords = languages.distinct().flatMap { lang -> keywordsOf(lang).entries }
        return keywords.filter { it.key.startsWith(needle) }.sortedBy { it.key.length }.flatMap { it.value }.distinct()
    }

    private suspend fun keywordsOf(lang: String): Map<String, List<String>> {
        lock.withLock { keywords[lang] }?.let { return it }
        val difference = telegram.client().invoke(GetEmojiKeywords(lang))
        val found = difference.keywords.filterIsInstance<EmojiKeyword>().associate { it.keyword.lowercase() to it.emoticons }
        lock.withLock { keywords[lang] = found }
        return found
    }

    suspend fun searchCustomEmoji(emoticons: List<String>): List<MediaItem> {
        val ids = emoticons.take(CUSTOM_SEARCH_EMOTICONS).flatMap { emoticon ->
            (telegram.client().invoke(SearchCustomEmoji(emoticon, 0)) as? EmojiList)?.documentId.orEmpty()
        }.distinct()
        if (ids.isEmpty()) return emptyList()
        val documents = customEmoji(ids)
        return ids.mapNotNull(documents::get)
    }

    suspend fun searchStickers(query: String, languages: List<String>): List<MediaItem> = telegram.foundStickers(query.trim(), languages.distinct())

    suspend fun savedGifs(): List<MediaItem> = lock.withLock {
        restore(StickerLists.GIFS) { telegram.storedSavedGifs()?.let { (hash, list) -> gifsHash = hash; gifs = list } }
        telegram.savedGifs(gifsHash)?.let { (hash, list) ->
            gifsHash = hash
            gifs = list
        }
        gifs
    }

    suspend fun saveGif(mediaKey: String) {
        telegram.client().invoke(SaveGif(registry.inputDocuments[mediaKey] ?: error("unknown gif $mediaKey"), unsave = false))
        lock.withLock { gifsHash = 0 }
    }

    suspend fun unsaveGif(mediaKey: String) {
        withDocument(mediaKey) { telegram.client().invoke(SaveGif(it, unsave = true)) }
        lock.withLock {
            gifsHash = 0
            gifs = gifs.filter { it.key != mediaKey }
        }
    }

    suspend fun fave(sticker: MediaItem, fave: Boolean): List<MediaItem> {
        withDocument(sticker.key) { telegram.client().invoke(FaveSticker(it, unfave = !fave)) }
        return lock.withLock {
            favedHash = 0
            val without = faved.filter { it.key != sticker.key }
            faved = if (fave) listOf(sticker) + without else without
            faved
        }
    }

    suspend fun reorder(setKeys: List<String>, emoji: Boolean) {
        telegram.client().invoke(ReorderStickerSets(emojis = emoji, order = setKeys.map { it.removePrefix("set").toLong() }))
        lock.withLock {
            if (emoji) {
                emojiSetsHash = 0
                emojiSets = emojiSets.orderedBy(setKeys)
            } else {
                setsHash = 0
                sets = sets.orderedBy(setKeys)
            }
        }
    }

    suspend fun searchGifs(query: String, offset: String = ""): GifPage = telegram.foundGifs(query.trim(), offset)

    suspend fun featured(emoji: Boolean): FeaturedSets = lock.withLock {
        telegram.featuredSets(emoji, if (emoji) featuredEmojiHash else featuredHash)?.let { (hash, sets) ->
            if (emoji) {
                featuredEmojiHash = hash
                featuredEmoji = sets
            } else {
                featuredHash = hash
                featured = sets
            }
        }
        if (emoji) featuredEmoji else featured
    }

    suspend fun readFeatured(setKeys: Collection<String>) {
        if (setKeys.isEmpty()) return
        telegram.client().invoke(ReadFeaturedStickers(setKeys.map { it.removePrefix("set").toLong() }))
        lock.withLock {
            featured = featured.read(setKeys)
            featuredEmoji = featuredEmoji.read(setKeys)
        }
    }

    suspend fun categories(stickers: Boolean): List<EmojiCategoryGroup> {
        val list = lock.withLock {
            telegram.emojiGroups(stickers, if (stickers) stickerGroupsHash else emojiGroupsHash)?.let { (hash, found) ->
                if (stickers) {
                    stickerGroupsHash = hash
                    stickerGroups = found
                } else {
                    emojiGroupsHash = hash
                    emojiGroups = found
                }
            }
            if (stickers) stickerGroups else emojiGroups
        }
        val missing = list.filter { it.icon == null }.map { it.iconId }
        if (missing.isEmpty()) return list
        val icons = customEmoji(missing)
        val withIcons = list.map { group -> group.icon?.let { group } ?: group.copy(icon = icons[group.iconId]) }
        lock.withLock { if (stickers) stickerGroups = withIcons else emojiGroups = withIcons }
        return withIcons
    }

    suspend fun searchCategory(category: EmojiCategoryGroup, languages: List<String>, emojis: Boolean): List<MediaItem> =
        telegram.foundStickers("", languages.distinct(), category.emoticons.joinToString(" "), emojis)

    suspend fun install(setKey: String) {
        telegram.client().invoke(InstallStickerSet(registry.stickerSets[setKey] ?: error("unknown set $setKey"), archived = false))
        changed(setKey, installed = true)
    }

    suspend fun uninstall(setKey: String) {
        telegram.client().invoke(UninstallStickerSet(registry.stickerSets[setKey] ?: error("unknown set $setKey")))
        changed(setKey, installed = false)
    }

    private suspend fun withDocument(mediaKey: String, call: suspend (TypeInputDocument) -> Unit) {
        suspend fun once() = call(registry.inputDocuments[mediaKey] ?: error("unknown document $mediaKey"))
        try {
            once()
        } catch (e: RpcException) {
            if (!e.errorMessage.startsWith("FILE_REFERENCE_") || !telegram.refreshReference(mediaKey)) throw e
            once()
        }
    }

    private suspend fun changed(setKey: String, installed: Boolean) = lock.withLock {
        setsHash = 0
        emojiSetsHash = 0
        details[setKey]?.let { details[setKey] = it.copy(info = it.info.copy(installed = installed)) }
        featured = featured.withInstalled(setKey, installed)
        featuredEmoji = featuredEmoji.withInstalled(setKey, installed)
    }

    private companion object {
        const val CUSTOM_SEARCH_EMOTICONS = 3
    }
}
