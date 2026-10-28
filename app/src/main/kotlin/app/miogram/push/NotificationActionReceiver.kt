package app.miogram.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import app.miogram.connection.ConnectionKeeper
import app.miogram.data.DialogsStore
import app.miogram.data.telegram.DialogsRepository
import app.miogram.data.telegram.MessagesRepository
import app.miogram.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationActions @Inject constructor(
    private val notifier: PushNotifier,
    private val messages: MessagesRepository,
    private val dialogs: DialogsRepository,
    private val store: DialogsStore,
    private val session: BackgroundSession,
    private val connection: ConnectionKeeper,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun perform(action: String, dialogKey: String, text: String?, maxId: Int?, done: () -> Unit) {
        scope.launch {
            try {
                withTimeoutOrNull(WAIT) {
                    when (action) {
                        NotificationActionReceiver.ACTION_REPLY -> reply(dialogKey, text.orEmpty())
                        NotificationActionReceiver.ACTION_READ -> markRead(dialogKey, maxId)
                        NotificationActionReceiver.ACTION_DISMISS -> dismissed(dialogKey)
                        else -> Unit
                    }
                }
            } finally {
                done()
            }
        }
    }

    suspend fun reply(dialogKey: String, text: String): Unit = connection.hold {
        val trimmed = text.trim()
        val sent = trimmed.isNotEmpty() && session.ready() && attempt { messages.send(dialogKey, trimmed) }
        if (sent) notifier.replied(dialogKey, trimmed) else notifier.repost(dialogKey)
    }

    suspend fun markRead(dialogKey: String, maxId: Int?): Unit = connection.hold {
        notifier.read(dialogKey, maxId)
        if (!session.ready() || !attempt { dialogs.markRead(dialogKey, clearMark = false, upTo = maxId) }) return@hold
        if (store.state.value.loaded) attempt { store.reload(dialogKey) }
    }

    suspend fun dismissed(dialogKey: String) = notifier.dismissed(dialogKey)

    private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val WAIT = 50_000L
    }
}

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var actions: NotificationActions

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(PushNotifier.EXTRA_DIALOG) ?: return
        val action = intent.action ?: return
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(EXTRA_REPLY)?.toString()
        val maxId = intent.getIntExtra(EXTRA_MAX_ID, 0).takeIf { it != 0 }
        val pending = goAsync()
        actions.perform(action, key, reply, maxId, pending::finish)
    }

    companion object {
        const val ACTION_REPLY = "app.miogram.notification.REPLY"
        const val ACTION_READ = "app.miogram.notification.READ"
        const val ACTION_DISMISS = "app.miogram.notification.DISMISS"

        const val EXTRA_REPLY = "app.miogram.reply"

        const val EXTRA_MAX_ID = "app.miogram.max_id"
    }
}
