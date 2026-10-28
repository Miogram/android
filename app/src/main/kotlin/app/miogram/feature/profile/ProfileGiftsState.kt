package app.miogram.feature.profile

import app.miogram.data.telegram.GiftsRepository
import app.miogram.data.telegram.MediaRepository
import app.miogram.feature.chat.ChatMedia
import cn.ellerotta.core.model.GiftPage
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.ProfileGifts
import cn.ellerotta.core.model.SavedGift
import cn.ellerotta.core.model.UiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ProfileGiftsState(
    private val scope: CoroutineScope,
    private val dialogKey: String?,
    private val repository: GiftsRepository,
    media: MediaRepository,
    fail: (UiError) -> Unit,
    private val refresh: (shown: Boolean, suspend () -> Unit) -> Unit,
    private val work: (suspend () -> Unit) -> Unit,
) {
    private val state = MutableStateFlow(ProfileGifts())
    private val files = ChatMedia(scope, media, fail)

    val gifts: StateFlow<ProfileGifts> = state.asStateFlow()
    val stickers: StateFlow<Map<String, ByteArray>> = files.stickers

    fun load() {
        if (dialogKey != null && dialogKey.startsWith("c") && !dialogKey.startsWith("ch")) return
        scope.launch {
            if (state.value.page == null) repository.stored(dialogKey)?.let { stored -> state.update { if (it.page == null) it.copy(page = stored) else it } }
            refresh(state.value.page != null) { firstPage() }
        }
    }

    private suspend fun firstPage() {
        val page = repository.gifts(dialogKey)
        state.update { it.copy(page = page) }
        val collections = try {
            repository.collections(dialogKey)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        state.update { it.copy(collections = collections) }
    }

    fun chooseCollection(id: Int?) {
        state.update { it.copy(collection = id) }
        if (id == null || id in state.value.collectionPages) return
        work {
            val page = repository.gifts(dialogKey, collectionId = id)
            state.update { it.copy(collectionPages = it.collectionPages + (id to page)) }
        }
    }

    fun loadSticker(sticker: MediaItem) = files.loadSticker(sticker)

    fun loadFile(media: MediaItem) = files.loadSticker(media, preview = false)

    fun more() = work {
        val collection = state.value.collection
        val current = state.value.shown ?: return@work
        val offset = current.nextOffset ?: return@work
        val next = repository.gifts(dialogKey, offset, collection)
        val merged = next.copy(gifts = (current.gifts + next.gifts).distinctBy { gift -> gift.key })
        state.update { if (collection == null) it.copy(page = merged) else it.copy(collectionPages = it.collectionPages + (collection to merged)) }
    }

    fun setSaved(gift: SavedGift, saved: Boolean) {
        val key = gift.manageKey ?: return
        change(gift.key, saved)
        work {
            try {
                repository.setSaved(key, saved)
            } catch (e: Exception) {
                change(gift.key, !saved)
                throw e
            }
        }
    }

    fun openCatalogue() = work {
        val catalogue = repository.catalogue()
        state.update { it.copy(catalogue = catalogue) }
    }

    private fun change(key: String, saved: Boolean) = state.update { current ->
        fun GiftPage.changed() = copy(gifts = gifts.map { if (it.key == key) it.copy(saved = saved) else it })
        current.copy(page = current.page?.changed(), collectionPages = current.collectionPages.mapValues { (_, page) -> page.changed() })
    }
}
