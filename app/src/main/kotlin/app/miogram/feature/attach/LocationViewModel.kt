package app.miogram.feature.attach

import androidx.lifecycle.viewModelScope
import app.miogram.data.DeviceLocation
import app.miogram.data.maps.MapsRepository
import app.miogram.data.maps.PlacesSession
import app.miogram.data.telegram.AttachRepository
import app.miogram.location.LiveLocations
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.PlaceSuggestion
import cn.ellerotta.core.ui.components.attach.LocationShareState
import cn.ellerotta.core.ui.components.attach.LocationStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = LocationViewModel.Factory::class)
class LocationViewModel @AssistedInject constructor(
    @Assisted private val dialogKey: String,
    @Assisted("title") private val title: String,
    @Assisted("replyTo") private val replyTo: Int?,
    @Assisted("topicId") private val topicId: Int?,
    @Assisted("alertName") alertName: String?,
    private val device: DeviceLocation,
    private val attach: AttachRepository,
    private val live: LiveLocations,
    private val maps: MapsRepository,
) : WorkViewModel() {
    private val stateFlow = MutableStateFlow(LocationShareState(maps = maps.enabled, alertName = alertName))
    private val sentEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val session = PlacesSession()
    private var locating: Job? = null
    private var picking: Job? = null
    private var searching: Job? = null

    val state: StateFlow<LocationShareState> = stateFlow.asStateFlow()

    val sent: SharedFlow<Unit> = sentEvents.asSharedFlow()

    init {
        locate()
        viewModelScope.launch { live.shares.collect { all -> stateFlow.update { it.copy(sharing = dialogKey in all) } } }
    }

    fun locate() {
        locating?.cancel()
        val status = when {
            !device.permitted() -> LocationStatus.NoPermission
            !device.enabled() -> LocationStatus.Off
            else -> LocationStatus.Locating
        }
        stateFlow.update { it.copy(status = status) }
        if (status != LocationStatus.Locating) return
        locating = viewModelScope.launch {
            val fix = device.current()
            stateFlow.update {
                if (fix == null) it.copy(status = LocationStatus.Unavailable)
                else it.copy(status = LocationStatus.Found, place = GeoPlace(fix.latitude, fix.longitude), accuracy = fix.accuracy.roundToInt().takeIf { _ -> fix.hasAccuracy() })
            }
        }
    }

    fun pick(latitude: Double, longitude: Double) {
        picking?.cancel()
        stateFlow.update { it.copy(picked = GeoPlace(latitude, longitude)) }
        if (!maps.enabled) return
        picking = viewModelScope.launch {
            delay(PICK_PAUSE_MS)
            val address = maps.address(latitude, longitude)
            stateFlow.update { current ->
                val shown = current.picked
                if (shown?.latitude == latitude && shown.longitude == longitude) current.copy(picked = address ?: shown) else current
            }
            val nearby = maps.nearby(latitude, longitude)
            stateFlow.update { it.copy(nearby = nearby) }
        }
    }

    fun search(query: String) {
        searching?.cancel()
        stateFlow.update { it.copy(query = query, suggestions = if (query.isBlank()) emptyList() else it.suggestions) }
        if (query.isBlank()) {
            session.end()
            return
        }
        searching = viewModelScope.launch {
            delay(SEARCH_PAUSE_MS)
            val near = stateFlow.value.picked ?: stateFlow.value.place
            val found = maps.suggest(query, session, near)
            stateFlow.update { if (it.query == query) it.copy(suggestions = found) else it }
        }
    }

    fun sendSuggestion(suggestion: PlaceSuggestion) {
        work {
            val venue = maps.details(suggestion, session) ?: error("place not found")
            attach.sendPlace(dialogKey, venue, replyTo, topicId)
            sentEvents.emit(Unit)
        }
    }

    fun sendPlace(place: GeoPlace) {
        work {
            attach.sendPlace(dialogKey, place.takeIf { it.venue != null } ?: GeoPlace(place.latitude, place.longitude), replyTo, topicId)
            sentEvents.emit(Unit)
        }
    }

    fun sendCurrent() {
        stateFlow.value.place?.let(::sendPlace)
    }

    fun startLive(seconds: Int, radius: Int?) {
        val place = stateFlow.value.place ?: return
        work {
            live.start(dialogKey, title, place, heading = null, seconds, radius, replyTo, topicId)
            sentEvents.emit(Unit)
        }
    }

    fun stopLive() {
        work { live.stop(dialogKey) }
    }

    @AssistedFactory
    interface Factory {
        fun create(dialogKey: String, @Assisted("title") title: String, @Assisted("replyTo") replyTo: Int?, @Assisted("topicId") topicId: Int?, @Assisted("alertName") alertName: String?): LocationViewModel
    }

    private companion object {
        const val PICK_PAUSE_MS = 400L
        const val SEARCH_PAUSE_MS = 300L
    }
}
