package app.miogram.feature.profile

import app.miogram.data.SettingsStore
import androidx.lifecycle.viewModelScope
import app.miogram.data.telegram.PrivacyRepository
import app.miogram.data.telegram.SafetyRepository
import app.miogram.data.telegram.TwoStepRepository
import app.miogram.feature.common.WorkViewModel
import app.miogram.lock.DeviceAuth
import cn.ellerotta.core.model.AppPrivacy
import cn.ellerotta.core.model.GlobalPrivacy
import cn.ellerotta.core.model.PrivacyBase
import cn.ellerotta.core.model.PrivacyKey
import cn.ellerotta.core.model.PrivacyState
import cn.ellerotta.core.model.UiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val repository: PrivacyRepository,
    private val settings: SettingsStore,
    private val deviceAuth: DeviceAuth,
    private val safety: SafetyRepository,
    private val twoStep: TwoStepRepository,
) : WorkViewModel() {
    private val stateFlow = MutableStateFlow(repository.stored() ?: PrivacyState())

    val state: StateFlow<PrivacyState> = stateFlow.asStateFlow()

    val app: StateFlow<AppPrivacy> = settings.appPrivacy

    fun setApp(privacy: AppPrivacy) {
        if (privacy.lock && !app.value.lock && !deviceAuth.available()) {
            fail(UiError.NoScreenLock)
            return
        }
        settings.setAppPrivacy(privacy)
    }

    init {
        refresh(stateFlow.value.settings.isNotEmpty()) { load() }
        viewModelScope.launch { stateFlow.collect { if (it.settings.isNotEmpty()) repository.remember(it) } }
        viewModelScope.launch { twoStep.enabled.collect { enabled -> stateFlow.update { it.copy(twoStepEnabled = enabled ?: it.twoStepEnabled) } } }
        viewModelScope.launch {
            safety.changes.collect { runCatching { safety.blockedCount() }.onSuccess { count -> stateFlow.update { it.copy(blockedCount = count) } } }
        }
    }

    private suspend fun load(): Unit = coroutineScope {
        val keys = PrivacyKey.entries.map { key -> async { runCatching { repository.privacy(key) } } }
        val global = async { repository.globalPrivacy() }
        val ttl = async { repository.accountTtl() }
        val blockedCount = async { runCatching { safety.blockedCount() }.getOrNull() }
        launch { runCatching { twoStep.status() } }
        val results = keys.awaitAll()
        stateFlow.update { it.copy(settings = results.mapNotNull { result -> result.getOrNull() }) }
        stateFlow.update { it.copy(global = global.await(), accountTtlDays = ttl.await(), blockedCount = blockedCount.await()) }
        results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
        Unit
    }

    fun setPrivacy(key: PrivacyKey, base: PrivacyBase) = work {
        stateFlow.update { state -> state.copy(settings = state.settings.map { if (it.key == key) it.copy(base = base) else it }) }
        try {
            val saved = repository.setPrivacy(key, base)
            stateFlow.update { state -> state.copy(settings = state.settings.map { if (it.key == key) saved else it }) }
        } catch (e: Exception) {
            runCatching { repository.privacy(key) }.onSuccess { real ->
                stateFlow.update { state -> state.copy(settings = state.settings.map { if (it.key == key) real else it }) }
            }
            throw e
        }
    }

    fun setGlobal(global: GlobalPrivacy) = work {
        val before = stateFlow.value.global
        stateFlow.update { it.copy(global = global) }
        try {
            val saved = repository.setGlobalPrivacy(global)
            stateFlow.update { it.copy(global = saved) }
        } catch (e: Exception) {
            stateFlow.update { it.copy(global = before) }
            throw e
        }
    }

    fun setAccountTtl(days: Int) = work {
        val before = stateFlow.value.accountTtlDays
        stateFlow.update { it.copy(accountTtlDays = days) }
        try {
            repository.setAccountTtl(days)
        } catch (e: Exception) {
            stateFlow.update { it.copy(accountTtlDays = before) }
            throw e
        }
    }
}
