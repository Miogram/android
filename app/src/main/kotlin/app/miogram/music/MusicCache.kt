package app.miogram.music

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class MusicCache @Inject constructor(@ApplicationContext context: Context, private val databases: DatabaseProvider) {
    private val dir = File(context.filesDir, "music")
    private val lazyCache = lazy { SimpleCache(dir, LeastRecentlyUsedCacheEvictor(MAX_BYTES), databases) }

    val cache: Cache by lazyCache

    fun bytes(): Long = if (lazyCache.isInitialized()) cache.cacheSpace else dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun clear() {
        if (lazyCache.isInitialized()) cache.keys.toList().forEach(cache::removeResource) else dir.deleteRecursively()
    }

    private companion object {
        const val MAX_BYTES = 256L * 1024 * 1024
    }
}
