package app.miogram.data

import app.miogram.data.telegram.MediaRepository
import app.miogram.di.ApplicationScope
import cn.ellerotta.core.model.bigPhotoKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvatarStore @Inject constructor(
    private val media: MediaRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    private val requested = ConcurrentHashMap.newKeySet<String>()

    val avatars: StateFlow<Map<String, ByteArray>> = state.asStateFlow()

    fun load(keys: Iterable<String?>) = keys.filterNotNull().forEach { key -> load(key, key, big = false) }

    fun loadBig(keys: Iterable<String?>) = keys.filterNotNull().forEach { key -> load(bigPhotoKey(key), key, big = true) }

    private fun load(stateKey: String, key: String, big: Boolean) {
        if (!requested.add(stateKey)) return
        scope.launch {
            val bytes = runCatching { media.avatar(key, big) }.getOrNull()
            if (bytes == null) requested.remove(stateKey) else state.update { it + (stateKey to bytes) }
        }
    }

    fun clear() {
        requested.clear()
        state.value = emptyMap()
    }
}
