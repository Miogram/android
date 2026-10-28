package app.miogram.lock

import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import app.miogram.data.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AppLock @Inject constructor(private val settings: SettingsStore, private val auth: DeviceAuth) {
    private val lockedState = MutableStateFlow(settings.appPrivacy.value.lock)
    private var backgroundAt: Long? = null
    @Volatile private var prompting = false

    val locked: StateFlow<Boolean> = lockedState.asStateFlow()

    fun onBackground() {
        if (!prompting) backgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        val since = backgroundAt ?: return
        backgroundAt = null
        val privacy = settings.appPrivacy.value
        if (privacy.lock && SystemClock.elapsedRealtime() - since >= privacy.lockAfterMinutes * 60_000L) lockedState.value = true
    }

    fun unlock(activity: FragmentActivity, title: String) {
        if (prompting || !lockedState.value) return
        prompting = true
        auth.authenticate(activity, title) { confirmed ->
            prompting = false
            if (confirmed) lockedState.value = false
        }
    }
}
