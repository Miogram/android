package app.miogram

import android.app.Application
import android.app.NotificationManager
import androidx.lifecycle.ProcessLifecycleOwner
import app.miogram.location.ProximityAlerts
import app.miogram.outgoing.OutgoingKeeper
import app.miogram.connection.ConnectionKeeper
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MiogramApp : Application() {
    @Inject
    lateinit var connection: ConnectionKeeper

    @Inject
    lateinit var proximity: ProximityAlerts

    @Inject
    lateinit var outgoing: OutgoingKeeper

    override fun onCreate() {
        super.onCreate()
        startFirebase()
        connection.attach(ProcessLifecycleOwner.get().lifecycle)
        proximity.start()
        outgoing.start()
        getSystemService(NotificationManager::class.java).deleteNotificationChannel(OLD_CONNECTION_CHANNEL)
    }

    private fun startFirebase() {
        if (FirebaseApp.getApps(this).isNotEmpty() || BuildConfig.FIREBASE_APP_ID.isEmpty()) return
        val options = FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            .build()
        FirebaseApp.initializeApp(this, options)
    }

    private companion object {
        const val OLD_CONNECTION_CHANNEL = "connection"
    }
}
