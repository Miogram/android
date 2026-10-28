package app.miogram.data.maps

import android.content.Context
import android.util.LruCache
import app.miogram.data.FileCache
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.PlaceSuggestion
import cn.ellerotta.core.model.Route
import cn.ellerotta.core.model.StaticMap
import cn.ellerotta.core.model.StaticMaps
import cn.ellerotta.core.model.TravelMode
import cn.ellerotta.core.model.Venue
import cn.ellerotta.core.ui.components.maps.MapImages
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class MapsRepository(private val http: MapsHttp, private val config: MapsConfig, dir: File) : MapImages {
    @Inject
    constructor(http: MapsHttp, config: MapsConfig, @ApplicationContext context: Context) : this(http, config, File(context.cacheDir, "maps"))

    private val pictures = FileCache(File(dir, "static"), MAX_PICTURES)
    private val addresses = FileCache(File(dir, "geocode"), MAX_ADDRESSES)
    private val shownAddresses = LruCache<String, GeoPlace>(ADDRESSES_IN_MEMORY)
    private val routes = LruCache<String, Route>(ROUTES_IN_MEMORY)
    private val loading = ConcurrentHashMap<String, Mutex>()

    val enabled: Boolean get() = config.enabled

    override suspend fun load(map: StaticMap): ByteArray? {
        if (!enabled) return null
        val shown = map.clamped()
        val key = shown.cacheKey
        return loading.getOrPut(key) { Mutex() }.withLock {
            withContext(Dispatchers.IO) { pictures.hit(pictures.file("$key.png"))?.readBytes() } ?: fetch(StaticMaps.url(shown, config.apiKey))?.also { bytes ->
                withContext(Dispatchers.IO) { keep(pictures, "$key.png", bytes) }
            }
        }
    }

    suspend fun address(latitude: Double, longitude: Double, language: String = Locale.getDefault().toLanguageTag()): GeoPlace? {
        if (!enabled) return null
        val key = String.format(Locale.ROOT, "%.4f_%.4f_%s", latitude, longitude, language)
        shownAddresses.get(key)?.let { return it.copy(latitude = latitude, longitude = longitude) }
        val stored = withContext(Dispatchers.IO) { addresses.hit(addresses.file("$key.txt"))?.readText() }
        val formatted = stored ?: run {
            val url = "$BASE/geocode/json?latlng=${point(latitude, longitude)}&language=${encode(language)}&key=${encode(config.apiKey)}"
            val json = fetchJson(url) ?: return null
            json.optJSONArray("results")?.optJSONObject(0)?.optString("formatted_address")?.takeIf { it.isNotBlank() } ?: return null
        }
        if (stored == null) withContext(Dispatchers.IO) { keep(addresses, "$key.txt", formatted.toByteArray()) }
        val comma = formatted.indexOf(',')
        val place = if (comma < 0) GeoPlace(latitude, longitude, formatted) else GeoPlace(latitude, longitude, formatted.substring(0, comma).trim(), formatted.substring(comma + 1).trim())
        shownAddresses.put(key, place)
        return place
    }

    suspend fun nearby(latitude: Double, longitude: Double, language: String = Locale.getDefault().toLanguageTag()): List<GeoPlace> {
        if (!enabled) return emptyList()
        val url = "$BASE/place/nearbysearch/json?location=${point(latitude, longitude)}&radius=$NEARBY_METRES&language=${encode(language)}&key=${encode(config.apiKey)}"
        val results = fetchJson(url)?.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { index ->
            val result = results.optJSONObject(index) ?: return@mapNotNull null
            val types = result.optJSONArray("types")?.let { array -> (0 until array.length()).map(array::optString) }.orEmpty()
            if (types.any { it in AREAS }) return@mapNotNull null
            venueOf(result, result.optString("vicinity"), types.firstOrNull().orEmpty())
        }
    }

    suspend fun route(from: GeoPlace, to: GeoPlace, mode: TravelMode, language: String = Locale.getDefault().toLanguageTag()): Route? {
        if (!enabled) return null
        val key = String.format(Locale.ROOT, "%.4f_%.4f_%.4f_%.4f_%s_%s", from.latitude, from.longitude, to.latitude, to.longitude, mode.api, language)
        routes.get(key)?.let { return it }
        val url = "$BASE/directions/json?origin=${point(from.latitude, from.longitude)}&destination=${point(to.latitude, to.longitude)}&mode=${mode.api}&language=${encode(language)}&key=${encode(config.apiKey)}"
        val route = fetchJson(url)?.optJSONArray("routes")?.optJSONObject(0) ?: return null
        val leg = route.optJSONArray("legs")?.optJSONObject(0) ?: return null
        val line = route.optJSONObject("overview_polyline")?.optString("points")?.takeIf { it.isNotBlank() } ?: return null
        val found = Route(mode, leg.optJSONObject("distance")?.optInt("value") ?: 0, leg.optJSONObject("duration")?.optInt("value") ?: 0, line)
        routes.put(key, found)
        return found
    }

    suspend fun suggest(query: String, session: PlacesSession, near: GeoPlace?, language: String = Locale.getDefault().toLanguageTag()): List<PlaceSuggestion> {
        if (!enabled || query.isBlank()) return emptyList()
        val bias = near?.let { "&location=${point(it.latitude, it.longitude)}&radius=$SUGGEST_METRES" }.orEmpty()
        val url = "$BASE/place/autocomplete/json?input=${encode(query.trim())}&sessiontoken=${session.token()}$bias&language=${encode(language)}&key=${encode(config.apiKey)}"
        val predictions = fetchJson(url)?.optJSONArray("predictions") ?: return emptyList()
        return (0 until predictions.length()).mapNotNull { index ->
            val prediction = predictions.optJSONObject(index) ?: return@mapNotNull null
            val formatting = prediction.optJSONObject("structured_formatting")
            val title = formatting?.optString("main_text")?.takeIf { it.isNotBlank() } ?: prediction.optString("description")
            PlaceSuggestion(prediction.optString("place_id"), title, formatting?.optString("secondary_text").orEmpty()).takeIf { it.placeId.isNotBlank() }
        }
    }

    suspend fun details(suggestion: PlaceSuggestion, session: PlacesSession, language: String = Locale.getDefault().toLanguageTag()): GeoPlace? {
        if (!enabled) return null
        val url = "$BASE/place/details/json?place_id=${encode(suggestion.placeId)}&fields=$DETAILS_FIELDS&sessiontoken=${session.token()}&language=${encode(language)}&key=${encode(config.apiKey)}"
        session.end()
        val result = fetchJson(url)?.optJSONObject("result") ?: return null
        val types = result.optJSONArray("types")
        return venueOf(result, result.optString("formatted_address"), types?.optString(0).orEmpty())
    }

    private fun venueOf(result: JSONObject, address: String, type: String): GeoPlace? {
        val location = result.optJSONObject("geometry")?.optJSONObject("location") ?: return null
        val id = result.optString("place_id").takeIf { it.isNotBlank() } ?: return null
        val name = result.optString("name").takeIf { it.isNotBlank() } ?: return null
        return GeoPlace(location.optDouble("lat"), location.optDouble("lng"), name, address.ifBlank { null }, venue = Venue(Venue.GOOGLE, id, type))
    }

    private suspend fun fetchJson(url: String): JSONObject? {
        val bytes = fetch(url) ?: return null
        val json = runCatching { JSONObject(String(bytes)) }.getOrNull() ?: return null
        return json.takeIf { it.optString("status") == "OK" }
    }

    private suspend fun fetch(url: String): ByteArray? = try {
        http.get(url)
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        null
    }

    private fun keep(cache: FileCache, name: String, bytes: ByteArray) {
        val file = cache.file(name)
        val part = cache.file("$name.${System.nanoTime()}${FileCache.PART}")
        part.writeBytes(bytes)
        if (!part.renameTo(file)) part.delete()
        cache.trim(keep = file)
    }

    private fun point(latitude: Double, longitude: Double) = String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude)

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val BASE = "https://maps.googleapis.com/maps/api"
        const val MAX_PICTURES = 48L * 1024 * 1024
        const val MAX_ADDRESSES = 1L * 1024 * 1024
        const val ADDRESSES_IN_MEMORY = 200
        const val ROUTES_IN_MEMORY = 50
        const val NEARBY_METRES = 300
        const val SUGGEST_METRES = 50_000
        const val DETAILS_FIELDS = "place_id,name,formatted_address,geometry/location,types"

        val AREAS = setOf("political", "locality", "sublocality", "neighborhood", "route", "country", "postal_code")
    }
}
