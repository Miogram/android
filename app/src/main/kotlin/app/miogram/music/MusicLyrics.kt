package app.miogram.music

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import java.nio.charset.Charset

@OptIn(UnstableApi::class)
fun lyricsOf(tracks: Tracks): String? {
    for (group in tracks.groups) {
        for (i in 0 until group.length) {
            val metadata = group.getTrackFormat(i).metadata ?: continue
            lyricsOf(metadata)?.let { return it }
        }
    }
    return null
}

@OptIn(UnstableApi::class)
internal fun lyricsOf(metadata: Metadata): String? {
    for (i in 0 until metadata.length()) {
        val text = when (val entry = metadata.get(i)) {
            is BinaryFrame -> if (entry.id == "USLT") usltText(entry.data) else null
            is TextInformationFrame -> entry.values.firstOrNull()?.takeIf { entry.id == "USLT" || entry.id == "TXXX" && entry.description?.uppercase() in LYRICS_NAMES }
            is VorbisComment -> entry.value.takeIf { entry.key.uppercase() in LYRICS_NAMES }
            else -> null
        }
        text?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.replace("\r\n", "\n").replace('\r', '\n') }
    }
    return null
}

internal fun usltText(data: ByteArray): String? {
    if (data.size < 5) return null
    val charset: Charset = when (data[0].toInt()) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> return null
    }
    val wide = data[0].toInt() == 1 || data[0].toInt() == 2
    var at = 4
    if (wide) {
        while (at + 1 < data.size && !(data[at].toInt() == 0 && data[at + 1].toInt() == 0)) at += 2
        at += 2
    } else {
        while (at < data.size && data[at].toInt() != 0) at++
        at += 1
    }
    if (at >= data.size) return null
    var end = data.size
    while (end > at && data[end - 1].toInt() == 0) end--
    if (wide && (end - at) % 2 == 1) end++
    return String(data, at, end - at, charset)
}

private val LYRICS_NAMES = setOf("LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS")
