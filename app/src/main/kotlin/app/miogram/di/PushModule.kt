package app.miogram.di

import android.content.Context
import app.miogram.push.FirebasePushTokens
import app.miogram.push.NotificationPeers
import app.miogram.push.NotificationPreviews
import app.miogram.push.PushHandler
import app.miogram.push.PushReceiver
import app.miogram.push.PushTokens
import app.miogram.push.StoredNotificationPeers
import app.miogram.push.WorkNotificationPreviews
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {
    @Binds
    abstract fun receiver(handler: PushHandler): PushReceiver

    @Binds
    abstract fun peers(peers: StoredNotificationPeers): NotificationPeers

    @Binds
    abstract fun previews(previews: WorkNotificationPreviews): NotificationPreviews

    companion object {
        @Provides
        @Singleton
        fun tokens(@ApplicationContext context: Context): PushTokens = FirebasePushTokens(context)
    }
}
