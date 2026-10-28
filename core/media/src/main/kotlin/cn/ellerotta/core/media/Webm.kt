package cn.ellerotta.core.media

import java.io.IOException

class WebmException(message: String) : IOException(message)

class WebmFrame(val timestampMs: Long, val color: ByteArray, val alpha: ByteArray?, val keyframe: Boolean)

class WebmVideo(
    val width: Int,
    val height: Int,
    val alphaMode: Boolean,
    val frameDurationMs: Long,
    val durationMs: Long,
    val frames: List<WebmFrame>,
) {
    val hasAlpha: Boolean = frames.all { it.alpha != null }
}

object Webm {
    fun parse(bytes: ByteArray): WebmVideo = WebmParser(bytes).parse()
}

private const val EBML = 0x1A45DFA3L
private const val DOC_TYPE = 0x4282L
private const val SEGMENT = 0x18538067L
private const val SEEK_HEAD = 0x114D9B74L
private const val INFO = 0x1549A966L
private const val TIMECODE_SCALE = 0x2AD7B1L
private const val TRACKS = 0x1654AE6BL
private const val TRACK_ENTRY = 0xAEL
private const val TRACK_NUMBER = 0xD7L
private const val TRACK_TYPE = 0x83L
private const val CODEC_ID = 0x86L
private const val DEFAULT_DURATION = 0x23E383L
private const val CONTENT_ENCODINGS = 0x6D80L
private const val VIDEO = 0xE0L
private const val PIXEL_WIDTH = 0xB0L
private const val PIXEL_HEIGHT = 0xBAL
private const val ALPHA_MODE = 0x53C0L
private const val CLUSTER = 0x1F43B675L
private const val TIMECODE = 0xE7L
private const val SIMPLE_BLOCK = 0xA3L
private const val BLOCK_GROUP = 0xA0L
private const val BLOCK = 0xA1L
private const val REFERENCE_BLOCK = 0xFBL
private const val BLOCK_ADDITIONS = 0x75A1L
private const val BLOCK_MORE = 0xA6L
private const val BLOCK_ADD_ID = 0xEEL
private const val BLOCK_ADDITIONAL = 0xA5L
private const val CUES = 0x1C53BB6BL
private const val CHAPTERS = 0x1043A770L
private const val TAGS = 0x1254C367L
private const val ATTACHMENTS = 0x1941A469L

private const val UNKNOWN = -1L
private const val TRACK_TYPE_VIDEO = 1L

private val TOP_LEVEL = setOf(EBML, SEGMENT, SEEK_HEAD, INFO, TRACKS, CLUSTER, CUES, CHAPTERS, TAGS, ATTACHMENTS)

private class WebmParser(private val b: ByteArray) {
    private var pos = 0
    private var id = 0L
    private var size = 0L
    private var scaleNs = 1_000_000L

    private var trackNumber = UNKNOWN
    private var width = 0
    private var height = 0
    private var alphaMode = false
    private var defaultDurationNs = 0L
    private val frames = ArrayList<WebmFrame>()

    fun parse(): WebmVideo {
        if (b.size < 4 || readId() != EBML) throw WebmException("not an ebml file")
        pos = 0
        header(b.size)
        val headerEnd = end(b.size)
        val docType = findString(pos, headerEnd, DOC_TYPE)
        if (docType != null && docType != "webm" && docType != "matroska") throw WebmException("doc type $docType")
        pos = headerEnd
        while (pos < b.size) {
            header(b.size)
            if (id == SEGMENT) {
                segment(pos, if (size == UNKNOWN) b.size else (pos + size).coerceAtMost(b.size.toLong()).toInt())
                break
            }
            pos = end(b.size)
        }
        if (trackNumber == UNKNOWN) throw WebmException("no video track")
        if (frames.isEmpty()) throw WebmException("no frames")
        if (!frames[0].keyframe) throw WebmException("the first frame is not a keyframe")
        val frameDuration = when {
            defaultDurationNs > 0 -> defaultDurationNs / 1_000_000
            frames.size > 1 -> (frames.last().timestampMs - frames[0].timestampMs) / (frames.size - 1)
            else -> 33
        }.coerceAtLeast(1)
        return WebmVideo(width, height, alphaMode, frameDuration, frames.last().timestampMs + frameDuration, frames)
    }

