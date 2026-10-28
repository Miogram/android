package app.miogram.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import cn.ellerotta.core.model.NetworkType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class NetworkMonitor @Inject constructor(@ApplicationContext context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val state = MutableStateFlow(manager.activeNetwork != null)
    private var current: Long? = manager.activeNetwork?.networkHandle

    val online: StateFlow<Boolean> = state.asStateFlow()

    fun type(): NetworkType? {
        val network = manager.activeNetwork ?: return null
        val capabilities = manager.getNetworkCapabilities(network) ?: return NetworkType.Mobile
        return when {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkType.Wifi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) -> NetworkType.Roaming
            else -> NetworkType.Mobile
        }
    }

    init {
        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = change(network.networkHandle, available = true)

                override fun onLost(network: Network) = change(network.networkHandle, available = false)
            },
        )
    }

    @Synchronized
    private fun change(handle: Long, available: Boolean) {
        if (available) {
            current = handle
            state.value = true
        } else if (handle == current) {
            current = null
            state.value = false
        }
    }
}
