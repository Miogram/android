package app.miogram.feature.stories

import android.net.Uri
import androidx.lifecycle.viewModelScope
import app.miogram.data.Attachments
import app.miogram.data.MediaFileInfo
import app.miogram.data.telegram.MediaRepository
import app.miogram.data.telegram.StoriesRepository
import app.miogram.data.telegram.StoryUpload
import app.miogram.feature.chat.ChatMedia
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.StoryAudience
import cn.ellerotta.core.model.StoryItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class PickedStory(val uri: Uri, val preview: ByteArray?, val isVideo: Boolean)

@HiltViewModel(assistedFactory = StoryComposerViewModel.Factory::class)
class StoryComposerViewModel @AssistedInject constructor(
    @Assisted private val editId: Int,
    private val stories: StoriesRepository,
    private val attachments: Attachments,
    repository: MediaRepository,
) : WorkViewModel() {
    private val media = ChatMedia(viewModelScope, repository, ::fail)
    private val pickedState = MutableStateFlow<PickedStory?>(null)
    private val sendingState = MutableStateFlow(false)
    private val progressState = MutableStateFlow<Float?>(null)
    private val postedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val picked: StateFlow<PickedStory?> = pickedState.asStateFlow()
    val sending: StateFlow<Boolean> = sendingState.asStateFlow()

    val progress: StateFlow<Float?> = progressState.asStateFlow()
    val posted: SharedFlow<Unit> = postedEvents.asSharedFlow()

    val editing: StateFlow<StoryItem?> = stories.self
        .map { self -> if (editId == 0) null else self?.stories?.firstOrNull { it.id == editId } }
        .onEach { story -> story?.media?.takeIf { it.kind == MediaKind.Photo }?.let(media::download) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val downloads: StateFlow<Map<String, DownloadState>> = media.downloads

    fun pick(uri: Uri) = work {
        val video = attachments.mimeType(uri).startsWith("video/")
        val preview = withContext(Dispatchers.IO) { attachments.preview(uri, video) }
        pickedState.value = PickedStory(uri, preview, video)
    }

    fun send(caption: String, audience: StoryAudience?) {
        val picked = pickedState.value
        val story = editing.value
        if (editId != 0 && story == null) return
        if (picked == null && story == null) return
        if (sendingState.value) return
        sendingState.value = true
        progressState.value = null
        work {
            try {
                if (picked == null) {
                    save(story, caption, audience, null)
                } else {
                    val info = if (picked.isVideo) withContext(Dispatchers.IO) { attachments.mediaInfo(picked.uri) } else MediaFileInfo()
                    (if (picked.isVideo) attachments.open(picked.uri) else attachments.openUpload(picked.uri)).use { file -> save(story, caption, audience, StoryUpload(file.name, file.mimeType, file.source, info)) }
                }
                postedEvents.tryEmit(Unit)
            } finally {
                sendingState.value = false
            }
        }
    }

    private suspend fun save(story: StoryItem?, caption: String, audience: StoryAudience?, upload: StoryUpload?) {
        val steps = when {
            story == null -> stories.post(upload ?: return, caption, audience ?: StoryAudience.Everybody)
            else -> {
                val newCaption = caption.takeIf { it != story.caption }
                val newAudience = audience?.takeIf { it != story.audience }
                if (newCaption == null && newAudience == null && upload == null) return
                stories.edit(story.id, newCaption, newAudience, upload)
            }
        }
        steps.collect { step -> step.total?.takeIf { it > 0 }?.let { progressState.value = step.downloaded.toFloat() / it } }
    }

    @AssistedFactory
    interface Factory {
        fun create(editId: Int): StoryComposerViewModel
    }
}
