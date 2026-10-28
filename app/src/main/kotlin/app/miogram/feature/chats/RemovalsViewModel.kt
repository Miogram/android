package app.miogram.feature.chats

import androidx.lifecycle.viewModelScope
import app.miogram.data.ChatRemovals
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.ChatRemoval
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.PendingRemoval
import cn.ellerotta.core.model.UiError
import cn.ellerotta.ktproto.session.RpcException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemovalsViewModel @Inject constructor(private val removals: ChatRemovals) : WorkViewModel() {
    val pending: StateFlow<PendingRemoval?> = removals.pending

    init {
        viewModelScope.launch {
            removals.failures.collect { e -> fail(if (e is RpcException) UiError.Rpc(e.code, e.errorMessage) else UiError.Message(e.message ?: e::class.simpleName.orEmpty())) }
        }
    }

    fun remove(dialog: DialogItem, removal: ChatRemoval, revoke: Boolean) = removals.remove(dialog, removal, revoke)

    fun undo() = removals.undoPending()
}
