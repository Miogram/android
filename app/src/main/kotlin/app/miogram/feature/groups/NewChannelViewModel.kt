package app.miogram.feature.groups

import android.net.Uri
import app.miogram.data.Attachments
import app.miogram.data.telegram.GroupsRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.CreatedChat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class NewChannelViewModel @Inject constructor(
    private val groups: GroupsRepository,
    private val attachments: Attachments,
) : WorkViewModel() {
    private val nameState = MutableStateFlow("")
    private val aboutState = MutableStateFlow("")
    private val photoState = MutableStateFlow<ByteArray?>(null)
    private val createdFlow = MutableSharedFlow<CreatedChat>(extraBufferCapacity = 1)

    private var created: CreatedChat? = null
    private var photoSet = false

    val name: StateFlow<String> = nameState.asStateFlow()
    val about: StateFlow<String> = aboutState.asStateFlow()

    val photo: StateFlow<ByteArray?> = photoState.asStateFlow()

    val done: SharedFlow<CreatedChat> = createdFlow.asSharedFlow()

    fun setName(text: String) {
        if (created == null) nameState.value = text
    }

    fun setAbout(text: String) {
        if (created == null) aboutState.value = text
    }

    fun pickPhoto(uri: Uri) {
        if (created != null) return
        work { photoState.value = attachments.squareJpeg(uri) }
    }

    fun removePhoto() {
        if (created == null) photoState.value = null
    }

    fun create() {
        val title = nameState.value
        if (title.isBlank()) return
        work {
            val chat = created ?: groups.createChannel(title, aboutState.value).also { created = it }
            if (!photoSet) {
                photoState.value?.let { groups.setPhoto(chat.key, it) }
                photoSet = true
            }
            createdFlow.tryEmit(chat)
        }
    }
}
