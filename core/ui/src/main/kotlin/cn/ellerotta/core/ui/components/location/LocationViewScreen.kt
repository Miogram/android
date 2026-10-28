package cn.ellerotta.core.ui.components.location

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.LiveSharer
import cn.ellerotta.core.model.Route
import cn.ellerotta.core.model.TravelMode
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.BackPill
import cn.ellerotta.core.ui.components.common.ConnectedChoice
import cn.ellerotta.core.ui.components.common.TopBarPill
import cn.ellerotta.core.ui.components.common.TopBarTokens
import cn.ellerotta.core.ui.components.maps.AvatarMarker
import cn.ellerotta.core.ui.components.maps.InteractiveMap
import cn.ellerotta.core.ui.components.maps.MapMarker
import cn.ellerotta.core.ui.components.maps.MapPin
import cn.ellerotta.core.ui.components.maps.MyLocationDot
import cn.ellerotta.core.ui.components.maps.distanceLabel
import cn.ellerotta.core.ui.components.maps.durationLabel
import cn.ellerotta.core.ui.components.maps.rememberMapCamera
import cn.ellerotta.core.ui.components.maps.updatedLabel
import kotlinx.coroutines.delay

data class LocationViewState(
    val place: GeoPlace,
    val title: String? = null,
    val address: String? = null,
    val me: GeoPlace? = null,
    val sharers: List<LiveSharer> = emptyList(),
    val focus: String? = null,
    val travel: TravelMode? = null,
    val route: Route? = null,
    val routing: Boolean = false,
) {
    val live: Boolean get() = place.live != null

    val distance: Double? get() = me?.distanceTo(place)

    val routable: Boolean get() = travel != null && (distance ?: 0.0) >= Route.MIN_METRES
}

class LocationViewActions(
    val onDirections: () -> Unit,
    val onShare: () -> Unit,
    val onOpenInMaps: () -> Unit,
    val onBack: () -> Unit,
    val onFocus: (LiveSharer) -> Unit = {},
    val onAlert: ((Int) -> Unit)? = null,
    val onShareAlert: ((seconds: Int, radius: Int?) -> Unit)? = null,
    val onTravel: (TravelMode) -> Unit = {},
)

