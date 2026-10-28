package app.miogram.data.telegram

import cn.ellerotta.core.model.NotifyScope
import cn.ellerotta.ktproto.api.functions.account.GetNotifySettings
import cn.ellerotta.ktproto.api.types.InputNotifyBroadcasts
import cn.ellerotta.ktproto.api.types.InputNotifyChats
import cn.ellerotta.ktproto.api.types.InputNotifyUsers
import cn.ellerotta.ktproto.api.types.PeerNotifySettings
import cn.ellerotta.ktproto.api.types.TypeInputNotifyPeer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal val NotifyScope.input: TypeInputNotifyPeer
    get() = when (this) {
        NotifyScope.Users -> InputNotifyUsers
        NotifyScope.Chats -> InputNotifyChats
        NotifyScope.Broadcasts -> InputNotifyBroadcasts
    }

internal suspend fun TelegramClientHolder.notifySettingsOf(peer: TypeInputNotifyPeer): PeerNotifySettings? = try {
    client().invoke(GetNotifySettings(peer))
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    log("notify settings not fetched: ${e.message}")
    null
}

internal suspend fun TelegramClientHolder.fetchDefaultMute(scope: NotifyScope): Int? = notifySettingsOf(scope.input)?.let { settings ->
    (settings.muteUntil ?: 0).also { peers.rememberDefaultMute(scope, it) }
}

internal suspend fun TelegramClientHolder.defaultMute(scope: NotifyScope): Int? {
    peers.defaultMute(scope)?.let { return it }
    if (!connected || !isAuthorized) return null
    return fetchDefaultMute(scope)
}

internal suspend fun TelegramClientHolder.loadDefaultMutes() {
    val missing = NotifyScope.entries.filter { peers.defaultMute(it) == null }
    if (missing.isEmpty()) return
    coroutineScope { missing.map { scope -> async { defaultMute(scope) } }.awaitAll() }
}
