package app.miogram

import android.Manifest
import android.location.LocationManager
import app.miogram.data.DeviceLocation
import app.miogram.data.SettingsStore
import app.miogram.location.ChatLiveLocations
import app.miogram.push.PushNotifier
import cn.ellerotta.core.model.UiError
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import app.miogram.location.LiveProgress
import app.miogram.location.Nearby
import app.miogram.location.liveProgress
import android.app.Application
import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.telegram.SignInResult
import app.miogram.location.LiveLocationService
import app.miogram.location.LiveLocations
import app.miogram.location.LiveShare
import app.miogram.location.ProximityAlerts
import android.app.NotificationManager
import cn.ellerotta.core.model.LiveInfo
import cn.ellerotta.core.model.LivePeriods
import cn.ellerotta.core.model.LiveSharer
import cn.ellerotta.core.model.MessageItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.ProximityAlert
import cn.ellerotta.core.model.ServiceKind
import cn.ellerotta.ktproto.api.types.InputGeoPoint
import cn.ellerotta.ktproto.api.types.InputMediaGeoLive
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveLocationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

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
    fun aLiveLocationStartsMovesAndStops(): Unit = withChat { repos, fake, saved ->
        val live = LiveLocations(context, repos.attach, DeviceLocation(context))
        live.start(saved, "Saved Messages", GeoPlace(55.75, 37.62), heading = null, seconds = 900, radius = 500, replyTo = null, topicId = null)

        val sent = assertIs<InputMediaGeoLive>(fake.attach.sent.single().media)
        assertEquals(900, sent.period)
        assertEquals(500, sent.proximityNotificationRadius)
        val share = assertNotNull(live.shares.value[saved])
        assertNotNull(share.expires)
        assertEquals(LiveLocationService::class.java.name, shadowOf(context).nextStartedService.component?.className)
        val shown = repos.messages.history(saved).last().place
        assertEquals(900, shown?.live?.period)

        live.moved(GeoPlace(55.76, 37.63), heading = 90)
        val moved = assertIs<InputMediaGeoLive>(fake.attach.liveEdits.single().media)
        assertEquals(55.76 to 37.63, assertIs<InputGeoPoint>(moved.geoPoint).let { it.lat to it.long })
        assertEquals(90, moved.heading)

        live.stop(saved)
        val stopped = assertIs<InputMediaGeoLive>(fake.attach.liveEdits.last().media)
        assertTrue(stopped.stopped)
        assertEquals(55.76, assertIs<InputGeoPoint>(stopped.geoPoint).lat)
        assertNull(live.shares.value[saved])
    }

    @Test
    fun theNotificationIsAPromotedOngoingLiveUpdateCountingDown(): Unit = runBlocking {
        val end = System.currentTimeMillis() / 1000 + 900
        val one = LiveShare("u501", "Alice", 7, end, 500, GeoPlace(1.0, 2.0))
        val notification = LiveLocationService.liveNotification(context, listOf(one))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(Notification.CATEGORY_LOCATION_SHARING, notification.category)
        assertEquals(end * 1000, notification.`when`)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(listOf("Change alert", "Stop sharing"), notification.actions.map { it.title.toString() })
        assertEquals("Notify when nearby", LiveLocationService.liveNotification(context, listOf(one.copy(radius = null))).actions.first().title.toString())
        assertTrue(!notification.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertNull(notification.group)

        val forever = LiveLocationService.liveNotification(context, listOf(one.copy(expires = null)))
        assertTrue(!forever.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
    }

    @Test
    fun aProximityAlertShowsWhoCameHowClose(): Unit = withChat { repos, fake, saved ->
        fake.attach.addProximity(250)
        val alert = repos.messages.history(saved).last()
        assertEquals(ServiceKind.Proximity, alert.service)
        assertEquals(ProximityAlert("Alice", "", 250), alert.proximity)
    }

    @Test
    fun aProximityAlertIsSetUpdatedAndRemovedOnTheOwnLiveLocation(): Unit = withChat { repos, fake, saved ->
        val live = LiveLocations(context, repos.attach, DeviceLocation(context))
        live.start(saved, "Saved Messages", GeoPlace(55.75, 37.62), heading = null, seconds = 0, radius = null, replyTo = null, topicId = null)
        live.alert(saved, 1000, own = null)
        assertEquals(1000, assertIs<InputMediaGeoLive>(fake.attach.liveEdits.last().media).proximityNotificationRadius)
        assertEquals(1000, live.shares.value[saved]?.radius)
        live.alert(saved, 0, own = null)
        assertEquals(0, assertIs<InputMediaGeoLive>(fake.attach.liveEdits.last().media).proximityNotificationRadius)
        assertNull(live.shares.value[saved]?.radius)

        val id = live.shares.value.getValue(saved).messageId
        val other = LiveLocations(context, repos.attach, DeviceLocation(context))
        other.alert(saved, 250, own = LiveSharer(id, self = true, GeoPlace(55.75, 37.62, live = LiveInfo(period = LivePeriods.FOREVER))))
        val edit = fake.attach.liveEdits.last()
        assertEquals(id, edit.id)
        assertEquals(250, assertIs<InputMediaGeoLive>(edit.media).proximityNotificationRadius)
    }

    @Test
    fun theAlertIsReadBackFromTheOwnLiveMessageWhenTheChatOpensAgain(): Unit = withChat { repos, _, saved ->
        val live = LiveLocations(context, repos.attach, DeviceLocation(context))
        live.start(saved, "Saved Messages", GeoPlace(55.75, 37.62), heading = null, seconds = 0, radius = 500, replyTo = null, topicId = null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val reopened = ChatLiveLocations(saved, repos.attach, repos.updates, scope)
        val own = withTimeout(10_000) { reopened.sharers.first { list -> list.any { it.self } } }.first { it.self }
        assertEquals(500, own.live.radius)
        live.alert(saved, 1000, own = null)
        val again = ChatLiveLocations(saved, repos.attach, repos.updates, scope)
        assertEquals(1000, withTimeout(10_000) { again.sharers.first { list -> list.any { it.self } } }.first { it.self }.live.radius)
        scope.cancel()
    }

    @Test
    fun sharingHereNeedsTheLocationPermissionAndAFix(): Unit = withChat { repos, fake, saved ->
        val live = LiveLocations(context, repos.attach, DeviceLocation(context))
        assertEquals(UiError.LocationDenied, live.startHere(saved, "Saved Messages", seconds = 900, radius = 500))
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager = context.getSystemService(LocationManager::class.java)
        manager.allProviders.forEach { shadowOf(manager).setProviderEnabled(it, false) }
        assertEquals(UiError.LocationUnavailable, live.startHere(saved, "Saved Messages", seconds = 900, radius = 500))
        assertTrue(fake.attach.sent.isEmpty())
        assertTrue(live.shares.value.isEmpty())
    }

    @Test
    fun oneChatsNotificationSaysItsAlertAndOpensTheChatWithTheSheet() {
        val share = LiveShare("u501", "Alice", 7, null, 500, GeoPlace(1.0, 2.0))
        val notification = LiveLocationService.liveNotification(context, listOf(share))
        assertEquals("With Alice · alert within 0.31 mi, waiting for their location…", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(LiveLocationService.ACTION_DISMISSED, shadowOf(notification.deleteIntent).savedIntent.action)
        val action = notification.actions.first()
        assertEquals("Change alert", action.title.toString())
        val opened = shadowOf(action.actionIntent).savedIntent
        assertEquals("u501", opened.getStringExtra(PushNotifier.EXTRA_DIALOG))
        assertTrue(opened.getBooleanExtra(LiveLocationService.EXTRA_PROXIMITY, false))
        assertTrue(!shadowOf(notification.contentIntent).savedIntent.getBooleanExtra(LiveLocationService.EXTRA_PROXIMITY, false))
        val two = LiveLocationService.liveNotification(context, listOf(share, share.copy(dialogKey = "u502", title = "Bob")))
        assertEquals(listOf("Stop sharing"), two.actions.map { it.title.toString() })
    }

    @Test
    fun aFiredAlertIsANotificationOfItsOwnChannel(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            val settings = SettingsStore(context)
            val alerts = ProximityAlerts(context, repos.updates, CoroutineScope(Dispatchers.Default), settings)
            val alert = MessageItem(9, "", out = false, date = FakeTelegram.NOW, service = ServiceKind.Proximity, proximity = ProximityAlert("Alice", "", 250))
            val notification = assertNotNull(alerts.notify("u501", alert))
            assertEquals(ProximityAlerts.CHANNEL, notification.channelId)
            assertEquals("Alice is now within 820 ft of you", notification.extras.getString(Notification.EXTRA_TEXT))
            val manager = context.getSystemService(NotificationManager::class.java)
            assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(ProximityAlerts.CHANNEL).importance)
            assertEquals(1, shadowOf(manager).allNotifications.count { it.channelId == ProximityAlerts.CHANNEL })
            assertNull(alerts.notify("u501", MessageItem(10, "hi", out = false, date = FakeTelegram.NOW)))
            settings.setProximityAlerts(false)
            assertNull(alerts.notify("u501", alert))
            assertEquals(1, shadowOf(manager).allNotifications.count { it.channelId == ProximityAlerts.CHANNEL })
            settings.setProximityAlerts(true)
            dir.deleteRecursively()
        }
    }

    @Test
    fun theChipIsTheDistanceToTheNearestAlertedPersonAndTheProgressTheirApproach() {
        val share = LiveShare("u501", "Alice", 7, null, 500, GeoPlace(1.0, 2.0))
        val bob = Nearby("Bob", 1500.0, from = 2000.0)
        val notification = LiveLocationService.liveNotification(context, listOf(share), nearest = bob)
        assertTrue(notification.extras.keySet().any { notification.extras.getCharSequence(it)?.toString() == "0.93mi" })
        assertTrue(!notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals("Bob is 0.93 mi away · alert within 0.31 mi", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(LiveProgress(listOf(1500, 500), listOf(1500), 500, approach = true), liveProgress(share, bob, now = 0))
    }

    @Test
    fun aTimedShareShowsItsTimeGoneByAndOneUntilStoppedNoProgress() {
        val share = LiveShare("u501", "Alice", 7, expires = 1_900, radius = null, GeoPlace(1.0, 2.0), started = 1_000)
        assertEquals(LiveProgress(listOf(900), emptyList(), 300, approach = false), liveProgress(share, null, now = 1_300))
        assertEquals(null, liveProgress(share.copy(expires = null), null, now = 1_300))
        assertEquals(300, liveProgress(share.copy(radius = 500), null, now = 1_300)?.value)
    }
}
