package app.miogram.location

import android.content.Context
import app.miogram.data.DeviceLocation
import app.miogram.data.telegram.AttachRepository
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.LiveSharer
import cn.ellerotta.core.model.UiError
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LiveShare(
    val dialogKey: String,
    val title: String,
    val messageId: Int,
    val expires: Long?,
    val radius: Int?,
    val place: GeoPlace,
    val started: Long? = null,
) {
    fun isOver(now: Long): Boolean = expires != null && expires <= now
}

@Singleton
class LiveLocations @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attach: AttachRepository,
    private val device: DeviceLocation,
) {
    private val state = MutableStateFlow<Map<String, LiveShare>>(emptyMap())
    private val lock = Mutex()

    val shares: StateFlow<Map<String, LiveShare>> = state.asStateFlow()

    suspend fun start(dialogKey: String, title: String, place: GeoPlace, heading: Int?, seconds: Int, radius: Int?, replyTo: Int?, topicId: Int?) {
        stop(dialogKey)
        val id = attach.startLive(dialogKey, place, heading, seconds, radius, replyTo, topicId) ?: return
        val started = now()
        val expires = if (seconds <= 0) null else started + seconds
        lock.withLock { state.update { it + (dialogKey to LiveShare(dialogKey, title, id, expires, radius, place, started)) } }
        LiveLocationService.start(context)
    }

    suspend fun startHere(dialogKey: String, title: String, seconds: Int, radius: Int?): UiError? {
        if (!device.permitted()) return UiError.LocationDenied
        val fix = device.current() ?: return UiError.LocationUnavailable
        start(dialogKey, title, GeoPlace(fix.latitude, fix.longitude), fix.bearing.roundToInt().takeIf { fix.hasBearing() }, seconds, radius, replyTo = null, topicId = null)
        return null
    }

    suspend fun stop(dialogKey: String) {
        val share = lock.withLock { state.value[dialogKey]?.also { state.update { all -> all - dialogKey } } } ?: return
        attempt { attach.stopLive(dialogKey, share.messageId, share.place) }
    }

    suspend fun alert(dialogKey: String, radius: Int, own: LiveSharer?) {
        val share = state.value[dialogKey]
        if (share != null) {
            attach.updateLive(dialogKey, share.messageId, share.place, heading = null, radius = radius)
            lock.withLock { state.update { all -> all[dialogKey]?.let { all + (dialogKey to it.copy(radius = radius.takeIf { r -> r > 0 })) } ?: all } }
        } else if (own != null) {
            attach.updateLive(dialogKey, own.messageId, own.place, own.live.heading, radius)
        }
    }

    suspend fun stopAll() {
        state.value.keys.forEach { stop(it) }
    }

    suspend fun moved(place: GeoPlace, heading: Int?) {
        expire()
        state.value.values.forEach { share ->
            if (attempt { attach.updateLive(share.dialogKey, share.messageId, place, heading) }) {
                lock.withLock { state.update { all -> all[share.dialogKey]?.let { all + (share.dialogKey to it.copy(place = place)) } ?: all } }
            }
        }
    }

    suspend fun expire() {
        val now = now()
        lock.withLock { state.update { all -> all.filterValues { !it.isOver(now) } } }
    }

    private fun now() = System.currentTimeMillis() / 1000

    private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}
