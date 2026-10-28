package app.miogram.data.telegram

import cn.ellerotta.core.database.DialogRow
import cn.ellerotta.core.model.Mutes
import cn.ellerotta.core.model.NotificationSettingsState
import cn.ellerotta.core.model.NotifyException
import cn.ellerotta.core.model.NotifyScope
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.ScopeNotifySettings
import cn.ellerotta.ktproto.api.functions.account.GetNotifyExceptions
import cn.ellerotta.ktproto.api.functions.account.GetNotifySettings
import cn.ellerotta.ktproto.api.functions.account.ResetNotifySettings
import cn.ellerotta.ktproto.api.functions.account.UpdateNotifySettings
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.Chat
import cn.ellerotta.ktproto.api.types.InputNotifyPeer
import cn.ellerotta.ktproto.api.types.InputPeerNotifySettings
import cn.ellerotta.ktproto.api.types.NotifyPeer
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.TypeChat
import cn.ellerotta.ktproto.api.types.TypeUpdate
import cn.ellerotta.ktproto.api.types.TypeUser
import cn.ellerotta.ktproto.api.types.UpdateNotifySettings as UpdateNotifySettingsUpdate
import cn.ellerotta.ktproto.api.types.UpdateShort
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.UpdatesCombined
import cn.ellerotta.ktproto.api.types.User
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

private const val SETTINGS = "notifySettings"

@Singleton
class NotifySettingsRepository @Inject constructor(private val telegram: TelegramClientHolder, private val updates: UpdatesRepository) {
    private val registry get() = telegram.peers

    suspend fun muted(key: String, kind: PeerKind?): Boolean {
        val own = own(key)
        val default = if (own == null) telegram.defaultMute(scopeOf(key, kind)) else null
        return Mutes.muted(own, default, System.currentTimeMillis() / 1000)
    }

    suspend fun refreshDefaults(): Boolean {
        if (telegram.userId == null) return false
        return NotifyScope.entries.all { telegram.fetchDefaultMute(it) != null }
    }

    fun stored(): NotificationSettingsState? = telegram.memory[SETTINGS]

    fun remember(state: NotificationSettingsState) {
        telegram.memory[SETTINGS] = state
    }

    suspend fun defaults(): Map<NotifyScope, ScopeNotifySettings> = coroutineScope {
        NotifyScope.entries.map { scope ->
            async {
                val settings = telegram.client().invoke(GetNotifySettings(scope.input))
                val until = settings.muteUntil ?: 0
                registry.rememberDefaultMute(scope, until)
                scope to ScopeNotifySettings(until, settings.showPreviews ?: true)
            }
        }.awaitAll().toMap()
    }

    suspend fun setDefault(scope: NotifyScope, settings: ScopeNotifySettings) {
        telegram.client().invoke(UpdateNotifySettings(scope.input, InputPeerNotifySettings(showPreviews = settings.showPreviews, muteUntil = settings.muteUntil)))
        registry.rememberDefaultMute(scope, settings.muteUntil)
        updates.local(ChatEvent.DefaultMute(scope, settings.muteUntil))
    }

    suspend fun exceptions(): Map<NotifyScope, List<NotifyException>> {
        val (list, users, chats) = when (val answer = telegram.client().invoke(GetNotifyExceptions())) {
            is Updates -> Triple(answer.updates, answer.users, answer.chats)
            is UpdatesCombined -> Triple(answer.updates, answer.users, answer.chats)
            is UpdateShort -> Triple(listOf(answer.update), emptyList<TypeUser>(), emptyList<TypeChat>())
            else -> Triple(emptyList<TypeUpdate>(), emptyList<TypeUser>(), emptyList<TypeChat>())
        }
        registry.rememberUsers(users)
        registry.rememberChats(chats)
        val byUser = users.filterIsInstance<User>().associateBy { it.id }
        val byChat = chats.associateBy(::chatId)
        val now = System.currentTimeMillis() / 1000
        return list.filterIsInstance<UpdateNotifySettingsUpdate>().mapNotNull { update ->
            val peer = (update.peer as? NotifyPeer)?.peer ?: return@mapNotNull null
            val item = registry.dialogOf(peer, byUser, byChat, now) ?: return@mapNotNull null
            val chat = if (peer is PeerUser) null else byChat[item.peerId]
            if ((chat as? Channel)?.left == true || (chat as? Chat)?.let { it.left || it.deactivated } == true) return@mapNotNull null
            registry.rememberMute(item.key, update.notifySettings)
            NotifyException(item.key, item.title, item.kind, item.avatarKey, update.notifySettings.muteUntil, update.notifySettings.showPreviews)
        }.groupBy { NotifyScope.of(it.kind) }
    }

    suspend fun setException(key: String, muteUntil: Int?) {
        telegram.client().invoke(UpdateNotifySettings(InputNotifyPeer(registry.peer(key)), InputPeerNotifySettings(muteUntil = muteUntil)))
        registry.rememberMute(key, muteUntil ?: DialogRow.FOLLOWS_DEFAULT)
        updates.local(ChatEvent.Muted(key, muteUntil))
    }

    suspend fun reset(): Map<NotifyScope, ScopeNotifySettings> {
        telegram.client().invoke(ResetNotifySettings)
        registry.muteUntil.keys.toList().forEach { key ->
            if (registry.ownMute(key) != null) {
                registry.rememberMute(key, DialogRow.FOLLOWS_DEFAULT)
                updates.local(ChatEvent.Muted(key, null))
            }
        }
        return defaults().onEach { (scope, settings) -> updates.local(ChatEvent.DefaultMute(scope, settings.muteUntil)) }
    }

    fun clear() = registry.forgetMutes()

    private suspend fun own(key: String): Int? {
        registry.muteUntil[key]?.let { return registry.ownMute(key) }
        telegram.cache?.takeIf { telegram.isPrepared }?.muteUntil(key)?.let { return it.takeIf { until -> until != DialogRow.FOLLOWS_DEFAULT } }
        telegram.chatMutes?.get(key)?.let { return it }
        if (!telegram.connected || !telegram.isAuthorized) return null
        val peer = registry.inputPeer(key) ?: return null
        val settings = telegram.notifySettingsOf(InputNotifyPeer(peer)) ?: return null
        registry.rememberMute(key, settings)
        return registry.ownMute(key)
    }

    private fun scopeOf(key: String, kind: PeerKind?): NotifyScope = when {
        kind != null -> NotifyScope.of(kind)
        key.startsWith("u") -> NotifyScope.Users
        else -> NotifyScope.Chats
    }
}
