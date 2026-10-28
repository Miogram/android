package cn.ellerotta.core.ui.components.common

import android.graphics.ImageFormat
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import cn.ellerotta.core.media.Plane
import cn.ellerotta.core.media.WebmFrame
import cn.ellerotta.core.media.WebmVideo
import cn.ellerotta.core.media.yuvaToArgb
import java.io.Closeable
import java.io.IOException

private const val STALL_NANOS = 2_000_000_000L

private const val POLL_MICROS = 5_000L

private val vp9DecoderName: String? by lazy {
    val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
        !info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_VP9, ignoreCase = true) }
    }
    (decoders.firstOrNull { it.isSoftwareOnly } ?: decoders.firstOrNull())?.name
}

internal class VideoStickerDecoder(private val video: WebmVideo, val width: Int, val height: Int) : Closeable {
    val pixels = IntArray(width * height)
    private val color: Lane
    private val alpha: Lane?
    private val y = Plane()
    private val u = Plane()
    private val v = Plane()
    private val a = Plane()

    init {
        color = Lane(video, WebmFrame::color)
        alpha = try {
            if (video.hasAlpha) Lane(video) { it.alpha!! } else null
        } catch (e: Exception) {
            color.close()
            throw e
        }
    }

    fun next(convert: Boolean): Int {
        val deadline = System.nanoTime() + STALL_NANOS
        while (true) {
            color.feed()
            alpha?.feed()
            color.poll()
            alpha?.poll()
            if (color.ended && (alpha == null || alpha.ended)) {
                color.restart()
                alpha?.restart()
                continue
            }
            if (color.ready && (alpha == null || alpha.ready)) {
                if (alpha != null && alpha.pts != color.pts) {
                    if (alpha.pts < color.pts) alpha.release() else color.release()
                    continue
                }
                val index = color.pts.toInt()
                if (convert) convert()
                color.release()
                alpha?.release()
                return index
            }
            if (alpha != null) {
                if (color.ended && alpha.ready) alpha.release()
                if (alpha.ended && color.ready) color.release()
            }
            if (System.nanoTime() > deadline) throw IOException("vp9 decoder stalled")
        }
    }

    private fun convert() {
        val colorImage = color.image()
        try {
            val crop = colorImage.cropRect
            val planes = colorImage.planes
            set(y, planes[0], crop.left, crop.top)
            set(u, planes[1], crop.left / 2, crop.top / 2)
            set(v, planes[2], crop.left / 2, crop.top / 2)
            val alphaImage = alpha?.image()
            try {
                val alphaPlane = alphaImage?.let { set(a, it.planes[0], it.cropRect.left, it.cropRect.top) }
                yuvaToArgb(crop.width(), crop.height(), y, u, v, alphaPlane, pixels, width, height)
            } finally {
                alphaImage?.close()
            }
        } finally {
            colorImage.close()
        }
    }

    private fun set(target: Plane, plane: Image.Plane, left: Int, top: Int): Plane {
        val buffer = plane.buffer
        return target.set(buffer, buffer.position() + top * plane.rowStride + left * plane.pixelStride, plane.rowStride, plane.pixelStride)
    }

    override fun close() {
        color.close()
        alpha?.close()
    }

    private class Lane(video: WebmVideo, private val data: (WebmFrame) -> ByteArray) : Closeable {
        private val frames = video.frames
        private val codec = MediaCodec.createByCodecName(vp9DecoderName ?: throw IOException("no vp9 decoder"))
        private val info = MediaCodec.BufferInfo()
        private var nextInput = 0
        private var inputDone = false
        private var output = -1
        private var outputEos = false

        var ended = false
            private set

        var pts = -1L
            private set

        val ready: Boolean get() = output >= 0

        init {
            try {
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_VP9, video.width, video.height)
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                codec.configure(format, null, null, 0)
                codec.start()
            } catch (e: Exception) {
                codec.release()
                throw e
            }
        }

        fun feed() {
            while (!inputDone) {
                val index = codec.dequeueInputBuffer(0)
                if (index < 0) return
                if (nextInput == frames.size) {
                    codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                    return
                }
                val bytes = data(frames[nextInput])
                val buffer = codec.getInputBuffer(index) ?: throw IOException("no input buffer")
                buffer.clear()
                buffer.put(bytes)
                codec.queueInputBuffer(index, 0, bytes.size, nextInput.toLong(), 0)
                nextInput++
            }
        }

        fun poll() {
            if (ready || ended) return
            val index = codec.dequeueOutputBuffer(info, POLL_MICROS)
            if (index < 0) return
            val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            if (info.size == 0) {
                codec.releaseOutputBuffer(index, false)
                if (eos) ended = true
                return
            }
            output = index
            outputEos = eos
            pts = info.presentationTimeUs
        }

        fun image(): Image {
            val image = codec.getOutputImage(output) ?: throw IOException("no yuv output")
            if (image.format != ImageFormat.YUV_420_888) {
                image.close()
                throw IOException("output format ${image.format}")
            }
            return image
        }

        fun release() {
            codec.releaseOutputBuffer(output, false)
            output = -1
            if (outputEos) ended = true
        }

        fun restart() {
            codec.flush()
            nextInput = 0
            inputDone = false
            output = -1
            outputEos = false
            ended = false
        }

        override fun close() {
            codec.release()
        }
    }
}
