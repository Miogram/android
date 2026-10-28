package app.miogram.feature.chat

import cn.ellerotta.core.model.ChatAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class ChatTyping(
    private val scope: CoroutineScope,
    private val send: suspend (ChatAction) -> Unit,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val queue = Channel<ChatAction>(Channel.UNLIMITED)
    private var last: ChatAction? = null
    private var lastAt = 0L
    private var recording: Job? = null

    init {
        scope.launch {
            for (action in queue) {
                try {
                    send(action)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
    }

    fun typed(text: String) {
        if (text.isEmpty()) cancel() else post(ChatAction.Typing)
    }

    fun startRecording() {
        if (recording?.isActive == true) return
        recording = scope.launch {
            while (true) {
                emit(ChatAction.RecordVoice)
                delay(REPEAT)
            }
        }
    }

    fun stopRecording() {
        recording?.cancel()
        recording = null
    }

    fun uploading(action: ChatAction.Upload) = post(action)

    fun cancel() {
        stopRecording()
        if (last == null) return
        emit(ChatAction.Cancel)
        last = null
    }

    private fun post(action: ChatAction) {
        val time = now()
        val previous = last
        if (previous != null && kindOf(previous) == kindOf(action) && time - lastAt < REPEAT) return
        emit(action)
        lastAt = time
    }

    private fun emit(action: ChatAction) {
        if (action != ChatAction.Cancel) last = action
        queue.trySend(action)
    }

    private fun kindOf(action: ChatAction): Any = if (action is ChatAction.Upload) action.kind else action

    companion object {
        const val REPEAT = 5_000L
    }
}
