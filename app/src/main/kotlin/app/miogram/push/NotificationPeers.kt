package app.miogram.push

import app.miogram.data.telegram.NotifySettingsRepository
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import cn.ellerotta.core.model.PeerKind
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

interface NotificationPeers {
    suspend fun avatar(peerKey: String): ByteArray?

    suspend fun muted(dialogKey: String, kind: PeerKind?): Boolean
}

@Singleton
class BackgroundSession @Inject constructor(private val telegram: TelegramClientHolder, private val server: ServerConfig) {
    suspend fun ready(): Boolean {
        if (server.apiId == 0 && !telegram.isPrepared) return false
        return try {
            telegram.prepareOnce(server.apiId, server.apiHash)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}

class StoredNotificationPeers @Inject constructor(
    private val avatars: NotificationAvatars,
    private val settings: NotifySettingsRepository,
) : NotificationPeers {
    override suspend fun avatar(peerKey: String): ByteArray? = avatars.read(peerKey)

    override suspend fun muted(dialogKey: String, kind: PeerKind?): Boolean = settings.muted(dialogKey, kind)
}
