package app.miogram

import app.miogram.data.reduceWaveform
import app.miogram.data.telegram.documentItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.DocumentAttributeAudio
import cn.ellerotta.ktproto.api.types.MessageMediaDocument
import cn.ellerotta.ktproto.client.encodeWaveform
import kotlin.test.assertEquals
import org.junit.Test

class VoiceTest {
    @Test
    fun amplitudesBecomeHundredSamplesScaledByThePeak() {
        assertEquals(emptyList(), reduceWaveform(emptyList()))
        assertEquals(listOf(0, 15, 31, 7), reduceWaveform(listOf(0, 16_000, 32_000, 8_000), size = 4))
        val rising = reduceWaveform(List(1_000) { it })
        assertEquals(100, rising.size)
        assertEquals(List(100) { (10 * it + 9) * 31 / 999 }, rising)
        assertEquals(List(50) { 15 } + List(50) { 31 }, reduceWaveform(listOf(10, 20)))
        assertEquals(List(100) { 0 }, reduceWaveform(listOf(0, 0, 0)))
    }

    @Test
    fun voiceDocumentMapsToDurationAndWaveform() {
        val samples = List(100) { (it * 7) % 32 }
        val audio = DocumentAttributeAudio(voice = true, duration = 12, waveform = encodeWaveform(samples.toIntArray()))
        val voice = document(41, "audio/ogg", audio)
        val item = documentItem(MessageMediaDocument(document = voice), voice)
        assertEquals(MediaKind.Voice, item.kind)
        assertEquals("doc41", item.key)
        assertEquals("voice_41.ogg", item.fileName)
        assertEquals(12, item.duration)
        assertEquals(samples, item.waveform)
        assertEquals(3_000L, item.size)
        assertEquals(2, item.dcId)

        val song = document(42, "audio/mpeg", DocumentAttributeAudio(duration = 200, title = "song", performer = "band"))
        val music = documentItem(MessageMediaDocument(document = song), song)
        assertEquals(MediaKind.Music, music.kind)
        assertEquals(200, music.duration)
        assertEquals("song", music.title)
        assertEquals("band", music.performer)
        assertEquals(emptyList(), music.waveform)
        assertEquals("file_42.mp3", music.fileName)
    }

    private fun document(id: Long, mimeType: String, audio: DocumentAttributeAudio) = Document(
        id = id,
        accessHash = 1,
        fileReference = byteArrayOf(1),
        date = FakeTelegram.NOW,
        mimeType = mimeType,
        size = 3_000,
        dcId = 2,
        attributes = listOf(audio),
    )
}
