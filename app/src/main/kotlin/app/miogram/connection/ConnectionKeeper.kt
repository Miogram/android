package app.miogram.connection

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.ApplicationScope
import app.miogram.music.MusicPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionKeeper internal constructor(
    private val telegram: TelegramClientHolder,
    private val musicPlaying: Flow<Boolean>,
    private val scope: CoroutineScope,
    private val idleMillis: Long,
    private val clock: () -> Long,
) {
    @Inject
    constructor(telegram: TelegramClientHolder, music: MusicPlayer, @ApplicationScope scope: CoroutineScope) :
        this(telegram, music.music.map { it?.playing == true }, scope, IDLE_MILLIS, SystemClock::elapsedRealtime)

    private val visible = MutableStateFlow(false)
    private val holds = MutableStateFlow(0)

    private val released = MutableStateFlow(0)

    @Volatile private var backgroundSince = clock() - idleMillis
    private val started = AtomicBoolean(false)

    val foreground: StateFlow<Boolean> = visible.asStateFlow()

    fun attach(lifecycle: Lifecycle) {
        start()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = foreground(true)

            override fun onStop(owner: LifecycleOwner) = foreground(false)
        })
    }

    internal fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(visible, musicPlaying, holds, released) { shown: Boolean, playing: Boolean, held: Int, done: Int -> (shown || playing || held > 0) to done }
                .distinctUntilChanged()
                .collectLatest { (keep: Boolean, _: Int) ->
                    if (keep) return@collectLatest
                    delay(backgroundSince + idleMillis - clock())
                    telegram.disconnect()
                }
        }
    }

    internal fun foreground(shown: Boolean) {
        if (!shown) backgroundSince = clock()
        visible.value = shown
        if (shown) {
            scope.launch {
                try {
                    telegram.reconnect()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun <T> hold(block: suspend () -> T): T {
        holds.update { it + 1 }
        try {
            return block()
        } finally {
            holds.update { it - 1 }
            released.update { it + 1 }
        }
    }

    internal companion object {
        const val IDLE_MILLIS = 60_000L
    }
}
