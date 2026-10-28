package cn.ellerotta.core.ui.components.attach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.LivePeriods
import cn.ellerotta.core.model.PlaceSuggestion
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.tipOnCentre
import cn.ellerotta.core.ui.components.common.BackPill
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.TopBarPill
import cn.ellerotta.core.ui.components.common.TopBarTokens
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.components.maps.InteractiveMap
import cn.ellerotta.core.ui.components.maps.MapPin
import cn.ellerotta.core.ui.components.location.LiveShareSheet
import cn.ellerotta.core.ui.components.maps.distanceLabel
import cn.ellerotta.core.ui.components.maps.rememberMapCamera
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

enum class LocationStatus { Locating, Found, NoPermission, Off, Unavailable }

data class LocationShareState(
    val status: LocationStatus = LocationStatus.Locating,
    val place: GeoPlace? = null,
    val accuracy: Int? = null,
    val sharing: Boolean = false,
    val maps: Boolean = false,
    val picked: GeoPlace? = null,
    val nearby: List<GeoPlace> = emptyList(),
    val query: String = "",
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val alertName: String? = null,
) {
    val pickedElsewhere: Boolean
        get() {
            val point = picked ?: return false
            val here = place ?: return true
            return here.distanceTo(point) > SAME_PLACE_METRES
        }

    private companion object {
        const val SAME_PLACE_METRES = 30.0
    }
}

class LiveActions(val onShare: (seconds: Int, radius: Int?) -> Unit, val onStop: () -> Unit)

class LocationPickActions(
    val onPick: (latitude: Double, longitude: Double) -> Unit,
    val onQuery: (String) -> Unit,
    val onSuggestion: (PlaceSuggestion) -> Unit,
    val onSendPlace: (GeoPlace) -> Unit,
)

@Composable
fun LocationShareScreen(
    state: LocationShareState,
    busy: Boolean,
    onAllow: () -> Unit,
    onRetry: () -> Unit,
    onSendCurrent: () -> Unit,
    live: LiveActions?,
    onBack: () -> Unit,
    pick: LocationPickActions? = null,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    if (state.maps && pick != null) {
        MapPicker(state, busy, onAllow, onRetry, onSendCurrent, live, onBack, pick) { choosing = true }
    } else {
        val list = rememberLazyListState()
        val title = stringResource(R.string.location_title)
        Page(title, rememberTopBarState(list), onBack = onBack) { padding ->
            LazyColumn(Modifier.fillMaxSize().testTag("locationList"), state = list, contentPadding = padding) {
                largeTitle(title)
                locationRows(state, busy, onAllow, onRetry, onSendCurrent, live, pick = null) { choosing = true }
                item(key = "end") { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
    if (choosing && live != null) {
        LiveShareSheet(state.alertName, distance = null, askFirst = false, onShare = live.onShare, onDismiss = { choosing = false })
    }
}

@Composable
private fun MapPicker(
    state: LocationShareState,
    busy: Boolean,
    onAllow: () -> Unit,
    onRetry: () -> Unit,
    onSendCurrent: () -> Unit,
    live: LiveActions?,
    onBack: () -> Unit,
    pick: LocationPickActions,
    onChooseLive: () -> Unit,
) {
    val start = state.picked ?: state.place
    val camera = rememberMapCamera(start?.latitude ?: 0.0, start?.longitude ?: 0.0, zoom = if (start == null) 2 else 16)
    var centred by rememberSaveable { mutableStateOf(start != null) }
    LaunchedEffect(state.place) {
        val here = state.place
        if (here != null && !centred) {
            centred = true
            camera.moveTo(here.latitude, here.longitude, 16f)
        }
    }
    LaunchedEffect(camera) {
        snapshotFlow { if (camera.moving) null else camera.latitude to camera.longitude }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { (lat, lon) -> if (centred) pick.onPick(lat, lon) }
    }
    LaunchedEffect(camera) { snapshotFlow { camera.moving }.collect { if (it) centred = true } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            InteractiveMap(camera, stringResource(R.string.location_picker_map), Modifier.fillMaxWidth().height(MAP_HEIGHT).testTag("locationMap")) {
                MapPin(Modifier.align(Alignment.Center).tipOnCentre().testTag("locationPin"), lifted = camera.moving)
                BackPill(onBack, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(TopBarTokens.EdgeInset))
                state.place?.let { here ->
                    TopBarPill(
                        R.drawable.ic_my_location, stringResource(R.string.location_my_location),
                        onClick = { camera.moveTo(here.latitude, here.longitude, 16f) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(TopBarTokens.EdgeInset).testTag("locationMe"),
                    )
                }
            }
            SearchField(state.query, pick.onQuery)
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("locationList"), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (state.query.isNotBlank()) {
                    items(state.suggestions, key = { "s_${it.placeId}" }) { suggestion ->
                        PlaceRow(R.drawable.ic_location_on, suggestion.title, suggestion.subtitle, "suggestion_${suggestion.placeId}", enabled = !busy) { pick.onSuggestion(suggestion) }
                    }
                } else {
                    locationRows(state, busy, onAllow, onRetry, onSendCurrent, live, pick, onChooseLive)
                }
            }
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.location_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
        trailingIcon = if (query.isEmpty()) null else ({
            IconButton(onClick = { onQuery("") }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag("locationSearchClear")) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.location_search_clear))
            }
        }),
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("locationSearch"),
    )
}

private fun LazyListScope.locationRows(
    state: LocationShareState,
    busy: Boolean,
    onAllow: () -> Unit,
    onRetry: () -> Unit,
    onSendCurrent: () -> Unit,
    live: LiveActions?,
    pick: LocationPickActions?,
    onChooseLive: () -> Unit,
) {
    item(key = "busy") { if (busy || state.status == LocationStatus.Locating) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) }
    item(key = "current") { CurrentRow(state, busy, onAllow, onRetry, onSendCurrent) }
    val picked = state.picked
    if (pick != null && picked != null && state.pickedElsewhere) {
        item(key = "picked") {
            PlaceRow(
                R.drawable.ic_pin_drop, stringResource(R.string.location_send_picked),
                listOfNotNull(picked.title, picked.address).joinToString(", ").ifEmpty { picked.coordinates },
                "locationPicked", enabled = !busy,
            ) { pick.onSendPlace(picked) }
        }
    }
    if (live != null) {
        item(key = "live") {
            if (state.sharing) {
                PlaceRow(R.drawable.ic_close, stringResource(R.string.location_live_stop), null, "locationStop", enabled = !busy, onClick = live.onStop)
            } else {
                PlaceRow(R.drawable.ic_share_location, stringResource(R.string.location_live_start), stringResource(R.string.location_live_hint), "locationLive", enabled = !busy && state.place != null, onClick = onChooseLive)
            }
        }
    }
    if (pick != null && state.nearby.isNotEmpty()) {
        item(key = "nearbyTitle") {
            Text(
                stringResource(R.string.location_nearby),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
            )
        }
        items(state.nearby, key = { "n_${it.venue?.id ?: it.coordinates}" }) { place ->
            PlaceRow(R.drawable.ic_location_on, place.title.orEmpty(), place.address, "nearby_${place.venue?.id}", enabled = !busy, quiet = true) { pick.onSendPlace(place) }
        }
    }
}

