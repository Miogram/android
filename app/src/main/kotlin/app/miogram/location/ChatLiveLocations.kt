package app.miogram.location

import app.miogram.data.telegram.AttachRepository
import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.UpdatesRepository
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.LiveSharer
import cn.ellerotta.core.model.currentSharers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ChatLiveLocations(
    private val dialogKey: String,
    private val attach: AttachRepository,
    updates: UpdatesRepository,
    private val scope: CoroutineScope,
) {
    private val byMessage = linkedMapOf<Int, LiveSharer>()
    private val state = MutableStateFlow<List<LiveSharer>>(emptyList())

    val sharers: StateFlow<List<LiveSharer>> = state.asStateFlow()

    init {
        scope.launch { refresh() }
        scope.launch { updates.events().collect(::apply) }
        scope.launch {
            while (isActive) {
                delay(EXPIRE_MS)
                publish()
            }
        }
    }

    suspend fun refresh() {
        val recent = try {
            attach.recentLocations(dialogKey)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        synchronized(byMessage) { recent.forEach { byMessage[it.messageId] = it } }
        publish()
    }

    fun ownRadius(radius: Int) {
        synchronized(byMessage) {
            byMessage.values.filter { it.self }.forEach { own ->
                byMessage[own.messageId] = own.copy(place = own.place.copy(live = own.live.copy(radius = radius.takeIf { it > 0 })))
            }
        }
        publish()
    }

    private fun apply(event: ChatEvent) {
        when (event) {
            is ChatEvent.NewMessage -> if (event.key == dialogKey) changed(event.message.id, event.message.out, event.message.place)
            is ChatEvent.Edited -> if (event.key == dialogKey) changed(event.message.id, event.message.out, event.message.place)
            is ChatEvent.Deleted -> if (event.key == null || event.key == dialogKey) {
                synchronized(byMessage) { event.ids.forEach { byMessage.remove(it) } }
                publish()
            }
            else -> Unit
        }
    }

    private fun changed(id: Int, out: Boolean, place: GeoPlace?) {
        val live = place?.live ?: return
        synchronized(byMessage) {
            val known = byMessage[id]?.live
            val merged = if (known != null && live.senderKey == null) place.copy(live = live.copy(senderKey = known.senderKey, senderName = known.senderName, senderAvatar = known.senderAvatar)) else place
            byMessage[id] = LiveSharer(id, out, merged)
        }
        publish()
    }

    private fun publish() {
        val now = (System.currentTimeMillis() / 1000).toInt()
        state.value = synchronized(byMessage) { currentSharers(byMessage.values.toList(), now) }
    }

    private companion object {
        const val EXPIRE_MS = 30_000L
    }
}
