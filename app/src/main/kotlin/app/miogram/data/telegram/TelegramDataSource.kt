package app.miogram.data.telegram

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import cn.ellerotta.ktproto.api.types.TypeInputFileLocation
import cn.ellerotta.ktproto.client.downloadFile
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(UnstableApi::class)
internal class TelegramDataSource(
    private val telegram: TelegramClientHolder,
    private val location: TypeInputFileLocation,
    private val dcId: Int,
    private val size: Long,
    private val refresh: suspend () -> TypeInputFileLocation,
) : BaseDataSource(true) {
    private var uri: Uri? = null
    private var scope: CoroutineScope? = null
    private var parts: Channel<ByteArray>? = null
    private var chunk: ByteArray? = null
    private var at = 0
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val position = dataSpec.position
        if (position > size) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(dataSpec.length, size - position) else size - position
        val channel = Channel<ByteArray>(BUFFERED_PARTS)
        val job = CoroutineScope(Dispatchers.IO + SupervisorJob())
        job.launch {
            try {
                telegram.client().downloadFile(location, dcId, size, offset = position, parallel = PARALLEL, refresh = refresh).collect { channel.send(it.bytes) }
                channel.close()
            } catch (e: Throwable) {
                channel.close(e)
            }
        }
        scope = job
        parts = channel
        chunk = null
        at = 0
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        var current = chunk
        if (current == null || at >= current.size) {
            val channel = parts ?: throw IOException("not opened")
            val next = runBlocking { channel.receiveCatching() }
            current = next.getOrNull() ?: next.exceptionOrNull()?.let { throw IOException(it) } ?: return C.RESULT_END_OF_INPUT
            chunk = current
            at = 0
        }
        val count = minOf(length.toLong(), (current.size - at).toLong(), remaining).toInt()
        System.arraycopy(current, at, buffer, offset, count)
        at += count
        remaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        scope?.cancel()
        scope = null
        parts = null
        chunk = null
        uri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory internal constructor(private val create: () -> TelegramDataSource) : DataSource.Factory {
        override fun createDataSource(): DataSource = create()
    }

    private companion object {
        const val PARALLEL = 2
        const val BUFFERED_PARTS = 2
    }
}

@OptIn(UnstableApi::class)
internal class MusicDataSource(private val media: MediaRepository, private val telegram: TelegramClientHolder) : DataSource {
    private val listeners = mutableListOf<androidx.media3.datasource.TransferListener>()
    private var current: TelegramDataSource? = null

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        listeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val key = uri.lastPathSegment ?: throw IOException("no media in $uri")
        val size = uri.getQueryParameter("size")?.toLongOrNull() ?: throw IOException("no size in $uri")
        val dc = uri.getQueryParameter("dc")?.toIntOrNull() ?: throw IOException("no dc in $uri")
        val location = telegram.peers.locations[key] ?: throw IOException("unknown media $key")
        val source = TelegramDataSource(telegram, location, dc, size) { media.refreshed(key) }
        listeners.forEach(source::addTransferListener)
        current = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = current?.read(buffer, offset, length) ?: throw IOException("not opened")

    override fun getUri(): Uri? = current?.uri

    override fun close() {
        current?.close()
        current = null
    }

    companion object {
        fun uriOf(key: String, dcId: Int, size: Long): Uri = Uri.parse("tg://music/$key?dc=$dcId&size=$size")
    }
}
