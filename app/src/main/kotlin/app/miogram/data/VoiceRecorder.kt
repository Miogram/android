package app.miogram.data

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class VoiceClip(val file: File, val durationMs: Long)

interface VoiceRecorder {
    fun start()

    fun maxAmplitude(): Int

    fun stop(): VoiceClip?

    fun cancel()
}

class MediaVoiceRecorder @Inject constructor(@ApplicationContext private val context: Context) : VoiceRecorder {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    override fun start() {
        cancel()
        val target = File(File(context.cacheDir, "recordings").apply { mkdirs() }, "voice_${System.currentTimeMillis()}.ogg")
        val started = MediaRecorder(context)
        try {
            started.setAudioSource(MediaRecorder.AudioSource.MIC)
            started.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            started.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            started.setAudioChannels(1)
            started.setAudioSamplingRate(48_000)
            started.setAudioEncodingBitRate(32_000)
            started.setOutputFile(target)
            started.prepare()
            started.start()
        } catch (e: Exception) {
            started.release()
            target.delete()
            throw e
        }
        recorder = started
        file = target
        startedAt = SystemClock.elapsedRealtime()
    }

    override fun maxAmplitude(): Int = recorder?.maxAmplitude ?: 0

    override fun stop(): VoiceClip? {
        val current = recorder ?: return null
        val target = file ?: return null
        recorder = null
        file = null
        val duration = SystemClock.elapsedRealtime() - startedAt
        val stopped = runCatching { current.stop() }.isSuccess
        current.release()
        if (!stopped || target.length() == 0L) {
            target.delete()
            return null
        }
        return VoiceClip(target, duration)
    }

    override fun cancel() {
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        file?.delete()
        recorder = null
        file = null
    }
}

const val WAVEFORM_SAMPLES = 100

fun reduceWaveform(amplitudes: List<Int>, size: Int = WAVEFORM_SAMPLES): List<Int> {
    if (amplitudes.isEmpty()) return emptyList()
    val peak = amplitudes.max()
    return List(size) { i ->
        val from = i * amplitudes.size / size
        val to = maxOf(from + 1, (i + 1) * amplitudes.size / size)
        if (peak <= 0) 0 else amplitudes.subList(from, to).max().coerceAtLeast(0) * 31 / peak
    }
}
