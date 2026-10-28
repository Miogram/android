package app.miogram.data.telegram

import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MediaSize
import cn.ellerotta.ktproto.api.functions.channels.GetMessages as ChannelsGetMessages
import cn.ellerotta.ktproto.api.functions.messages.GetMessages
import cn.ellerotta.ktproto.api.types.InputMessageID
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageMediaDocument
import cn.ellerotta.ktproto.api.types.MessageMediaPhoto
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.messages.ChannelMessages
import cn.ellerotta.ktproto.api.types.messages.Messages
import cn.ellerotta.ktproto.api.types.messages.MessagesSlice
import cn.ellerotta.ktproto.client.downloadFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

class NotificationImage(val bytes: ByteArray, val mime: String)

val PREVIEW_KINDS: Set<MediaKind> = setOf(MediaKind.Photo, MediaKind.Sticker, MediaKind.Video, MediaKind.Gif)

@Singleton
class NotificationMediaRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    suspend fun image(dialogKey: String, id: Int): NotificationImage? {
        val ids = listOf(InputMessageID(id))
        val result = if (peerOfKey(dialogKey) is PeerChannel) {
            telegram.client().invoke(ChannelsGetMessages(registry.channel(dialogKey), ids))
        } else {
            telegram.client().invoke(GetMessages(ids))
        }
        val messages = when (result) {
            is Messages -> result.messages
            is MessagesSlice -> result.messages
            is ChannelMessages -> result.messages
            else -> emptyList()
        }
        val message = messages.filterIsInstance<Message>().firstOrNull { it.id == id } ?: return null
        when (val media = message.media) {
            is MessageMediaPhoto -> if (media.spoiler || media.ttlSeconds != null) return null
            is MessageMediaDocument -> if (media.spoiler || media.ttlSeconds != null) return null
            else -> return null
        }
        val item = registry.itemOf(message, dialogKey)?.media ?: return null
        if (item.kind !in PREVIEW_KINDS) return null
        if (item.kind == MediaKind.Sticker && !item.animated && item.mimeType == "image/webp") {
            fetch(item.key, item.dcId, item.size)?.let { return it }
        }
        val size = sizeOf(item.sizes)
        val sharp = size?.let { fetch("${item.key}@${it.type}", item.dcId, it.bytes) }
        return sharp ?: item.thumb?.let(::imageOf)
    }

    private suspend fun fetch(key: String, dcId: Int, size: Long?): NotificationImage? = withTimeoutOrNull(DOWNLOAD_MILLIS) {
        try {
            download(key, dcId, size)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun sizeOf(sizes: List<MediaSize>): MediaSize? {
        val fits = sizes.filter { it.bytes <= MAX_BYTES }
        return fits.filter { maxOf(it.width, it.height) >= SHARP }.minByOrNull { it.width * it.height } ?: fits.maxByOrNull { it.width * it.height }
    }

    private suspend fun download(key: String, dcId: Int, size: Long?): NotificationImage? {
        val location = registry.locations[key] ?: return null
        if (size != null && size > MAX_BYTES) return null
        val out = ByteArrayOutputStream()
        telegram.client().downloadFile(location, dcId.takeIf { it != 0 }, size).takeWhile { part ->
            out.write(part.bytes)
            out.size() <= MAX_BYTES
        }.collect()
        return if (out.size() > MAX_BYTES) null else imageOf(out.toByteArray())
    }

    private fun imageOf(bytes: ByteArray): NotificationImage? = when {
        bytes.isEmpty() -> null
        bytes.size > 12 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(bytes, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> NotificationImage(bytes, "image/webp")
        bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> NotificationImage(bytes, "image/png")
        else -> NotificationImage(bytes, "image/jpeg")
    }

    private companion object {
        const val MAX_BYTES = 1L shl 20
        const val SHARP = 320
        const val DOWNLOAD_MILLIS = 6_000L
    }
}
