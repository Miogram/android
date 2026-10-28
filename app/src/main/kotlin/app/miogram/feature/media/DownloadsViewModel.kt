package app.miogram.feature.media

import androidx.lifecycle.viewModelScope
import app.miogram.data.DownloadsSaver
import app.miogram.data.SavedFile
import app.miogram.data.telegram.PremiumSpeedNotice
import app.miogram.data.telegram.PremiumSpeedNotices
import app.miogram.feature.common.WorkViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadsViewModel @Inject constructor(saver: DownloadsSaver, premium: PremiumSpeedNotices) : WorkViewModel() {
    private val savedState = MutableStateFlow<SavedFile?>(null)
    private val premiumState = MutableStateFlow<PremiumSpeedNotice?>(null)

    val saved: StateFlow<SavedFile?> = savedState.asStateFlow()

    val premiumNotice: StateFlow<PremiumSpeedNotice?> = premiumState.asStateFlow()

    init {
        viewModelScope.launch { saver.saved.collect { savedState.value = it } }
        viewModelScope.launch { premium.notices.collect { premiumState.value = it } }
    }

    fun dismissPremium() {
        premiumState.value = null
    }

    fun dismiss() {
        savedState.value = null
    }
}