@Composable
private fun CurrentRow(state: LocationShareState, busy: Boolean, onAllow: () -> Unit, onRetry: () -> Unit, onSend: () -> Unit) {
    val found = state.status == LocationStatus.Found && state.place != null
    val subtitle = when (state.status) {
        LocationStatus.Found -> state.accuracy?.let { stringResource(R.string.location_accuracy, distanceLabel(it)) } ?: state.place?.coordinates
        LocationStatus.Locating -> stringResource(R.string.location_locating)
        LocationStatus.NoPermission -> stringResource(R.string.location_permission)
        LocationStatus.Off -> stringResource(R.string.location_off)
        LocationStatus.Unavailable -> stringResource(R.string.location_unavailable)
    }
    Column {
        PlaceRow(R.drawable.ic_my_location, stringResource(R.string.location_send_mine), subtitle, "locationSend", enabled = found && !busy, onClick = onSend)
        when (state.status) {
            LocationStatus.NoPermission -> FilledTonalButton(onClick = onAllow, modifier = Modifier.padding(start = 72.dp, bottom = 8.dp).heightIn(min = 48.dp).testTag("locationAllow"), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.location_allow))
            }
            LocationStatus.Off, LocationStatus.Unavailable -> FilledTonalButton(onClick = onRetry, modifier = Modifier.padding(start = 72.dp, bottom = 8.dp).heightIn(min = 48.dp).testTag("locationRetry"), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.location_retry))
            }
            else -> Unit
        }
    }
}

@Composable
private fun PlaceRow(icon: Int, title: String, subtitle: String?, tag: String, enabled: Boolean, quiet: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val (container, content) = if (quiet) colors.surfaceContainerHigh to colors.onSurface else colors.primaryContainer to colors.onPrimaryContainer
        Surface(shape = CircleShape, color = container, contentColor = content) {
            Icon(painterResource(icon), null, Modifier.padding(10.dp).size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled || quiet) colors.onSurface else colors.onSurface.copy(alpha = 0.38f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
internal fun LiveDurationContent(onShare: (seconds: Int) -> Unit, alertName: String? = null, alertOffered: Boolean = false, radius: Int? = null, onAlert: () -> Unit = {}) {
    var period by rememberSaveable { mutableIntStateOf(LivePeriods.offered[1]) }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(stringResource(R.string.location_live_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() })
        Text(stringResource(R.string.location_live_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Column(Modifier.selectableGroup()) {
            LivePeriods.offered.forEach { seconds ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(selected = period == seconds, role = Role.RadioButton) { period = seconds }
                        .padding(horizontal = 24.dp)
                        .testTag("locationPeriod_$seconds"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RadioButton(selected = period == seconds, onClick = null)
                    Text(periodLabel(seconds), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (alertOffered) {
            val colors = MaterialTheme.colorScheme
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(role = Role.Button, onClick = onAlert)
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .testTag("locationLiveAlert"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(painterResource(if (radius != null) R.drawable.ic_notifications_active_filled else R.drawable.ic_notifications), null, tint = colors.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(alertName?.let { stringResource(R.string.proximity_ask, it) } ?: stringResource(R.string.proximity_notify), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                    Text(
                        radius?.let { stringResource(R.string.proximity_within, distanceLabel(it)) } ?: stringResource(R.string.proximity_off),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.testTag("locationLiveAlertValue"),
                    )
                }
            }
        }
        Button(
            onClick = { onShare(period) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).heightIn(min = 56.dp).testTag("locationLiveShare"),
            shapes = ButtonDefaults.shapes(),
        ) { Text(stringResource(R.string.location_live_start)) }
    }
}

@Composable
internal fun periodLabel(seconds: Int): String = when {
    seconds <= 0 -> stringResource(R.string.location_period_forever)
    seconds < 3600 -> stringResource(R.string.location_period_minutes, seconds / 60)
    else -> stringResource(R.string.location_period_hours, seconds / 3600)
}

private val MAP_HEIGHT = 320.dp
