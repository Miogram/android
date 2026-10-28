package app.miogram.data

import app.miogram.data.telegram.DialogsRepository
import app.miogram.di.ApplicationScope
import app.miogram.push.PushNotifier
import cn.ellerotta.core.model.ChatRemoval
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.PendingRemoval
import cn.ellerotta.core.model.Preview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Singleton
class ChatRemovals @Inject constructor(
    private val repository: DialogsRepository,
    private val store: DialogsStore,
    private val notifier: PushNotifier,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val pendingState = MutableStateFlow<PendingRemoval?>(null)
    private val failureFlow = MutableSharedFlow<Exception>(extraBufferCapacity = 1)
    private var timer: Job? = null

    @Volatile var undo: Duration = UNDO

    val pending: StateFlow<PendingRemoval?> = pendingState.asStateFlow()

    val failures: SharedFlow<Exception> = failureFlow.asSharedFlow()

    @Synchronized
    fun remove(dialog: DialogItem, removal: ChatRemoval, revoke: Boolean = false) {
        sendNow()
        val removing = PendingRemoval(dialog, removal, revoke && dialog.revokable(removal))
        if (removal == ChatRemoval.Clear) {
            store.change(dialog.key) { it.copy(preview = Preview(), draft = null, unread = 0, unreadMark = false, mentions = 0, reactions = 0, topMessage = 0) }
        } else {
            store.hide(dialog.key)
        }
        pendingState.value = removing
        timer = scope.launch {
            delay(undo)
            send(removing)
        }
    }

    @Synchronized
    fun undoPending() {
        val removing = pendingState.value ?: return
        timer?.cancel()
        timer = null
        pendingState.value = null
        store.unhide(removing.dialog.key)
        scope.launch { runCatching { store.reload(removing.dialog.key) } }
    }

    @Synchronized
    private fun sendNow() {
        val removing = pendingState.value ?: return
        timer?.cancel()
        timer = null
        scope.launch { send(removing) }
    }

    private suspend fun send(removing: PendingRemoval) {
        synchronized(this) { if (pendingState.value == removing) pendingState.value = null }
        val key = removing.dialog.key
        try {
            repository.remove(key, removing.removal, removing.revoke)
            notifier.forget(key)
            if (removing.removal == ChatRemoval.Clear) {
                store.reload(key)
            } else {
                store.forget(key)
                store.unhide(key)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            store.unhide(key)
            runCatching { store.reload(key) }
            failureFlow.tryEmit(e)
        }
    }

    private companion object {
        val UNDO = 5.seconds
    }
}
