package app.miogram.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.graphics.drawable.IconCompat
import app.miogram.data.DeviceLocation
import app.miogram.data.maps.MapsRepository
import app.miogram.data.telegram.AttachRepository
import app.miogram.data.telegram.UpdatesRepository
import app.miogram.push.NotificationAvatars
import app.miogram.push.PushNotifier
import cn.ellerotta.core.model.DistanceAmount
import cn.ellerotta.core.model.DistanceUnit
import cn.ellerotta.core.model.DistanceUnits
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.LiveSharer
import cn.ellerotta.core.model.Route
import cn.ellerotta.core.model.TravelGuess
import cn.ellerotta.core.model.TravelMode
import cn.ellerotta.core.ui.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LiveLocationService : Service() {
    @Inject lateinit var live: LiveLocations
    @Inject lateinit var device: DeviceLocation
    @Inject lateinit var attach: AttachRepository
    @Inject lateinit var updates: UpdatesRepository
    @Inject lateinit var maps: MapsRepository
    @Inject lateinit var avatars: NotificationAvatars

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { live.stopAll() }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_DISMISSED) {
            dismissed = live.shares.value.keys
            return START_NOT_STICKY
        }
        val shares = live.shares.value.values
        if (shares.isEmpty() || !device.permitted()) {
            stopSelf()
            return START_NOT_STICKY
        }
        dismissed = null
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(shares), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        if (!running) {
            running = true
            val speed = MutableStateFlow(0f)
            scope.launch {
                device.updates(UPDATE_MS, UPDATE_METRES).collect { fix ->
                    speed.value = if (fix.hasSpeed()) fix.speed else 0f
                    live.moved(GeoPlace(fix.latitude, fix.longitude), fix.bearing.roundToInt().takeIf { fix.hasBearing() })
                }
            }
            val others = MutableStateFlow<Map<String, List<LiveSharer>>>(emptyMap())
            scope.launch {
                live.shares.map { all -> watched(all.values) }.distinctUntilChanged().collectLatest { keys ->
                    others.value = emptyMap()
                    if (keys.isEmpty()) return@collectLatest
                    coroutineScope {
                        val chats = keys.map { key -> key to ChatLiveLocations(key, attach, updates, this) }
                        combine(chats.map { (key, chat) -> chat.sharers.map { sharers -> key to sharers.filter { !it.self } } }) { it.toMap() }.collect { others.value = it }
                    }
                }
            }
            val nearest = MutableStateFlow<Nearby?>(null)
            scope.launch {
                var alertedBefore = emptySet<String>()
                combine(live.shares, others) { mine, near -> mine to near }.collect { (mine, near) ->
                    val alerted = mine.values.filter { (it.radius ?: 0) > 0 }.map { it.dialogKey }.toSet()
                    if (alerted != alertedBefore) {
                        nearest.value = null
                        alertedBefore = alerted
                    }
                    val found = alerted.flatMap { key ->
                        val here = mine[key]?.place ?: return@flatMap emptyList()
                        near[key].orEmpty().map { it to here.distanceTo(it.place) }
                    }.minByOrNull { it.second }
                    nearest.value = found?.let { (sharer, metres) -> Nearby(sharer.name, metres, maxOf(metres, nearest.value?.from ?: 0.0), sharer.key) }
                }
            }
            val frame = MutableStateFlow(LiveFrame())
            val picture = LiveMapPicture(maps, avatars::read)
            val routes = LiveRoutes(maps)
            val travel = TravelGuess()
            scope.launch(Dispatchers.Default) {
                combine(live.shares, others, nearest, speed) { mine, near, closest, moving -> LiveInputs(mine.values, near, closest, moving) }.conflate().collect { inputs ->
                    val me = inputs.shares.firstOrNull()?.place ?: return@collect
                    val single = inputs.shares.singleOrNull()
                    val closest = inputs.nearest
                    val people = (if (single != null) inputs.others[single.dialogKey].orEmpty() else inputs.others.values.flatten().filter { it.key == closest?.key })
                        .map { MapPerson(it.key, it.name, it.place) }
                    val target = people.firstOrNull { it.key == closest?.key } ?: people.minByOrNull { me.distanceTo(it.place) }
                    val now = System.currentTimeMillis() / 1000
                    val mode = travel.next(inputs.speed, now)
                    val route = routes.route(me, target?.key, target?.place, mode, now)
                    val dark = resources.configuration.isNightModeActive
                    val accent = getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
                    frame.value = LiveFrame(
                        picture.draw(me, people, target?.key, single?.radius?.takeIf { it > 0 }, dark, accent, now, route),
                        closest?.key?.let { picture.photo(it, closest.name) },
                        route,
                    )
                }
            }
            scope.launch {
                combine(live.shares, nearest, frame) { all, near, shown -> Triple(all, near, shown) }.collect { (all, near, shown) ->
                    if (all.isEmpty()) {
                        ServiceCompat.stopForeground(this@LiveLocationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else if (dismissed != all.keys) {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, liveNotification(this@LiveLocationService, all.values, near, frame = shown))
                    }
                }
            }
            scope.launch {
                while (isActive) {
                    delay(EXPIRE_MS)
                    live.expire()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private var dismissed: Set<String>? = null

    private fun notification(shares: Collection<LiveShare>) = liveNotification(this, shares)

    companion object {
        const val ACTION_STOP = "app.miogram.location.STOP"
        const val ACTION_DISMISSED = "app.miogram.location.DISMISSED"
        const val CHANNEL = "live_location"
        const val NOTIFICATION_ID = 0x11FE
        private const val UPDATE_MS = 30_000L
        private const val UPDATE_METRES = 25f
        private const val EXPIRE_MS = 30_000L

        const val EXTRA_PROXIMITY = "app.miogram.proximity"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, LiveLocationService::class.java))
        }

        fun liveNotification(context: Context, shares: Collection<LiveShare>, nearest: Nearby? = null, now: Long = System.currentTimeMillis() / 1000, frame: LiveFrame = LiveFrame()): android.app.Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.live_channel), NotificationManager.IMPORTANCE_DEFAULT))
            val single = shares.singleOrNull()
            val radius = single?.radius?.takeIf { it > 0 }
            val text = when {
                single == null -> context.getString(R.string.live_text, context.resources.getQuantityString(R.plurals.live_chats, shares.size, shares.size))
                radius != null && nearest != null -> context.getString(R.string.live_text_near, nearest.name, distanceText(context, nearest.metres), distanceText(context, radius.toDouble()))
                radius != null -> context.getString(R.string.live_text_waiting, single.title, distanceText(context, radius.toDouble()))
                else -> context.getString(R.string.live_text, single.title)
            }.let { line -> frame.route?.let { "$line · ${routeText(context, it)}" } ?: line }
            val stop = PendingIntent.getService(
                context, 0, Intent(context, LiveLocationService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val dismissed = PendingIntent.getService(
                context, 1, Intent(context, LiveLocationService::class.java).setAction(ACTION_DISMISSED), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val open = Intent().setComponent(ComponentName(context, PushNotifier.OPEN_CHAT)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            single?.let { open.putExtra(PushNotifier.EXTRA_DIALOG, it.dialogKey) }
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_share_location_filled)
                .setContentTitle(context.getString(R.string.live_title))
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_LOCATION_SHARING)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setRequestPromotedOngoing(true)
                .setDeleteIntent(dismissed)
                .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            frame.picture?.let { builder.setLargeIcon(it) }
            if (single != null) {
                val alert = Intent(open).putExtra(EXTRA_PROXIMITY, true)
                val label = context.getString(if (radius != null) R.string.live_alert_change else R.string.proximity_notify)
                builder.addAction(0, label, PendingIntent.getActivity(context, 1, alert, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                liveProgress(single, nearest, now)?.let { progress ->
                    val style = NotificationCompat.ProgressStyle()
                        .setProgressSegments(progress.segments.map { NotificationCompat.ProgressStyle.Segment(it) })
                        .setProgressPoints(progress.points.map { NotificationCompat.ProgressStyle.Point(it) })
                        .setProgress(progress.value)
                        .setStyledByProgress(true)
                    val tracker = when {
                        !progress.approach -> IconCompat.createWithResource(context, R.drawable.ic_share_location_filled)
                        frame.tracker != null -> IconCompat.createWithBitmap(frame.tracker)
                        else -> IconCompat.createWithResource(context, R.drawable.ic_person_pin_circle_filled)
                    }
                    style.setProgressTrackerIcon(tracker)
                    if (progress.approach) style.setProgressEndIcon(IconCompat.createWithResource(context, R.drawable.ic_notifications_active_filled))
                    builder.setStyle(style)
                }
            }
            builder.addAction(0, context.getString(R.string.live_stop), stop)
            val end = shares.mapNotNull { it.expires }.minOrNull()
            if (nearest != null) {
                builder.setShowWhen(false).setShortCriticalText(chipDistance(context, nearest.metres))
            } else if (end != null && shares.all { it.expires != null }) {
                builder.setWhen(end * 1000).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            } else if (frame.route != null) {
                builder.setShowWhen(false).setShortCriticalText(chipMinutes(context, frame.route.minutes))
            } else {
                builder.setShowWhen(false).setShortCriticalText(context.getString(R.string.live_chip))
            }
            return builder.build()
        }
    }
}

data class Nearby(val name: String, val metres: Double, val from: Double, val key: String? = null)

data class LiveFrame(val picture: Bitmap? = null, val tracker: Bitmap? = null, val route: Route? = null)

private data class LiveInputs(val shares: Collection<LiveShare>, val others: Map<String, List<LiveSharer>>, val nearest: Nearby?, val speed: Float)

internal fun watched(shares: Collection<LiveShare>): Set<String> =
    shares.filter { (it.radius ?: 0) > 0 }.map { it.dialogKey }.toSet() + listOfNotNull(shares.singleOrNull()?.dialogKey)

data class LiveProgress(val segments: List<Int>, val points: List<Int>, val value: Int, val approach: Boolean)

fun liveProgress(share: LiveShare, nearest: Nearby?, now: Long): LiveProgress? {
    val radius = share.radius?.takeIf { it > 0 }
    if (radius != null && nearest != null) {
        val total = maxOf(nearest.from, nearest.metres, radius + 1.0).roundToInt()
        val outside = total - radius
        return LiveProgress(listOf(outside, radius), listOf(outside), (total - nearest.metres.roundToInt()).coerceIn(0, total), approach = true)
    }
    val end = share.expires ?: return null
    val start = share.started ?: return null
    val period = (end - start).toInt().takeIf { it > 0 } ?: return null
    return LiveProgress(listOf(period), emptyList(), (now - start).toInt().coerceIn(0, period), approach = false)
}

internal fun chipDistance(context: Context, metres: Double): String {
    val amount = DistanceAmount.of(metres, DistanceUnits.of(context.resources.configuration.locales[0].country))
    val format = when (amount.unit) {
        DistanceUnit.Metres -> R.string.live_chip_metres
        DistanceUnit.Kilometres -> R.string.live_chip_kilometres
        DistanceUnit.Feet -> R.string.live_chip_feet
        DistanceUnit.Miles -> R.string.live_chip_miles
    }
    return context.getString(format, amount.text)
}

internal fun chipMinutes(context: Context, minutes: Int): String =
    if (minutes < 100) context.getString(R.string.location_period_minutes, minutes) else context.getString(R.string.location_period_hours, minutes / 60)

internal fun routeText(context: Context, route: Route): String {
    val minutes = route.minutes
    val time = when {
        minutes < 60 -> context.getString(R.string.location_period_minutes, minutes)
        minutes % 60 == 0 -> context.getString(R.string.location_period_hours, minutes / 60)
        else -> context.getString(R.string.route_hours_minutes, minutes / 60, minutes % 60)
    }
    val format = when (route.mode) {
        TravelMode.Walking -> R.string.live_route_walking
        TravelMode.Driving -> R.string.live_route_driving
        TravelMode.Transit -> R.string.live_route_transit
    }
    return context.getString(format, distanceText(context, route.metres.toDouble()), time)
}
