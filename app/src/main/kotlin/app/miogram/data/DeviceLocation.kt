package app.miogram.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class DeviceLocation @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager: LocationManager? get() = context.getSystemService(LocationManager::class.java)

    fun enabled(): Boolean = manager?.isLocationEnabled == true

    fun permitted(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    @SuppressLint("MissingPermission")
    suspend fun current(): Location? {
        val manager = manager ?: return null
        val provider = provider(manager) ?: return null
        val current = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { continuation.resume(it) }
            }
        }
        return current ?: manager.getLastKnownLocation(provider)
    }

    @SuppressLint("MissingPermission")
    fun updates(intervalMs: Long, minMetres: Float): Flow<Location> = callbackFlow {
        val manager = manager
        val provider = manager?.let(::provider)
        if (manager == null || provider == null) {
            close()
            return@callbackFlow
        }
        val listener = LocationListener { trySend(it) }
        manager.requestLocationUpdates(provider, intervalMs, minMetres, context.mainExecutor, listener)
        awaitClose { manager.removeUpdates(listener) }
    }

    private fun provider(manager: LocationManager): String? {
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!fine && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        val providers = listOfNotNull(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER.takeIf { fine }, LocationManager.NETWORK_PROVIDER)
        return providers.firstOrNull { manager.allProviders.contains(it) && manager.isProviderEnabled(it) }
    }

    private fun granted(permission: String): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
