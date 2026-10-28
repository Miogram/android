package app.miogram.data.telegram

import cn.ellerotta.core.database.ChatDatabase
import cn.ellerotta.core.model.ConfigSummary
import cn.ellerotta.core.model.LogLine
import cn.ellerotta.ktproto.KtprotoVersion
import cn.ellerotta.ktproto.api.LAYER
import cn.ellerotta.ktproto.api.types.TypeReaction
import cn.ellerotta.ktproto.client.ClientConfig
import cn.ellerotta.ktproto.client.ClientEvent
import cn.ellerotta.ktproto.client.ClientState
import cn.ellerotta.ktproto.client.InMemoryPeerStorage
import cn.ellerotta.ktproto.client.InMemoryUpdateStateStorage
import cn.ellerotta.ktproto.client.PeerCache
import cn.ellerotta.ktproto.client.PeerStorage
import cn.ellerotta.ktproto.client.StoredSession
import cn.ellerotta.ktproto.client.applyDcUpdate
import cn.ellerotta.ktproto.client.forgetLogin
import cn.ellerotta.ktproto.client.TelegramClient
import cn.ellerotta.ktproto.client.UpdateStateStorage
import cn.ellerotta.ktproto.client.UpdatesManager
import cn.ellerotta.ktproto.session.ProtocolLogger
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ServerConfig(val testMode: Boolean, val apiId: Int, val apiHash: String, val appVersion: String)

