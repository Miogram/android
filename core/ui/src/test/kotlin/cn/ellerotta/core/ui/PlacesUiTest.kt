package cn.ellerotta.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.LiveInfo
import cn.ellerotta.core.model.LivePeriods
import cn.ellerotta.core.model.LiveSharer
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.ProximityRadii
import cn.ellerotta.core.model.Route
import cn.ellerotta.core.model.StaticMap
import cn.ellerotta.core.model.GeoMap
import cn.ellerotta.core.model.GeoMaps
import cn.ellerotta.core.model.TravelMode
import cn.ellerotta.core.ui.components.chat.BubblePosition
import cn.ellerotta.core.ui.components.chat.LiveBar
import cn.ellerotta.core.ui.components.chat.MessageBubble
import cn.ellerotta.core.ui.components.location.LocationViewActions
import cn.ellerotta.core.ui.components.location.ProximityContent
import cn.ellerotta.core.ui.components.location.LocationViewScreen
import cn.ellerotta.core.ui.components.location.LocationViewState
import cn.ellerotta.core.ui.components.maps.GeoMapImages
import cn.ellerotta.core.ui.components.maps.LocalGeoMapImages
import cn.ellerotta.core.ui.components.maps.LocalMapImages
import cn.ellerotta.core.ui.components.maps.MapImages
import cn.ellerotta.core.ui.theme.MioTheme
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rGB-w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlacesUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val asked = CopyOnWriteArrayList<StaticMap>()
    private val maps = MapImages { map -> asked += map; null }
    private val geoAsked = CopyOnWriteArrayList<GeoMap>()
    private val geoMaps = GeoMapImages { map -> geoAsked += map; null }
    private val now = (System.currentTimeMillis() / 1000).toInt()

    private fun live(key: String, name: String, lat: Double, lon: Double, updated: Int = now - 120) =
        GeoPlace(lat, lon, live = LiveInfo(period = LivePeriods.FOREVER, heading = 30, updated = updated, senderKey = key, senderName = name), accessHash = 7)

    private fun bubble(message: MessageItem, withMaps: Boolean) = compose.setContent {
        MioTheme(useDynamicColor = false, darkTheme = true) {
            CompositionLocalProvider(LocalMapImages provides maps, LocalGeoMapImages provides geoMaps.takeIf { withMaps }) {
                MessageBubble(message, BubblePosition(first = true, last = true), emptyMap(), emptyMap(), onDownload = {}, onOpenPlace = { events += "open ${message.id}" })
            }
        }
    }

    @Test
    fun aPlaceInABubbleShowsTelegramsMapAndOpensTheScreen() {
        bubble(MessageItem(4, "", out = false, date = now, place = GeoPlace(52.52, 13.405, "Alexanderplatz", "10178 Berlin", accessHash = 77, accuracy = 20)), withMaps = true)
        compose.onNodeWithTag("placeMap_4", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Alexanderplatz").assertExists()
        compose.waitUntil(5_000) { geoAsked.isNotEmpty() }
        val map = geoAsked.last()
        assertEquals(GeoMap(52.52, 13.405, 77, 264, 158, GeoMaps.VENUE_ZOOM, 2, accuracy = 20), map)
        compose.onNodeWithTag("place_4").performClick()
        assertEquals(listOf("open 4"), events)
        assertTrue(asked.isEmpty(), "no google map for a bubble")
    }

    @Test
    fun aLiveLocationSaysWhenItLastMoved() {
        bubble(MessageItem(5, "", out = false, date = now, place = live("u2", "Alice", 1.0, 2.0)), withMaps = true)
        compose.onNodeWithText("updated 2 min ago").assertExists()
        compose.waitUntil(5_000) { geoAsked.isNotEmpty() }
        assertEquals(GeoMaps.LIVE_ZOOM, geoAsked.last().zoom)
        assertEquals(7L, geoAsked.last().accessHash)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun aPointBeingSentHasItsMapWithoutAPictureYet() {
        bubble(MessageItem(7, "", out = true, date = now, place = GeoPlace(52.52, 13.405)), withMaps = true)
        compose.onNodeWithTag("placeMap_7", useUnmergedTree = true).assertExists()
        compose.waitForIdle()
        assertTrue(geoAsked.isEmpty())
        assertTrue(asked.isEmpty())
    }

    @Test
    fun withoutMapsAPlaceIsTheCardItWas() {
        bubble(MessageItem(6, "", out = false, date = now, place = GeoPlace(52.52, 13.405, accessHash = 1)), withMaps = false)
        compose.onNodeWithTag("placeMap_6", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("52.52000, 13.40500").assertExists()
        compose.onNodeWithTag("place_6").performClick()
        assertEquals(listOf("open 6"), events)
    }

    @Test
    fun theLiveBarSaysWhoSharesAndStopsTheOwn() {
        val own = LiveSharer(1, self = true, live("u1", "Me", 1.0, 2.0))
        val alice = LiveSharer(2, self = false, live("u2", "Alice", 1.0, 2.0))
        val bob = LiveSharer(3, self = false, live("u3", "Bob", 1.0, 2.0))
        var sharers by mutableStateOf(listOf(own, alice))
        compose.setContent {
            MioTheme(useDynamicColor = false) { LiveBar(sharers, onOpen = { events += "open" }, onStop = { events += "stop" }) }
        }
        compose.onNodeWithTag("liveBarText", useUnmergedTree = true).assertTextEquals("You and Alice are sharing")
        compose.onNodeWithTag("liveBarStop").performClick()
        compose.onNodeWithTag("liveBar").performClick()
        assertEquals(listOf("stop", "open"), events)
        sharers = listOf(alice, bob)
        compose.onNodeWithTag("liveBarText", useUnmergedTree = true).assertTextEquals("Alice and 1 other are sharing")
        compose.onNodeWithTag("liveBarStop").assertDoesNotExist()
    }

    @Test
    fun theLocationScreenShowsThePlaceItsDistanceAndTheWayThere() {
        val place = GeoPlace(52.52, 13.405, "Alexanderplatz", "10178 Berlin")
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                LocationViewScreen(
                    LocationViewState(place, place.title, place.address, me = GeoPlace(52.53, 13.405)),
                    emptyMap(),
                    LocationViewActions(onDirections = { events += "directions" }, onShare = { events += "share" }, onOpenInMaps = { events += "maps" }, onBack = { events += "back" }),
                )
            }
        }
        compose.onNodeWithTag("locationTitle").assertTextEquals("Alexanderplatz")
        compose.onNodeWithTag("locationAddress").assertTextEquals("10178 Berlin · 1.1 km away")
        compose.onNodeWithTag("locationDirections").performClick()
        compose.onNodeWithTag("locationShare").performClick()
        compose.onNodeWithTag("locationOpen").performClick()
        compose.onNodeWithTag("back").performClick()
        assertEquals(listOf("directions", "share", "maps", "back"), events)
    }

    @Test
    fun theLocationScreenDrawsTheWayThereWithItsLengthTimeAndModes() {
        val place = GeoPlace(52.53, 13.42, "TV Tower")
        var state by mutableStateOf(LocationViewState(place, place.title, me = GeoPlace(52.52, 13.405), travel = TravelMode.Walking, routing = true))
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                LocationViewScreen(state, emptyMap(), LocationViewActions({}, {}, {}, {}, onTravel = { events += "travel ${it.api}" }))
            }
        }
        compose.onNodeWithTag("locationRoute").assertTextEquals("Finding the way…")
        compose.onNodeWithTag("mapRoute").assertDoesNotExist()
        state = state.copy(route = Route(TravelMode.Walking, 1500, 1140, "_yp_IgdypAsS{^{T_b@_S{Y"), routing = false)
        compose.onNodeWithTag("locationRoute").assertTextEquals("1.5 km · 19 min")
        compose.onNodeWithTag("mapRoute").assertExists()
        compose.onNodeWithTag("travel_walking").assertIsSelected()
        compose.onNodeWithTag("travel_driving").performClick()
        compose.onNodeWithTag("travel_transit").performClick()
        assertEquals(listOf("travel driving", "travel transit"), events)
        state = state.copy(travel = TravelMode.Transit, route = null)
        compose.onNodeWithTag("locationRoute").assertTextEquals("No route found")
        compose.onNodeWithTag("mapRoute").assertDoesNotExist()
        state = state.copy(travel = null)
        compose.onNodeWithTag("travel_walking").assertDoesNotExist()
        state = state.copy(travel = TravelMode.Walking, me = GeoPlace(52.5301, 13.42))
        compose.onNodeWithTag("locationRoute").assertDoesNotExist()
    }

    @Test
    fun aLiveLocationListsEveryoneSharingAndFocusesATappedOne() {
        val own = LiveSharer(1, self = true, live("u1", "Me", 52.52, 13.405, updated = now))
        val alice = LiveSharer(2, self = false, live("u2", "Alice", 52.53, 13.405))
        val focused = mutableListOf<String>()
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                LocationViewScreen(
                    LocationViewState(alice.place, "Alice", me = GeoPlace(52.52, 13.405), sharers = listOf(own, alice), focus = "u2"),
                    emptyMap(),
                    LocationViewActions({}, {}, {}, {}, onFocus = { focused += it.key }),
                )
            }
        }
        compose.onNodeWithTag("sharer_u1").assertTextContains("You", substring = true)
        compose.onNodeWithTag("sharer_u1").assertTextContains("updated just now", substring = true)
        compose.onNodeWithTag("sharer_u2").assertTextContains("1.1 km away · updated 2 min ago", substring = true)
        compose.onNodeWithTag("sharer_u1").performClick()
        assertEquals(listOf("u1"), focused)
    }

    @Test
    fun theProximitySheetSaysTheSentenceAndSetsOrRemovesTheAlert() {
        var current by mutableStateOf<Int?>(null)
        var distance by mutableStateOf<Double?>(1240.0)
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ProximityContent("Alice", distance, current, onSet = { events += "set $it" }, onRemove = { events += "remove" })
            }
        }
        compose.onNodeWithTag("proximityDistance").assertTextEquals("Alice is 1.2 km away")
        compose.onNodeWithTag("proximitySentence").assertTextEquals("Notify when Alice is within 500 m")
        compose.onNodeWithTag("proximityRemove").assertDoesNotExist()
        compose.onNodeWithTag("proximityAlready").assertDoesNotExist()
        compose.onNodeWithTag("proximitySlider").performSemanticsAction(SemanticsActions.SetProgress) { it(ProximityRadii.metric.indexOf(700).toFloat()) }
        compose.onNodeWithTag("proximitySentence").assertTextEquals("Notify when Alice is within 700 m")
        compose.onNodeWithTag("proximitySet").assertTextEquals("Set alert").performClick()
        current = 2000
        distance = 300.0
        compose.onNodeWithTag("proximitySentence").assertTextEquals("Notify when Alice is within 2 km")
        compose.onNodeWithTag("proximityAlready").assertExists()
        compose.onNodeWithTag("proximitySet").assertTextEquals("Update alert")
        compose.onNodeWithTag("proximityRemove").performClick()
        assertEquals(listOf("set 700", "remove"), events)
    }

    @Test
    fun theLiveBarOffersTheAlertWhileTheAccountSharesAndInPrivateChats() {
        val own = LiveSharer(1, self = true, live("u1", "Me", 1.0, 2.0))
        val alice = LiveSharer(2, self = false, live("u2", "Alice", 1.0, 2.0))
        var sharers by mutableStateOf(listOf(own, alice))
        var privateChat by mutableStateOf(false)
        compose.setContent {
            MioTheme(useDynamicColor = false) { LiveBar(sharers, onOpen = {}, onStop = {}, onAlert = { events += "alert" }, private = privateChat) }
        }
        compose.onNodeWithTag("liveBarAlert").assertContentDescriptionEquals("Notify when nearby").performClick()
        assertEquals(listOf("alert"), events)
        compose.onNodeWithTag("liveBarTitle", useUnmergedTree = true).assertTextEquals("Live location")
        sharers = listOf(own)
        compose.onNodeWithTag("liveBarAlert").assertExists()
        sharers = listOf(alice)
        compose.onNodeWithTag("liveBarAlert").assertDoesNotExist()
        privateChat = true
        compose.onNodeWithTag("liveBarAlert").assertExists()
        sharers = listOf(own.copy(place = own.place.copy(live = own.live.copy(radius = 500))), alice)
        compose.onNodeWithTag("liveBarTitle", useUnmergedTree = true).assertTextEquals("Live location · Alert within 500 m")
        compose.onNodeWithTag("liveBarAlert").assertContentDescriptionEquals("Alert within 500 m")
    }

    @Test
    fun theLocationScreenOffersTheAlertOnceTheSharersAreKnownAndStartsSharingWithIt() {
        val alice = LiveSharer(2, self = false, live("u2", "Alice", 52.53, 13.405))
        var sharers by mutableStateOf(emptyList<LiveSharer>())
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                LocationViewScreen(
                    LocationViewState(alice.place, "Alice", me = GeoPlace(52.52, 13.405), sharers = sharers, focus = "u2"),
                    emptyMap(),
                    LocationViewActions({}, {}, {}, {}, onAlert = { events += "alert $it" }, onShareAlert = { seconds, radius -> events += "share $seconds $radius" }),
                )
            }
        }
        compose.onNodeWithTag("locationAlert").assertDoesNotExist()
        sharers = listOf(alice)
        compose.onNodeWithTag("locationAlert").assertContentDescriptionEquals("Notify when nearby").performClick()
        compose.onNodeWithTag("proximityDistance").assertTextEquals("Alice is 1.1 km away")
        compose.onNodeWithTag("proximityShareHint").assertExists()
        compose.onNodeWithTag("proximitySet").assertTextEquals("Share my location and notify").performClick()
        compose.onNodeWithTag("locationLiveAlertValue", useUnmergedTree = true).assertTextEquals("Alert within 500 m")
        compose.onNodeWithTag("locationPeriod_900").performClick()
        compose.onNodeWithTag("locationLiveShare").performClick()
        assertEquals(listOf("share 900 500"), events)

        val own = LiveSharer(1, self = true, live("u1", "Me", 52.52, 13.405, updated = now).let { it.copy(live = it.live!!.copy(radius = 500)) })
        sharers = listOf(own, alice)
        compose.onNodeWithTag("ownAlert", useUnmergedTree = true).assertTextEquals("Alert within 500 m")
        compose.onNodeWithTag("locationAlert").assertContentDescriptionEquals("Alert within 500 m")
        compose.onNodeWithTag("alert_u1").performClick()
        compose.onNodeWithTag("proximitySet").assertTextEquals("Update alert")
        compose.onNodeWithTag("proximityRemove").performClick()
        assertEquals(listOf("share 900 500", "alert 0"), events)
    }

    @Test
    fun withoutAPersonTheAlertIsAboutAnyone() {
        compose.setContent {
            MioTheme(useDynamicColor = false) { ProximityContent(null, distance = 300.0, current = null, onSet = {}, onRemove = {}) }
        }
        compose.onNodeWithTag("proximitySentence").assertTextEquals("Notify when someone is within 500 m")
        compose.onNodeWithTag("proximityDistance").assertDoesNotExist()
        compose.onNodeWithTag("proximityAlready").assertDoesNotExist()
        compose.onNodeWithTag("proximityShareHint").assertDoesNotExist()
    }
}
