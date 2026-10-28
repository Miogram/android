package app.miogram.data.telegram

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import app.miogram.data.FileCache
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.StorageCategory
import cn.ellerotta.ktproto.api.types.InputWebFileLocation
import cn.ellerotta.ktproto.api.types.TypeInputFileLocation
import cn.ellerotta.ktproto.client.downloadFile
import cn.ellerotta.ktproto.client.downloadProfilePhoto
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class VideoStream(val uri: Uri, val factory: DataSource.Factory)

fun peerAvatarFile(filesDir: File, peerKey: String): File? =
    peerKey.takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) }?.let { File(File(filesDir, "avatars"), "peer_$it.jpg") }

@Singleton
class MediaRepository @Inject constructor(private val telegram: TelegramClientHolder, private val databases: Provider<DatabaseProvider>) {
    private val registry get() = telegram.peers
    private val downloads by lazy { FileCache(File(telegram.filesDir, "downloads"), MAX_DOWNLOADS) }

    private val stickers by lazy { FileCache(File(telegram.filesDir, "stickers"), stickerCacheBytes) }

    private val stickerLocks = Array(STICKER_LOCKS) { Mutex() }

    internal var stickerCacheBytes = MAX_STICKERS
    private val avatars by lazy { FileCache(File(telegram.filesDir, "avatars"), MAX_AVATARS) }

    @OptIn(UnstableApi::class)
    private val streamsLazy = lazy { SimpleCache(File(telegram.filesDir, STREAMS), LeastRecentlyUsedCacheEvictor(MAX_STREAMS), databases.get()) }
    private val streams by streamsLazy

    suspend fun sticker(mediaKey: String): ByteArray? = withContext(Dispatchers.IO) {
        val cache = stickers.file(mediaKey)
        stickers.hit(cache)?.let { return@withContext it.readBytes() }
        stickerLocks[(mediaKey.hashCode() and Int.MAX_VALUE) % stickerLocks.size].withLock {
            stickers.hit(cache)?.let { return@withContext it.readBytes() }
            fetchSticker(mediaKey, cache)
        }
    }

    private suspend fun fetchSticker(mediaKey: String, cache: File): ByteArray? {
        val web = registry.webFiles[mediaKey]
        val location = registry.locations[mediaKey]
        if (web == null && location == null) return null
        val bytes = runCatching {
            val out = ByteArrayOutputStream()
            if (web != null) {
                webFile(web, out)
            } else if (location != null) {
                telegram.client().downloadFile(location, registry.fileDcs[mediaKey], null, refresh = { refreshed(mediaKey) }).collect { part ->
                    out.write(part.bytes)
                    if (out.size() > MAX_STICKER) error("sticker too big")
                }
            }
            out.toByteArray()
        }.getOrNull() ?: return null
        val part = stickers.file("$mediaKey.${System.nanoTime()}${FileCache.PART}")
        part.writeBytes(bytes)
        if (!part.renameTo(cache)) part.delete()
        stickers.trim(keep = cache)
        return bytes
    }

    suspend fun hasSticker(mediaKey: String): Boolean = withContext(Dispatchers.IO) { stickers.file(mediaKey).exists() }

