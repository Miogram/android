package app.miogram.data.telegram

import android.content.Context
import app.miogram.data.FileCache
import cn.ellerotta.core.model.GeoMap
import cn.ellerotta.core.ui.components.maps.GeoMapImages
import cn.ellerotta.ktproto.api.types.InputGeoPoint
import cn.ellerotta.ktproto.api.types.InputWebFileGeoPointLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class GeoMapsRepository(private val telegram: TelegramClientHolder, dir: File) : GeoMapImages {
    @Inject
    constructor(telegram: TelegramClientHolder, @ApplicationContext context: Context) : this(telegram, File(context.cacheDir, "maps"))

    private val pictures = FileCache(File(dir, "telegram"), MAX_PICTURES)
    private val loading = ConcurrentHashMap<String, Mutex>()

    override suspend fun load(map: GeoMap): ByteArray? {
        val shown = map.clamped()
        val name = "${shown.cacheKey}.img"
        return loading.getOrPut(name) { Mutex() }.withLock {
            withContext(Dispatchers.IO) { pictures.hit(pictures.file(name))?.readBytes() } ?: fetch(shown)?.also { bytes ->
                withContext(Dispatchers.IO) { keep(name, bytes) }
            }
        }
    }

    private suspend fun fetch(map: GeoMap): ByteArray? = try {
        val location = InputWebFileGeoPointLocation(InputGeoPoint(map.latitude, map.longitude, map.accuracy), map.accessHash, map.width, map.height, map.zoom, map.scale)
        telegram.webFile(location, MAX_PICTURE).takeIf { it.isNotEmpty() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun keep(name: String, bytes: ByteArray) {
        val file = pictures.file(name)
        val part = pictures.file("$name.${System.nanoTime()}${FileCache.PART}")
        part.writeBytes(bytes)
        if (!part.renameTo(file)) part.delete()
        pictures.trim(keep = file)
    }

    private companion object {
        const val MAX_PICTURES = 32L * 1024 * 1024
        const val MAX_PICTURE = 2 * 1024 * 1024
    }
}
