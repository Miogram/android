package app.miogram.data.telegram

import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.ktproto.client.PushNotification
import cn.ellerotta.ktproto.client.registerPushDevice
import cn.ellerotta.ktproto.client.unregisterPushDevice
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PushAction { Show, Delete, Read, Revoke, DcUpdate, Ignore }

data class PushMessage(
    val action: PushAction,
    val dialogKey: String?,
    val kind: PeerKind?,
    val title: String,
    val sender: String?,
    val text: String,
    val media: MediaKind?,
    val messageId: Int?,
    val messageIds: List<Int>,
    val maxId: Int?,
    val silent: Boolean,
    val senderKey: String? = null,
    val mention: Boolean = false,
    val article: ArticleMessage? = null,
    val quiet: Boolean = false,
    val userId: Long? = null,
    val dc: Int? = null,
    val address: String? = null,
)

@Singleton
class PushRepository @Inject constructor(private val telegram: TelegramClientHolder, private val auth: AuthRepository) {
    val isAuthorized: Boolean get() = telegram.isAuthorized

    val authorized: StateFlow<Boolean> get() = telegram.authorized

    suspend fun liveMessage(event: ChatEvent.NewMessage): PushMessage? {
        val item = event.message
        if (item.out || item.service != null || item.ephemeral != null || item.id == 0) return null
        val key = event.key
        val kind = when {
            key.startsWith("u") -> PeerKind.User
            event.post -> PeerKind.Channel
            else -> PeerKind.Group
        }
        val senderKey = if (kind == PeerKind.Group) item.senderId.takeIf { it != 0L }?.let { "u$it" } else key
        val names = names(listOfNotNull(key, senderKey))
        return PushMessage(
            action = PushAction.Show,
            dialogKey = key,
            kind = kind,
            title = names[key] ?: item.senderName.orEmpty(),
            sender = if (kind == PeerKind.Group) senderKey?.let(names::get) ?: item.senderName else null,
            text = item.text,
            media = item.media?.kind?.takeIf { item.text.isBlank() },
            messageId = item.id,
            messageIds = emptyList(),
            maxId = null,
            silent = event.silent,
            senderKey = senderKey,
            mention = event.mention,
            article = item.article,
        )
    }

    private suspend fun names(keys: List<String>): Map<String, String> {
        val registry = telegram.peers
        val missing = keys.filter { !registry.peerNames.containsKey(it) }
        if (missing.isNotEmpty()) {
            telegram.cache?.peers(missing)?.let { (users, chats) ->
                registry.rememberUsers(users)
                registry.rememberChats(chats)
            }
        }
        return keys.mapNotNull { key -> registry.peerNames[key]?.let { key to it } }.toMap()
    }

    suspend fun registerPush(token: String) {
        telegram.client().registerPushDevice(token, noMuted = true)
    }

    suspend fun catchUp() {
        telegram.client()
    }

    suspend fun applyDcUpdate(dc: Int?, address: String?): Boolean {
        if (dc == null || address == null) return false
        return telegram.applyDcUpdate(dc, address).also { if (it) telegram.log("dc $dc: address $address from a push") }
    }

    suspend fun revokeSession(userId: Long?): Boolean {
        if (!telegram.revokeLogin(userId)) return false
        auth.forgetAccount()
        return true
    }

    fun log(line: String) = telegram.log(line)

    suspend fun unregisterPush(token: String) {
        telegram.client().unregisterPushDevice(token)
    }

    suspend fun decryptPush(data: Map<String, String>): PushMessage? {
        val payload = data["p"] ?: return null
        val secret = telegram.storedSession()?.pushSecret ?: return null
        val push = runCatching { PushNotification.decrypt(payload, secret) }.getOrElse {
            telegram.log("push dropped: ${it.message}")
            return null
        }
        return pushMessageOf(push)
    }
}
