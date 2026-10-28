package app.miogram.feature.groups

import app.miogram.data.telegram.GroupsRepository
import cn.ellerotta.core.model.CreatedChat
import cn.ellerotta.core.model.MissingInvitee
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class MissingPrompt(val chat: CreatedChat, val missing: List<MissingInvitee>)

class MissingInvites(private val groups: GroupsRepository, private val run: (suspend () -> Unit) -> Unit) {
    private val state = MutableStateFlow<MissingPrompt?>(null)
    private val doneFlow = MutableSharedFlow<CreatedChat>(extraBufferCapacity = 1)

    val prompt: StateFlow<MissingPrompt?> = state.asStateFlow()

    val done: SharedFlow<CreatedChat> = doneFlow.asSharedFlow()

    fun settle(chat: CreatedChat, missing: List<MissingInvitee>) {
        if (missing.isEmpty()) doneFlow.tryEmit(chat) else state.value = MissingPrompt(chat, missing)
    }

    fun send() {
        val asked = state.value ?: return
        run {
            try {
                groups.sendInviteLink(asked.chat.key, asked.missing.map { it.userKey })
            } finally {
                state.value = null
                doneFlow.tryEmit(asked.chat)
            }
        }
    }

    fun skip() {
        val asked = state.value ?: return
        state.value = null
        doneFlow.tryEmit(asked.chat)
    }
}
