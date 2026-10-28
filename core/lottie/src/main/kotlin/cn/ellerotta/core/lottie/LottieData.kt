package cn.ellerotta.core.lottie

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

object LottieData {
    const val MAX_TGS_JSON = 4 * 1024 * 1024

    fun fromTgs(bytes: ByteArray, limit: Int = MAX_TGS_JSON): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(bytes.inputStream()).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (out.size() + read > limit) throw IOException("tgs inflates beyond $limit bytes")
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    fun isGzip(bytes: ByteArray): Boolean = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
}
