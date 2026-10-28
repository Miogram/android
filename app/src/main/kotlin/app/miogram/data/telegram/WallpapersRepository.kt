package app.miogram.data.telegram

import app.miogram.data.FileCache
import cn.ellerotta.core.model.ChatWallpaper
import cn.ellerotta.core.model.DEFAULT_PATTERN_INTENSITY
import cn.ellerotta.core.model.NO_WALLPAPER
import cn.ellerotta.core.model.PLAIN_WALLPAPER
import cn.ellerotta.core.model.Wallpaper
import cn.ellerotta.core.model.WallpaperAction
import cn.ellerotta.core.model.WallpaperKind
import cn.ellerotta.ktproto.api.functions.account.GetWallPaper
import cn.ellerotta.ktproto.api.functions.account.GetWallPapers
import cn.ellerotta.ktproto.api.functions.account.InstallWallPaper
import cn.ellerotta.ktproto.api.functions.account.UploadWallPaper
import cn.ellerotta.ktproto.api.functions.messages.SetChatWallPaper
import cn.ellerotta.ktproto.api.functions.users.GetFullUser
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.InputDocumentFileLocation
import cn.ellerotta.ktproto.api.types.InputWallPaper
import cn.ellerotta.ktproto.api.types.InputWallPaperNoFile
import cn.ellerotta.ktproto.api.types.MessageActionSetChatWallPaper
import cn.ellerotta.ktproto.api.types.PhotoSize
import cn.ellerotta.ktproto.api.types.PhotoSizeProgressive
import cn.ellerotta.ktproto.api.types.TypeInputFile
import cn.ellerotta.ktproto.api.types.TypeMessageAction
import cn.ellerotta.ktproto.api.types.TypeUpdate
import cn.ellerotta.ktproto.api.types.TypeUpdates
import cn.ellerotta.ktproto.api.types.TypeWallPaper
import cn.ellerotta.ktproto.api.types.UpdatePeerWallpaper
import cn.ellerotta.ktproto.api.types.UpdateShort
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.UpdatesCombined
import cn.ellerotta.ktproto.api.types.WallPaper
import cn.ellerotta.ktproto.api.types.WallPaperNoFile
import cn.ellerotta.ktproto.api.types.WallPaperSettings
import cn.ellerotta.ktproto.api.types.account.TypeWallPapers
import cn.ellerotta.ktproto.api.types.account.WallPapers
import cn.ellerotta.ktproto.api.types.account.WallPapersNotModified
import cn.ellerotta.ktproto.client.Upload
import cn.ellerotta.ktproto.client.UploadSource
import cn.ellerotta.ktproto.client.downloadFile
import cn.ellerotta.ktproto.client.uploadFile
import cn.ellerotta.ktproto.tl.decode
import cn.ellerotta.ktproto.tl.toByteArray
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class WallpapersRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val dir get() = File(telegram.filesDir, DIR)
    private val registry get() = telegram.peers
    private val previews by lazy { FileCache(File(dir, "previews"), MAX_PREVIEWS) }

    @Volatile
    private var list: WallPapers? = null

    private val known = ConcurrentHashMap<String, TypeWallPaper>()

    private val chosenState by lazy { MutableStateFlow(readChosen()) }

    val chosen: StateFlow<ChatWallpaper?> by lazy { chosenState.asStateFlow() }

    suspend fun wallpapers(): List<Wallpaper> = withContext(Dispatchers.IO) {
        val cached = list ?: readList()
        val current = when (val answer: TypeWallPapers = telegram.client().invoke(GetWallPapers(cached?.hash ?: 0))) {
            is WallPapers -> answer.also { listFile().writeAtomically(it.toByteArray()) }
            WallPapersNotModified -> cached ?: error("wallpapers not modified without a list")
        }
        list = current
        current.wallpapers.mapNotNull { paper -> wallpaperOf(paper)?.also { known[it.key] = paper } }
    }

    suspend fun stored(): List<Wallpaper>? = withContext(Dispatchers.IO) {
        val cached = list ?: readList() ?: return@withContext null
        cached.wallpapers.mapNotNull { paper -> wallpaperOf(paper)?.also { known.putIfAbsent(it.key, paper) } }
    }

    suspend fun preview(key: String): String? = withContext(Dispatchers.IO) {
        val paper = paperOf(key) as? WallPaper ?: return@withContext null
        val document = paper.document as? Document ?: return@withContext null
        val thumb = if (paper.pattern) "" else thumbOf(document).orEmpty()
        val target = previews.file(if (thumb.isEmpty()) key else "$key@$thumb")
        previews.hit(target)?.let { return@withContext it.path }
        if (!download(paper, thumb, target)) return@withContext null
        previews.trim(keep = target)
        target.path
    }

    suspend fun file(key: String): String? = withContext(Dispatchers.IO) {
        val paper = paperOf(key) as? WallPaper ?: return@withContext null
        val target = previews.file(key)
        previews.hit(target)?.let { return@withContext it.path }
        if (!download(paper, "", target)) return@withContext null
        previews.trim(keep = target)
        target.path
    }

    suspend fun choose(key: String?, edited: Wallpaper? = null) = withContext(Dispatchers.IO) {
        if (key == null || key == NO_WALLPAPER) {
            clearChosen()
            if (key == null) {
                chosenState.value = null
            } else {
                plainFile().writeAtomically(ByteArray(0))
                chosenState.value = ChatWallpaper(PLAIN_WALLPAPER)
            }
            return@withContext
        }
        val seen = known[key] ?: error("unknown wallpaper $key")
        val wallpaper = edited ?: wallpaperOf(seen) ?: error("wallpaper $key cannot be shown")
        val settings = settingsOf(wallpaper)
        val paper = withSettings(seen, settings)
        var file: File? = null
        if (paper is WallPaper) {
            val target = File(dir.apply { mkdirs() }, "$CHOSEN_FILE${paper.id}")
            if (!target.isFile) previews.hit(previews.file(key))?.copyTo(target, overwrite = true)
            if (!target.isFile && !download(paper, "", target)) error("wallpaper $key did not download")
            telegram.client().invoke(InstallWallPaper(InputWallPaper(paper.id, paper.accessHash), settings))
            file = target
        }
        dir.listFiles()?.filter { it.name.startsWith(CHOSEN_FILE) && it != file }?.forEach { it.delete() }
        chosenFile().writeAtomically(paper.toByteArray())
        chosenState.value = ChatWallpaper(wallpaperOf(paper) ?: wallpaper, file?.path)
    }

    suspend fun importPhoto(jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        File(dir.apply { mkdirs() }, PHOTO_FILE).apply { writeAtomically(jpeg) }.path
    }

    suspend fun choosePhoto(path: String, edited: Wallpaper) = withContext(Dispatchers.IO) {
        val paper = uploadPhoto(File(path), settingsOf(edited), forChat = false)
        val key = keyOf(paper.id)
        known[key] = paper
        File(path).let { photo -> photo.copyTo(previews.file(key), overwrite = true).also { photo.delete() } }
        choose(key, edited.copy(key = key))
    }

    private suspend fun uploadPhoto(file: File, settings: WallPaperSettings, forChat: Boolean): WallPaper {
        val client = telegram.client()
        var uploaded: TypeInputFile? = null
        client.uploadFile(file.name, UploadSource.of(file)).collect { step -> if (step is Upload.Done) uploaded = step.file }
        val input = uploaded ?: error("the upload ended without a file")
        val paper = client.invoke(UploadWallPaper(forChat = forChat, file = input, mimeType = PHOTO_MIME, settings = settings))
        return paper as? WallPaper ?: error("telegram made no wallpaper of the photo")
    }

    private val chatsState by lazy { MutableStateFlow(readPlainChats().associateWith { ChatWallpaper(PLAIN_WALLPAPER) }) }

    private val chatFiles = Mutex()

    val chats: StateFlow<Map<String, ChatWallpaper>> by lazy { chatsState.asStateFlow() }

    fun allowsOwn(dialogKey: String): Boolean = dialogKey.startsWith("u") && dialogKey != "u${telegram.userId}"

    suspend fun loadChat(dialogKey: String) = withContext(Dispatchers.IO) {
        if (!allowsOwn(dialogKey)) return@withContext
        val full = telegram.client().invoke(GetFullUser(registry.user(dialogKey)))
        registry.rememberUsers(full.users)
        registry.rememberChatWallpaper(dialogKey, full.fullUser.wallpaper, full.fullUser.wallpaperOverridden)
        showChat(dialogKey)
    }

    suspend fun showChat(dialogKey: String) = withContext(Dispatchers.IO) {
        val paper = registry.chatWallpapers[dialogKey]
        val wallpaper = paper?.let(::wallpaperOf)
        chatFiles.withLock {
            if (dialogKey in readPlainChats()) {
                chatsState.update { it + (dialogKey to ChatWallpaper(PLAIN_WALLPAPER)) }
            } else if (paper == null || wallpaper == null) {
                chatsState.update { it - dialogKey }
            } else {
                registry.wallpapers[wallpaper.key] = paper
                var file: File? = null
                if (paper is WallPaper) {
                    val target = File(dir.apply { mkdirs() }, "$CHAT_FILE${paper.id}")
                    if (!target.isFile) previews.hit(previews.file(wallpaper.key))?.copyTo(target, overwrite = true)
                    if (!target.isFile && !download(paper, "", target)) error("wallpaper ${wallpaper.key} did not download")
                    file = target
                }
                chatsState.update { it + (dialogKey to ChatWallpaper(wallpaper, file?.path)) }
            }
            val shown = chatsState.value.values.mapNotNull { it.file }.toSet()
            dir.listFiles()?.filter { it.name.startsWith(CHAT_FILE) && it.path !in shown }?.forEach { it.delete() }
        }
    }

    suspend fun setChatPlain(dialogKey: String) = withContext(Dispatchers.IO) {
        chatFiles.withLock { writePlainChats(readPlainChats() + dialogKey) }
        showChat(dialogKey)
    }

    suspend fun setChat(dialogKey: String, key: String, edited: Wallpaper) = withContext(Dispatchers.IO) {
        val paper = paperOf(key) ?: error("unknown wallpaper $key")
        val settings = settingsOf(edited)
        val input = when (paper) {
            is WallPaper -> InputWallPaper(paper.id, paper.accessHash)
            is WallPaperNoFile -> InputWallPaperNoFile(paper.id)
        }
        telegram.client().invoke(SetChatWallPaper(peer = registry.peer(dialogKey), wallpaper = input, settings = settings))
        registry.rememberChatWallpaper(dialogKey, withSettings(paper, settings), overridden = false)
        dropPlain(dialogKey)
        showChat(dialogKey)
    }

    suspend fun setChatPhoto(dialogKey: String, path: String, edited: Wallpaper) = withContext(Dispatchers.IO) {
        val paper = uploadPhoto(File(path), settingsOf(edited), forChat = true)
        val key = keyOf(paper.id)
        registry.wallpapers[key] = paper
        File(path).let { photo -> photo.copyTo(previews.file(key), overwrite = true).also { photo.delete() } }
        setChat(dialogKey, key, edited.copy(key = key))
    }

    suspend fun applyFromMessage(dialogKey: String, messageId: Int, edited: Wallpaper) = withContext(Dispatchers.IO) {
        val paper = paperOf(edited.key) ?: error("unknown wallpaper ${edited.key}")
        val settings = settingsOf(edited)
        telegram.client().invoke(SetChatWallPaper(peer = registry.peer(dialogKey), settings = settings, id = messageId))
        registry.rememberChatWallpaper(dialogKey, withSettings(paper, settings), overridden = false)
        dropPlain(dialogKey)
        showChat(dialogKey)
    }

    suspend fun resetChat(dialogKey: String) = withContext(Dispatchers.IO) {
        dropPlain(dialogKey)
        val revert = dialogKey in registry.wallpaperOverridden
        val answer = telegram.client().invoke(SetChatWallPaper(revert = revert, peer = registry.peer(dialogKey)))
        val update = updatesOf(answer).filterIsInstance<UpdatePeerWallpaper>().lastOrNull()
        when {
            !revert -> registry.rememberChatWallpaper(dialogKey, null, overridden = false)
            update != null -> registry.rememberChatWallpaper(dialogKey, update.wallpaper, update.wallpaperOverridden)
            else -> {
                loadChat(dialogKey)
                return@withContext
            }
        }
        showChat(dialogKey)
    }

    private suspend fun dropPlain(dialogKey: String) = chatFiles.withLock {
        val plain = readPlainChats()
        if (dialogKey in plain) writePlainChats(plain - dialogKey)
    }

    fun forget() {
        list = null
        known.clear()
        dir.deleteRecursively()
        chosenState.value = null
        chatsState.value = emptyMap()
    }

    private fun paperOf(key: String): TypeWallPaper? = known[key] ?: registry.wallpapers[key]

    private suspend fun download(paper: WallPaper, thumb: String, target: File): Boolean {
        val key = keyOf(paper.id)
        val temp = File(target.path + FileCache.PART)
        val done = runCatching {
            target.parentFile?.mkdirs()
            val document = paper.document as Document
            val location = InputDocumentFileLocation(document.id, document.accessHash, document.fileReference, thumb)
            temp.outputStream().use { out ->
                telegram.client().downloadFile(location, document.dcId, if (thumb.isEmpty()) document.size else null, refresh = { refreshed(key, thumb) }).collect { out.write(it.bytes) }
            }
            temp.renameTo(target)
        }.getOrElse { e ->
            telegram.log("wallpaper $key: ${e.message}")
            false
        }
        if (!done) temp.delete()
        return done
    }

    private suspend fun refreshed(key: String, thumb: String): InputDocumentFileLocation {
        val old = paperOf(key) as? WallPaper ?: error("unknown wallpaper $key")
        val fresh = telegram.client().invoke(GetWallPaper(InputWallPaper(old.id, old.accessHash))) as? WallPaper ?: error("wallpaper $key has no file")
        known[key] = fresh
        val document = fresh.document as? Document ?: error("wallpaper $key has no file")
        return InputDocumentFileLocation(document.id, document.accessHash, document.fileReference, thumb)
    }

    private fun readList(): WallPapers? = runCatching { TypeWallPapers.decode(listFile().readBytes()) as? WallPapers }.getOrNull()

    private fun readChosen(): ChatWallpaper? = runCatching {
        if (plainFile().isFile) return@runCatching ChatWallpaper(PLAIN_WALLPAPER)
        val paper = TypeWallPaper.decode(chosenFile().readBytes())
        val wallpaper = wallpaperOf(paper) ?: return@runCatching null
        known.putIfAbsent(wallpaper.key, paper)
        val file = (paper as? WallPaper)?.let { File(dir, "$CHOSEN_FILE${it.id}") }
        if (file != null && !file.isFile) return@runCatching null
        ChatWallpaper(wallpaper, file?.path)
    }.getOrNull()

    private fun clearChosen() {
        chosenFile().delete()
        plainFile().delete()
        dir.listFiles()?.filter { it.name.startsWith(CHOSEN_FILE) }?.forEach { it.delete() }
    }

    private fun readPlainChats(): Set<String> = runCatching { File(dir, PLAIN_CHATS_FILE).readLines().filter { it.isNotBlank() }.toSet() }.getOrDefault(emptySet())

    private fun writePlainChats(keys: Set<String>) = File(dir.apply { mkdirs() }, PLAIN_CHATS_FILE).writeAtomically(keys.joinToString("\n").toByteArray())

    private fun listFile() = File(dir.apply { mkdirs() }, "list.tl")

    private fun chosenFile() = File(dir.apply { mkdirs() }, "chosen.tl")

    private fun plainFile() = File(dir, "none")

    private fun File.writeAtomically(bytes: ByteArray) {
        val temp = File(path + FileCache.PART)
        temp.writeBytes(bytes)
        if (!temp.renameTo(this)) {
            writeBytes(bytes)
            temp.delete()
        }
    }

    private fun thumbOf(document: Document): String? {
        val sizes = document.thumbs.orEmpty().mapNotNull { size ->
            when (size) {
                is PhotoSize -> size.type to max(size.w, size.h)
                is PhotoSizeProgressive -> size.type to max(size.w, size.h)
                else -> null
            }
        }
        return (sizes.filter { it.second >= PREVIEW_SIZE }.minByOrNull { it.second } ?: sizes.maxByOrNull { it.second })?.first
    }

    private companion object {
        const val DIR = "wallpapers"
        const val CHOSEN_FILE = "chosen_"
        const val CHAT_FILE = "chat_"
        const val PLAIN_CHATS_FILE = "none_chats.txt"
        const val PHOTO_FILE = "photo.jpg"
        const val PHOTO_MIME = "image/jpeg"
        const val MAX_PREVIEWS = 32L * 1024 * 1024
        const val PREVIEW_SIZE = 320
    }
}