    private suspend fun webFile(file: WebFile, out: ByteArrayOutputStream) {
        val hash = file.accessHash
        if (hash == null) {
            require(file.url.startsWith("https://")) { "not an https url" }
            val connection = URL(file.url).openConnection() as HttpURLConnection
            connection.connectTimeout = WEB_TIMEOUT
            connection.readTimeout = WEB_TIMEOUT
            try {
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        if (out.size() > MAX_STICKER) error("thumbnail too big")
                    }
                }
            } finally {
                connection.disconnect()
            }
            return
        }
        out.write(telegram.webFile(InputWebFileLocation(file.url, hash), MAX_STICKER))
    }

    private fun cacheOf(media: MediaItem) = if (media.kind == MediaKind.Gif) stickers else downloads

    private fun nameOf(media: MediaItem) = if (media.kind == MediaKind.Gif) "${media.key}_${media.fileName}" else media.fileName

    fun download(media: MediaItem): Flow<DownloadState> = flow {
        val cache = cacheOf(media)
        val target = cache.file(nameOf(media))
        if (media.size != null) {
            cache.hit(target, media.size)?.let {
                emit(DownloadState.Done(it))
                return@flow
            }
        }
        val temp = cache.file(nameOf(media) + FileCache.PART)
        temp.outputStream().use { out ->
            val location = registry.locations[media.key] ?: error("unknown media ${media.key}")
            telegram.client().downloadFile(location, media.dcId, media.size, refresh = { refreshed(media.key) }).collect { part ->
                out.write(part.bytes)
                emit(DownloadState.Progress(part.downloaded, media.size))
            }
        }
        temp.renameTo(target)
        cache.trim(keep = target)
        telegram.log("saved ${target.name} (${target.length()} bytes)")
        emit(DownloadState.Done(target))
    }.flowOn(Dispatchers.IO)

    @OptIn(UnstableApi::class)
    fun stream(media: MediaItem): VideoStream {
        val size = media.size ?: error("unknown size of ${media.key}")
        cacheOf(media).let { it.hit(it.file(nameOf(media)), size) }?.let { return VideoStream(Uri.fromFile(it), FileDataSourceFactory) }
        val location = registry.locations[media.key] ?: error("unknown media ${media.key}")
        val upstream = TelegramDataSource.Factory { TelegramDataSource(telegram, location, media.dcId, size) { refreshed(media.key) } }
        val factory = CacheDataSource.Factory()
            .setCache(streams)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return VideoStream(Uri.parse("tg://media/${media.key}"), factory)
    }

    @OptIn(UnstableApi::class)
    suspend fun prefetch(media: MediaItem, bytes: Long = PREFETCH_BYTES) = withContext(Dispatchers.IO) {
        val stream = stream(media)
        val factory = stream.factory as? CacheDataSource.Factory ?: return@withContext
        val spec = DataSpec.Builder().setUri(stream.uri).setLength(minOf(bytes, media.size ?: bytes)).build()
        val writer = CacheWriter(factory.createDataSource(), spec, null, null)
        val cancel = coroutineContext.job.invokeOnCompletion { writer.cancel() }
        try {
            writer.cache()
        } catch (_: InterruptedIOException) {
        } finally {
            cancel.dispose()
        }
    }

    @OptIn(UnstableApi::class)
    fun clearStreams() {
        if (streamsLazy.isInitialized()) streams.keys.toList().forEach(streams::removeResource) else File(telegram.filesDir, STREAMS).deleteRecursively()
    }

    val filesDir: File get() = telegram.filesDir

    @OptIn(UnstableApi::class)
    fun cacheBytes(category: StorageCategory): Long = when (category) {
        StorageCategory.Media -> downloads.size()
        StorageCategory.Stickers -> stickers.size()
        StorageCategory.Avatars -> avatars.size()
        StorageCategory.Streams -> if (streamsLazy.isInitialized()) streams.cacheSpace else File(telegram.filesDir, STREAMS).walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        StorageCategory.Music -> 0
    }

    fun clearCache(category: StorageCategory) {
        when (category) {
            StorageCategory.Media -> downloads.clear()
            StorageCategory.Stickers -> stickers.clear()
            StorageCategory.Avatars -> avatars.clear()
            StorageCategory.Streams -> clearStreams()
            StorageCategory.Music -> Unit
        }
    }

    fun musicSource(): DataSource.Factory = DataSource.Factory { MusicDataSource(this, telegram) }

    fun musicUri(media: MediaItem): Uri = MusicDataSource.uriOf(media.key, media.dcId, media.size ?: 0)

    suspend fun avatar(avatarKey: String, big: Boolean = false): ByteArray? = withContext(Dispatchers.IO) {
        val cache = avatars.file(if (big) "${avatarKey}_big.jpg" else "$avatarKey.jpg")
        avatars.hit(cache)?.let { bytes ->
            return@withContext bytes.readBytes().also { if (!big) keepForPush(avatarKey, it) }
        }
        val ref = registry.avatarRefs[avatarKey] ?: return@withContext null
        val bytes = runCatching {
            val out = ByteArrayOutputStream()
            val current = telegram.client()
            val peer = registry.inputPeer(ref.peerKey) ?: return@withContext null
            current.downloadProfilePhoto(peer, ref.photoId, ref.dcId, big).collect { out.write(it.bytes) }
            out.toByteArray()
        }.getOrNull() ?: return@withContext null
        cache.writeBytes(bytes)
        if (!big) keepForPush(avatarKey, bytes)
        avatars.trim(keep = cache)
        bytes
    }

    private fun keepForPush(avatarKey: String, bytes: ByteArray) {
        val ref = registry.avatarRefs[avatarKey] ?: return
        val file = peerAvatarFile(telegram.filesDir, ref.peerKey) ?: return
        if (file.exists() && file.readBytes().contentEquals(bytes)) return
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    internal suspend fun refreshed(mediaKey: String): TypeInputFileLocation {
        if (!telegram.refreshReference(mediaKey)) error("no origin for $mediaKey")
        return registry.locations[mediaKey] ?: error("$mediaKey is gone")
    }

    private companion object {
        const val STICKER_LOCKS = 64
        const val MAX_STICKER = 1024 * 1024
        const val MAX_DOWNLOADS = 512L * 1024 * 1024
        const val MAX_STICKERS = 300L * 1024 * 1024
        const val MAX_AVATARS = 32L * 1024 * 1024
        const val MAX_STREAMS = 256L * 1024 * 1024
        const val PREFETCH_BYTES = 1024L * 1024
        const val STREAMS = "streams"
        const val WEB_TIMEOUT = 10_000
        val FileDataSourceFactory = androidx.media3.datasource.FileDataSource.Factory()
    }
}
