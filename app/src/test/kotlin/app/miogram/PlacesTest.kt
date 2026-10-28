package app.miogram

import androidx.test.core.app.ApplicationProvider
import app.miogram.data.maps.MapsConfig
import app.miogram.data.maps.MapsRepository
import app.miogram.data.maps.PlacesSession
import app.miogram.data.telegram.GeoMapsRepository
import app.miogram.data.telegram.SignInResult
import app.miogram.location.ChatLiveLocations
import cn.ellerotta.core.model.GeoMap
import cn.ellerotta.core.model.GeoMaps
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.LivePeriods
import cn.ellerotta.core.model.StaticMap
import cn.ellerotta.core.model.TravelMode
import cn.ellerotta.core.model.Venue
import cn.ellerotta.ktproto.api.types.GeoPoint
import cn.ellerotta.ktproto.api.types.InputGeoPoint
import cn.ellerotta.ktproto.api.types.InputMediaVenue
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageMediaVenue
import cn.ellerotta.ktproto.api.types.PeerUser
import java.io.File
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlacesTest {
    private fun withMaps(key: String = FakeMapsHttp.KEY, block: suspend (MapsRepository, FakeMapsHttp, File) -> Unit): Unit = runBlocking {
        val dir = Files.createTempDirectory("maps").toFile()
        val http = FakeMapsHttp()
        block(MapsRepository(http, MapsConfig(key), dir), http, dir)
        dir.deleteRecursively()
    }

    private fun withChat(block: suspend (repos: TestRepositories, fake: FakeTelegram, saved: String) -> Unit): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                fake.addSavedMessages(1)
                block(repos, fake, repos.dialogs.dialogs().dialogs.first { it.isSaved }.key)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun aStaticMapIsFetchedOnceAndThenReadFromTheDisk(): Unit = withMaps { maps, http, dir ->
        val map = StaticMap(52.52, 13.405, 15, 264, 158, dark = false)
        assertContentEquals(FakeMapsHttp.PNG, maps.load(map))
        assertContentEquals(FakeMapsHttp.PNG, maps.load(map))
        assertEquals(1, http.asked("staticmap").size)
        assertTrue("key=${FakeMapsHttp.KEY}" in http.urls.single())
        val again = FakeMapsHttp()
        assertContentEquals(FakeMapsHttp.PNG, MapsRepository(again, MapsConfig(FakeMapsHttp.KEY), dir).load(map))
        assertTrue(again.urls.isEmpty())
        maps.load(map.copy(dark = true))
        assertEquals(2, http.asked("staticmap").size)
        assertTrue("&style=" in http.asked("staticmap").last())
    }

    @Test
    fun aFailedMapIsNotKeptAndNoKeyAsksNothing(): Unit {
        withMaps { maps, http, _ ->
            val map = StaticMap(1.0, 2.0, 14, 100, 60, dark = false)
            http.failing = true
            assertNull(maps.load(map))
            http.failing = false
            assertNotNull(maps.load(map))
            assertEquals(2, http.urls.size)
        }
        withMaps(key = "") { maps, http, _ ->
            assertNull(maps.load(StaticMap(1.0, 2.0, 14, 100, 60, dark = false)))
            assertNull(maps.address(1.0, 2.0))
            assertTrue(http.urls.isEmpty())
            assertNull(maps.route(GeoPlace(1.0, 2.0), GeoPlace(1.1, 2.1), TravelMode.Walking))
            assertTrue(http.urls.isEmpty())
        }
    }

    @Test
    fun aRouteIsParsedAskedOnceForPointsCloseTogetherAndNoneWhereNothingGoes(): Unit = withMaps { maps, http, _ ->
        val me = GeoPlace(52.52, 13.405)
        val bob = GeoPlace(52.53, 13.42)
        val walk = assertNotNull(maps.route(me, bob, TravelMode.Walking, "en"))
        assertEquals(TravelMode.Walking, walk.mode)
        assertEquals(1500, walk.metres)
        assertEquals(19, walk.minutes)
        assertEquals(52.52 to 13.405, walk.points.first().let { it.latitude to it.longitude })
        assertEquals(52.53 to 13.42, walk.points.last().let { it.latitude to it.longitude })
        val asked = http.asked("directions/json").single()
        assertTrue("origin=52.520000%2C13.405000" in asked || "origin=52.520000,13.405000" in asked, asked)
        assertTrue("mode=walking" in asked && "key=${FakeMapsHttp.KEY}" in asked, asked)
        assertEquals(walk, maps.route(GeoPlace(52.52001, 13.40501), bob, TravelMode.Walking, "en"))
        assertEquals(1, http.asked("directions/json").size)
        assertEquals(TravelMode.Driving, maps.route(me, bob, TravelMode.Driving, "en")?.mode)
        assertNull(maps.route(me, bob, TravelMode.Transit, "en"))
        assertEquals(3, http.asked("directions/json").size)
    }

    @Test
    fun anAddressIsReverseGeocodedOnceForPointsCloseTogether(): Unit = withMaps { maps, http, _ ->
        val place = assertNotNull(maps.address(52.52, 13.405, "en"))
        assertEquals("Alexanderplatz 1", place.title)
        assertEquals("10178 Berlin, Germany", place.address)
        assertTrue("latlng=52.520000,13.405000" in http.urls.single())
        assertTrue("language=en" in http.urls.single())
        assertEquals("Alexanderplatz 1", maps.address(52.52001, 13.40502, "en")?.title)
        assertEquals(1, http.asked("geocode/json").size)
        maps.address(52.52, 13.405, "ru")
        assertEquals(2, http.asked("geocode/json").size)
    }

    @Test
    fun aVenueKeepsItsProviderAndId(): Unit = withChat { repos, fake, saved ->
        fake.addSaved { id ->
            Message(id = id, fromId = PeerUser(fake.alice.id), peerId = PeerUser(fake.selfId), date = FakeTelegram.NOW, message = "", media = MessageMediaVenue(GeoPoint(13.405, 52.52, 1), "Alexanderplatz", "10178 Berlin", "gplaces", "ChIJ-alex", "transit_station"))
        }
        val place = assertNotNull(repos.messages.history(saved).last().place)
        assertEquals("Alexanderplatz", place.title)
        assertEquals(Venue("gplaces", "ChIJ-alex", "transit_station"), place.venue)
    }

    @Test
    fun everyoneSharingInAChatIsListedAndFollowsTheirMoves(): Unit = withChat { repos, fake, saved ->
        val alice = fake.attach.addPlace(52.52, 13.405, period = LivePeriods.FOREVER, heading = 90)
        fake.attach.addPlace(1.0, 2.0)

        val recent = repos.attach.recentLocations(saved)
        assertEquals(InputPeerSelf, fake.attach.recentCalls.single().peer)
        val sharer = recent.single()
        assertEquals(alice, sharer.messageId)
        assertEquals("u${fake.alice.id}", sharer.key)
        assertEquals("Alice Example", sharer.name)
        assertEquals(90, sharer.live.heading)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val live = ChatLiveLocations(saved, repos.attach, repos.updates, scope)
        withTimeout(10_000) { live.sharers.first { it.isNotEmpty() } }
        delay(300)
        fake.attach.pushMove(alice, 52.53, 13.41)
        val moved = withTimeout(10_000) { live.sharers.first { list -> list.singleOrNull()?.place?.latitude == 52.53 } }.single()
        assertEquals(13.41, moved.place.longitude)
        assertEquals("u${fake.alice.id}", moved.key)
        assertEquals(FakeTelegram.NOW + 60, moved.live.updated)
        scope.cancel()
    }

    @Test
    fun placesNearbyAreVenuesWithoutTheAreasAround(): Unit = withMaps { maps, http, _ ->
        val nearby = maps.nearby(52.52, 13.405, "en")
        assertEquals(listOf("ALEXA", "TV Tower"), nearby.map { it.title })
        val alexa = nearby.first()
        assertEquals(Venue("gplaces", "ChIJ-alexa", "shopping_mall"), alexa.venue)
        assertEquals("Grunerstraße 20, Berlin", alexa.address)
        assertEquals(52.5196, alexa.latitude)
        assertTrue("location=52.520000,13.405000&radius=300" in http.asked("place/nearbysearch/json").single())
    }

    @Test
    fun aSearchIsOneSessionUntilAPlaceIsPicked(): Unit = withMaps { maps, http, _ ->
        var tokens = 0
        val session = PlacesSession { "token${++tokens}" }
        assertTrue(maps.suggest("al", session, near = null).isEmpty())
        val found = maps.suggest("alexa", session, near = GeoPlace(52.52, 13.405))
        assertEquals(listOf("ALEXA"), found.map { it.title })
        assertEquals("Grunerstraße, Berlin, Germany", found.single().subtitle)
        val venue = assertNotNull(maps.details(found.single(), session))
        assertEquals("Grunerstraße 20, 10179 Berlin, Germany", venue.address)
        assertEquals(Venue("gplaces", "ChIJ-alexa", "shopping_mall"), venue.venue)
        maps.suggest("alexa", session, near = null)
        val asked = http.asked("place/autocomplete/json") + http.asked("place/details/json")
        assertTrue(asked.take(2).all { "sessiontoken=token1" in it })
        assertTrue("sessiontoken=token1" in http.asked("place/details/json").single())
        assertTrue("sessiontoken=token2" in http.asked("place/autocomplete/json").last())
        assertTrue("location=52.520000,13.405000" in http.asked("place/autocomplete/json")[1])
        assertTrue(maps.suggest(" ", session, near = null).isEmpty())
        assertEquals(3, http.asked("place/autocomplete/json").size)
    }

    @Test
    fun aVenueGoesOutWithItsProviderIdAndType(): Unit = withChat { repos, fake, saved ->
        val alexa = GeoPlace(52.5196, 13.4155, "ALEXA", "Grunerstraße 20", venue = Venue(Venue.GOOGLE, "ChIJ-alexa", "shopping_mall"))
        repos.attach.sendPlace(saved, alexa)
        val sent = assertIs<InputMediaVenue>(fake.attach.sent.single().media)
        assertEquals("gplaces", sent.provider)
        assertEquals("ChIJ-alexa", sent.venueId)
        assertEquals("shopping_mall", sent.venueType)
        assertEquals("ALEXA", sent.title)
        assertEquals(alexa.copy(accessHash = FakeAttach.GEO_HASH), repos.messages.history(saved).last().place)
    }

    @Test
    fun aBubblesMapIsTelegramsPictureOfTheMessagesPointFetchedOnceFromTheWebFileDc(): Unit = withChat { repos, fake, saved ->
        fake.attach.addPlace(52.52, 13.405)
        val place = assertNotNull(repos.messages.history(saved).last().place)
        assertEquals(FakeAttach.GEO_HASH, place.accessHash)
        val dir = Files.createTempDirectory("geomaps").toFile()
        val maps = GeoMapsRepository(repos.telegram, dir)
        val map = GeoMap(place.latitude, place.longitude, FakeAttach.GEO_HASH, 264, 158, GeoMaps.zoomOf(place), 2)
        assertContentEquals(FakeAttach.MAP_PICTURE, maps.load(map))
        assertContentEquals(FakeAttach.MAP_PICTURE, maps.load(map))
        assertEquals(2, fake.attach.mapCalls.size)
        val (dc, location) = fake.attach.mapCalls.first()
        assertEquals(fake.cluster.fileDcId, dc)
        assertEquals(FakeAttach.GEO_HASH, location.accessHash)
        assertEquals(listOf(264, 158, GeoMaps.PLACE_ZOOM, 2), listOf(location.w, location.h, location.zoom, location.scale))
        val point = assertIs<InputGeoPoint>(location.geoPoint)
        assertEquals(52.52 to 13.405, point.lat to point.long)
        assertContentEquals(FakeAttach.MAP_PICTURE, GeoMapsRepository(repos.telegram, dir).load(map))
        assertEquals(2, fake.attach.mapCalls.size)
        assertContentEquals(FakeAttach.MAP_PICTURE, maps.load(map.copy(width = 200)))
        assertEquals(4, fake.attach.mapCalls.size)
        assertNull(maps.load(map.copy(accessHash = 5, zoom = 16)))
        assertNull(maps.load(map.copy(accessHash = 5, zoom = 16)))
        assertEquals(6, fake.attach.mapCalls.size)
        dir.deleteRecursively()
    }
}