class TelegramClientHolder(
    internal val filesDir: File,
    private val updateStorage: () -> UpdateStateStorage = { InMemoryUpdateStateStorage() },
    private val peerStorage: () -> PeerStorage = { InMemoryPeerStorage() },
    private val networkWatcher: (TelegramClient) -> AutoCloseable? = { null },
    database: ChatDatabase? = null,
    internal val chatMutes: ChatMutes? = null,
    private val configFactory: (apiId: Int, apiHash: String, logger: ProtocolLogger) -> ClientConfig,
) {
    private val logState = MutableStateFlow<List<LogLine>>(emptyList())

    val log: StateFlow<List<LogLine>> = logState.asStateFlow()

    @Volatile private var client: TelegramClient? = null
    private val connectLock = Mutex()
    @Volatile private var summary: ConfigSummary? = null

    @Volatile private var offline = false

    @Volatile internal var defaultReaction: TypeReaction? = null
        private set

    @Volatile internal var webfileDcId: Int = 0
        private set

    @Volatile internal var gifSearchUsername: String? = null
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var events: Job? = null
    private var states: Job? = null
    private var network: AutoCloseable? = null
    private var cacheWrites: Job? = null

    private val loggedIn = AtomicBoolean(false)
    private val authorizedFlow = MutableStateFlow(false)

    internal val authorized: StateFlow<Boolean> = authorizedFlow.asStateFlow()
    private val endedSignals = Channel<Unit>(Channel.CONFLATED)

    internal val sessionEnded: Flow<Unit> = endedSignals.receiveAsFlow()

    private val premiumWaits = MutableSharedFlow<ClientEvent.PremiumSpeedLimited>(extraBufferCapacity = 8)

    internal val premiumSpeedLimits: SharedFlow<ClientEvent.PremiumSpeedLimited> = premiumWaits.asSharedFlow()

    private val readyFlow = MutableStateFlow(false)

    internal val ready: StateFlow<Boolean> = readyFlow.asStateFlow()

    private val updatesFlow = MutableStateFlow<UpdatesManager?>(null)

    internal val updates: StateFlow<UpdatesManager?> = updatesFlow.asStateFlow()

    @Volatile private var peerCache: PeerCache? = null

    internal val peers = PeerRegistry(chatMutes, selfId = { userId }) { peerCache }

    internal val cache: ChatCache? = database?.let { ChatCache(it, log = { line -> log(line) }) }

    internal val memory = ScreenMemory()

    internal var apiId = 0
        private set
    internal var apiHash = ""
        private set

    private val logger = ProtocolLogger { line ->
        logState.update { (it + LogLine(System.currentTimeMillis(), line)).takeLast(MAX_LOG) }
    }

    internal fun log(line: String) = logger.log(line)

    internal fun requireClient() = client ?: error("not connected")

    val isAuthorized: Boolean get() = client?.userId != null

    internal val userId: Long? get() = client?.userId

    suspend fun prepare(apiId: Int, apiHash: String): Boolean = connectLock.withLock { prepared(apiId, apiHash) }

    suspend fun prepareOnce(apiId: Int, apiHash: String): Boolean = connectLock.withLock {
        if (client != null) storedSession()?.userId != null else prepared(apiId, apiHash)
    }

    internal val isPrepared: Boolean get() = client != null

    private suspend fun prepared(apiId: Int, apiHash: String): Boolean {
        release()
        cold = null
        this.apiId = apiId
        this.apiHash = apiHash
        val config = configFactory(apiId, apiHash, logger)
        val created = TelegramClient(config)
        if (offline) created.disconnect()
        client = created
        follow(created)
        val manager = UpdatesManager(created, updateStorage())
        peerCache = PeerCache(created, peerStorage(), manager).also { it.start() }
        cache?.let { cache -> cacheWrites = scope.launch(start = CoroutineStart.UNDISPATCHED) { manager.events.collect(cache::apply) } }
        manager.start()
        updatesFlow.value = manager
        summary = null
        val owner = config.storage.load()?.userId
        cache?.let { cache -> scope.launch { cache.open(owner) } }
        return (owner != null).also { logged ->
            loggedIn.set(logged)
            authorizedFlow.value = logged
        }
    }

    private fun follow(current: TelegramClient) {
        states = scope.launch(start = CoroutineStart.UNDISPATCHED) { current.state.collect { readyFlow.value = it == ClientState.Ready } }
        events = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            current.events.collect { event ->
                if (event is ClientEvent.PremiumSpeedLimited) premiumWaits.tryEmit(event)
                if (event !is ClientEvent.AuthorizationChanged) return@collect
                val userId = event.userId
                if (userId != null) {
                    loggedIn.set(true)
                    authorizedFlow.value = true
                    watch(current)
                    cache?.owner(userId)
                } else {
                    authorizedFlow.value = false
                    unwatch()
                    cache?.clear()
                    peers.forgetMutes()
                    if (loggedIn.getAndSet(false)) {
                        logger.log("the session was ended elsewhere")
                        endedSignals.trySend(Unit)
                    }
                }
            }
        }
    }

    internal fun expectLogout() = loggedIn.set(false)

    @Synchronized
    private fun watch(current: TelegramClient) {
        if (network == null && client === current && loggedIn.get() && connected) network = networkWatcher(current)
    }

    @Synchronized
    private fun unwatch() {
        network?.close()
        network = null
    }

    private suspend fun release() {
        authorizedFlow.value = false
        events?.cancel()
        events = null
        states?.cancel()
        states = null
        readyFlow.value = false
        cacheWrites?.cancel()
        cacheWrites = null
        unwatch()
        updatesFlow.value?.close()
        peerCache?.close()
        peerCache = null
        client?.close()
    }

    val server: ConfigSummary? get() = summary

    val libraryVersion: String get() = KtprotoVersion.NAME

    val layer: Int get() = LAYER

    suspend fun connect(): ConfigSummary = connectLock.withLock {
        if (!offline) summary?.let { return@withLock it }
        val current = client ?: error("not prepared")
        logger.log("connecting with api_id $apiId")
        val config = current.connect()
        offline = false
        defaultReaction = config.reactionsDefault
        webfileDcId = config.webfileDcId
        gifSearchUsername = config.gifSearchUsername
        ConfigSummary(
            thisDc = config.thisDc,
            testMode = config.testMode,
            dcs = config.dcOptions.filter { !it.ipv6 }.map { "dc${it.id} ${it.ipAddress}:${it.port}${if (it.mediaOnly) " media" else ""}" }.distinct(),
        ).also {
            summary = it
            watch(current)
        }
    }

    suspend fun connect(apiId: Int, apiHash: String): ConfigSummary {
        prepare(apiId, apiHash)
        return connect()
    }

    internal suspend fun client(): TelegramClient {
        if (summary == null || offline) connect()
        return requireClient()
    }

    internal val connected: Boolean get() = summary != null && !offline

    suspend fun disconnect() {
        val wasOffline = offline
        offline = true
        unwatch()
        val current = client ?: return
        if (!wasOffline) logger.log("closing the connection in the background")
        current.disconnect()
    }

    suspend fun reconnect() {
        if (!offline) return
        if (client == null) offline = false else connect()
    }

    private val coldStorage by lazy { configFactory(apiId, apiHash, logger).storage }
    private val coldLock = Mutex()

    private class Stored(val session: StoredSession?)

    @Volatile private var cold: Stored? = null

    internal suspend fun applyDcUpdate(dcId: Int, address: String): Boolean {
        client?.let { return it.applyDcUpdate(dcId, address) }
        return coldLock.withLock { coldStorage.applyDcUpdate(dcId, address).also { cold = null } }
    }

    internal suspend fun revokeLogin(userId: Long?): Boolean {
        val current = storedSession()?.userId ?: return false
        if (userId != null && userId != current) return false
        client?.let { return it.forgetLogin() }
        coldLock.withLock {
            coldStorage.clear()
            cold = null
        }
        return true
    }

    internal suspend fun storedSession(): StoredSession? {
        client?.let { return it.config.storage.load() }
        cold?.let { return it.session }
        return coldLock.withLock { cold ?: Stored(coldStorage.load()).also { cold = it } }.session
    }

    suspend fun close() {
        release()
        updatesFlow.value = null
        client = null
        cold = null
        summary = null
        offline = false
    }

    private companion object {
        const val MAX_LOG = 500
    }
}
