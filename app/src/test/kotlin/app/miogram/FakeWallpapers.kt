package app.miogram

import app.miogram.FakeTelegram.Companion.NOW
import app.miogram.data.telegram.withSettings
import cn.ellerotta.ktproto.api.functions.account.GetWallPaper
import cn.ellerotta.ktproto.api.functions.account.GetWallPapers
import cn.ellerotta.ktproto.api.functions.account.InstallWallPaper
import cn.ellerotta.ktproto.api.functions.account.SaveWallPaper
import cn.ellerotta.ktproto.api.functions.account.UploadWallPaper
import cn.ellerotta.ktproto.api.functions.messages.SetChatWallPaper
import cn.ellerotta.ktproto.api.functions.upload.GetFile
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.DocumentAttributeFilename
import cn.ellerotta.ktproto.api.types.InputDocumentFileLocation
import cn.ellerotta.ktproto.api.types.InputFile
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.InputWallPaper
import cn.ellerotta.ktproto.api.types.InputWallPaperNoFile
import cn.ellerotta.ktproto.api.types.MessageActionSetChatWallPaper
import cn.ellerotta.ktproto.api.types.MessageService
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.PhotoSize
import cn.ellerotta.ktproto.api.types.TypeInputPeer
import cn.ellerotta.ktproto.api.types.TypeWallPaper
import cn.ellerotta.ktproto.api.types.UpdatePeerWallpaper
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.WallPaper
import cn.ellerotta.ktproto.api.types.WallPaperNoFile
import cn.ellerotta.ktproto.api.types.WallPaperSettings
import cn.ellerotta.ktproto.api.types.account.WallPapers
import cn.ellerotta.ktproto.api.types.account.WallPapersNotModified
import cn.ellerotta.ktproto.api.types.storage.FilePartial
import cn.ellerotta.ktproto.api.types.upload.File
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeCluster
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

class FakeWallpapers {
    val getCalls: MutableList<GetWallPapers> = CopyOnWriteArrayList()
    val installCalls: MutableList<InstallWallPaper> = CopyOnWriteArrayList()
    val saveCalls: MutableList<SaveWallPaper> = CopyOnWriteArrayList()
    val uploadCalls: MutableList<UploadWallPaper> = CopyOnWriteArrayList()

    private val uploads: MutableMap<Long, Pair<WallPaper, ByteArray>> = ConcurrentHashMap()

    private val forChat: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    val setChatCalls: MutableList<SetChatWallPaper> = CopyOnWriteArrayList()

    val chats: MutableMap<Long, ChatPaper> = ConcurrentHashMap()

    private val services: MutableMap<Long, MutableList<MessageService>> = ConcurrentHashMap()
    private val nextServiceId = AtomicInteger(SERVICE_ID)

    fun history(peer: TypeInputPeer): List<MessageService> = (peer as? InputPeerUser)?.let { services[it.userId] }.orEmpty()

    fun offer(userId: Long, paper: TypeWallPaper, forBoth: Boolean = false): MessageService {
        if (forBoth) chats[userId] = ChatPaper(paper, overridden = true, before = chats[userId]?.paper)
        return service(userId, out = false, MessageActionSetChatWallPaper(forBoth = forBoth, wallpaper = paper))
    }

    private fun service(userId: Long, out: Boolean, action: MessageActionSetChatWallPaper): MessageService {
        val message = MessageService(out = out, id = nextServiceId.getAndIncrement(), fromId = if (out) null else PeerUser(userId), peerId = PeerUser(userId), date = NOW, action = action)
        services.getOrPut(userId) { CopyOnWriteArrayList() } += message
        return message
    }

    private fun setChat(userId: Long, call: SetChatWallPaper): FakeAnswer? {
        fun settled(paper: TypeWallPaper) = call.settings?.let { withSettings(paper, it) } ?: paper
        when {
            call.revert -> {
                val overridden = chats[userId]?.takeIf { it.overridden } ?: return FakeAnswer.Error(400, "WALLPAPER_NOT_FOUND")
                val before = overridden.before
                if (before == null) chats.remove(userId) else chats[userId] = ChatPaper(before)
            }
            call.id != null -> {
                val offered = services[userId]?.firstOrNull { it.id == call.id && !it.out }?.action as? MessageActionSetChatWallPaper
                    ?: return FakeAnswer.Error(400, "MESSAGE_ID_INVALID")
                val paper = settled(offered.wallpaper)
                chats[userId] = ChatPaper(paper)
                service(userId, out = true, MessageActionSetChatWallPaper(same = true, wallpaper = paper))
            }
            call.wallpaper == null -> chats.remove(userId)
            else -> {
                val found: TypeWallPaper? = when (val input = call.wallpaper) {
                    is InputWallPaper -> find(input)?.takeIf { it.id !in uploads || it.id in forChat }
                    is InputWallPaperNoFile -> wallpapers().filterIsInstance<WallPaperNoFile>().firstOrNull { it.id == input.id }
                    else -> null
                }
                val paper = settled(found ?: return FakeAnswer.Error(400, "WALLPAPER_INVALID"))
                chats[userId] = ChatPaper(paper)
                service(userId, out = true, MessageActionSetChatWallPaper(forBoth = call.forBoth, wallpaper = paper))
            }
        }
        return null
    }

