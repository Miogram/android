package app.miogram.data.telegram

import cn.ellerotta.core.model.AppConfig
import cn.ellerotta.ktproto.api.functions.help.GetAppConfig
import cn.ellerotta.ktproto.api.types.JsonArray
import cn.ellerotta.ktproto.api.types.JsonBool
import cn.ellerotta.ktproto.api.types.JsonNull
import cn.ellerotta.ktproto.api.types.JsonNumber
import cn.ellerotta.ktproto.api.types.JsonObject
import cn.ellerotta.ktproto.api.types.JsonString
import cn.ellerotta.ktproto.api.types.TypeJSONValue
import cn.ellerotta.ktproto.api.types.help.AppConfig as TlAppConfig
import cn.ellerotta.ktproto.client.TelegramClient
import cn.ellerotta.ktproto.client.setDownloadQueueLimits
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AppConfigRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val lock = Mutex()
    private var config: AppConfig? = null
    private var hash = 0
    private var fetched: TimeMark? = null

    internal var timeSource: TimeSource = TimeSource.Monotonic

    private var limited: Pair<TelegramClient, AppConfig>? = null

    suspend fun config(): AppConfig = lock.withLock {
        val client = telegram.client()
        val known = config
        val current = if (known != null && fetched?.let { it.elapsedNow() < MAX_AGE } == true) {
            known
        } else {
            val answer = client.invoke(GetAppConfig(hash))
            fetched = timeSource.markNow()
            if (answer is TlAppConfig) {
                hash = answer.hash
                config = AppConfig((valueOf(answer.config) as? Map<*, *>).orEmpty().mapKeys { it.key.toString() })
            }
            config ?: AppConfig()
        }
        if (limited?.let { it.first === client && it.second == current } != true) {
            client.setDownloadQueueLimits(current.smallDownloadQueue, current.largeDownloadQueue)
            limited = client to current
        }
        current
    }

    private companion object {
        val MAX_AGE = 1.hours
    }
}

private fun valueOf(json: TypeJSONValue): Any? = when (json) {
    JsonNull -> null
    is JsonBool -> json.value
    is JsonNumber -> json.value
    is JsonString -> json.value
    is JsonArray -> json.value.map(::valueOf)
    is JsonObject -> json.value.associate { it.key to valueOf(it.value) }
}
