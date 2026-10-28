package app.miogram.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.miogram.data.SettingsStore
import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.UpdatesRepository
import app.miogram.di.ApplicationScope
import app.miogram.push.PushNotifier
import cn.ellerotta.core.model.DistanceAmount
import cn.ellerotta.core.model.DistanceUnit
import cn.ellerotta.core.model.DistanceUnits
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.ui.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class ProximityAlerts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updates: UpdatesRepository,
    @ApplicationScope private val scope: CoroutineScope,
    private val settings: SettingsStore,
) {
    fun start() {
        scope.launch {
            updates.events().collect { event -> if (event is ChatEvent.NewMessage) notify(event.key, event.message) }
        }
    }

    fun channel(): String {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.proximity_channel), NotificationManager.IMPORTANCE_HIGH))
        return CHANNEL
    }

    fun blocked(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return !manager.areNotificationsEnabled() || manager.getNotificationChannel(channel())?.importance == NotificationManager.IMPORTANCE_NONE
    }

    fun notify(key: String, message: MessageItem): Notification? {
        val alert = message.proximity ?: return null
        if (!settings.proximityAlerts.value) return null
        val manager = context.getSystemService(NotificationManager::class.java)
        channel()
        val distance = distanceText(context, alert.distance.toDouble())
        val text = when {
            alert.to.isEmpty() -> context.getString(R.string.chat_proximity_you, alert.from, distance)
            alert.from.isEmpty() -> context.getString(R.string.chat_proximity_from_you, alert.to, distance)
            else -> context.getString(R.string.chat_proximity, alert.from, alert.to, distance)
        }
        val open = Intent().setComponent(ComponentName(context, PushNotifier.OPEN_CHAT)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(PushNotifier.EXTRA_DIALOG, key)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_distance)
            .setContentTitle(context.getString(R.string.proximity_title))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setWhen(message.date * 1000L)
            .setContentIntent(PendingIntent.getActivity(context, key.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        manager.notify(key, NOTIFICATION_ID, notification)
        return notification
    }

    companion object {
        const val CHANNEL = "proximity"
        private const val NOTIFICATION_ID = 0x11FF
    }
}

internal fun distanceText(context: Context, metres: Double): String {
    val amount = DistanceAmount.of(metres, DistanceUnits.of(context.resources.configuration.locales[0].country))
    val format = when (amount.unit) {
        DistanceUnit.Metres -> R.string.location_metres
        DistanceUnit.Kilometres -> R.string.location_kilometres
        DistanceUnit.Feet -> R.string.location_feet
        DistanceUnit.Miles -> R.string.location_miles
    }
    return context.getString(format, amount.text)
}
