package app.miogram.push

import app.miogram.di.ApplicationScope
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@AndroidEntryPoint
class MessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var receiver: PushReceiver

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onNewToken(token: String) = receiver.onNewToken(token)

    override fun onMessageReceived(message: RemoteMessage) {
        if (!scope.awaitWithin(DEADLINE) { receiver.onMessage(message.data) }) receiver.onLate()
    }

    override fun onDeletedMessages() = receiver.onDeleted()

    companion object {
        const val DEADLINE = 8_000L
    }
}
