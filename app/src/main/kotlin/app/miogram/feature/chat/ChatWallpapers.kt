package app.miogram.feature.chat

import app.miogram.data.telegram.WallpapersRepository
import cn.ellerotta.core.model.ChatWallpaper
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.Wallpaper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ChatWallpapers(
    private val scope: CoroutineScope,
    private val dialogKey: String,
    private val repository: WallpapersRepository,
    private val work: (suspend () -> Unit) -> Job,
) {
    val allowsOwn: Boolean = repository.allowsOwn(dialogKey)

    val shown: StateFlow<ChatWallpaper?> = combine(repository.chats, repository.chosen) { chats, chosen -> chats[dialogKey] ?: chosen }
        .stateIn(scope, SharingStarted.Eagerly, repository.chats.value[dialogKey] ?: repository.chosen.value)

    private val previewsState = MutableStateFlow<Map<String, String>>(emptyMap())

    val previews: StateFlow<Map<String, String>> = previewsState.asStateFlow()

    private val asked: MutableSet<String> = HashSet()

    fun load() {
        if (allowsOwn) quietly { repository.loadChat(dialogKey) }
    }

    fun changed() = quietly { repository.showChat(dialogKey) }

    fun loadPreview(wallpaper: Wallpaper) {
        if (!asked.add(wallpaper.key)) return
        quietly {
            val path = repository.preview(wallpaper.key) ?: return@quietly
            previewsState.update { it + (wallpaper.key to path) }
        }
    }

    fun apply(message: MessageItem) {
        val set = message.wallpaper ?: return
        work { repository.applyFromMessage(dialogKey, message.id, set.wallpaper) }
    }

    private fun quietly(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }
}
