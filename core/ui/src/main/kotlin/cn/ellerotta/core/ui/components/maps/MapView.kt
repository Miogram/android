package cn.ellerotta.core.ui.components.maps

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.GeoMap
import cn.ellerotta.core.model.GeoMaps
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.StaticMap
import cn.ellerotta.core.model.StaticMaps
import cn.ellerotta.core.model.WebMercator
import cn.ellerotta.core.ui.R
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun darkMap(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

internal class MapPicture(val map: StaticMap, val bitmap: ImageBitmap)

@Composable
internal fun rememberMapPicture(map: StaticMap?): State<MapPicture?> {
    val images = LocalMapImages.current
    return produceState<MapPicture?>(null, map, images) {
        if (map == null || images == null) return@produceState
        val bytes = images.load(map) ?: return@produceState
        withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }?.let { value = MapPicture(map, it) }
    }
}

@Composable
fun StaticMapView(latitude: Double, longitude: Double, zoom: Int, modifier: Modifier = Modifier, overlay: @Composable BoxScope.() -> Unit = {}) {
    val dark = darkMap()
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val map = StaticMap(latitude, longitude, zoom, maxWidth.value.roundToInt(), maxHeight.value.roundToInt(), dark).clamped()
        val picture by rememberMapPicture(map)
        MapPlaceholder()
        picture?.let { Image(it.bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        overlay()
    }
}

@Composable
fun GeoMapView(place: GeoPlace, zoom: Int, modifier: Modifier = Modifier, overlay: @Composable BoxScope.() -> Unit = {}) {
    val dark = darkMap()
    val scale = GeoMaps.scaleOf(LocalDensity.current.density)
    val images = LocalGeoMapImages.current
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val hash = place.accessHash
        val map = hash?.let { GeoMap(place.latitude, place.longitude, it, maxWidth.value.roundToInt(), maxHeight.value.roundToInt(), zoom, scale, place.accuracy).clamped() }
        val picture by produceState<ImageBitmap?>(null, map, images) {
            if (map == null || images == null) return@produceState
            val bytes = images.load(map) ?: return@produceState
            withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }?.let { value = it }
        }
        MapPlaceholder()
        picture?.let {
            val dim = if (dark) ColorFilter.tint(Color.Black.copy(alpha = GEO_MAP_DIM), BlendMode.SrcAtop) else null
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = dim)
        }
        overlay()
    }
}

const val GEO_MAP_DIM = 0.25f

@Composable
private fun MapPlaceholder() {
    val ground = MaterialTheme.colorScheme.surfaceContainerHigh
    val street = MaterialTheme.colorScheme.surfaceContainerLowest
    Canvas(Modifier.fillMaxSize()) {
        drawRect(ground)
        val step = 48.dp.toPx()
        val width = 6.dp.toPx()
        var x = step / 2
        while (x < size.width) {
            drawLine(street, Offset(x, 0f), Offset(x, size.height), width)
            x += step
        }
        var y = step / 2
        while (y < size.height) {
            drawLine(street, Offset(0f, y), Offset(size.width, y), width)
            y += step
        }
    }
}

data class MapTarget(val latitude: Double, val longitude: Double, val zoom: Float, val serial: Int)

@Stable
class MapCamera(latitude: Double, longitude: Double, zoom: Float) {
    var latitude by mutableDoubleStateOf(latitude)
        private set
    var longitude by mutableDoubleStateOf(longitude)
        private set
    var zoom by mutableFloatStateOf(zoom)
        private set
    var moving by mutableStateOf(false)
        private set

    var target by mutableStateOf<MapTarget?>(null)
        private set

    fun moveTo(latitude: Double, longitude: Double, zoom: Float = this.zoom) {
        val shown = zoom.coerceIn(StaticMaps.MIN_ZOOM.toFloat(), StaticMaps.MAX_ZOOM.toFloat())
        this.latitude = latitude
        this.longitude = longitude
        this.zoom = shown
        target = MapTarget(latitude, longitude, shown, (target?.serial ?: 0) + 1)
    }

    fun zoomBy(by: Int) = moveTo(latitude, longitude, zoom.roundToInt() + by.toFloat())

    fun report(latitude: Double, longitude: Double, zoom: Float, moving: Boolean) {
        this.latitude = latitude
        this.longitude = longitude
        this.zoom = zoom
        this.moving = moving
    }

    companion object {
        val Saver = listSaver<MapCamera, Double>(
            save = { listOf(it.latitude, it.longitude, it.zoom.toDouble()) },
            restore = { MapCamera(it[0], it[1], it[2].toFloat()) },
        )
    }
}

@Composable
fun rememberMapCamera(latitude: Double, longitude: Double, zoom: Int = StaticMaps.SCREEN_ZOOM): MapCamera =
    rememberSaveable(saver = MapCamera.Saver) { MapCamera(latitude, longitude, zoom.toFloat()) }

class MapMarker(val key: Any, val latitude: Double, val longitude: Double, val bottom: Boolean = false, val version: Any? = null, val content: @Composable () -> Unit)

interface MapRenderer {
    @Composable
    fun Map(camera: MapCamera, markers: List<MapMarker>, route: List<GeoPlace>, modifier: Modifier)
}

val LocalMapRenderer = staticCompositionLocalOf<MapRenderer?> { null }

@Composable
fun InteractiveMap(camera: MapCamera, label: String, modifier: Modifier = Modifier, markers: List<MapMarker> = emptyList(), route: List<GeoPlace> = emptyList(), overlay: @Composable BoxScope.() -> Unit = {}) {
    val renderer = LocalMapRenderer.current
    val zoomIn = stringResource(R.string.map_zoom_in)
    val zoomOut = stringResource(R.string.map_zoom_out)
    Box(
        modifier
            .clipToBounds()
            .semantics {
                contentDescription = label
                customActions = listOf(
                    CustomAccessibilityAction(zoomIn) { camera.zoomBy(1); true },
                    CustomAccessibilityAction(zoomOut) { camera.zoomBy(-1); true },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (renderer != null) {
            renderer.Map(camera, markers, route, Modifier.fillMaxSize())
        } else {
            StaticMapView(camera.latitude, camera.longitude, camera.zoom.roundToInt(), Modifier.fillMaxSize())
            if (route.size > 1) RouteLayer(camera, route)
            MarkerLayer(camera, markers)
        }
        overlay()
    }
}

@Composable
private fun RouteLayer(camera: MapCamera, points: List<GeoPlace>) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize().testTag("mapRoute")) {
        val path = Path()
        points.forEachIndexed { index, point ->
            val (dx, dy) = WebMercator.project(point.latitude, point.longitude, camera.latitude, camera.longitude, camera.zoom.toDouble())
            val x = size.width / 2 + dx.toFloat().dp.toPx()
            val y = size.height / 2 + dy.toFloat().dp.toPx()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = ROUTE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

val ROUTE_WIDTH = 5.dp

@Composable
private fun MarkerLayer(camera: MapCamera, markers: List<MapMarker>) {
    Layout(
        content = { markers.forEach { marker -> key(marker.key) { Box { marker.content() } } } },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val marker = markers[index]
                val (dx, dy) = WebMercator.project(marker.latitude, marker.longitude, camera.latitude, camera.longitude, camera.zoom.toDouble())
                val x = constraints.maxWidth / 2f + dx.toFloat().dp.toPx() - placeable.width / 2f
                val y = constraints.maxHeight / 2f + dy.toFloat().dp.toPx() - if (marker.bottom) placeable.height.toFloat() else placeable.height / 2f
                placeable.place(x.roundToInt(), y.roundToInt())
            }
        }
    }
}