@Composable
fun LocationViewScreen(state: LocationViewState, avatars: Map<String, ByteArray>, actions: LocationViewActions) {
    val place = state.place
    val camera = rememberMapCamera(place.latitude, place.longitude)
    val inspecting = LocalInspectionMode.current
    val now by produceState(System.currentTimeMillis() / 1000) {
        if (!inspecting) while (true) {
            delay(TICK_MS)
            value = System.currentTimeMillis() / 1000
        }
    }
    val own = state.sharers.firstOrNull { it.self }
    val ownRadius = own?.live?.radius?.takeIf { it > 0 }
    val alertOffered = actions.onAlert != null && state.live && state.sharers.isNotEmpty() && (own != null || actions.onShareAlert != null)
    var alerting by rememberSaveable { mutableStateOf(false) }
    val title = state.title ?: stringResource(if (state.live) R.string.chat_place_live else R.string.chat_place)
    val markers = buildList {
        if (state.live) {
            state.sharers.forEach { sharer ->
                val photo = sharer.live.senderAvatar?.let(avatars::get)
                add(MapMarker(sharer.key, sharer.place.latitude, sharer.place.longitude, version = listOf(photo?.size, sharer.live.heading)) {
                    AvatarMarker(sharer.name, photo, sharer.live.heading)
                })
            }
        } else {
            add(MapMarker("place", place.latitude, place.longitude, bottom = true) { MapPin() })
        }
        state.me?.let { me -> add(MapMarker("me", me.latitude, me.longitude) { MyLocationDot() }) }
    }
    val route = state.route.takeIf { state.routable }
    val line = remember(route) { route?.points.orEmpty() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            InteractiveMap(
                camera,
                label = stringResource(R.string.location_view_map, title),
                markers = markers,
                route = line,
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("locationMap"),
            ) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = TopBarTokens.EdgeInset, vertical = TopBarTokens.EdgeInset).align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.spacedBy(TopBarTokens.PillGap),
                ) {
                    BackPill(actions.onBack)
                    Box(Modifier.weight(1f))
                    if (alertOffered) {
                        TopBarPill(
                            if (ownRadius != null) R.drawable.ic_notifications_active_filled else R.drawable.ic_notifications,
                            ownRadius?.let { stringResource(R.string.proximity_within, distanceLabel(it)) } ?: stringResource(R.string.proximity_notify),
                            { alerting = true },
                            Modifier.testTag("locationAlert"),
                        )
                    }
                    TopBarPill(R.drawable.ic_share, stringResource(R.string.location_share), actions.onShare, Modifier.testTag("locationShare"))
                }
                state.me?.let { me ->
                    TopBarPill(
                        R.drawable.ic_my_location, stringResource(R.string.location_my_location),
                        onClick = { camera.moveTo(me.latitude, me.longitude) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(TopBarTokens.EdgeInset).testTag("locationMe"),
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0)),
                modifier = Modifier.fillMaxWidth().testTag("locationPanel"),
            ) {
                Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(top = 20.dp, bottom = 16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() }.testTag("locationTitle"))
                    val details = listOfNotNull(state.address ?: place.coordinates.takeIf { state.title == null }, state.distance?.let { stringResource(R.string.location_away, distanceLabel(it)) })
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp).testTag("locationAddress"),
                        )
                    }
                    val travel = state.travel
                    if (travel != null && state.routable) {
                        ConnectedChoice(
                            TravelMode.entries, travel, actions.onTravel,
                            label = { stringResource(it.label) },
                            tag = { "travel_${it.api}" },
                            icon = { it.icon },
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        )
                        Text(
                            when {
                                state.routing -> stringResource(R.string.location_route_finding)
                                route != null -> stringResource(R.string.location_route, distanceLabel(route.metres), durationLabel(route.minutes))
                                else -> stringResource(R.string.location_route_none)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp).testTag("locationRoute"),
                        )
                    }
                    if (state.live && state.sharers.isNotEmpty()) {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 232.dp).padding(top = 8.dp).testTag("liveList")) {
                            items(state.sharers, key = { it.key }) { sharer ->
                                val alert = if (alertOffered && sharer.self) ({ alerting = true }) else null
                                SharerRow(sharer, avatars, state.me, now, focused = sharer.key == state.focus, alertRadius = ownRadius.takeIf { sharer.self }, onAlert = alert) {
                                    camera.moveTo(sharer.place.latitude, sharer.place.longitude)
                                    actions.onFocus(sharer)
                                }
                            }
                        }
                    }
                    Button(
                        onClick = actions.onDirections,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).heightIn(min = 56.dp).testTag("locationDirections"),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(painterResource(R.drawable.ic_directions), null, Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.location_directions))
                    }
                    FilledTonalButton(
                        onClick = actions.onOpenInMaps,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).heightIn(min = 48.dp).testTag("locationOpen"),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(painterResource(R.drawable.ic_map), null, Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.location_open_maps))
                    }
                }
            }
        }
    }
    val onAlert = actions.onAlert
    if (alerting && alertOffered && onAlert != null) {
        LiveAlertSheet(state.sharers, person = null, me = state.me, onAlert = onAlert, onShare = actions.onShareAlert, onDismiss = { alerting = false }, focus = state.focus)
    }
}

@Composable
private fun SharerRow(sharer: LiveSharer, avatars: Map<String, ByteArray>, me: GeoPlace?, now: Long, focused: Boolean, alertRadius: Int?, onAlert: (() -> Unit)?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val updated = sharer.live.updated?.let { updatedLabel((now - it).coerceAtLeast(0)) }
    val distance = me?.takeIf { !sharer.self }?.let { stringResource(R.string.location_away, distanceLabel(it.distanceTo(sharer.place))) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = stringResource(R.string.location_show_on_map), role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .testTag("sharer_${sharer.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Avatar(sharer.name, sharer.live.senderAvatar?.let(avatars::get), size = 40)
        Column(Modifier.weight(1f)) {
            Text(
                if (sharer.self) stringResource(R.string.location_you) else sharer.name,
                style = if (focused) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val line = listOfNotNull(distance, updated).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (alertRadius != null) {
                Text(
                    stringResource(R.string.proximity_within, distanceLabel(alertRadius)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("ownAlert"),
                )
            }
        }
        if (onAlert != null) {
            IconButton(onClick = onAlert, shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag("alert_${sharer.key}")) {
                val label = alertRadius?.let { stringResource(R.string.proximity_within, distanceLabel(it)) } ?: stringResource(R.string.proximity_notify)
                Icon(painterResource(if (alertRadius != null) R.drawable.ic_notifications_active_filled else R.drawable.ic_notifications), label, tint = colors.onSurfaceVariant)
            }
        }
    }
}

private const val TICK_MS = 30_000L

private val TravelMode.label: Int
    get() = when (this) {
        TravelMode.Walking -> R.string.location_travel_walking
        TravelMode.Driving -> R.string.location_travel_driving
        TravelMode.Transit -> R.string.location_travel_transit
    }

private val TravelMode.icon: Int
    get() = when (this) {
        TravelMode.Walking -> R.drawable.ic_directions_walk
        TravelMode.Driving -> R.drawable.ic_directions_car
        TravelMode.Transit -> R.drawable.ic_directions_bus
    }
