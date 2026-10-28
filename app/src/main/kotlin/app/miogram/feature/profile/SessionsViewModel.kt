package app.miogram.feature.profile

import app.miogram.data.telegram.AuthRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.SessionInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SessionsViewModel @Inject constructor(private val repository: AuthRepository) : WorkViewModel() {
    private val sessionsState = MutableStateFlow(repository.storedSessions())

    val sessions: StateFlow<List<SessionInfo>?> = sessionsState.asStateFlow()

    init {
        refresh(sessionsState.value != null) { load() }
    }

    private suspend fun load() {
        sessionsState.value = repository.sessions()
    }

    fun terminate(session: SessionInfo) = work {
        repository.terminateSession(session.hash)
        load()
    }

    fun terminateOthers() = work {
        repository.terminateOtherSessions()
        load()
    }
}
