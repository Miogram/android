package app.miogram.feature.profile

import app.miogram.data.SettingsStore
import app.miogram.data.StorageRepository
import app.miogram.feature.common.WorkViewModel
import androidx.lifecycle.viewModelScope
import cn.ellerotta.core.model.AutoDownloadSettings
import cn.ellerotta.core.model.DataStorageState
import cn.ellerotta.core.model.StorageCategory
import cn.ellerotta.core.model.StorageUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class DataStorageViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val storage: StorageRepository,
) : WorkViewModel() {
    private val usage = MutableStateFlow(storage.lastUsage)
    private val clearing = MutableStateFlow<Set<StorageCategory>>(emptySet())

    val state: StateFlow<DataStorageState> = combine(settings.autoDownload, usage, clearing) { auto, usage, clearing -> DataStorageState(auto, usage, clearing) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataStorageState(settings.autoDownload.value, usage.value))

    init {
        refresh(usage.value != null) { usage.value = storage.usage() }
    }

    fun setAutoDownload(value: AutoDownloadSettings) = settings.setAutoDownload(value)

    fun clear(category: StorageCategory?) {
        val categories = category?.let(::setOf) ?: StorageCategory.entries.toSet()
        clearing.update { it + categories }
        work {
            try {
                storage.clear(category)
            } finally {
                clearing.update { it - categories }
                usage.value = storage.usage()
            }
        }
    }
}
