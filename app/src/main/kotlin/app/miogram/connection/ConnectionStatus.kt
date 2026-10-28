package app.miogram.connection

import app.miogram.data.DialogsStore
import app.miogram.data.telegram.TelegramClientHolder
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class ConnectionStatus @Inject constructor(private val network: NetworkMonitor, private val telegram: TelegramClientHolder, private val store: DialogsStore) {
    val status: Flow<ChatsStatus> = combine(network.online, telegram.ready, store.state) { online, ready, state ->
        statusOf(online, ready, state.loaded, state.refreshing)
    }.distinctUntilChanged()

    val chat: Flow<ChatsStatus> = combine(network.online, telegram.ready, store.state) { online, ready, state ->
        statusOf(online, ready, loaded = true, refreshing = state.loaded && state.refreshing)
    }.distinctUntilChanged()

    val chatNow: ChatsStatus get() = statusOf(network.online.value, telegram.ready.value, loaded = true, refreshing = store.state.value.let { it.loaded && it.refreshing })
}

internal fun statusOf(online: Boolean, ready: Boolean, loaded: Boolean, refreshing: Boolean): ChatsStatus = when {
    !online -> ChatsStatus.WaitingForNetwork
    !ready || !loaded -> ChatsStatus.Connecting
    refreshing -> ChatsStatus.Updating
    else -> ChatsStatus.Ready
}
