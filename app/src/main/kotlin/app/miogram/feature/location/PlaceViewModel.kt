package app.miogram.feature.location

import androidx.lifecycle.viewModelScope
import app.miogram.data.AvatarStore
import app.miogram.data.DeviceLocation
import app.miogram.data.maps.MapsRepository
import app.miogram.data.telegram.AttachRepository
import app.miogram.data.telegram.UpdatesRepository
import app.miogram.feature.common.WorkViewModel
import app.miogram.location.ChatLiveLocations
import app.miogram.location.LiveLocations
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.LiveSharer
import cn.ellerotta.core.model.MapRefresh
import cn.ellerotta.core.model.TravelMode
import cn.ellerotta.core.ui.components.location.LocationViewState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = PlaceViewModel.Factory::class)
class PlaceViewModel @AssistedInject constructor(
    @Assisted private val dialogKey: String,
    @Assisted private val messageId: Int,
    @Assisted private val place: GeoPlace,
    attach: AttachRepository,
    updates: UpdatesRepository,
    private val liveLocations: LiveLocations,
    private val maps: MapsRepository,
    private val device: DeviceLocation,
    private val avatarStore: AvatarStore,
) : WorkViewModel() {
    private val stateFlow = MutableStateFlow(
        LocationViewState(
            place, title = place.title ?: place.live?.senderName, address = place.address, focus = place.live?.senderKey,
            travel = TravelMode.Walking.takeIf { maps.enabled },
        ),
    )
    private var following: Job? = null
    private var locations: ChatLiveLocations? = null
    private var described: GeoPlace? = null
    private var routing: Job? = null
    private var routed: List<GeoPlace>? = null
    private var routedAt = 0L

    val state: StateFlow<LocationViewState> = stateFlow.asStateFlow()
    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars

    init {
        if (place.live == null) {
            if (place.title == null) viewModelScope.launch { describe(place) }
        } else {
            val live = ChatLiveLocations(dialogKey, attach, updates, viewModelScope)
            locations = live
            viewModelScope.launch { live.sharers.collect(::show) }
        }
        follow()
    }

    fun follow() {
        if (!device.permitted() || following?.isActive == true) return
        following = viewModelScope.launch {
            device.current()?.let { fix -> moved(GeoPlace(fix.latitude, fix.longitude)) }
            device.updates(MOVE_MS, MOVE_METRES).collect { fix -> moved(GeoPlace(fix.latitude, fix.longitude)) }
        }
    }

    fun travel(mode: TravelMode) {
        if (stateFlow.value.travel == null || stateFlow.value.travel == mode) return
        stateFlow.update { it.copy(travel = mode, route = null) }
        route(force = true)
    }

    private fun moved(me: GeoPlace) {
        stateFlow.update { it.copy(me = me) }
        route()
    }

    private fun route(force: Boolean = false) {
        val shown = stateFlow.value
        val me = shown.me ?: return
        val mode = shown.travel ?: return
        if (!shown.routable) {
            routing?.cancel()
            routed = null
            stateFlow.update { it.copy(route = null, routing = false) }
            return
        }
        val points = listOf(me, shown.place)
        val now = System.currentTimeMillis() / 1000
        if (!force && !MapRefresh.due(routed, routedAt, points, now)) return
        routed = points
        routedAt = now
        routing?.cancel()
        if (shown.route == null) stateFlow.update { it.copy(routing = true) }
        routing = viewModelScope.launch {
            val found = try {
                maps.route(me, shown.place, mode)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            stateFlow.update { it.copy(route = found ?: it.route?.takeIf { route -> route.mode == mode }, routing = false) }
        }
    }

    fun setAlert(radius: Int) {
        work {
            liveLocations.alert(dialogKey, radius, stateFlow.value.sharers.firstOrNull { it.self })
            locations?.ownRadius(radius)
        }
    }

    fun share(title: String, seconds: Int, radius: Int?) {
        work {
            val failed = liveLocations.startHere(dialogKey, title, seconds, radius)
            if (failed != null) fail(failed) else locations?.refresh()
        }
    }

    fun focus(sharer: LiveSharer) {
        stateFlow.update { it.copy(place = sharer.place, focus = sharer.key, title = sharer.name.ifBlank { null }, address = null, route = null) }
        route(force = true)
        described = null
        viewModelScope.launch { describe(sharer.place) }
    }

    private fun show(sharers: List<LiveSharer>) {
        avatarStore.load(sharers.map { it.live.senderAvatar })
        val focus = stateFlow.value.focus
        val focused = sharers.firstOrNull { it.key == focus } ?: sharers.firstOrNull { it.messageId == messageId }
        stateFlow.update { it.copy(sharers = sharers, place = focused?.place ?: it.place, title = it.title ?: focused?.name?.ifBlank { null }) }
        route()
        val at = focused?.place ?: return
        if (described.let { it == null || it.distanceTo(at) > ADDRESS_METRES }) viewModelScope.launch { describe(at) }
    }

    private suspend fun describe(at: GeoPlace) {
        described = at
        val found = try {
            maps.address(at.latitude, at.longitude)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return
        stateFlow.update { current ->
            when {
                current.place.distanceTo(at) > ADDRESS_METRES -> current
                current.live -> current.copy(address = listOfNotNull(found.title, found.address).joinToString(", "))
                else -> current.copy(title = found.title, address = found.address)
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(dialogKey: String, messageId: Int, place: GeoPlace): PlaceViewModel
    }

    private companion object {
        const val MOVE_MS = 10_000L
        const val MOVE_METRES = 10f
        const val ADDRESS_METRES = 50.0
    }
}
