package app.miogram.maps

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.StaticMaps
import cn.ellerotta.core.ui.components.maps.MapCamera
import cn.ellerotta.core.ui.components.maps.MapMarker
import cn.ellerotta.core.ui.components.maps.MapRenderer
import cn.ellerotta.core.ui.components.maps.ROUTE_WIDTH
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

object GoogleMapRenderer : MapRenderer {
    @Composable
    override fun Map(camera: MapCamera, markers: List<MapMarker>, route: List<GeoPlace>, modifier: Modifier) {
        val position = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(LatLng(camera.latitude, camera.longitude), camera.zoom)
        }
        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val lineColor = MaterialTheme.colorScheme.primary
        val width = with(LocalDensity.current) { ROUTE_WIDTH.toPx() }
        val line = remember(route) { route.map { LatLng(it.latitude, it.longitude) } }
        LaunchedEffect(camera, position) {
            snapshotFlow { camera.target }.collect { target ->
                target ?: return@collect
                position.animate(CameraUpdateFactory.newLatLngZoom(LatLng(target.latitude, target.longitude), target.zoom), ANIMATION_MS)
            }
        }
        LaunchedEffect(camera, position) {
            snapshotFlow { position.position to position.isMoving }.collect { (shown, moving) ->
                camera.report(shown.target.latitude, shown.target.longitude, shown.zoom, moving)
            }
        }
        val properties = remember { MapProperties(minZoomPreference = StaticMaps.MIN_ZOOM.toFloat(), maxZoomPreference = StaticMaps.MAX_ZOOM.toFloat()) }
        val settings = remember {
            MapUiSettings(
                compassEnabled = false,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false,
                rotationGesturesEnabled = false,
                tiltGesturesEnabled = false,
                zoomControlsEnabled = false,
                indoorLevelPickerEnabled = false,
            )
        }
        GoogleMap(
            modifier = modifier,
            cameraPositionState = position,
            properties = properties,
            uiSettings = settings,
            mapColorScheme = if (dark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
        ) {
            if (line.size > 1) Polyline(points = line, color = lineColor, width = width, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap())
            markers.forEach { marker ->
                val state = remember(marker.key) { MarkerState(LatLng(marker.latitude, marker.longitude)) }
                state.position = LatLng(marker.latitude, marker.longitude)
                MarkerComposable(
                    marker.key, marker.version ?: Unit,
                    state = state,
                    anchor = if (marker.bottom) Offset(0.5f, 1f) else Offset(0.5f, 0.5f),
                ) { marker.content() }
            }
        }
    }

    private const val ANIMATION_MS = 400
}