private fun keyOf(id: Long) = "wp$id"

private fun updatesOf(answer: TypeUpdates): List<TypeUpdate> = when (answer) {
    is Updates -> answer.updates
    is UpdatesCombined -> answer.updates
    is UpdateShort -> listOf(answer.update)
    else -> emptyList()
}

internal fun PeerRegistry.wallpaperActionOf(action: TypeMessageAction): WallpaperAction? {
    val set = action as? MessageActionSetChatWallPaper ?: return null
    val wallpaper = wallpaperOf(set.wallpaper) ?: return null
    wallpapers[wallpaper.key] = set.wallpaper
    return WallpaperAction(wallpaper, set.same, set.forBoth)
}

internal fun withSettings(paper: TypeWallPaper, settings: WallPaperSettings): TypeWallPaper = when (paper) {
    is WallPaper -> WallPaper(
        id = paper.id, creator = paper.creator, default = paper.default, pattern = paper.pattern, dark = paper.dark,
        accessHash = paper.accessHash, slug = paper.slug, document = paper.document, settings = settings,
    )
    is WallPaperNoFile -> WallPaperNoFile(id = paper.id, default = paper.default, dark = paper.dark, settings = settings)
}

internal fun wallpaperOf(paper: TypeWallPaper): Wallpaper? {
    val settings: WallPaperSettings?
    val kind: WallpaperKind
    val id: Long
    val dark: Boolean
    val isDefault: Boolean
    var slug = ""
    when (paper) {
        is WallPaper -> {
            if (paper.document !is Document) return null
            settings = paper.settings
            kind = if (paper.pattern) WallpaperKind.Pattern else WallpaperKind.Image
            id = paper.id
            dark = paper.dark
            isDefault = paper.default
            slug = paper.slug
        }
        is WallPaperNoFile -> {
            settings = paper.settings
            kind = WallpaperKind.Fill
            id = paper.id
            dark = paper.dark
            isDefault = paper.default
        }
    }
    val colors = listOf(settings?.backgroundColor, settings?.secondBackgroundColor, settings?.thirdBackgroundColor, settings?.fourthBackgroundColor)
        .takeWhile { it != null }.map { it!! and 0xffffff }
    if (kind == WallpaperKind.Fill && colors.isEmpty()) return null
    return Wallpaper(
        key = keyOf(id),
        kind = kind,
        colors = colors,
        rotation = settings?.rotation ?: 0,
        intensity = settings?.intensity ?: DEFAULT_PATTERN_INTENSITY,
        blur = settings?.blur == true,
        motion = settings?.motion == true,
        dark = dark,
        isDefault = isDefault,
        slug = slug,
    )
}

internal fun settingsOf(wallpaper: Wallpaper): WallPaperSettings {
    val colors = wallpaper.colors
    return WallPaperSettings(
        blur = wallpaper.blur,
        motion = wallpaper.motion,
        backgroundColor = colors.getOrNull(0),
        secondBackgroundColor = colors.getOrNull(1),
        thirdBackgroundColor = colors.getOrNull(2),
        fourthBackgroundColor = colors.getOrNull(3),
        intensity = if (wallpaper.kind == WallpaperKind.Pattern) wallpaper.intensity else null,
        rotation = if (colors.size >= 2) wallpaper.rotation else null,
    )
}