    private val saved: MutableList<WallPaper> = CopyOnWriteArrayList()

    fun hash(): Long = HASH + saved.size

    val downloads: MutableList<String> = CopyOnWriteArrayList()

    @Volatile
    var reference: ByteArray = byteArrayOf(5, 1)
        private set

    fun expireReference() {
        reference = byteArrayOf(5, (reference.last() + 1).toByte())
    }

    val patternBytes: ByteArray = gzip(PATTERN_SVG.toByteArray())
    val imageBytes = ByteArray(3000) { (it * 13).toByte() }
    val thumbBytes = ByteArray(600) { (it * 17).toByte() }

    fun wallpapers(): List<TypeWallPaper> = listOf(
        WallPaper(
            id = PATTERN_ID, pattern = true, accessHash = PATTERN_ID + 1, slug = "freeform-pattern",
            document = document(PATTERN_ID, "application/x-tgwallpattern", patternBytes.size),
            settings = WallPaperSettings(
                backgroundColor = 0xdbddbb, secondBackgroundColor = 0x6ba587, thirdBackgroundColor = 0xd5d88d, fourthBackgroundColor = 0x88b884,
                intensity = 50, rotation = 0,
            ),
        ),
        WallPaper(
            id = DARK_PATTERN_ID, pattern = true, dark = true, accessHash = DARK_PATTERN_ID + 1, slug = "dark-pattern",
            document = document(DARK_PATTERN_ID, "application/x-tgwallpattern", patternBytes.size),
            settings = WallPaperSettings(backgroundColor = 0x1b2836, secondBackgroundColor = 0x5f7f9c, intensity = -60, rotation = 45),
        ),
        WallPaper(
            id = IMAGE_ID, accessHash = IMAGE_ID + 1, slug = "image",
            document = document(IMAGE_ID, "image/jpeg", imageBytes.size, thumbs = listOf(PhotoSize("m", 320, 640, thumbBytes.size))),
            settings = WallPaperSettings(blur = true, motion = true),
        ),
        WallPaperNoFile(id = FILL_ID, settings = WallPaperSettings(backgroundColor = 0x7fa381)),
        WallPaperNoFile(id = GRADIENT_ID, settings = WallPaperSettings(backgroundColor = 0xfec496, secondBackgroundColor = 0xdd6cb9, rotation = 90)),
    ) + saved

    private fun document(id: Long, mimeType: String, size: Int, thumbs: List<PhotoSize>? = null) = Document(
        id = id,
        accessHash = id + 1,
        fileReference = reference,
        date = NOW,
        mimeType = mimeType,
        size = size.toLong(),
        thumbs = thumbs,
        dcId = 2,
        attributes = listOf(DocumentAttributeFilename("wallpaper")),
    )

    fun file(call: GetFile, location: InputDocumentFileLocation): FakeAnswer? {
        val bytes = when (location.id) {
            in uploads -> uploads.getValue(location.id).second
            PATTERN_ID, DARK_PATTERN_ID -> patternBytes
            IMAGE_ID -> if (location.thumbSize == "m") thumbBytes else if (location.thumbSize.isEmpty()) imageBytes else return FakeAnswer.Error(400, "LOCATION_INVALID")
            else -> return null
        }
        if (!location.fileReference.contentEquals(reference)) return FakeAnswer.Error(400, "FILE_REFERENCE_EXPIRED")
        if (call.offset == 0L) downloads += if (location.thumbSize.isEmpty()) "${location.id}" else "${location.id}@${location.thumbSize}"
        val start = minOf(call.offset, bytes.size.toLong()).toInt()
        return FakeAnswer.Result(File(FilePartial, 0, bytes.copyOfRange(start, minOf(start + call.limit, bytes.size))))
    }

