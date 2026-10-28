package app.miogram

import android.app.Application
import android.app.Notification
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.maps.MapsConfig
import app.miogram.data.maps.MapsRepository
import app.miogram.data.maps.UrlMapsHttp
import app.miogram.location.LiveFrame
import app.miogram.location.LiveLocationService
import app.miogram.location.LiveMapPicture
import app.miogram.location.LiveRoutes
import app.miogram.location.LiveShare
import app.miogram.location.MapPerson
import app.miogram.location.Nearby
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.Route
import cn.ellerotta.core.model.TravelMode
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveMapTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val me = GeoPlace(52.52, 13.405)
    private val bob = MapPerson("u502", "Bob", GeoPlace(52.53, 13.42))
    private val accent = 0xFF3D5AFE.toInt()

    private fun withPicture(key: String = FakeMapsHttp.KEY, block: suspend (LiveMapPicture, FakeMapsHttp) -> Unit): Unit = runBlocking {
        val dir: File = Files.createTempDirectory("maps").toFile()
        val http = FakeMapsHttp()
        block(LiveMapPicture(MapsRepository(http, MapsConfig(key), dir)) { null }, http)
        dir.deleteRecursively()
    }

    @Test
    fun theMapIsAskedAgainOnlyAfterAMinuteOrAHundredMetres(): Unit = withPicture { picture, http ->
        val first = assertNotNull(picture.draw(me, listOf(bob), target = "u502", radius = 500, dark = false, accent = accent, now = 1_000))
        assertEquals(320, first.width)
        assertEquals(320, first.height)
        val asked = http.asked("staticmap").single()
        assertTrue("size=160x160" in asked && "scale=2" in asked, asked)
        val moved = bob.copy(place = GeoPlace(52.53045, 13.42))
        assertNotNull(picture.draw(me, listOf(moved), "u502", 500, dark = false, accent = accent, now = 1_030))
        assertEquals(1, http.asked("staticmap").size)
        picture.draw(me, listOf(moved), "u502", 500, dark = false, accent = accent, now = 1_061)
        assertEquals(2, http.asked("staticmap").size)
        picture.draw(me, listOf(bob.copy(place = GeoPlace(52.5318, 13.42))), "u502", 500, dark = false, accent = accent, now = 1_065)
        assertEquals(3, http.asked("staticmap").size)
        picture.draw(me, listOf(bob.copy(place = GeoPlace(52.5318, 13.42))), "u502", 500, dark = false, accent = accent, now = 5_000)
        assertEquals(3, http.asked("staticmap").size)
        picture.draw(me, listOf(bob.copy(place = GeoPlace(52.5318, 13.42))), "u502", 500, dark = true, accent = accent, now = 5_001)
        assertTrue("&style=" in http.asked("staticmap").last())
    }

    @Test
    fun theDeviceIsTheAccentDotInTheMiddleWhenAlone(): Unit = withPicture { picture, _ ->
        val drawn = assertNotNull(picture.draw(me, emptyList(), target = null, radius = null, dark = false, accent = accent, now = 0))
        assertEquals(accent, drawn.getPixel(160, 160))
    }

    @Test
    fun withoutAKeyOrTheNetworkThereIsNoPicture() {
        withPicture(key = "") { picture, http ->
            assertNull(picture.draw(me, listOf(bob), null, null, dark = false, accent = accent, now = 0))
            assertTrue(http.urls.isEmpty())
        }
        withPicture { picture, http ->
            http.failing = true
            assertNull(picture.draw(me, listOf(bob), null, null, dark = false, accent = accent, now = 0))
            assertNull(picture.draw(me, listOf(bob), null, null, dark = false, accent = accent, now = 10))
            assertEquals(1, http.urls.size)
        }
    }

    @Test
    fun theMapIsTheLargeIconOfAPromotableLiveUpdate() {
        val share = LiveShare("u501", "Alice", 7, null, 500, me)
        val map = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val photo = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val notification = LiveLocationService.liveNotification(context, listOf(share), Nearby("Bob", 1500.0, 2000.0, "u502"), frame = LiveFrame(map, photo))
        assertNotNull(notification.getLargeIcon())
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Sharing live location", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(!notification.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertTrue(notification.extras.getBoolean(EXTRA_REQUEST_PROMOTED_ONGOING))
        assertNull(notification.extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java))
        assertTrue(notification.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().let { it.isEmpty() || it.endsWith("ProgressStyle") }, notification.extras.getString(Notification.EXTRA_TEMPLATE))
        assertNull(notification.contentView)
        assertNull(notification.bigContentView)
        assertNull(LiveLocationService.liveNotification(context, listOf(share)).getLargeIcon())
    }

    @Test
    fun theRouteIsAskedOnlyByTheThrottleAndDrawnByGoogle(): Unit = runBlocking {
        val dir: File = Files.createTempDirectory("maps").toFile()
        val http = FakeMapsHttp()
        val maps = MapsRepository(http, MapsConfig(FakeMapsHttp.KEY), dir)
        val routes = LiveRoutes(maps)
        val walk = assertNotNull(routes.route(me, "u502", bob.place, TravelMode.Walking, now = 1_000))
        assertEquals(FakeMapsHttp.LINE, walk.polyline)
        val moved = GeoPlace(52.53045, 13.42)
        assertEquals(walk, routes.route(me, "u502", moved, TravelMode.Walking, now = 1_030))
        assertEquals(1, http.asked("directions/json").size)
        routes.route(me, "u502", moved, TravelMode.Walking, now = 1_061)
        assertEquals(2, http.asked("directions/json").size)
        assertEquals(TravelMode.Driving, routes.route(me, "u502", moved, TravelMode.Driving, now = 1_062)?.mode)
        routes.route(me, "u503", GeoPlace(52.54, 13.43), TravelMode.Driving, now = 1_063)
        assertEquals(4, http.asked("directions/json").size)
        routes.route(me, "u504", GeoPlace(52.54, 13.43), TravelMode.Driving, now = 1_064)
        assertEquals(4, http.asked("directions/json").size)
        assertNull(routes.route(me, null, null, TravelMode.Walking, now = 1_065))
        assertNull(routes.route(me, "u502", GeoPlace(52.5201, 13.405), TravelMode.Walking, now = 1_066))
        assertEquals(4, http.asked("directions/json").size)

        val picture = LiveMapPicture(maps) { null }
        picture.draw(me, listOf(bob), "u502", null, dark = false, accent = accent, now = 1_000, route = walk)
        assertTrue("&path=" in http.asked("staticmap").last())
        picture.draw(me, listOf(bob), "u502", null, dark = false, accent = accent, now = 1_001)
        assertTrue("&path=" !in http.asked("staticmap").last())
        assertEquals(2, http.asked("staticmap").size)
        dir.deleteRecursively()
    }

    @Test
    fun theRoutesLengthAndTimeFollowTheTextAndTheChipShowsTheTime() {
        val share = LiveShare("u501", "Alice", 7, null, null, me)
        val route = Route(TravelMode.Walking, 1500, 1140, FakeMapsHttp.LINE)
        val notification = LiveLocationService.liveNotification(context, listOf(share), frame = LiveFrame(route = route))
        assertEquals("With Alice · 0.93 mi · 19 min walk", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(notification.extras.keySet().any { notification.extras.getCharSequence(it)?.toString() == "19 min" })
        val drive = LiveLocationService.liveNotification(context, listOf(share), frame = LiveFrame(route = route.copy(mode = TravelMode.Driving, seconds = 3_900)))
        assertEquals("With Alice · 0.93 mi · 1 h 5 min drive", drive.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        val plain = LiveLocationService.liveNotification(context, listOf(share))
        assertTrue(plain.extras.keySet().any { plain.extras.getCharSequence(it)?.toString() == "Live" })
    }

    @Test
    fun picturesFromGoogle(): Unit = runBlocking {
        val dir = System.getenv("MIOGRAM_SCREENSHOTS")
        assumeNotNull(dir)
        assumeTrue(BuildConfig.MAPS_API_KEY.isNotBlank())
        val cache = Files.createTempDirectory("maps").toFile()
        val maps = MapsRepository(UrlMapsHttp(), MapsConfig(BuildConfig.MAPS_API_KEY), cache)
        val alice = MapPerson("u501", "Alice", GeoPlace(52.5162, 13.3777))
        val walk = assertNotNull(maps.route(me, bob.place, TravelMode.Walking))
        listOf(false, true).forEach { dark ->
            val accent = if (dark) 0xFFBAC3FF.toInt() else 0xFF4355B9.toInt()
            val drawn = assertNotNull(LiveMapPicture(maps) { null }.draw(me, listOf(bob, alice), target = "u502", radius = 500, dark = dark, accent = accent, now = 0))
            File(dir, "live-map-${if (dark) "dark" else "light"}.png").outputStream().use { drawn.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val routed = assertNotNull(LiveMapPicture(maps) { null }.draw(me, listOf(bob), target = "u502", radius = null, dark = dark, accent = accent, now = 0, route = walk))
            File(dir, "live-map-route-${if (dark) "dark" else "light"}.png").outputStream().use { routed.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        cache.deleteRecursively()
    }

    private companion object {
        const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    }
}
