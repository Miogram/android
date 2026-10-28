package app.miogram.links

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class LinkRouter @Inject constructor() {
    private val pendingState = MutableStateFlow<String?>(null)

    val pending: StateFlow<String?> = pendingState.asStateFlow()

    fun submit(url: String) {
        if (url.isNotBlank()) pendingState.value = url
    }

    fun take(url: String): Boolean {
        var taken = false
        pendingState.update { current ->
            taken = current == url
            if (taken) null else current
        }
        return taken
    }
}
