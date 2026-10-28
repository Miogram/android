package app.miogram.location

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import app.miogram.data.maps.MapsRepository
import app.miogram.push.placeholderPhoto
import app.miogram.push.roundPhoto
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.MapRefresh
import cn.ellerotta.core.model.Route
import cn.ellerotta.core.model.StaticMap
import cn.ellerotta.core.model.StaticMaps
import cn.ellerotta.core.model.TravelMode
import cn.ellerotta.core.model.WebMercator
import cn.ellerotta.core.ui.components.maps.MapImages
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MapPerson(val key: String, val name: String, val place: GeoPlace)

class LiveMapPicture(private val maps: MapImages, private val avatars: suspend (String) -> ByteArray?) {
    private var asked: List<GeoPlace>? = null
    private var askedAt = 0L
    private var askedDark: Boolean? = null
    private var askedRoute: String? = null
    private var base: Pair<StaticMap, Bitmap>? = null
    private val photos = HashMap<String, Bitmap?>()

    suspend fun draw(me: GeoPlace, people: List<MapPerson>, target: String?, radius: Int?, dark: Boolean, accent: Int, now: Long, route: Route? = null): Bitmap? {
        val points = listOf(me) + people.map { it.place }
        if (askedDark != dark || askedRoute != route?.polyline || MapRefresh.due(asked, askedAt, points, now)) {
            asked = points
            askedAt = now
            askedDark = dark
            askedRoute = route?.polyline
            val map = StaticMaps.fit(points + route?.points.orEmpty(), StaticMaps.LIVE_PICTURE, dark, margin = MARGIN)?.copy(path = route?.polyline, pathColor = accent)
            val bytes = map?.let { maps.load(it) }
            val decoded = bytes?.let { withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size) } }
            if (map != null && decoded != null) base = map to decoded
        }
        val (map, picture) = base ?: return null
        people.forEach { person -> if (person.key !in photos) photos[person.key] = avatars(person.key)?.let(::roundPhoto) }
        return withContext(Dispatchers.Default) { compose(map, picture, me, people, target, radius, accent) }
    }

    suspend fun photo(key: String, name: String): Bitmap {
        if (key !in photos) photos[key] = avatars(key)?.let(::roundPhoto)
        return photos[key] ?: placeholderPhoto(name, key, TRACKER)
    }

    private fun compose(map: StaticMap, picture: Bitmap, me: GeoPlace, people: List<MapPerson>, target: String?, radius: Int?, accent: Int): Bitmap {
        val side = map.width * StaticMaps.SCALE
        val scale = StaticMaps.SCALE.toFloat()
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(picture, Rect(0, 0, picture.width, picture.height), Rect(0, 0, side, side), paint)
        fun at(place: GeoPlace): Pair<Float, Float> {
            val (dx, dy) = WebMercator.project(place.latitude, place.longitude, map.latitude, map.longitude, map.zoom.toDouble())
            val edge = MARKER * scale
            return (side / 2f + dx.toFloat() * scale).coerceIn(edge, side - edge) to (side / 2f + dy.toFloat() * scale).coerceIn(edge, side - edge)
        }
        val (meX, meY) = at(me)
        if (radius != null && radius > 0) {
            val metresPerDp = EARTH * cos(me.latitude * PI / 180) / WebMercator.TILE / 2.0.pow(map.zoom)
            val ring = (radius / metresPerDp).toFloat() * scale
            paint.style = Paint.Style.FILL
            paint.color = withAlpha(accent, 0x2E)
            canvas.drawCircle(meX, meY, ring, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * scale
            paint.color = withAlpha(accent, 0xB0)
            canvas.drawCircle(meX, meY, ring, paint)
        }
        paint.style = Paint.Style.FILL
        people.forEach { person ->
            val (x, y) = at(person.place)
            val ring = MARKER * scale
            paint.color = if (person.key == target) accent else RING
            canvas.drawCircle(x, y, ring, paint)
            val inner = ring - 2 * scale
            val photo = photos[person.key] ?: placeholderPhoto(person.name, person.key, (inner * 2).toInt().coerceAtLeast(1))
            canvas.drawBitmap(photo, Rect(0, 0, photo.width, photo.height), RectF(x - inner, y - inner, x + inner, y + inner), paint)
        }
        paint.color = RING
        canvas.drawCircle(meX, meY, 8 * scale, paint)
        paint.color = accent
        canvas.drawCircle(meX, meY, 5.5f * scale, paint)
        return out
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private companion object {
        const val MARKER = 14

        const val MARGIN = 20

        const val TRACKER = 96

        const val EARTH = 40_075_016.686

        const val RING = 0xFFFFFFFF.toInt()
    }
}

class LiveRoutes(private val maps: MapsRepository) {
    private var asked: List<GeoPlace>? = null
    private var askedAt = 0L
    private var askedFor: Pair<String, TravelMode>? = null
    private var last: Route? = null

    suspend fun route(me: GeoPlace, key: String?, target: GeoPlace?, mode: TravelMode, now: Long): Route? {
        if (key == null || target == null || me.distanceTo(target) < Route.MIN_METRES) {
            asked = null
            askedFor = null
            last = null
            return null
        }
        val points = listOf(me, target)
        if (askedFor != key to mode || MapRefresh.due(asked, askedAt, points, now)) {
            asked = points
            askedAt = now
            askedFor = key to mode
            last = maps.route(me, target, mode) ?: last?.takeIf { it.mode == mode }
        }
        return last
    }
}
