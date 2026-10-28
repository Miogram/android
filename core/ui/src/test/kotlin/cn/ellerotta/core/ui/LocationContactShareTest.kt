package cn.ellerotta.core.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.PlaceSuggestion
import cn.ellerotta.core.model.Venue
import cn.ellerotta.core.ui.components.attach.ContactShareScreen
import cn.ellerotta.core.ui.components.attach.LiveActions
import cn.ellerotta.core.ui.components.attach.LocationPickActions
import cn.ellerotta.core.ui.components.attach.LocationShareScreen
import cn.ellerotta.core.ui.components.attach.LocationShareState
import cn.ellerotta.core.ui.components.attach.LocationStatus
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocationContactShareTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun location(state: LocationShareState, live: LiveActions? = null, pick: LocationPickActions? = null) = compose.setContent {
        MioTheme(useDynamicColor = false) {
            LocationShareScreen(state, busy = false, onAllow = { events += "allow" }, onRetry = { events += "retry" }, onSendCurrent = { events += "send" }, live = live, onBack = {}, pick = pick)
        }
    }

    private val pick = LocationPickActions(
        onPick = { _, _ -> },
        onQuery = { events += "query $it" },
        onSuggestion = { events += "suggestion ${it.placeId}" },
        onSendPlace = { events += "place ${it.venue?.id ?: it.coordinates}" },
    )

    @Test
    fun withoutThePermissionItAsksAndSendsNothing() {
        location(LocationShareState(LocationStatus.NoPermission))
        compose.onNodeWithTag("locationSend").assertIsNotEnabled()
        compose.onNodeWithTag("locationAllow").performClick()
        assertEquals(listOf("allow"), events)
    }

    @Test
    fun aFoundPlaceSendsAndWithoutLiveActionsNoLiveSectionShows() {
        location(LocationShareState(LocationStatus.Found, GeoPlace(48.8566, 2.3522), accuracy = 12))
        compose.onNodeWithTag("locationSend").assertIsEnabled().performClick()
        assertEquals(listOf("send"), events)
        assertTrue(compose.onAllNodes(hasTestTag("locationLive")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aLiveLocationGoesForThePeriodChosenInTheSheet() {
        var started: Pair<Int, Int?>? = null
        location(LocationShareState(LocationStatus.Found, GeoPlace(1.0, 2.0)), LiveActions(onShare = { s, r -> started = s to r }, onStop = {}))
        compose.onNodeWithTag("locationLive").performClick()
        compose.onNodeWithTag("locationPeriod_900").performClick()
        compose.onNodeWithTag("locationLiveShare").performClick()
        assertEquals(900 to null, started)
        compose.onNodeWithTag("locationLive").performClick()
        assertTrue(compose.onAllNodes(hasTestTag("locationLiveAlert")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aPrivateChatsLiveLocationMayAskForAProximityAlertFirst() {
        var started: Pair<Int, Int?>? = null
        location(LocationShareState(LocationStatus.Found, GeoPlace(1.0, 2.0), alertName = "Alice"), LiveActions(onShare = { s, r -> started = s to r }, onStop = {}))
        compose.onNodeWithTag("locationLive").performClick()
        compose.onNodeWithTag("locationLiveAlert").assertTextContains("Notify me when Alice is nearby", substring = true)
        compose.onNodeWithTag("locationLiveAlertValue", useUnmergedTree = true).assertTextEquals("Off")
        compose.onNodeWithTag("locationLiveAlert").performClick()
        compose.onNodeWithTag("proximitySentence").assertTextEquals("Notify when Alice is within 0.25 mi")
        compose.onNodeWithTag("proximitySet").assertTextEquals("Set alert").performClick()
        compose.onNodeWithTag("locationLiveAlertValue", useUnmergedTree = true).assertTextEquals("Alert within 0.25 mi")
        compose.onNodeWithTag("locationPeriod_3600").performClick()
        compose.onNodeWithTag("locationLiveShare").performClick()
        assertEquals(3600 to 402, started)
    }

    @Test
    fun aProximityAlertPickedForTheLiveLocationCanBeRemovedAgain() {
        var started: Pair<Int, Int?>? = null
        location(LocationShareState(LocationStatus.Found, GeoPlace(1.0, 2.0), alertName = "Alice"), LiveActions(onShare = { s, r -> started = s to r }, onStop = {}))
        compose.onNodeWithTag("locationLive").performClick()
        compose.onNodeWithTag("locationLiveAlert").performClick()
        compose.onNodeWithTag("proximitySet").performClick()
        compose.onNodeWithTag("locationLiveAlert").performClick()
        compose.onNodeWithTag("proximityRemove").performClick()
        compose.onNodeWithTag("locationLiveAlertValue", useUnmergedTree = true).assertTextEquals("Off")
        compose.onNodeWithTag("locationLiveShare").performClick()
        assertEquals(3600 to null, started)
    }

    @Test
    fun withMapsThePickedPointAndPlacesNearbyAreSent() {
        val here = GeoPlace(52.52, 13.405)
        val alexa = GeoPlace(52.5196, 13.4155, "ALEXA", "Grunerstraße 20", venue = Venue(Venue.GOOGLE, "p1", "shopping_mall"))
        location(LocationShareState(LocationStatus.Found, here, accuracy = 12, maps = true, picked = GeoPlace(52.53, 13.405, "Torstraße 1", "Berlin"), nearby = listOf(alexa)), pick = pick)
        compose.onNodeWithTag("locationSend").assertTextContains("Accurate to", substring = true).performClick()
        compose.onNodeWithTag("locationPicked").assertTextContains("Torstraße 1, Berlin", substring = true).performClick()
        compose.onNodeWithTag("nearby_p1").performScrollTo().performClick()
        assertEquals(listOf("send", "place 52.53000, 13.40500", "place p1"), events)
    }

    @Test
    fun aSearchShowsSuggestionsInPlaceOfTheRows() {
        location(
            LocationShareState(LocationStatus.Found, GeoPlace(52.52, 13.405), maps = true, query = "alexa", suggestions = listOf(PlaceSuggestion("p1", "ALEXA", "Berlin"))),
            pick = pick,
        )
        compose.onNodeWithTag("locationSend").assertDoesNotExist()
        compose.onNodeWithTag("suggestion_p1").performClick()
        compose.onNodeWithTag("locationSearchClear").performClick()
        assertEquals(listOf("suggestion p1", "query "), events)
    }

    @Test
    fun aContactIsSentAfterAsking() {
        val ada = ContactItem(key = "u7", userId = 7, name = "Ada Lovelace", phone = "4479460000")
        var sent: ContactItem? = null
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ContactShareScreen(listOf(ada), query = "", loaded = true, avatars = emptyMap(), busy = false, onQuery = {}, onSend = { sent = it }, onBack = {})
            }
        }
        compose.onNodeWithTag("contactShare_u7").performClick()
        assertEquals(null, sent)
        compose.onNodeWithTag("contactShareSend").performClick()
        assertEquals(ada, sent)
    }
}
