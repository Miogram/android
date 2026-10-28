package app.miogram.feature.chat

import app.miogram.data.telegram.MediaRepository
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.UiError
import cn.ellerotta.core.model.cover
import cn.ellerotta.core.model.stickerPreview
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ChatMedia(
    private val scope: CoroutineScope,
    private val repository: MediaRepository,
    private val fail: (UiError) -> Unit,
    private val downloaded: (MediaItem, File) -> Unit = { _, _ -> },
    private val autoDownload: (MediaItem) -> Boolean = { false },
) {
    private val downloadsState = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    private val stickersState = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    private val requestedStickers = ConcurrentHashMap.newKeySet<String>()
    private val running = ConcurrentHashMap.newKeySet<String>()

    val downloads: StateFlow<Map<String, DownloadState>> = downloadsState.asStateFlow()
    val stickers: StateFlow<Map<String, ByteArray>> = stickersState.asStateFlow()

    fun autoLoad(media: MediaItem) {
        when (media.kind) {
            MediaKind.Sticker -> loadSticker(media)
            MediaKind.Music -> media.cover()?.let { loadSticker(it, preview = false) }
            else -> if (autoDownload(media)) download(media)
        }
    }

    fun loadSticker(media: MediaItem, preview: Boolean = true) {
        if (!requestedStickers.add(media.key)) return
        scope.launch {
            if (preview) media.stickerPreview()?.takeIf { !repository.hasSticker(media.key) }?.let { loadSticker(it, preview = false) }
            val bytes = runCatching { repository.sticker(media.key) }.getOrNull()
            if (bytes == null) requestedStickers.remove(media.key) else stickersState.update { it + (media.key to bytes) }
        }
    }

    fun download(media: MediaItem) {
        if (downloads.value[media.key] is DownloadState.Done || !running.add(media.key)) return
        scope.launch {
            try {
                val (file, fetched) = fetch(media)
                if (fetched) downloaded(media, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(UiError.Message("download failed: ${e.message}"))
            } finally {
                running.remove(media.key)
            }
        }
    }

    suspend fun file(media: MediaItem): File {
        (downloads.value[media.key] as? DownloadState.Done)?.file?.takeIf { it.exists() }?.let { return it }
        if (!running.add(media.key)) {
            val state = downloads.first { it[media.key] !is DownloadState.Progress }[media.key]
            return (state as? DownloadState.Done)?.file ?: error("download failed")
        }
        try {
            return fetch(media).first
        } finally {
            running.remove(media.key)
        }
    }

    private suspend fun fetch(media: MediaItem): Pair<File, Boolean> {
        var fetched = false
        var done: File? = null
        try {
            repository.download(media).collect { progress ->
                if (progress is DownloadState.Progress) fetched = true
                if (progress is DownloadState.Done) done = progress.file
                downloadsState.update { it + (media.key to progress) }
            }
        } catch (e: Exception) {
            downloadsState.update { it - media.key }
            throw e
        }
        return (done ?: error("download of ${media.key} ended without a file")) to fetched
    }

    fun uploads(uploads: Map<String, DownloadState.Progress>) = downloadsState.update { all -> all.filterKeys { it !in uploaded } + uploads }.also { uploaded = uploads.keys }

    private var uploaded: Set<String> = emptySet()
}
