package app.miogram.feature.profile

import android.util.Log
import app.miogram.data.telegram.MediaRepository
import app.miogram.data.telegram.SharedMediaRepository
import app.miogram.feature.chat.ChatMedia
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.SharedList
import cn.ellerotta.core.model.SharedMedia
import cn.ellerotta.core.model.SharedTab
import cn.ellerotta.core.model.UiError
import cn.ellerotta.core.model.appended
import cn.ellerotta.core.model.nextOffset
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ProfileSharedState(
    private val scope: CoroutineScope,
    private val repository: SharedMediaRepository,
    media: MediaRepository,
    fail: (UiError) -> Unit,
) {
    private val state = MutableStateFlow(SharedMedia())
    private val files = ChatMedia(scope, media, fail)
    private var key: String? = null

    private val fresh = HashSet<SharedTab>()

    val shared: StateFlow<SharedMedia> = state.asStateFlow()
    val downloads: StateFlow<Map<String, DownloadState>> = files.downloads
    val covers: StateFlow<Map<String, ByteArray>> = files.stickers

    fun start(dialogKey: String, user: Boolean) {
        if (key != null) return
        key = dialogKey
        repository.stored(dialogKey)?.let { stored ->
            state.value = stored.copy(lists = stored.lists.mapValues { (_, list) -> list.copy(loading = false, failed = false) }, commonLoading = false)
            stored.lists.values.forEach { list -> list.items.forEach(::autoLoad) }
        }
        scope.launch { state.collect { repository.remember(dialogKey, it) } }
        scope.launch {
            val counts = try {
                repository.counts(dialogKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (counts != null) state.update { it.copy(counts = counts + listOfNotNull(it.counts[SharedTab.Groups]?.let { groups -> SharedTab.Groups to groups })) }
            if (user) {
                if (state.value.common.isEmpty()) loadCommon(dialogKey) else refreshCommon(dialogKey)
            }
            state.value.tab?.let(::load)
        }
    }

    fun choose(tab: SharedTab) {
        state.update { it.copy(chosen = tab) }
        load(tab)
    }

    fun more(tab: SharedTab) = load(tab)

    private fun load(tab: SharedTab) {
        val dialogKey = key ?: return
        if (tab == SharedTab.Groups) {
            if (!state.value.commonComplete && !state.value.commonLoading) scope.launch { loadCommon(dialogKey) }
            return
        }
        val list = state.value.list(tab)
        if (fresh.add(tab) && list.items.isNotEmpty()) {
            refreshFirst(dialogKey, tab, list)
            return
        }
        if (list.loading || list.complete) return
        setList(tab) { it.copy(loading = true, failed = false) }
        scope.launch {
            try {
                val page = repository.page(dialogKey, tab, list.nextOffset(), tab.pageSize)
                page.forEach(::autoLoad)
                setList(tab) { it.copy(loading = false).appended(page, tab.pageSize) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setList(tab) { it.copy(loading = false, failed = true) }
            }
        }
    }

    private fun refreshFirst(dialogKey: String, tab: SharedTab, shown: SharedList) = scope.launch {
        try {
            val page = repository.page(dialogKey, tab, 0, tab.pageSize)
            page.forEach(::autoLoad)
            setList(tab) { if (it.items == shown.items) SharedList().appended(page, tab.pageSize) else it.appended(page, tab.pageSize).copy(complete = it.complete) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "shared $tab of $dialogKey: ${e.message}")
        }
    }

    private suspend fun refreshCommon(dialogKey: String) {
        try {
            val page = repository.common(dialogKey, 0, SharedTab.Groups.pageSize)
            state.update { now ->
                now.copy(
                    common = page,
                    commonComplete = page.size < SharedTab.Groups.pageSize,
                    counts = if (page.isEmpty()) now.counts - SharedTab.Groups else now.counts + (SharedTab.Groups to page.size),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "chats in common of $dialogKey: ${e.message}")
        }
    }

    private suspend fun loadCommon(dialogKey: String) {
        val current = state.value
        if (current.commonLoading || current.commonComplete) return
        state.update { it.copy(commonLoading = true) }
        try {
            val after = current.common.lastOrNull()?.peerId ?: 0L
            val page = repository.common(dialogKey, after, SharedTab.Groups.pageSize)
            state.update { now ->
                val all = (now.common + page).distinctBy { it.key }
                now.copy(
                    common = all,
                    commonLoading = false,
                    commonComplete = page.size < SharedTab.Groups.pageSize,
                    counts = if (all.isEmpty()) now.counts - SharedTab.Groups else now.counts + (SharedTab.Groups to all.size),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.update { it.copy(commonLoading = false) }
        }
    }

    private fun setList(tab: SharedTab, change: (SharedList) -> SharedList) =
        state.update { it.copy(lists = it.lists + (tab to change(it.list(tab)))) }

    private fun autoLoad(message: MessageItem) {
        message.media?.let(files::autoLoad)
    }

    fun download(media: MediaItem) = files.download(media)

    suspend fun file(media: MediaItem): File = files.file(media)

    fun removed(ids: Set<Int>) = state.update { current ->
        val lists = current.lists.mapValues { (_, list) -> list.copy(items = list.items.filter { it.id !in ids }) }
        val counts = current.counts.mapValues { (tab, count) ->
            val gone = current.list(tab).items.count { it.id in ids }
            (count - gone).coerceAtLeast(0)
        }
        current.copy(lists = lists, counts = counts.filterValues { it > 0 })
    }

    private companion object {
        const val TAG = "shared"
    }
}
