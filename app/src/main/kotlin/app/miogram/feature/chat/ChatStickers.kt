package app.miogram.feature.chat

import app.miogram.data.SettingsStore
import app.miogram.data.telegram.StickersRepository
import cn.ellerotta.core.model.ChatStickerState
import cn.ellerotta.core.model.EmojiCategoryGroup
import cn.ellerotta.core.model.EmojiSearch
import cn.ellerotta.core.model.GifResults
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.PickedEmoji
import cn.ellerotta.core.model.StickerSearch
import cn.ellerotta.core.model.isSingleEmoji
import cn.ellerotta.core.model.orderedBy
import cn.ellerotta.core.model.recentEmoji
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
internal class ChatStickers(
    private val scope: CoroutineScope,
    private val repository: StickersRepository,
    private val settings: SettingsStore,
    private val refresh: (shown: Boolean, suspend () -> Unit) -> Unit,
    private val work: (suspend () -> Unit) -> Unit,
) {
    private val state = MutableStateFlow(repository.storedPanels() ?: ChatStickerState())
    private val draft = MutableStateFlow("")
    private var searching: Job? = null
    private var searchingStickers: Job? = null
    private var searchingGifs: Job? = null
    private var trendingGifs: Job? = null

    val ui: StateFlow<ChatStickerState> = state.asStateFlow()

    init {
        scope.launch {
            draft.debounce(SUGGEST_PAUSE).distinctUntilChanged().collect { text ->
                val emoji = text.trim().takeIf(::isSingleEmoji)
                val found = emoji?.let { runCatching { repository.forEmoji(it) }.getOrNull() }.orEmpty()
                state.update { it.copy(suggestions = found) }
            }
        }
        scope.launch { settings.recentEmoji.collect { list -> state.update { it.copy(recentEmoji = list) } } }
        scope.launch { settings.emojiTones.collect { tones -> state.update { it.copy(emojiTones = tones) } } }
        scope.launch { settings.keyboardHeight.collect { dp -> state.update { it.copy(keyboardHeight = dp) } } }
        scope.launch {
            state.collect { now ->
                val gifs = now.gifs.copy(trending = now.gifs.trending?.copy(loading = false), search = null)
                repository.rememberPanels(ChatStickerState(panel = now.panel, setStickers = now.setStickers, emojiSets = now.emojiSets, gifs = gifs, featured = now.featured, featuredEmoji = now.featuredEmoji, emojiCategories = now.emojiCategories, stickerCategories = now.stickerCategories))
            }
        }
    }

    fun openPanel() {
        refresh(state.value.panel != null) {
            val panel = repository.panel()
            state.update { it.copy(panel = panel) }
        }
        quietly {
            val featured = repository.featured(emoji = false)
            state.update { it.copy(featured = featured) }
        }
        quietly {
            val categories = repository.categories(stickers = true)
            state.update { it.copy(stickerCategories = categories) }
        }
    }

    fun openTab(setKey: String) = refresh(setKey in state.value.setStickers) {
        val detail = repository.set(setKey)
        state.update { it.copy(setStickers = it.setStickers + (setKey to detail.stickers)) }
    }

    fun openEmoji() {
        refresh(state.value.emojiSets != null) {
            val sets = repository.emojiSets()
            state.update { it.copy(emojiSets = sets) }
        }
        quietly {
            val featured = repository.featured(emoji = true)
            state.update { it.copy(featuredEmoji = featured) }
        }
        emojiCategories()
    }

    private fun emojiCategories() = quietly {
        val categories = repository.categories(stickers = false)
        state.update { it.copy(emojiCategories = categories) }
    }

    private fun quietly(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun searchEmojiCategory(category: EmojiCategoryGroup, languages: List<String>, custom: Boolean) {
        searching?.cancel()
        val key = category.searchKey
        state.update { it.copy(emojiSearch = EmojiSearch(key, category.emoticons)) }
        if (!custom) return
        searching = scope.launch {
            try {
                val found = repository.searchCategory(category, languages, emojis = true)
                state.update { it.copy(emojiSearch = EmojiSearch(key, category.emoticons, found)) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun searchStickerCategory(category: EmojiCategoryGroup, languages: List<String>) {
        searchingStickers?.cancel()
        val key = category.searchKey
        state.update { it.copy(stickerSearch = StickerSearch(key)) }
        searchingStickers = scope.launch {
            val found = try {
                repository.searchCategory(category, languages, emojis = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            state.update { it.copy(stickerSearch = StickerSearch(key, found)) }
        }
    }

    fun readFeatured(setKeys: Collection<String>) {
        val unread = setKeys.filter { key -> state.value.featured?.unread?.contains(key) == true || state.value.featuredEmoji?.unread?.contains(key) == true }
        if (unread.isEmpty()) return
        state.update { now -> now.copy(featured = now.featured?.read(unread), featuredEmoji = now.featuredEmoji?.read(unread)) }
        quietly { repository.readFeatured(unread) }
    }

    fun searchEmoji(query: String, languages: List<String>, custom: Boolean) {
        searching?.cancel()
        if (query.isBlank()) {
            state.update { it.copy(emojiSearch = null) }
            return
        }
        searching = scope.launch {
            delay(SEARCH_PAUSE)
            try {
                val emoji = repository.searchEmoji(query, languages)
                state.update { it.copy(emojiSearch = EmojiSearch(query, emoji)) }
                if (custom && emoji.isNotEmpty()) {
                    val found = repository.searchCustomEmoji(emoji)
                    state.update { it.copy(emojiSearch = EmojiSearch(query, emoji, found)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun pickEmoji(emoji: PickedEmoji) = settings.setRecentEmoji(recentEmoji(settings.recentEmoji.value, emoji))

    fun chooseTone(base: String, toned: String) = settings.setEmojiTone(base, toned)

    fun keyboardHeight(dp: Int) = settings.setKeyboardHeight(dp)

    fun searchStickers(query: String, languages: List<String>) {
        searchingStickers?.cancel()
        if (query.isBlank()) {
            state.update { it.copy(stickerSearch = null) }
            return
        }
        state.update { it.copy(stickerSearch = StickerSearch(query)) }
        searchingStickers = scope.launch {
            delay(SEARCH_PAUSE)
            val found = try {
                repository.searchStickers(query, languages)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            state.update { it.copy(stickerSearch = StickerSearch(query, found)) }
        }
    }

    fun openGifs() {
        refresh(state.value.gifs.saved != null) {
            val saved = repository.savedGifs()
            state.update { it.copy(gifs = it.gifs.copy(saved = saved)) }
        }
        if (state.value.gifs.trending == null) loadTrending(GifResults(""))
        if (state.value.emojiCategories.isEmpty()) emojiCategories()
    }

    fun searchGifs(query: String) {
        searchingGifs?.cancel()
        if (query.isBlank()) {
            state.update { it.copy(gifs = it.gifs.copy(search = null)) }
            return
        }
        state.update { it.copy(gifs = it.gifs.copy(search = GifResults(query, loading = true))) }
        searchingGifs = scope.launch {
            delay(SEARCH_PAUSE)
            fetchGifs(GifResults(query), trending = false)
        }
    }

    fun moreGifs() {
        val gifs = state.value.gifs
        val search = gifs.search
        val current = search ?: gifs.trending ?: return
        if (current.loading || current.nextOffset == null) return
        if (search != null) {
            searchingGifs = scope.launch { fetchGifs(search, trending = false) }
        } else {
            loadTrending(current)
        }
    }

    private fun loadTrending(current: GifResults) {
        trendingGifs?.cancel()
        trendingGifs = scope.launch { fetchGifs(current, trending = true) }
    }

    private suspend fun fetchGifs(current: GifResults, trending: Boolean) {
        fun put(results: GifResults) = state.update { now ->
            when {
                trending -> now.copy(gifs = now.gifs.copy(trending = results))
                now.gifs.search?.query == results.query -> now.copy(gifs = now.gifs.copy(search = results))
                else -> now
            }
        }
        put(current.copy(loading = true))
        try {
            val page = repository.searchGifs(current.query, current.nextOffset.orEmpty())
            put(GifResults(current.query, (current.gifs + page.gifs).distinctBy { it.key }, page.nextOffset))
        } catch (e: CancellationException) {
            put(current.copy(loading = false))
            throw e
        } catch (_: Exception) {
            put(current.copy(loading = false))
        }
    }

    fun sentGif(gif: MediaItem) = work {
        if (state.value.gifs.saved?.any { it.key == gif.key } != true) repository.saveGif(gif.key)
        val saved = repository.savedGifs()
        state.update { it.copy(gifs = it.gifs.copy(saved = saved)) }
    }

    fun removeGif(gif: MediaItem) = work {
        state.update { it.copy(gifs = it.gifs.copy(saved = it.gifs.saved?.filter { saved -> saved.key != gif.key })) }
        repository.unsaveGif(gif.key)
    }

    fun fave(sticker: MediaItem, fave: Boolean) = work {
        val faved = repository.fave(sticker, fave)
        state.update { it.copy(panel = it.panel?.copy(faved = faved)) }
    }

    fun reorderSets(setKeys: List<String>, emoji: Boolean) = work {
        state.update { now ->
            if (emoji) now.copy(emojiSets = now.emojiSets?.orderedBy(setKeys)) else now.copy(panel = now.panel?.let { it.copy(sets = it.sets.orderedBy(setKeys)) })
        }
        repository.reorder(setKeys, emoji)
    }

    fun draft(text: String) {
        if (!isSingleEmoji(text)) state.update { it.copy(suggestions = emptyList()) }
        draft.value = text
    }

    fun showSet(sticker: MediaItem) = work {
        val detail = repository.setOf(sticker.key) ?: return@work
        state.update { it.copy(openSet = detail) }
    }

    fun install(setKey: String, install: Boolean) = work {
        if (install) repository.install(setKey) else repository.uninstall(setKey)
        state.update { current ->
            val open = current.openSet?.takeIf { it.info.key == setKey }?.let { it.copy(info = it.info.copy(installed = install)) } ?: current.openSet
            current.copy(openSet = open, featured = current.featured?.withInstalled(setKey, install), featuredEmoji = current.featuredEmoji?.withInstalled(setKey, install))
        }
        if (state.value.panel != null) state.update { it.copy(panel = repository.panel()) }
        if (state.value.emojiSets != null) state.update { it.copy(emojiSets = repository.emojiSets()) }
    }

    fun dismissSet() = state.update { it.copy(openSet = null) }

    private companion object {
        const val SUGGEST_PAUSE = 300L
        const val SEARCH_PAUSE = 250L
    }
}
