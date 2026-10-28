package app.miogram.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.miogram.connection.ConnectionKeeper
import app.miogram.data.telegram.NotificationMediaRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

interface NotificationPreviews {
    fun enqueue(key: String, id: Int, mention: Boolean)

    fun cancelAll()
}

class WorkNotificationPreviews @Inject constructor(@ApplicationContext private val context: Context) : NotificationPreviews {
    override fun enqueue(key: String, id: Int, mention: Boolean) {
        val request = OneTimeWorkRequestBuilder<NotificationPreviewWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(Data.Builder().putString(KEY, key).putInt(ID, id).putBoolean(MENTION, mention).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("$TAG/$key/$id", ExistingWorkPolicy.KEEP, request)
    }

    override fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    internal companion object {
        const val TAG = "notification_preview"
        const val KEY = "key"
        const val ID = "id"
        const val MENTION = "mention"
    }
}

@Singleton
class NotificationPreviewLoader @Inject constructor(
    private val notifier: PushNotifier,
    private val images: NotificationMediaRepository,
    private val session: BackgroundSession,
    private val connection: ConnectionKeeper,
) {
    suspend fun load(key: String, id: Int, mention: Boolean) {
        if (!notifier.wantsImage(key, id, mention)) return
        val image = withTimeoutOrNull(CAP_MILLIS) {
            connection.hold {
                if (!session.ready()) return@hold null
                try {
                    images.image(key, id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
        } ?: return
        notifier.attachImage(key, id, image.bytes, image.mime)
    }

    companion object {
        const val CAP_MILLIS = 10_000L
    }
}

class NotificationPreviewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun loader(): NotificationPreviewLoader
    }

    override suspend fun doWork(): Result {
        val key = inputData.getString(WorkNotificationPreviews.KEY) ?: return Result.success()
        val id = inputData.getInt(WorkNotificationPreviews.ID, 0)
        if (id == 0) return Result.success()
        EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).loader()
            .load(key, id, inputData.getBoolean(WorkNotificationPreviews.MENTION, false))
        return Result.success()
    }
}
