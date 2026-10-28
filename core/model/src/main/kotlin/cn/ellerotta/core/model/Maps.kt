package cn.ellerotta.core.model

import java.net.URLEncoder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

data class StaticMap(
    val latitude: Double,
    val longitude: Double,
    val zoom: Int,
    val width: Int,
    val height: Int,
    val dark: Boolean,
    val path: String? = null,
    val pathColor: Int = 0,
) {
    val cacheKey: String
        get() = String.format(Locale.ROOT, "%.6f_%.6f_%d_%dx%d_%s", latitude, longitude, zoom, width, height, if (dark) "dark" else "light") +
            path?.let { String.format(Locale.ROOT, "_%08x_%08x", it.hashCode(), pathColor) }.orEmpty()

    fun clamped(): StaticMap = copy(
        zoom = zoom.coerceIn(StaticMaps.MIN_ZOOM, StaticMaps.MAX_ZOOM),
        width = width.coerceIn(1, StaticMaps.MAX_SIZE),
        height = height.coerceIn(1, StaticMaps.MAX_SIZE),
    )
}

data class GeoMap(
    val latitude: Double,
    val longitude: Double,
    val accessHash: Long,
    val width: Int,
    val height: Int,
    val zoom: Int,
    val scale: Int,
    val accuracy: Int? = null,
) {
    val cacheKey: String
        get() = String.format(Locale.ROOT, "%.6f_%.6f_%dx%d_z%d_x%d", latitude, longitude, width, height, zoom, scale)

    fun clamped(): GeoMap = copy(
        width = width.coerceIn(GeoMaps.MIN_SIZE, GeoMaps.MAX_SIZE),
        height = height.coerceIn(GeoMaps.MIN_SIZE, GeoMaps.MAX_SIZE),
        zoom = zoom.coerceIn(GeoMaps.MIN_ZOOM, GeoMaps.MAX_ZOOM),
        scale = scale.coerceIn(1, GeoMaps.MAX_SCALE),
    )
}

object GeoMaps {
    const val MIN_SIZE = 16
    const val MAX_SIZE = 1024
    const val MIN_ZOOM = 13
    const val MAX_ZOOM = 20
    const val MAX_SCALE = 3

    const val PLACE_ZOOM = 15

    const val VENUE_ZOOM = 16

    const val LIVE_ZOOM = 15

    fun zoomOf(place: GeoPlace): Int = when {
        place.live != null -> LIVE_ZOOM
        place.venue != null || place.title != null -> VENUE_ZOOM
        else -> PLACE_ZOOM
    }

    fun scaleOf(density: Float): Int = if (density >= 2f) 2 else 1
}

object StaticMaps {
    const val SCALE = 2
    const val MAX_SIZE = 640
    const val MIN_ZOOM = 1
    const val MAX_ZOOM = 20

    const val SCREEN_ZOOM = 16

    private const val BASE = "https://maps.googleapis.com/maps/api/staticmap"

    val nightStyle: List<String> = listOf(
        "element:geometry|color:0x242f3e",
        "element:labels.text.stroke|color:0x242f3e",
        "element:labels.text.fill|color:0x746855",
        "feature:administrative.locality|element:labels.text.fill|color:0xd59563",
        "feature:poi|element:labels.text.fill|color:0xd59563",
        "feature:poi.park|element:geometry|color:0x263c3f",
        "feature:poi.park|element:labels.text.fill|color:0x6b9a76",
        "feature:road|element:geometry|color:0x38414e",
        "feature:road|element:geometry.stroke|color:0x212a37",
        "feature:road|element:labels.text.fill|color:0x9ca5b3",
        "feature:road.highway|element:geometry|color:0x746855",
        "feature:road.highway|element:geometry.stroke|color:0x1f2835",
        "feature:road.highway|element:labels.text.fill|color:0xf3d19c",
        "feature:transit|element:geometry|color:0x2f3948",
        "feature:transit.station|element:labels.text.fill|color:0xd59563",
        "feature:water|element:geometry|color:0x17263c",
        "feature:water|element:labels.text.fill|color:0x515c6d",
        "feature:water|element:labels.text.stroke|color:0x17263c",
    )

