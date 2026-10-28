package app.miogram.feature.profile

import androidx.lifecycle.viewModelScope
import app.miogram.data.AvatarStore
import app.miogram.data.SettingsStore
import app.miogram.data.telegram.NotifySettingsRepository
import app.miogram.feature.common.WorkViewModel
import app.miogram.location.ProximityAlerts
import cn.ellerotta.core.model.NotificationSettingsState
import cn.ellerotta.core.model.NotifyScope
import cn.ellerotta.core.model.ScopeNotifySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val repository: NotifySettingsRepository,
    private val avatarStore: AvatarStore,
    private val settings: SettingsStore,
    private val proximityAlerts: ProximityAlerts,
) : WorkViewModel() {
    private val stateFlow = MutableStateFlow(repository.stored() ?: NotificationSettingsState())

    val state: StateFlow<NotificationSettingsState> = stateFlow.asStateFlow()
    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars

    val proximity: StateFlow<Boolean> = settings.proximityAlerts

    init {
        stateFlow.value.exceptions?.let { avatarStore.load(it.values.flatten().map { exception -> exception.avatarKey }) }
        refresh(stateFlow.value.scopes.isNotEmpty()) { load() }
        viewModelScope.launch { stateFlow.collect { if (it.scopes.isNotEmpty()) repository.remember(it) } }
    }

    private suspend fun load() {
        val scopes = repository.defaults()
        stateFlow.update { it.copy(scopes = scopes) }
        val exceptions = repository.exceptions()
        stateFlow.update { it.copy(exceptions = exceptions) }
        avatarStore.load(exceptions.values.flatten().map { it.avatarKey })
    }

    fun setDefault(scope: NotifyScope, settings: ScopeNotifySettings) {
        val before = stateFlow.value.scopes[scope]
        stateFlow.update { it.copy(scopes = it.scopes + (scope to settings)) }
        work {
            try {
                repository.setDefault(scope, settings)
            } catch (e: Exception) {
                before?.let { old -> stateFlow.update { it.copy(scopes = it.scopes + (scope to old)) } }
                throw e
            }
        }
    }

    fun setException(key: String, muteUntil: Int?) {
        val before = stateFlow.value.exceptions
        stateFlow.update { state ->
            state.copy(
                exceptions = state.exceptions?.mapValues { (_, list) ->
                    if (muteUntil == null) list.filter { it.key != key } else list.map { if (it.key == key) it.copy(muteUntil = muteUntil) else it }
                },
            )
        }
        work {
            try {
                repository.setException(key, muteUntil)
            } catch (e: Exception) {
                stateFlow.update { it.copy(exceptions = before) }
                throw e
            }
        }
    }

    fun reset() = work {
        val scopes = repository.reset()
        stateFlow.update { it.copy(scopes = scopes, exceptions = emptyMap()) }
    }

    fun setProximity(on: Boolean) = settings.setProximityAlerts(on)

    fun proximityBlocked(): Boolean = proximityAlerts.blocked()

    fun proximityChannel(): String = proximityAlerts.channel()
}
