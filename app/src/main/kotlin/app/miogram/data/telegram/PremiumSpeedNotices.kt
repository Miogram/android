package app.miogram.data.telegram

import cn.ellerotta.core.model.AppConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

data class PremiumSpeedNotice(val upload: Boolean, val speedup: Int)

@Singleton
class PremiumSpeedNotices @Inject constructor(private val telegram: TelegramClientHolder, private val appConfig: AppConfigRepository) {
    private var shown: TimeMark? = null

    internal var timeSource: TimeSource = TimeSource.Monotonic

    val notices: Flow<PremiumSpeedNotice> = telegram.premiumSpeedLimits.mapNotNull { event ->
        val config = try {
            appConfig.config()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val period = (config?.premiumSpeedupNotifyPeriod ?: AppConfig.DEFAULT_PREMIUM_SPEEDUP_NOTIFY_PERIOD).seconds
        synchronized(this) {
            if (shown?.let { it.elapsedNow() < period } == true) return@mapNotNull null
            shown = timeSource.markNow()
        }
        PremiumSpeedNotice(event.upload, config?.premiumSpeedup(event.upload) ?: AppConfig.DEFAULT_PREMIUM_SPEEDUP)
    }
}
