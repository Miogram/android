package app.miogram

import android.Manifest
import android.app.Application
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.data.maps.MapsHttp
import app.miogram.di.TelegramModule
import app.miogram.location.LiveLocationService
import app.miogram.location.LiveShare
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.LivePeriods
import cn.ellerotta.ktproto.api.types.InputMediaGeoLive
import cn.ellerotta.ktproto.api.types.InputPeerUser
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class ProximityFlowTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("poc").toFile()
    private var scenario: ActivityScenario<MainActivity>? = null

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @BindValue
    @JvmField
    val server = ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test")

    @BindValue
    @JvmField
    val telegram = TelegramClientHolder(dir) { id, hash, logger -> fake.config(id, hash, logger) }

    @Inject lateinit var navigator: Navigator

    @Inject lateinit var mapsHttp: MapsHttp

    @Before
    fun setUp() {
        hilt.inject()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun standAtAlexanderplatz() {
        val manager = context.getSystemService(LocationManager::class.java)
        manager.allProviders.forEach { provider ->
            shadowOf(manager).setProviderEnabled(provider, true)
            val fix = Location(provider).apply {
                latitude = 52.52
                longitude = 13.405
                accuracy = 5f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            shadowOf(manager).simulateLocation(fix)
        }
    }

    private fun shareUntilStopped() {
        compose.waitForTag("locationLiveShare")
        compose.onNodeWithTag("locationPeriod_0").performSemanticsAction(SemanticsActions.OnClick)
        standAtAlexanderplatz()
        compose.onNodeWithTag("locationLiveShare").performSemanticsAction(SemanticsActions.OnClick)
    }

    @After
    fun tearDown() {
        scenario?.close()
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    private fun liveSends() = fake.attach.sent.filter { it.media is InputMediaGeoLive }

    @Test
    fun theLocationScreensBellComesWithTheSharersAndStartsSharingWithTheAlert() {
        fake.addSavedMessages(1)
        fake.attach.addPlace(52.53, 13.405, period = LivePeriods.FOREVER)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("liveBar")
        compose.onNodeWithTag("liveBar").performClick()
        compose.waitUntilIdle { navigator.current is Route.Place }

        compose.waitForTag("locationAlert")
        assertTrue(fake.attach.recentCalls.isNotEmpty())
        compose.onNodeWithTag("locationAlert").performClick()
        compose.waitForTag("proximitySet")
        compose.onNodeWithTag("proximitySet").assertTextEquals("Share my location and notify").performClick()
        shareUntilStopped()

        compose.waitUntilIdle { liveSends().isNotEmpty() }
        val sent = assertIs<InputMediaGeoLive>(liveSends().single().media)
        assertEquals(LivePeriods.FOREVER, sent.period)
        val radius = assertNotNull(sent.proximityNotificationRadius)
        assertTrue(radius in 300..600, "about 500 m in the locale's steps, not $radius")
        compose.waitForTag("ownAlert", unmerged = true)

        compose.runOnIdle { navigator.back() }
        compose.waitUntilIdle { navigator.current is Route.Chat }
        compose.waitForTag("liveBar")
        compose.onNodeWithTag("liveBar").performClick()
        compose.waitUntilIdle { navigator.current is Route.Place }
        compose.waitForTag("ownAlert", unmerged = true)
    }

    @Test
    fun theLiveUpdatesAlertActionOpensThePrivateChatWithTheSheet() {
        fake.attach.addAliceLive(52.53, 13.405)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.logIn()
        val notification = LiveLocationService.liveNotification(context, listOf(LiveShare("u501", "Alice Example", 1, null, null, GeoPlace(52.52, 13.405))))
        val action: Intent = shadowOf(notification.actions.first().actionIntent).savedIntent
        assertNotNull(scenario).onActivity { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, action) }
        compose.waitUntilIdle { navigator.current is Route.Chat }
        assertEquals(Route.Chat("u501", "Alice Example", proximity = true), navigator.current)

        compose.waitForTag("proximitySet")
        compose.onNodeWithTag("proximitySet").assertTextEquals("Share my location and notify").performClick()
        shareUntilStopped()
        compose.waitUntilIdle { liveSends().isNotEmpty() }
        val call = liveSends().single()
        assertEquals(fake.alice.id, assertIs<InputPeerUser>(call.peer).userId)
        assertNotNull(assertIs<InputMediaGeoLive>(call.media).proximityNotificationRadius)
        compose.waitForTag("liveBarAlert")
    }

    @Test
    fun theLocationScreenShowsTheWayThereOnFootByCarOrNoneByTransit() {
        standAtAlexanderplatz()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.logIn()
        compose.runOnIdle { navigator.go(Route.Place("u501", 1, GeoPlace(52.53, 13.42, "TV Tower"))) }
        compose.waitForText("0.93 mi · 19 min")
        val http = mapsHttp as FakeMapsHttp
        assertTrue("mode=walking" in http.asked("directions/json").single())
        compose.onNodeWithTag("travel_driving").performClick()
        compose.waitForText("1.2 mi · 5 min")
        compose.onNodeWithTag("travel_transit").performClick()
        compose.waitForText("No route found")
        assertEquals(3, http.asked("directions/json").size)
    }
}
