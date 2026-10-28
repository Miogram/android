package app.miogram.feature.chat

import app.miogram.data.VoiceRecorder
import app.miogram.data.reduceWaveform
import app.miogram.data.telegram.MessagesRepository
import app.miogram.music.MusicPlayer
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.MusicTrack
import cn.ellerotta.core.model.UiError
import cn.ellerotta.core.model.VoicePlayback
import cn.ellerotta.core.model.VoiceRecording
import cn.ellerotta.core.model.audioMedia
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class RecordedVoice(val file: File, val duration: Int, val waveform: List<Int>)

internal class ChatVoice(
    private val scope: CoroutineScope,
    private val dialogKey: String,
    private val recorder: VoiceRecorder,
    private val music: MusicPlayer,
    private val messages: MessagesRepository,
    private val shown: () -> List<MessageItem>,
    private val fail: (UiError) -> Unit,
) {
    private val recordingState = MutableStateFlow<VoiceRecording?>(null)
    private var recordingJob: Job? = null
    private val amplitudes = mutableListOf<Int>()

    val recording: StateFlow<VoiceRecording?> = recordingState.asStateFlow()

    val playback: StateFlow<VoicePlayback?> = music.music.map { it?.voicePlayback() }.stateIn(scope, SharingStarted.Eagerly, music.music.value?.voicePlayback())

    fun start() {
        if (recordingState.value != null) return
        try {
            recorder.start()
        } catch (e: Exception) {
            fail(UiError.Message(e.message ?: "cannot record"))
            return
        }
        music.pause()
        amplitudes.clear()
        val started = System.currentTimeMillis()
        recordingState.value = VoiceRecording(0, 0f)
        recordingJob = scope.launch {
            while (true) {
                delay(SAMPLE_PAUSE)
                val amplitude = recorder.maxAmplitude()
                amplitudes += amplitude
                recordingState.value = VoiceRecording(System.currentTimeMillis() - started, kotlin.math.sqrt(amplitude / MAX_AMPLITUDE).coerceIn(0f, 1f))
            }
        }
    }

    fun stop(send: Boolean): RecordedVoice? {
        if (recordingState.value == null) return null
        recordingJob?.cancel()
        recordingState.value = null
        if (!send) {
            recorder.cancel()
            return null
        }
        amplitudes += recorder.maxAmplitude()
        val clip = recorder.stop() ?: return null
        if (clip.durationMs < MIN_VOICE) {
            clip.file.delete()
            return null
        }
        return RecordedVoice(clip.file, ((clip.durationMs + 500) / 1000).toInt(), reduceWaveform(amplitudes))
    }

    fun toggle(voice: MediaItem) {
        val message = shown().firstOrNull { it.media?.key == voice.key }
            ?: shown().firstOrNull { it.article?.audioMedia()?.any { audio -> audio.key == voice.key } == true }
            ?: return
        val track = if (message.media?.key == voice.key) messages.voiceTrackOf(dialogKey, message) else messages.voiceTrackOf(dialogKey, message.copy(media = voice, mediaUnread = false))
        music.play(track ?: MusicTrack(dialogKey, message.id, voice))
    }

    fun cycleSpeed() = music.cycleSpeed()

    fun close() {
        if (recordingState.value != null) recorder.cancel()
    }

    private companion object {
        const val SAMPLE_PAUSE = 50L
        const val MIN_VOICE = 500L
        const val MAX_AMPLITUDE = 32767f
    }
}