    private fun segment(start: Int, end: Int) {
        pos = start
        while (pos < end) {
            header(end)
            if (id == CLUSTER) {
                if (trackNumber == UNKNOWN) throw WebmException("a cluster before the tracks")
                if (size == UNKNOWN) {
                    cluster(pos, end)
                } else {
                    val next = end(end)
                    cluster(pos, next)
                    pos = next
                }
                continue
            }
            val next = end(end)
            when (id) {
                INFO -> info(pos, next)
                TRACKS -> tracks(pos, next)
            }
            pos = next
        }
    }

    private fun info(start: Int, end: Int) {
        pos = start
        while (pos < end) {
            header(end)
            if (id == TIMECODE_SCALE) scaleNs = readUInt().takeIf { it > 0 } ?: throw WebmException("timecode scale 0")
            pos = end(end)
        }
    }

    private fun tracks(start: Int, end: Int) {
        pos = start
        while (pos < end) {
            header(end)
            val next = end(end)
            if (id == TRACK_ENTRY && trackNumber == UNKNOWN) trackEntry(pos, next)
            pos = next
        }
    }

    private fun trackEntry(start: Int, end: Int) {
        var number = UNKNOWN
        var type = 0L
        var codec: String? = null
        var encoded = false
        var w = 0
        var h = 0
        var alpha = false
        var duration = 0L
        pos = start
        while (pos < end) {
            header(end)
            val next = end(end)
            when (id) {
                TRACK_NUMBER -> number = readUInt()
                TRACK_TYPE -> type = readUInt()
                CODEC_ID -> codec = readString()
                DEFAULT_DURATION -> duration = readUInt()
                CONTENT_ENCODINGS -> encoded = true
                VIDEO -> {
                    while (pos < next) {
                        header(next)
                        when (id) {
                            PIXEL_WIDTH -> w = readUInt().toInt()
                            PIXEL_HEIGHT -> h = readUInt().toInt()
                            ALPHA_MODE -> alpha = readUInt() == 1L
                        }
                        pos = end(next)
                    }
                }
            }
            pos = next
        }
        if (type != TRACK_TYPE_VIDEO) return
        if (codec != "V_VP9") throw WebmException("unsupported codec $codec")
        if (encoded) throw WebmException("compressed or encrypted frames")
        if (w <= 0 || h <= 0) throw WebmException("no frame size")
        trackNumber = number
        width = w
        height = h
        alphaMode = alpha
        defaultDurationNs = duration
    }

    private fun cluster(start: Int, end: Int) {
        var time = 0L
        pos = start
        while (pos < end) {
            val elementStart = pos
            header(end)
            if (id in TOP_LEVEL) {
                pos = elementStart
                return
            }
            val next = end(end)
            when (id) {
                TIMECODE -> time = readUInt()
                SIMPLE_BLOCK -> block(pos, next, time, null)
                BLOCK_GROUP -> blockGroup(pos, next, time)
            }
            pos = next
        }
    }

    private fun blockGroup(start: Int, end: Int, time: Long) {
        var blockStart = -1
        var blockEnd = -1
        var referenced = false
        var alpha: ByteArray? = null
        pos = start
        while (pos < end) {
            header(end)
            val next = end(end)
            when (id) {
                BLOCK -> {
                    blockStart = pos
                    blockEnd = next
                }
                REFERENCE_BLOCK -> referenced = true
                BLOCK_ADDITIONS -> alpha = blockAdditional(pos, next)
            }
            pos = next
        }
        if (blockStart >= 0) block(blockStart, blockEnd, time, BlockGroupInfo(!referenced, alpha))
    }

