package app.miogram.data

import android.os.StatFs
import app.miogram.data.telegram.MediaRepository
import app.miogram.music.MusicCache
import cn.ellerotta.core.model.StorageCategory
import cn.ellerotta.core.model.StorageUsage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class StorageRepository @Inject constructor(private val media: MediaRepository, private val music: MusicCache) {
    @Volatile
    var lastUsage: StorageUsage? = null
        private set

    suspend fun usage(): StorageUsage = withContext(Dispatchers.IO) {
        val bytes = StorageCategory.entries.associateWith { category ->
            if (category == StorageCategory.Music) music.bytes() else media.cacheBytes(category)
        }
        StorageUsage(bytes, free = runCatching { StatFs(media.filesDir.path).availableBytes }.getOrNull()).also { lastUsage = it }
    }

    suspend fun clear(category: StorageCategory?) = withContext(Dispatchers.IO) {
        (category?.let(::listOf) ?: StorageCategory.entries).forEach {
            if (it == StorageCategory.Music) music.clear() else media.clearCache(it)
        }
    }
}
