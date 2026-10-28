package app.miogram.push

import android.content.Context
import app.miogram.connection.ConnectionKeeper
import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.NotifySettingsRepository
import app.miogram.data.telegram.PushAction
import app.miogram.data.telegram.PushRepository
import app.miogram.data.telegram.UpdatesRepository
import app.miogram.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushHandler @Inject constructor(
    private val repository: PushRepository,
    private val tokens: PushTokens,
    private val notifier: PushNotifier,
    updates: UpdatesRepository,
    private val connection: ConnectionKeeper,
    private val session: BackgroundSession,
    @ApplicationContext private val context: Context,
    private val notifySettings: NotifySettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : PushReceiver {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile private var defaultsFetched = false

    internal var clock: () -> Long = System::currentTimeMillis

    init {
        scope.launch {
            updates.events().collect { event ->
                when (event) {
                    is ChatEvent.NewMessage -> live(event)
                    is ChatEvent.ReadInbox -> notifier.read(event.key, event.maxId)
                    is ChatEvent.Deleted -> notifier.deleted(event.key, event.ids)
                    else -> Unit
                }
            }
        }
        scope.launch {
            combine(repository.authorized, connection.foreground) { logged, shown -> logged && shown }.filter { it }.collect {
                try {
                    register()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
        scope.launch {
            connection.foreground.filter { it }.collect { if (prefs.getBoolean(MISSED, false)) catchUp() }
        }
    }

    private suspend fun live(event: ChatEvent.NewMessage) {
        if (!connection.foreground.value) return
        try {
            repository.liveMessage(event)?.let { notifier.live(it.copy(quiet = clock() / 1000 - event.message.date > LATE_SECONDS)) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    override fun onNewToken(token: String) {
        prefs.edit().putString(TOKEN, token).remove(REGISTERED).putBoolean(DAILY, true).apply()
        PushRegistrationWorker.enqueueToken(context)
    }

    suspend fun register(again: Boolean = false): Boolean {
        if (!repository.isAuthorized) return true
        val token = tokens.current() ?: prefs.getString(TOKEN, null) ?: return true
        if (!again && !due(token)) return true
        if (!defaultsFetched || again) defaultsFetched = notifySettings.refreshDefaults()
        val sentAt = clock()
        val done = runCatching { repository.registerPush(token) }.isSuccess
        if (done) {
            prefs.edit().putString(TOKEN, token).putString(REGISTERED, token).putLong(REGISTERED_AT, sentAt).putBoolean(DAILY, true).apply()
            PushRegistrationWorker.schedule(context)
        } else if (!again) {
            PushRegistrationWorker.enqueueToken(context)
            prefs.edit().putBoolean(DAILY, true).apply()
        }
        return done
    }

    private fun due(token: String): Boolean {
        val age = clock() - prefs.getLong(REGISTERED_AT, 0)
        return token != prefs.getString(REGISTERED, null) || age !in 0 until DAY
    }

    suspend fun refresh(): Boolean = connection.hold {
        if (!session.ready()) return@hold true
        register(again = true)
    }

    suspend fun unregister() {
        val token = prefs.getString(REGISTERED, null) ?: prefs.getString(TOKEN, null)
        if (token != null && repository.isAuthorized) runCatching { repository.unregisterPush(token) }
        endLocally()
    }

    private suspend fun endLocally() {
        if (prefs.getBoolean(DAILY, false)) PushRegistrationWorker.cancel(context)
        prefs.edit().remove(DAILY).remove(MISSED).remove(REGISTERED).remove(REGISTERED_AT).apply()
        notifier.clear()
        notifySettings.clear()
        defaultsFetched = false
    }

    override suspend fun onMessage(data: Map<String, String>) {
        val push = repository.decryptPush(data) ?: return
        when (push.action) {
            PushAction.Revoke -> revoked(push.userId)
            PushAction.DcUpdate -> repository.applyDcUpdate(push.dc, push.address)
            else -> notifier.handle(push)
        }
    }

    private suspend fun revoked(userId: Long?) {
        if (!repository.revokeSession(userId)) return
        endLocally()
        repository.log("the session was ended by a push")
    }

    override fun onLate() = repository.log("a push was not shown in time")

    override fun onDeleted() {
        if (connection.foreground.value || prefs.getBoolean(MISSED, false)) return
        prefs.edit().putBoolean(MISSED, true).apply()
        repository.log("fcm dropped pending pushes: catching up in the foreground")
    }

    val missedPushes: Boolean get() = prefs.getBoolean(MISSED, false)

    private suspend fun catchUp() {
        try {
            repository.catchUp()
            prefs.edit().remove(MISSED).apply()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val PREFS = "push"
        const val TOKEN = "token"
        const val MISSED = "missed"
        const val DAILY = "daily"

        const val REGISTERED = "registered"
        const val REGISTERED_AT = "registered_at"
        const val DAY = 24 * 60 * 60 * 1000L

        const val LATE_SECONDS = 60L
    }
}
