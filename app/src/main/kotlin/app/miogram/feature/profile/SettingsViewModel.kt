package app.miogram.feature.profile

import androidx.lifecycle.viewModelScope
import app.miogram.data.SettingsStore
import app.miogram.data.telegram.AuthRepository
import app.miogram.data.telegram.BotsRepository
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.feature.common.WorkViewModel
import app.miogram.push.PushHandler
import cn.ellerotta.core.model.Appearance
import cn.ellerotta.core.model.AttachMenuBot
import cn.ellerotta.core.model.ConfigSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val telegram: TelegramClientHolder,
    private val auth: AuthRepository,
    private val settings: SettingsStore,
    private val push: PushHandler,
    private val bots: BotsRepository,
) : WorkViewModel() {
    private val sessionsState = MutableStateFlow(auth.storedSessions()?.size)

    val sessions: StateFlow<Int?> = sessionsState.asStateFlow()
    val appearance: StateFlow<Appearance> = settings.appearance

    val autoSaveDownloads: StateFlow<Boolean> = settings.autoSaveDownloads
    val server: ConfigSummary? get() = telegram.server
    val libraryVersion: String get() = telegram.libraryVersion
    val layer: Int get() = telegram.layer

    private val appsState = MutableStateFlow<List<AttachMenuBot>>(emptyList())

    val apps: StateFlow<List<AttachMenuBot>> = appsState.asStateFlow()

    fun refresh() {
        refresh(sessionsState.value != null) { sessionsState.value = auth.sessions().size }
        viewModelScope.launch {
            try {
                appsState.value = bots.attachMenuBots().filter { it.sideMenu }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun removeApp(bot: AttachMenuBot) = work {
        bots.disableAttachBot(bot.key)
        appsState.value = bots.attachMenuBots().filter { it.sideMenu }
    }

    fun setAppearance(appearance: Appearance) = settings.setAppearance(appearance)

    fun setAutoSaveDownloads(on: Boolean) = settings.setAutoSaveDownloads(on)

    fun logOut(onLoggedOut: () -> Unit) = work {
        push.unregister()
        auth.logOut()
        onLoggedOut()
    }
}
