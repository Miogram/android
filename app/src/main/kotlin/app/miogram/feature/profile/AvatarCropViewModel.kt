package app.miogram.feature.profile

import android.net.Uri
import app.miogram.data.Attachments
import app.miogram.data.cropSquare
import app.miogram.data.telegram.ProfileEditRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.CropRect
import cn.ellerotta.core.model.UiError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@HiltViewModel(assistedFactory = AvatarCropViewModel.Factory::class)
class AvatarCropViewModel @AssistedInject constructor(
    @Assisted private val uri: String,
    @Assisted private val temporary: Boolean,
    private val attachments: Attachments,
    private val edits: ProfileEditRepository,
) : WorkViewModel() {
    private val photoState = MutableStateFlow<ByteArray?>(null)
    private val unreadableState = MutableStateFlow(false)
    private val doneEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val photo: StateFlow<ByteArray?> = photoState.asStateFlow()

    val unreadable: StateFlow<Boolean> = unreadableState.asStateFlow()

    val done: SharedFlow<Unit> = doneEvents.asSharedFlow()

    init {
        work {
            val bytes = withContext(Dispatchers.IO) { attachments.photoBytes(Uri.parse(uri)) }
            if (temporary) withContext(Dispatchers.IO) { attachments.captureFile().delete() }
            if (bytes == null) {
                fail(UiError.PhotoUnreadable)
                unreadableState.value = true
            } else {
                photoState.value = bytes
            }
        }
    }

    fun apply(rect: CropRect) {
        val bytes = photoState.value ?: return
        work {
            val cropped = withContext(Dispatchers.IO) { cropSquare(bytes, rect) } ?: run {
                fail(UiError.PhotoUnreadable)
                return@work
            }
            edits.uploadPhoto(cropped)
            doneEvents.tryEmit(Unit)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(uri: String, temporary: Boolean): AvatarCropViewModel
    }
}
