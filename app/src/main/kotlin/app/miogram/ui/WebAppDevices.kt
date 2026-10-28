package app.miogram.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import app.miogram.data.WebAppServices
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.WebAppDevice
import cn.ellerotta.core.ui.components.chat.WebAppLocation
import cn.ellerotta.core.ui.components.chat.WebAppPermission
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
internal fun rememberWebAppDevice(services: WebAppServices): WebAppDevice {
    val context = LocalContext.current
    val camera = rememberPermission(ActivityResultContracts.RequestPermission()) { it }
    val location = rememberPermission(ActivityResultContracts.RequestMultiplePermissions()) { answers -> answers.values.any { it } }
    return remember(services, context) {
        object : WebAppDevice {
            override suspend fun canWrite(bot: String) = services.canWrite(bot)

            override suspend fun allowWrite(bot: String) = services.allowWrite(bot)

            override suspend fun sharePhone(bot: String) = services.sharePhone(bot)

            override suspend fun invokeCustomMethod(bot: String, method: String, params: String) = services.invokeCustomMethod(bot, method, params)

            override suspend fun clipboardAllowed(bot: String) = services.clipboardAllowed(bot)

            override suspend fun clipboardText() = services.clipboardText()

            override suspend fun storage(bot: String, secure: Boolean) = services.store.storage(bot, secure)

            override suspend fun saveStorage(bot: String, secure: Boolean, values: Map<String, String>) = services.store.saveStorage(bot, secure, values)

            override suspend fun permission(bot: String, kind: WebAppPermission) = services.store.permission(bot, kind.name)

            override suspend fun setPermission(bot: String, kind: WebAppPermission, granted: Boolean) = services.store.setPermission(bot, kind.name, granted)

            override fun biometryType() = services.biometryType()

            override suspend fun deviceId() = services.store.deviceId()

            override suspend fun biometryToken(bot: String) = services.store.biometryToken(bot)

            override suspend fun saveBiometryToken(bot: String, token: String?) = services.store.saveBiometryToken(bot, token)

            override suspend fun authenticate(title: String, reason: String?): Boolean {
                val activity = context.fragmentActivity() ?: return false
                return suspendCancellableCoroutine { continuation ->
                    services.auth.authenticate(activity, activity.getString(R.string.web_app_biometry_title, title), reason) { confirmed ->
                        if (continuation.isActive) continuation.resume(confirmed)
                    }
                }
            }

            override fun locationAvailable() = services.locationAvailable()

            override suspend fun locationPermission(): Boolean =
                services.granted(Manifest.permission.ACCESS_FINE_LOCATION) || services.granted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                    location(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

            override suspend fun location(): WebAppLocation? = services.location()?.let {
                WebAppLocation(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude = it.altitude.takeIf { _ -> it.hasAltitude() },
                    course = it.bearing.toDouble().takeIf { _ -> it.hasBearing() },
                    speed = it.speed.toDouble().takeIf { _ -> it.hasSpeed() },
                    horizontalAccuracy = it.accuracy.toDouble().takeIf { _ -> it.hasAccuracy() },
                    verticalAccuracy = it.verticalAccuracyMeters.toDouble().takeIf { _ -> it.hasVerticalAccuracy() },
                    courseAccuracy = it.bearingAccuracyDegrees.toDouble().takeIf { _ -> it.hasBearingAccuracy() },
                    speedAccuracy = it.speedAccuracyMetersPerSecond.toDouble().takeIf { _ -> it.hasSpeedAccuracy() },
                )
            }

            override suspend fun cameraPermission(): Boolean = services.granted(Manifest.permission.CAMERA) || camera(Manifest.permission.CAMERA)
        }
    }
}

@Composable
private fun <I, O> rememberPermission(contract: ActivityResultContract<I, O>, granted: (O) -> Boolean): suspend (I) -> Boolean {
    val pending = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val launcher = rememberLauncherForActivityResult(contract) { answer -> pending[0]?.complete(granted(answer)) }
    return remember(launcher) {
        { input ->
            val answer = CompletableDeferred<Boolean>()
            pending[0]?.complete(false)
            pending[0] = answer
            launcher.launch(input)
            answer.await()
        }
    }
}

private tailrec fun Context.fragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.fragmentActivity()
    else -> null
}
