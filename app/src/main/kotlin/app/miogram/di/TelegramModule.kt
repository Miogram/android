package app.miogram.di

import android.content.Context
import app.miogram.APP_VERSION
import app.miogram.BuildConfig
import app.miogram.data.telegram.ChatMutes
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import cn.ellerotta.core.database.ChatDatabase
import cn.ellerotta.ktproto.android.androidClientConfig
import cn.ellerotta.ktproto.android.androidPeerStorage
import cn.ellerotta.ktproto.android.androidUpdateStateStorage
import cn.ellerotta.ktproto.android.watchNetwork
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TelegramModule {
    @Provides
    @Singleton
    fun serverConfig(): ServerConfig = ServerConfig(
        testMode = BuildConfig.TEST_MODE,
        apiId = BuildConfig.TELEGRAM_API_ID,
        apiHash = BuildConfig.TELEGRAM_API_HASH,
        appVersion = APP_VERSION,
    )

    @Provides
    @Singleton
    fun telegram(@ApplicationContext context: Context, server: ServerConfig): TelegramClientHolder =
        TelegramClientHolder(
            context.filesDir,
            updateStorage = { androidUpdateStateStorage(context) },
            peerStorage = { androidPeerStorage(context) },
            networkWatcher = { it.watchNetwork(context) },
            database = ChatDatabase.open(context, File(context.noBackupFilesDir, "chats.db")),
            chatMutes = ChatMutes(context),
        ) { apiId, apiHash, logger ->
            androidClientConfig(context, apiId, apiHash, testMode = server.testMode).copy(logger = logger, appVersion = server.appVersion)
        }
}
