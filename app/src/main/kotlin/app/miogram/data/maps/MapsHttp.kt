package app.miogram.data.maps

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface MapsHttp {
    suspend fun get(url: String): ByteArray
}

class UrlMapsHttp @Inject constructor() : MapsHttp {
    override suspend fun get(url: String): ByteArray = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "not an https url" }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT
        connection.readTimeout = TIMEOUT
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("maps answered $code")
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_BYTES) throw IOException("maps answer too big")
                }
            }
            out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT = 15_000
        const val MAX_BYTES = 4 * 1024 * 1024
    }
}

data class MapsConfig(val apiKey: String, val sdk: Boolean = apiKey.isNotBlank()) {
    val enabled: Boolean get() = apiKey.isNotBlank()
}
