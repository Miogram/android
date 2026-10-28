package app.miogram.data

import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import app.miogram.data.telegram.AppConfigRepository
import app.miogram.data.telegram.BotsRepository
import app.miogram.lock.DeviceAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class WebAppServices @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bots: BotsRepository,
    private val appConfig: AppConfigRepository,
    val store: WebAppStore,
    val auth: DeviceAuth,
    private val deviceLocation: DeviceLocation,
) {
    suspend fun canWrite(bot: String): Boolean = bots.canWrite(bot)

    suspend fun allowWrite(bot: String): Boolean = attempt { bots.allowWrite(bot) }

    suspend fun sharePhone(bot: String): Boolean = attempt { bots.sharePhone(bot) }

    suspend fun invokeCustomMethod(bot: String, method: String, params: String): Result<String> = bots.invokeCustomMethod(bot, method, params)

    suspend fun clipboardAllowed(bot: String): Boolean =
        bots.attachMenuBots().any { it.key == bot && !it.inactive } || bot.removePrefix("u").toLongOrNull()?.let { it in appConfig.config().whitelistedBots } == true

    fun clipboardText(): String? {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
        return clip.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    }

    fun biometryType(): String? {
        if (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) return null
        val packages = context.packageManager
        val finger = packages.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        val face = packages.hasSystemFeature(PackageManager.FEATURE_FACE)
        return when {
            finger && !face -> "finger"
            face && !finger -> "face"
            else -> "unknown"
        }
    }

    fun locationAvailable(): Boolean = deviceLocation.enabled()

    fun granted(permission: String): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    suspend fun location(): Location? = deviceLocation.current()

    private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}
