package app.miogram.data.telegram

import app.miogram.data.MediaFileInfo
import cn.ellerotta.core.model.ArticleGate
import cn.ellerotta.core.model.ArticleMediaKind
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.ktproto.api.functions.messages.EditMessage
import cn.ellerotta.ktproto.api.functions.messages.SaveDraft
import cn.ellerotta.ktproto.api.functions.messages.SendMessage
import cn.ellerotta.ktproto.api.functions.messages.UploadMedia
import cn.ellerotta.ktproto.api.functions.users.GetFullUser
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.DocumentAttributeAudio
import cn.ellerotta.ktproto.api.types.DocumentAttributeFilename
import cn.ellerotta.ktproto.api.types.DocumentAttributeVideo
import cn.ellerotta.ktproto.api.types.InputMediaUploadedDocument
import cn.ellerotta.ktproto.api.types.InputMediaUploadedPhoto
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import cn.ellerotta.ktproto.api.types.InputUserSelf
import cn.ellerotta.ktproto.api.types.MessageMediaDocument
import cn.ellerotta.ktproto.api.types.MessageMediaPhoto
import cn.ellerotta.ktproto.api.types.Photo
import cn.ellerotta.ktproto.api.types.TypeInputFile
import cn.ellerotta.ktproto.api.types.TypeInputMedia
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.client.Upload
import cn.ellerotta.ktproto.client.UploadSource
import cn.ellerotta.ktproto.client.uploadFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class ArticlesRepository @Inject constructor(private val telegram: TelegramClientHolder, private val appConfig: AppConfigRepository) {
    private val registry get() = telegram.peers

    suspend fun gate(): ArticleGate {
        val config = appConfig.config()
        val self = telegram.client().invoke(GetFullUser(InputUserSelf)).users.filterIsInstance<User>().firstOrNull { it.self }
        return ArticleGate(config.articlePosting, self?.premium == true, config.articleLimits)
    }

    suspend fun send(dialogKey: String, article: ArticleMessage, replyTo: Int? = null, topicId: Int? = null) {
        telegram.client().invoke(
            SendMessage(
                noWebpage = true,
                clearDraft = true,
                peer = registry.peer(dialogKey),
                replyTo = replyToOf(replyTo, topicId),
                message = "",
                randomId = Random.nextLong(),
                richMessage = registry.inputRichMessageOf(article),
            ),
        )
    }

    suspend fun edit(dialogKey: String, id: Int, article: ArticleMessage) {
        telegram.client().invoke(EditMessage(peer = registry.peer(dialogKey), id = id, richMessage = registry.inputRichMessageOf(article)))
    }

    suspend fun saveDraft(dialogKey: String, article: ArticleMessage, replyTo: Int? = null) {
        val reply = replyTo?.let { InputReplyToMessage(replyToMsgId = it) }
        telegram.client().invoke(SaveDraft(replyTo = reply, peer = registry.peer(dialogKey), message = "", richMessage = registry.inputRichMessageOf(article, withUsers = false)))
    }

    suspend fun upload(kind: ArticleMediaKind, name: String, mimeType: String, source: UploadSource, info: MediaFileInfo = MediaFileInfo(), onProgress: (Long, Long) -> Unit = { _, _ -> }): MediaItem {
        val client = telegram.client()
        var uploaded: TypeInputFile? = null
        client.uploadFile(name, source).collect { step ->
            when (step) {
                is Upload.Progress -> onProgress(step.uploaded, step.total)
                is Upload.Done -> uploaded = step.file
            }
        }
        val file = uploaded ?: error("the upload ended without a file")
        return when (val media = client.invoke(UploadMedia(peer = InputPeerSelf, media = articleMediaOf(kind, file, name, mimeType, info)))) {
            is MessageMediaPhoto -> (media.photo as? Photo)?.let { registry.photoItem(it, null) }
            is MessageMediaDocument -> (media.document as? Document)?.let { registry.documentOf(media, it, null) }
            else -> null
        } ?: error("telegram made no ${kind.name.lowercase()} of $name")
    }
}

internal fun articleMediaOf(kind: ArticleMediaKind, file: TypeInputFile, name: String, mimeType: String, info: MediaFileInfo): TypeInputMedia = when (kind) {
    ArticleMediaKind.Photo -> InputMediaUploadedPhoto(file = file)
    ArticleMediaKind.Video -> InputMediaUploadedDocument(
        file = file,
        mimeType = "video/mp4",
        attributes = listOf(DocumentAttributeVideo(supportsStreaming = true, duration = info.duration, w = info.width, h = info.height)),
    )
    ArticleMediaKind.Audio -> InputMediaUploadedDocument(
        file = file,
        mimeType = mimeType,
        attributes = listOf(DocumentAttributeAudio(duration = info.duration.toInt(), title = info.title, performer = info.performer), DocumentAttributeFilename(name)),
    )
    ArticleMediaKind.File -> InputMediaUploadedDocument(forceFile = true, file = file, mimeType = "application/octet-stream", attributes = listOf(DocumentAttributeFilename(name)))
}