    private fun blockAdditional(start: Int, end: Int): ByteArray? {
        var found: ByteArray? = null
        pos = start
        while (pos < end) {
            header(end)
            val next = end(end)
            if (id == BLOCK_MORE) {
                var addId = 1L
                var data: ByteArray? = null
                while (pos < next) {
                    header(next)
                    when (id) {
                        BLOCK_ADD_ID -> addId = readUInt()
                        BLOCK_ADDITIONAL -> data = b.copyOfRange(pos, end(next))
                    }
                    pos = end(next)
                }
                if (addId == 1L && data != null) found = data
            }
            pos = next
        }
        return found
    }

    private class BlockGroupInfo(val keyframe: Boolean, val alpha: ByteArray?)

    private fun block(start: Int, end: Int, clusterTime: Long, group: BlockGroupInfo?) {
        pos = start
        val track = readVint(end, masked = true)
        if (track != trackNumber) return
        if (pos + 3 > end) throw WebmException("truncated block")
        val relative = ((b[pos].toInt() shl 8) or (b[pos + 1].toInt() and 0xff)).toShort()
        val flags = b[pos + 2].toInt()
        if (flags and 0x06 != 0) throw WebmException("laced video blocks")
        val keyframe = group?.keyframe ?: (flags and 0x80 != 0)
        val timestampMs = (clusterTime + relative) * scaleNs / 1_000_000
        frames += WebmFrame(timestampMs, b.copyOfRange(pos + 3, end), group?.alpha, keyframe)
    }

    private fun header(limit: Int) {
        id = readId()
        size = readVint(limit, masked = true, unknownAllowed = true)
    }

    private fun end(limit: Int): Int {
        if (size == UNKNOWN) throw WebmException("element ${id.toString(16)} of unknown size")
        if (pos + size > limit) throw WebmException("element ${id.toString(16)} past its parent")
        return (pos + size).toInt()
    }

    private fun readId(): Long {
        if (pos >= b.size) throw WebmException("truncated element id")
        val first = b[pos].toInt() and 0xff
        val length = Integer.numberOfLeadingZeros(first) - 23
        if (first == 0 || length > 4) throw WebmException("bad element id")
        return readBytes(length, b.size)
    }

    private fun readVint(limit: Int, masked: Boolean, unknownAllowed: Boolean = false): Long {
        if (pos >= limit) throw WebmException("truncated number")
        val first = b[pos].toInt() and 0xff
        if (first == 0) throw WebmException("bad number")
        val length = Integer.numberOfLeadingZeros(first) - 23
        val raw = readBytes(length, limit)
        if (!masked) return raw
        val bits = 7 * length
        val value = raw and ((1L shl bits) - 1)
        if (value == (1L shl bits) - 1) {
            if (unknownAllowed) return UNKNOWN
            throw WebmException("reserved number")
        }
        return value
    }

    private fun readBytes(length: Int, limit: Int): Long {
        if (pos + length > limit) throw WebmException("truncated")
        var value = 0L
        repeat(length) { value = (value shl 8) or (b[pos++].toLong() and 0xff) }
        return value
    }

    private fun readUInt(): Long {
        if (size !in 0..8) throw WebmException("integer of $size bytes")
        val start = pos
        val value = readBytes(size.toInt(), b.size)
        pos = start
        return value
    }

    private fun readString(): String {
        if (size == UNKNOWN || pos + size > b.size) throw WebmException("truncated string")
        return String(b, pos, size.toInt(), Charsets.US_ASCII).trimEnd('\u0000')
    }

    private fun findString(start: Int, end: Int, wanted: Long): String? {
        pos = start
        while (pos < end) {
            header(end)
            if (id == wanted) return readString()
            pos = end(end)
        }
        return null
    }
}
