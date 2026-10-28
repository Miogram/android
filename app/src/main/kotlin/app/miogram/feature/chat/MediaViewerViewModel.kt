package app.miogram.feature.chat

import android.net.Uri
import androidx.lifecycle.viewModelScope
import app.miogram.data.Attachments
import app.miogram.data.DownloadsSaver
import app.miogram.data.MediaSaver
import app.miogram.data.telegram.MediaRepository
import app.miogram.data.telegram.MessagesRepository
import app.miogram.data.telegram.ProfileEditRepository
import app.miogram.data.telegram.ProfileRepository
import app.miogram.data.telegram.VideoStream
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.UiError
import cn.ellerotta.core.model.visualMedia
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = MediaViewerViewModel.Factory::class)
class MediaViewerViewModel @AssistedInject constructor(
    @Assisted private val dialogKey: String,
    @Assisted private val messageId: Int,
    @Assisted("articleIndex") private val articleIndex: Int?,
    @Assisted private val profilePhotos: Boolean,
    @Assisted("gifs") private val gifs: Boolean,
    private val messages: MessagesRepository,
    private val profiles: ProfileRepository,
    private val profileEdits: ProfileEditRepository,
    private val repository: MediaRepository,
    private val saver: MediaSaver,
    private val downloadsSaver: DownloadsSaver,
    private val attachments: Attachments,
) : WorkViewModel() {
    private val itemsState = MutableStateFlow<List<MessageItem>>(emptyList())
    private val initialState = MutableStateFlow(0)
    private val media = ChatMedia(viewModelScope, repository, ::fail, downloadsSaver::downloaded)
    private val savedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val endsLoaded = mutableSetOf<Int>()

    private val emptiedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val own: Boolean = profilePhotos && profiles.isSelf(dialogKey)

    val emptied: SharedFlow<Unit> = emptiedEvents.asSharedFlow()

    val items: StateFlow<List<MessageItem>> = itemsState.asStateFlow()

    val initial: StateFlow<Int> = initialState.asStateFlow()
    val downloads: StateFlow<Map<String, DownloadState>> = media.downloads

    val saved: SharedFlow<Unit> = savedEvents.asSharedFlow()

    init {
        if (own) viewModelScope.launch { profiles.own.filterNotNull().map { it.avatarKey }.distinctUntilChanged().drop(1).collect { reload() } }
        work {
            if (profilePhotos) {
                profiles.storedPhotos(dialogKey)?.let { if (itemsState.value.isEmpty()) itemsState.value = pages(it) }
                itemsState.value = pages(profiles.photos(dialogKey))
                return@work
            }
            if (articleIndex != null) {
                val media = messages.richMessage(dialogKey, messageId)?.article?.visualMedia().orEmpty()
                initialState.value = articleIndex.coerceIn(0, maxOf(media.lastIndex, 0))
                itemsState.value = pages(media)
                return@work
            }
            val around = messages.visualMedia(dialogKey, messageId, gifs = gifs)
            initialState.value = around.indexOfFirst { it.id == messageId }.coerceAtLeast(0)
            itemsState.value = around
        }
    }

    private suspend fun reload() {
        val photos = profiles.photos(dialogKey)
        itemsState.value = pages(photos)
        if (photos.isEmpty()) emptiedEvents.tryEmit(Unit)
    }

    fun captureUri(): Uri = attachments.captureUri()

    fun cameraDenied() = fail(UiError.CameraDenied)

    fun setMain(item: MediaItem) = work {
        profileEdits.setMainPhoto(item.key)
        reload()
    }

    fun delete(item: MediaItem) = work {
        profileEdits.deletePhoto(item.key)
        reload()
    }

    fun onPage(index: Int) {
        val list = itemsState.value
        val item = list.getOrNull(index)?.media ?: return
        if (item.kind == MediaKind.Photo) media.download(item)
        if (articleIndex == null && !profilePhotos && (index == 0 || index == list.lastIndex)) {
            val edge = list[index].id
            if (!endsLoaded.add(edge)) return
            work {
                val more = messages.visualMedia(dialogKey, edge, gifs = gifs)
                itemsState.update { current -> (current + more).distinctBy { it.id }.sortedBy { it.id } }
            }
        }
    }

    fun stream(item: MediaItem): VideoStream = repository.stream(item)

    fun save(item: MediaItem) = work {
        saver.save(file(item), item.fileName, item.mimeType ?: if (item.kind == MediaKind.Photo) "image/jpeg" else "video/mp4")
        savedEvents.tryEmit(Unit)
    }

    fun saveToDownloads(item: MediaItem) = work { downloadsSaver.save(media.file(item), item) }

    fun share(item: MediaItem, ready: (File, String) -> Unit) = work {
        ready(file(item), item.mimeType ?: "image/jpeg")
    }

    private suspend fun file(item: MediaItem): File =
        repository.download(item).filterIsInstance<DownloadState.Done>().first().file

    private fun pages(media: List<MediaItem>): List<MessageItem> =
        media.mapIndexed { index, item -> MessageItem(id = index, text = "", out = false, date = 0, media = item) }

    @AssistedFactory
    interface Factory {
        fun create(dialogKey: String, messageId: Int, @Assisted("articleIndex") articleIndex: Int?, profilePhotos: Boolean, @Assisted("gifs") gifs: Boolean): MediaViewerViewModel
    }
}
