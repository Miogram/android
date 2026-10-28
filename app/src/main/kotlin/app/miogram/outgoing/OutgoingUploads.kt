package app.miogram.outgoing

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.miogram.connection.ConnectionKeeper
import app.miogram.data.telegram.MessagesRepository
import app.miogram.di.ApplicationScope
import cn.ellerotta.core.model.OutgoingUploads
import cn.ellerotta.core.ui.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Singleton
class OutgoingKeeper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messages: MessagesRepository,
    private val connection: ConnectionKeeper,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch { messages.outgoingBusy.collectLatest { busy -> if (busy) connection.hold { awaitCancellation() } } }
        scope.launch {
            messages.outgoingUploads.map { it != null }.distinctUntilChanged().collect { uploading ->
                if (!uploading) return@collect
                try {
                    context.startForegroundService(Intent(context, OutgoingUploadService::class.java))
                } catch (_: ForegroundServiceStartNotAllowedException) {
                }
            }
        }
    }
}

@AndroidEntryPoint
class OutgoingUploadService : Service() {
    @Inject
    lateinit var messages: MessagesRepository

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    private var following: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification(this, null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (following?.isActive != true) {
            following = scope.launch {
                messages.outgoingUploads.collect { uploads ->
                    if (uploads == null) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        following?.cancel()
                    } else {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(this@OutgoingUploadService, uploads))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        following?.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "uploads"
        const val NOTIFICATION_ID = 0x5E0D

        fun notification(context: Context, uploads: OutgoingUploads?): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.chat_upload_channel), NotificationManager.IMPORTANCE_LOW))
            val files = uploads?.files ?: 1
            val percent = uploads?.takeIf { it.total > 0 }?.let { (it.sent * 100 / it.total).toInt().coerceIn(0, 100) }
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_send)
                .setContentTitle(context.resources.getQuantityString(R.plurals.chat_uploading, files, files))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, percent ?: 0, percent == null)
                .build()
        }
    }
}
