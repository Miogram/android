package app.miogram.feature.location

import androidx.lifecycle.viewModelScope
import app.miogram.data.telegram.AttachRepository
import app.miogram.data.telegram.UpdatesRepository
import app.miogram.feature.common.WorkViewModel
import app.miogram.location.ChatLiveLocations
import app.miogram.location.LiveLocations
import cn.ellerotta.core.model.LiveSharer
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel(assistedFactory = ChatLiveViewModel.Factory::class)
class ChatLiveViewModel @AssistedInject constructor(
    @Assisted private val dialogKey: String,
    private val attach: AttachRepository,
    updates: UpdatesRepository,
    private val live: LiveLocations,
) : WorkViewModel() {
    private val locations = ChatLiveLocations(dialogKey, attach, updates, viewModelScope)

    val sharers: StateFlow<List<LiveSharer>> = locations.sharers

    fun stop() {
        work {
            if (dialogKey in live.shares.value) {
                live.stop(dialogKey)
            } else {
                sharers.value.firstOrNull { it.self }?.let { own -> attach.stopLive(dialogKey, own.messageId, own.place) }
            }
        }
    }

    fun setAlert(radius: Int) {
        work {
            live.alert(dialogKey, radius, sharers.value.firstOrNull { it.self })
            locations.ownRadius(radius)
        }
    }

    fun share(title: String, seconds: Int, radius: Int?) {
        work {
            val failed = live.startHere(dialogKey, title, seconds, radius)
            if (failed != null) fail(failed) else locations.refresh()
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(dialogKey: String): ChatLiveViewModel
    }
}
