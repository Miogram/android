package app.miogram.feature.attach

import app.miogram.data.telegram.AttachRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.PollDraft
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel(assistedFactory = NewPollViewModel.Factory::class)
class NewPollViewModel @AssistedInject constructor(
    @Assisted private val dialogKey: String,
    @Assisted kind: PeerKind,
    @Assisted("replyTo") private val replyTo: Int?,
    @Assisted("topicId") private val topicId: Int?,
    private val attach: AttachRepository,
) : WorkViewModel() {
    private val draftState = MutableStateFlow(PollDraft(kind = kind))
    private val sentEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val draft: StateFlow<PollDraft> = draftState.asStateFlow()

    val sent: SharedFlow<Unit> = sentEvents.asSharedFlow()

    fun change(draft: PollDraft) {
        draftState.value = draft
    }

    fun send() {
        val poll = draftState.value
        if (!poll.canSend) return
        work {
            attach.sendPoll(dialogKey, poll, replyTo, topicId)
            sentEvents.emit(Unit)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(dialogKey: String, kind: PeerKind, @Assisted("replyTo") replyTo: Int?, @Assisted("topicId") topicId: Int?): NewPollViewModel
    }
}