    fun find(input: InputWallPaper): WallPaper? =
        (wallpapers().filterIsInstance<WallPaper>() + uploads.values.map { it.first }).firstOrNull { it.id == input.id && it.accessHash == input.accessHash }

    fun register(server: FakeTelegramServer, cluster: FakeCluster) {
        server.rpc.on(GetWallPapers.CONSTRUCTOR_ID) { request ->
            val call = GetWallPapers.decode(request)
            getCalls += call
            FakeAnswer.Result(if (call.hash == hash()) WallPapersNotModified else WallPapers(hash(), wallpapers()))
        }
        server.rpc.on(GetWallPaper.CONSTRUCTOR_ID) { request ->
            val input = GetWallPaper.decode(request).wallpaper as? InputWallPaper ?: return@on FakeAnswer.Error(400, "WALLPAPER_INVALID")
            find(input)?.let { FakeAnswer.Result(it) } ?: FakeAnswer.Error(400, "WALLPAPER_NOT_FOUND")
        }
        server.rpc.on(InstallWallPaper.CONSTRUCTOR_ID) { request ->
            val call = InstallWallPaper.decode(request)
            val input = call.wallpaper as? InputWallPaper ?: return@on FakeAnswer.Error(400, "WALLPAPER_INVALID")
            val found = find(input) ?: return@on FakeAnswer.Error(400, "WALLPAPER_NOT_FOUND")
            if (found.id in uploads && saved.none { it.id == found.id }) saved += found
            installCalls += call
            FakeTelegram.BOOL_TRUE
        }
        server.rpc.on(UploadWallPaper.CONSTRUCTOR_ID) { request ->
            val call = UploadWallPaper.decode(request)
            val file = call.file as? InputFile ?: return@on FakeAnswer.Error(400, "WALLPAPER_FILE_INVALID")
            val bytes = cluster.uploadedFile(file.id, file.parts) ?: return@on FakeAnswer.Error(400, "WALLPAPER_FILE_INVALID")
            if (call.mimeType != "image/jpeg" && call.mimeType != "image/png") return@on FakeAnswer.Error(400, "WALLPAPER_MIME_INVALID")
            uploadCalls += call
            val id = UPLOADED_ID + uploads.size
            val paper = WallPaper(id = id, creator = true, accessHash = id + 1, slug = "own-$id", document = document(id, call.mimeType, bytes.size), settings = call.settings)
            uploads[id] = paper to bytes
            if (call.forChat) forChat += id
            FakeAnswer.Result(paper)
        }
        server.rpc.on(SaveWallPaper.CONSTRUCTOR_ID) { request ->
            saveCalls += SaveWallPaper.decode(request)
            FakeTelegram.BOOL_TRUE
        }
        server.rpc.on(SetChatWallPaper.CONSTRUCTOR_ID) { request ->
            val call = SetChatWallPaper.decode(request)
            val userId = (call.peer as? InputPeerUser)?.userId ?: return@on FakeAnswer.Error(400, "PEER_ID_INVALID")
            setChat(userId, call)?.let { return@on it }
            setChatCalls += call
            val chat = chats[userId]
            FakeAnswer.Result(Updates(listOf(UpdatePeerWallpaper(chat?.overridden == true, PeerUser(userId), chat?.paper)), emptyList(), emptyList(), NOW, 0))
        }
    }

    companion object {
        const val HASH = 7_654_321L
        const val PATTERN_ID = 7_601L
        const val DARK_PATTERN_ID = 7_602L
        const val IMAGE_ID = 7_603L
        const val FILL_ID = 7_604L
        const val GRADIENT_ID = 7_605L

        const val UPLOADED_ID = 7_700L

        const val SERVICE_ID = 7_800

        const val PATTERN_SVG = """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="100" height="200" viewBox="0 0 100 200">
  <style>.hollow { fill: none; stroke: #000; stroke-width: 4 }</style>
  <defs><clipPath id="c"><rect width="100" height="200"/></clipPath></defs>
  <g id="bounds"><rect width="100" height="200" fill="#000"/></g>
  <rect x="10" y="10" width="30" height="30"/>
  <circle class="hollow" cx="70" cy="40" r="15"/>
  <g transform="translate(0 100)">
    <path d="M10 10 L40 10 L40 40 Z"/>
    <polygon points="60,10 90,10 90,40" opacity="0.5"/>
  </g>
</svg>"""

        fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
    }
}

class ChatPaper(val paper: TypeWallPaper, val overridden: Boolean = false, val before: TypeWallPaper? = null)
