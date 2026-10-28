package app.miogram.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface PushReceiver {
    fun onNewToken(token: String)

    suspend fun onMessage(data: Map<String, String>)

    fun onDeleted()

    fun onLate()
}

fun interface PushTokens {
    suspend fun current(): String?
}

class FirebasePushTokens(private val context: Context) : PushTokens {
    override suspend fun current(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }
}