    fun url(map: StaticMap, key: String): String {
        val shown = map.clamped()
        val center = String.format(Locale.ROOT, "%.6f,%.6f", shown.latitude, shown.longitude)
        val styles = if (shown.dark) nightStyle.joinToString("") { "&style=${encode(it)}" } else ""
        val path = shown.path?.let { line ->
            val rgba = (shown.pathColor shl 8) or (shown.pathColor ushr 24)
            "&path=" + encode(String.format(Locale.ROOT, "color:0x%08x|weight:%d|enc:%s", rgba, PATH_WEIGHT, line))
        }.orEmpty()
        return "$BASE?center=$center&zoom=${shown.zoom}&size=${shown.width}x${shown.height}&scale=$SCALE&format=png&maptype=roadmap$styles$path&key=${encode(key)}"
    }

    private const val PATH_WEIGHT = 5

    const val LIVE_PICTURE = 160

    fun fit(points: List<GeoPlace>, size: Int, dark: Boolean, margin: Int = 24, maxZoom: Int = SCREEN_ZOOM): StaticMap? {
        if (points.isEmpty()) return null
        val first = points.first()
        val offsets = points.map { WebMercator.project(it.latitude, it.longitude, first.latitude, first.longitude, 0.0) }
        val left = offsets.minOf { it.first }
        val right = offsets.maxOf { it.first }
        val top = offsets.minOf { it.second }
        val bottom = offsets.maxOf { it.second }
        val span = maxOf(right - left, bottom - top)
        val room = (size - 2 * margin).coerceAtLeast(1)
        val zoom = if (span <= 0.0) maxZoom else floor(log2(room / span)).toInt().coerceIn(MIN_ZOOM, maxZoom)
        val (latitude, longitude) = WebMercator.offset(first.latitude, first.longitude, 0.0, (left + right) / 2, (top + bottom) / 2)
        return StaticMap(latitude, longitude, zoom, size, size, dark)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

object WebMercator {
    const val TILE = 256.0

    const val MAX_LATITUDE = 85.05112878

    fun worldSize(zoom: Double): Double = TILE * 2.0.pow(zoom)

    fun x(longitude: Double, zoom: Double): Double = (longitude + 180) / 360 * worldSize(zoom)

    fun y(latitude: Double, zoom: Double): Double {
        val s = sin(latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * PI / 180)
        return (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * worldSize(zoom)
    }

    fun longitude(x: Double, zoom: Double): Double {
        val raw = x / worldSize(zoom) * 360 - 180
        return ((raw + 180) % 360 + 360) % 360 - 180
    }

    fun latitude(y: Double, zoom: Double): Double {
        val n = PI - 2 * PI * y / worldSize(zoom)
        return (180 / PI * atan(0.5 * (exp(n) - exp(-n)))).coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
    }

    fun offset(latitude: Double, longitude: Double, zoom: Double, dx: Double, dy: Double): Pair<Double, Double> =
        latitude(y(latitude, zoom) + dy, zoom) to longitude(x(longitude, zoom) + dx, zoom)

    fun project(latitude: Double, longitude: Double, centerLatitude: Double, centerLongitude: Double, zoom: Double): Pair<Double, Double> {
        val world = worldSize(zoom)
        var dx = x(longitude, zoom) - x(centerLongitude, zoom)
        if (dx > world / 2) dx -= world else if (dx < -world / 2) dx += world
        return dx to y(latitude, zoom) - y(centerLatitude, zoom)
    }
}

object MapRefresh {
    const val SECONDS = 60L
    const val METRES = 100.0

    fun due(last: List<GeoPlace>?, lastAt: Long, next: List<GeoPlace>, now: Long): Boolean {
        if (last == null || last.size != next.size) return true
        val moved = last.zip(next).maxOfOrNull { (a, b) -> a.distanceTo(b) } ?: 0.0
        return moved > METRES || (moved > 0.0 && now - lastAt >= SECONDS)
    }
}

enum class TravelMode(val api: String) {
    Walking("walking"),
    Driving("driving"),
    Transit("transit"),
}

data class Route(val mode: TravelMode, val metres: Int, val seconds: Int, val polyline: String) {
    val minutes: Int get() = maxOf(1, (seconds + 59) / 60)

    val points: List<GeoPlace> get() = Polyline.decode(polyline)

    companion object {
        const val MIN_METRES = 50.0
    }
}

class TravelGuess {
    private var mode = TravelMode.Walking
    private var slowSince: Long? = null

    fun next(speed: Float, now: Long): TravelMode {
        when {
            speed > DRIVING_METRES_PER_SECOND -> {
                mode = TravelMode.Driving
                slowSince = null
            }
            speed < WALKING_METRES_PER_SECOND -> {
                val since = slowSince ?: now.also { slowSince = it }
                if (now - since >= WALK_AFTER_SECONDS) mode = TravelMode.Walking
            }
            else -> slowSince = null
        }
        return mode
    }

    companion object {
        const val DRIVING_METRES_PER_SECOND = 7f
        const val WALKING_METRES_PER_SECOND = 2f
        const val WALK_AFTER_SECONDS = 120L
    }
}

object Polyline {
    fun decode(encoded: String): List<GeoPlace> {
        val points = ArrayList<GeoPlace>()
        var index = 0
        var latitude = 0
        var longitude = 0
        fun next(): Int? {
            var result = 0
            var shift = 0
            while (index < encoded.length) {
                val b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
                if (b < 0x20) return if (result and 1 != 0) (result shr 1).inv() else result shr 1
            }
            return null
        }
        while (index < encoded.length) {
            latitude += next() ?: break
            longitude += next() ?: break
            points += GeoPlace(latitude / 1e5, longitude / 1e5)
        }
        return points
    }
}

enum class DistanceUnits {
    Metric,
    Imperial;

    companion object {
        fun of(country: String): DistanceUnits = if (country.uppercase(Locale.ROOT) in setOf("US", "LR", "MM")) Imperial else Metric
    }
}

enum class DistanceUnit { Metres, Kilometres, Feet, Miles }

data class DistanceAmount(val value: Double, val unit: DistanceUnit) {
    val text: String
        get() = if (value == value.roundToInt().toDouble()) value.roundToInt().toString() else String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')

    companion object {
        private const val FOOT = 0.3048
        private const val MILE = 1609.344

        fun of(metres: Double, units: DistanceUnits): DistanceAmount {
            val m = metres.coerceAtLeast(0.0)
            return when (units) {
                DistanceUnits.Metric -> when {
                    m < 1000 -> DistanceAmount(m.roundToInt().toDouble(), DistanceUnit.Metres)
                    else -> DistanceAmount(large(m / 1000), DistanceUnit.Kilometres)
                }
                DistanceUnits.Imperial -> {
                    val feet = m / FOOT
                    when {
                        feet < 100 -> DistanceAmount(feet.roundToInt().toDouble(), DistanceUnit.Feet)
                        feet < 1000 -> DistanceAmount((feet / 10).roundToInt() * 10.0, DistanceUnit.Feet)
                        else -> DistanceAmount(large(m / MILE), DistanceUnit.Miles)
                    }
                }
            }
        }

        private fun large(value: Double): Double = when {
            value < 1 -> (value * 100).roundToInt() / 100.0
            value < 10 -> (value * 10).roundToInt() / 10.0
            else -> value.roundToInt().toDouble()
        }

        fun feet(feet: Double): Double = feet * FOOT

        fun miles(miles: Double): Double = miles * MILE
    }
}

data class PlaceSuggestion(val placeId: String, val title: String, val subtitle: String = "")
