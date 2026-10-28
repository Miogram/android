package app.miogram.data.telegram

import cn.ellerotta.core.model.ChatAction
import cn.ellerotta.core.model.Draft
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.FoundMessages
import cn.ellerotta.core.model.ForumTopic
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.MusicQuery
import cn.ellerotta.core.model.MusicTrack
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.model.AttachmentDraft
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.SendOptions
import app.miogram.data.MediaFileInfo
import cn.ellerotta.ktproto.api.functions.messages.SendMultiMedia
import cn.ellerotta.ktproto.api.functions.messages.UploadMedia
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.DocumentAttributeVideo
import cn.ellerotta.ktproto.api.types.InputDocument
import cn.ellerotta.ktproto.api.types.InputMediaPhoto
import cn.ellerotta.ktproto.api.types.InputPhoto
import cn.ellerotta.ktproto.api.types.InputSingleMedia
import cn.ellerotta.ktproto.api.types.MessageMediaDocument
import cn.ellerotta.ktproto.api.types.MessageMediaPhoto
import cn.ellerotta.ktproto.api.types.Photo
import cn.ellerotta.ktproto.api.types.TypeMessageMedia
import cn.ellerotta.ktproto.client.TelegramClient
import cn.ellerotta.core.model.UploadKind
import cn.ellerotta.ktproto.api.functions.channels.DeleteMessages as ChannelsDeleteMessages
import cn.ellerotta.ktproto.api.functions.messages.DeleteMessages
import cn.ellerotta.core.model.isVisual
import cn.ellerotta.ktproto.api.functions.messages.EditMessage
import cn.ellerotta.ktproto.api.functions.messages.ForwardMessages
import cn.ellerotta.ktproto.api.functions.messages.GetHistory
import cn.ellerotta.ktproto.api.functions.messages.GetReplies
import cn.ellerotta.ktproto.api.functions.messages.GetDiscussionMessage
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.core.model.ReplyThread
import cn.ellerotta.ktproto.api.functions.messages.Search
import cn.ellerotta.ktproto.api.types.InputMessagesFilterEmpty
import cn.ellerotta.ktproto.api.types.InputMessagesFilterGif
import cn.ellerotta.ktproto.api.types.InputMessagesFilterMusic
import cn.ellerotta.ktproto.api.types.InputMessagesFilterVoice
import cn.ellerotta.ktproto.api.functions.channels.ReadMessageContents as ChannelsReadMessageContents
import cn.ellerotta.ktproto.api.functions.messages.ReadMessageContents
import cn.ellerotta.ktproto.api.types.InputMessagesFilterPhotoVideo
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.functions.messages.SendMedia
import cn.ellerotta.ktproto.api.functions.messages.SendMessage
import cn.ellerotta.ktproto.api.functions.messages.SaveDraft
import cn.ellerotta.ktproto.api.functions.messages.SetTyping
import cn.ellerotta.ktproto.api.types.SendMessageCancelAction
import cn.ellerotta.ktproto.api.types.SendMessageStopDraftAction
import cn.ellerotta.ktproto.api.types.SendMessageRecordAudioAction
import cn.ellerotta.ktproto.api.types.SendMessageTypingAction
import cn.ellerotta.ktproto.api.types.SendMessageUploadAudioAction
import cn.ellerotta.ktproto.api.types.SendMessageUploadDocumentAction
import cn.ellerotta.ktproto.api.types.SendMessageUploadPhotoAction
import cn.ellerotta.ktproto.api.types.SendMessageUploadVideoAction
import cn.ellerotta.ktproto.api.types.DocumentAttributeAudio
import cn.ellerotta.ktproto.api.types.DocumentAttributeFilename
import cn.ellerotta.ktproto.api.types.InputMediaDocument
import cn.ellerotta.ktproto.api.types.InputMediaUploadedDocument
import cn.ellerotta.ktproto.api.types.InputMediaUploadedPhoto
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import cn.ellerotta.ktproto.api.types.TypeInputFile
import cn.ellerotta.ktproto.api.types.TypeInputMedia
import cn.ellerotta.ktproto.api.types.DraftMessage
import cn.ellerotta.ktproto.api.types.TypeChat
import cn.ellerotta.ktproto.api.types.TypeMessage
import cn.ellerotta.ktproto.api.types.TypeUser
import cn.ellerotta.ktproto.api.types.messages.ChannelMessages
import cn.ellerotta.ktproto.api.types.messages.Messages
import cn.ellerotta.ktproto.api.types.messages.MessagesSlice
import cn.ellerotta.ktproto.api.types.messages.TypeMessages
import cn.ellerotta.ktproto.client.Upload
import cn.ellerotta.ktproto.client.UploadSource
import cn.ellerotta.ktproto.client.encodeWaveform
import cn.ellerotta.ktproto.client.uploadFile
import cn.ellerotta.ktproto.session.RpcException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.OutgoingLast
import cn.ellerotta.core.model.OutgoingUploads
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class OutgoingMedia(
    val name: String,
    val mimeType: String,
    val source: UploadSource,
    val asFile: Boolean = false,
    val spoiler: Boolean = false,
    val info: MediaFileInfo = MediaFileInfo(),
    val thumb: ByteArray? = null,
)

data class AlbumProgress(val index: Int, val progress: DownloadState.Progress)

data class StoredHistory(val messages: List<MessageItem>, val complete: Boolean)

@Singleton
class MessagesRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    suspend fun history(dialogKey: String, limit: Int = 50, before: Int = 0, topicId: Int? = null): List<MessageItem> {
        val cache = telegram.cache ?: return itemsOf(page(dialogKey, before, 0, limit, topicId = topicId), dialogKey)
        val topic = topicId ?: 0
        if (before != 0) {
            cache.older(dialogKey, topic, before, limit)?.takeIf { it.complete || it.messages.size >= limit }?.let { return itemsOf(it, dialogKey) }
            val result = page(dialogKey, before, 0, limit, topicId = topicId)
            val (messages, users, chats) = partsOf(result)
            cache.putOlder(dialogKey, topic, before, messages, users, chats, limit)
            return itemsOf(result, dialogKey)
        }
        cache.loading(dialogKey, topic)
        val result = try {
            page(dialogKey, 0, 0, limit, topicId = topicId)
        } catch (e: Throwable) {
            withContext(NonCancellable) { cache.failed(dialogKey, topic) }
            throw e
        }
        val (messages, users, chats) = partsOf(result)
        withContext(NonCancellable) { cache.putNewest(dialogKey, topic, messages, users, chats, limit) }
        return itemsOf(result, dialogKey)
    }

    suspend fun stored(dialogKey: String, limit: Int = 50, topicId: Int? = null): StoredHistory? {
        val stored = telegram.cache?.newest(dialogKey, topicId ?: 0, limit) ?: return null
        if (stored.messages.isEmpty() && !stored.complete) return null
        return StoredHistory(itemsOf(stored, dialogKey), stored.complete)
    }

    suspend fun historyAround(dialogKey: String, id: Int, limit: Int = 50, topicId: Int? = null): List<MessageItem> =
        itemsOf(page(dialogKey, id + 1, -limit / 2, limit, topicId = topicId), dialogKey)

    suspend fun historyAfter(dialogKey: String, after: Int, limit: Int = 50, topicId: Int? = null): List<MessageItem> =
        itemsOf(page(dialogKey, after + 1, -limit, limit, minId = after, topicId = topicId), dialogKey)

    private suspend fun page(dialogKey: String, offsetId: Int, addOffset: Int, limit: Int, minId: Int = 0, topicId: Int? = null): TypeMessages {
        val peer = registry.peer(dialogKey)
        val client = telegram.client()
        return if (topicId != null) client.invoke(GetReplies(peer, topicId, offsetId, 0, addOffset, limit, 0, minId, 0)) else client.invoke(GetHistory(peer, offsetId, 0, addOffset, limit, 0, minId, 0))
    }

    private fun partsOf(result: TypeMessages): Triple<List<TypeMessage>, List<TypeUser>, List<TypeChat>> = when (result) {
        is Messages -> Triple(result.messages, result.users, result.chats)
        is MessagesSlice -> Triple(result.messages, result.users, result.chats)
        is ChannelMessages -> Triple(result.messages, result.users, result.chats)
        else -> Triple(emptyList(), emptyList(), emptyList())
    }

    private fun itemsOf(result: TypeMessages, dialogKey: String): List<MessageItem> {
        val (messages, users, chats) = partsOf(result)
        registry.rememberUsers(users)
        registry.rememberChats(chats)
        return messages.mapNotNull { registry.itemOf(it, dialogKey) }.sortedBy { it.id }
    }

    private fun itemsOf(stored: CachedMessages, dialogKey: String): List<MessageItem> {
        registry.rememberUsers(stored.users)
        registry.rememberChats(stored.chats)
        return stored.messages.mapNotNull { message ->
            registry.itemOf(message, dialogKey)?.let { item -> stored.reactions[item.id]?.let { item.copy(reactions = registry.reactionsOf(it), reactorsListed = it.canSeeList) } ?: item }
        }.sortedBy { it.id }
    }

    suspend fun visualMedia(dialogKey: String, around: Int, limit: Int = 40, gifs: Boolean = false): List<MessageItem> {
        val search = Search(
            peer = registry.peer(dialogKey),
            q = "",
            filter = if (gifs) InputMessagesFilterGif else InputMessagesFilterPhotoVideo,
            minDate = 0,
            maxDate = 0,
            offsetId = around + 1,
            addOffset = -limit / 2,
            limit = limit,
            maxId = 0,
            minId = 0,
            hash = 0,
        )
        return itemsOf(telegram.client().invoke(search), dialogKey).filter { it.media?.isVisual == true }
    }

    suspend fun music(dialogKey: String, query: MusicQuery): List<MusicTrack> {
        val search = Search(
            peer = registry.peer(dialogKey),
            q = "",
            filter = InputMessagesFilterMusic,
            minDate = 0,
            maxDate = 0,
            offsetId = query.offsetId,
            addOffset = query.addOffset,
            limit = query.limit,
            maxId = 0,
            minId = query.minId,
            hash = 0,
        )
        return itemsOf(telegram.client().invoke(search), dialogKey).mapNotNull { message ->
            message.media?.takeIf { it.kind == MediaKind.Music }?.let { MusicTrack(dialogKey, message.id, it) }
        }
    }

    suspend fun voices(dialogKey: String, query: MusicQuery): List<MusicTrack> {
        val search = Search(
            peer = registry.peer(dialogKey),
            q = "",
            filter = InputMessagesFilterVoice,
            minDate = 0,
            maxDate = 0,
            offsetId = query.offsetId,
            addOffset = query.addOffset,
            limit = query.limit,
            maxId = 0,
            minId = query.minId,
            hash = 0,
        )
        return itemsOf(telegram.client().invoke(search), dialogKey).mapNotNull { voiceTrackOf(dialogKey, it) }
    }

    fun voiceTrackOf(dialogKey: String, message: MessageItem): MusicTrack? {
        val media = message.media?.takeIf { it.kind == MediaKind.Voice } ?: return null
        val sender = message.senderId.takeIf { it != 0L }?.let { "u$it" } ?: dialogKey
        return MusicTrack(
            dialogKey = dialogKey,
            messageId = message.id,
            media = media,
            senderName = registry.peerNames[sender] ?: message.senderName ?: registry.peerNames[dialogKey],
            senderId = message.senderId,
            avatarKey = registry.peerAvatars[sender],
            out = message.out,
            unread = !message.out && message.mediaUnread,
        )
    }

    suspend fun readContents(dialogKey: String, ids: List<Int>) {
        if (peerOfKey(dialogKey) is PeerChannel) {
            telegram.client().invoke(ChannelsReadMessageContents(registry.channel(dialogKey), ids))
        } else {
            telegram.client().invoke(ReadMessageContents(ids))
        }
    }

    suspend fun richMessage(dialogKey: String, id: Int): MessageItem? = telegram.richMessage(dialogKey, id)

    suspend fun discussion(dialogKey: String, id: Int): ReplyThread {
        val answer = telegram.client().invoke(GetDiscussionMessage(registry.peer(dialogKey), id))
        registry.rememberUsers(answer.users)
        registry.rememberChats(answer.chats)
        val first = answer.messages.minByOrNull { (it as? Message)?.id ?: Int.MAX_VALUE } ?: error("no discussion message")
        val key = keyOf(first) ?: dialogKey
        val items = answer.messages.mapNotNull { registry.itemOf(it, key) }.sortedBy { it.id }
        val top = items.firstOrNull()?.id ?: id
        return ReplyThread(
            dialogKey = key,
            topId = top,
            messages = items,
            maxId = answer.maxId,
            readInboxMaxId = answer.readInboxMaxId,
            readOutboxMaxId = answer.readOutboxMaxId,
            unread = answer.unreadCount,
            comments = key != dialogKey,
        )
    }

    suspend fun search(dialogKey: String, query: String, offsetId: Int = 0, limit: Int = 30, topicId: Int? = null): FoundMessages {
        val search = Search(
            peer = registry.peer(dialogKey),
            q = query,
            topMsgId = topicId,
            filter = InputMessagesFilterEmpty,
            minDate = 0,
            maxDate = 0,
            offsetId = offsetId,
            addOffset = 0,
            limit = limit,
            maxId = 0,
            minId = 0,
            hash = 0,
        )
        val result = telegram.client().invoke(search)
        val messages = itemsOf(result, dialogKey).reversed()
        val total = when (result) {
            is MessagesSlice -> result.count
            is ChannelMessages -> result.count
            else -> messages.size
        }
        return FoundMessages(messages, maxOf(total, messages.size))
    }

    suspend fun send(
        dialogKey: String,
        text: String,
        replyTo: Int? = null,
        entities: List<TextEntity> = emptyList(),
        topicId: Int? = null,
        randomId: Long = Random.nextLong(),
        options: SendOptions = SendOptions.None,
    ) = queue.await(queueText(dialogKey, text, replyTo, entities, topicId, randomId, options))

    private val queue get() = telegram.queue

    suspend fun queueText(
        dialogKey: String,
        text: String,
        replyTo: Int? = null,
        entities: List<TextEntity> = emptyList(),
        topicId: Int? = null,
        randomId: Long = Random.nextLong(),
        options: SendOptions = SendOptions.None,
    ): Long = queue.text(dialogKey, text, entities, replyTo, topicId, options, randomId)

    suspend fun awaitOutgoing(batch: Long) = queue.await(batch)

    fun outgoing(dialogKey: String, topicId: Int?): Flow<List<MessageItem>> = queue.shown(dialogKey, topicId)

    val outgoingProgress: Flow<Map<String, DownloadState.Progress>> get() = queue.progress

    val outgoingLast: Flow<Map<String, OutgoingLast>> get() = queue.last

    val outgoingUploads: Flow<OutgoingUploads?> get() = queue.uploads

    val outgoingSent: Flow<String> get() = queue.sent

    val outgoingBusy: Flow<Boolean> get() = queue.busy

    fun outgoingKeys(batch: Long, count: Int): List<String> = List(count) { OutgoingQueue.uploadKey(batch + it) }

    suspend fun retryOutgoing(dialogKey: String, id: Int): Long? {
        val batch = queue.batchOf(dialogKey, id) ?: return null
        queue.retry(batch)
        return batch
    }

    suspend fun dropOutgoing(dialogKey: String, id: Int): Boolean {
        val batch = queue.batchOf(dialogKey, id) ?: return false
        queue.drop(batch)
        return true
    }

    suspend fun saveDraft(dialogKey: String, draft: Draft) {
        val message = if (draft.isEmpty) "" else draft.text.text
        val entities = if (draft.isEmpty) null else registry.inputEntitiesOf(draft.text.entities).ifEmpty { null }
        val reply = draft.replyTo?.takeIf { !draft.isEmpty }?.let { InputReplyToMessage(replyToMsgId = it) }
        telegram.client().invoke(SaveDraft(replyTo = reply, peer = registry.peer(dialogKey), message = message, entities = entities))
        telegram.cache?.draft(dialogKey, if (draft.isEmpty) null else DraftMessage(replyTo = reply, message = message, entities = entities, date = (System.currentTimeMillis() / 1000).toInt()))
    }

    suspend fun setTyping(dialogKey: String, action: ChatAction, topicId: Int? = null) {
        val sent = when (action) {
            ChatAction.Typing -> SendMessageTypingAction
            ChatAction.Cancel -> SendMessageCancelAction
            ChatAction.RecordVoice -> SendMessageRecordAudioAction
            is ChatAction.Upload -> when (action.kind) {
                UploadKind.Photo -> SendMessageUploadPhotoAction(action.progress)
                UploadKind.Video -> SendMessageUploadVideoAction(action.progress)
                UploadKind.Voice -> SendMessageUploadAudioAction(action.progress)
                UploadKind.File -> SendMessageUploadDocumentAction(action.progress)
            }
            is ChatAction.StopDraft -> SendMessageStopDraftAction(action.randomId)
        }
        telegram.client().invoke(SetTyping(peer = registry.peer(dialogKey), topMsgId = topicId?.takeIf { it != ForumTopic.GENERAL_TOPIC }, action = sent))
    }

    suspend fun remind(dialogKey: String, id: Int, date: Int) {
        telegram.client().invoke(
            ForwardMessages(fromPeer = registry.peer(dialogKey), id = listOf(id), randomId = listOf(Random.nextLong()), toPeer = InputPeerSelf, scheduleDate = date),
        )
    }

    suspend fun forward(fromKey: String, ids: List<Int>, toKey: String, dropAuthor: Boolean = false, dropCaptions: Boolean = false) =
        queue.await(queueForward(fromKey, ids, toKey, dropAuthor, dropCaptions))

    suspend fun queueForward(fromKey: String, ids: List<Int>, toKey: String, dropAuthor: Boolean = false, dropCaptions: Boolean = false, topicId: Int? = null): Long =
        queue.forward(fromKey, ids, toKey, dropAuthor, dropCaptions, topicId)

    suspend fun sendDocument(dialogKey: String, media: MediaItem, topicId: Int? = null, replyTo: Int? = null) =
        queue.await(queueDocument(dialogKey, media, topicId, replyTo))

    suspend fun queueDocument(dialogKey: String, media: MediaItem, topicId: Int? = null, replyTo: Int? = null): Long = queue.document(dialogKey, media, replyTo, topicId)

    suspend fun edit(dialogKey: String, id: Int, text: String, entities: List<TextEntity> = emptyList()) = queue.await(queueEdit(dialogKey, id, text, entities))

    suspend fun queueEdit(dialogKey: String, id: Int, text: String, entities: List<TextEntity> = emptyList()): Long = queue.edit(dialogKey, id, text, entities)

    suspend fun delete(dialogKey: String, ids: List<Int>, revoke: Boolean) {
        if (peerOfKey(dialogKey) is PeerChannel) {
            telegram.client().invoke(ChannelsDeleteMessages(registry.channel(dialogKey), ids))
        } else {
            telegram.client().invoke(DeleteMessages(revoke = revoke, id = ids))
        }
    }

    fun sendFile(
        dialogKey: String,
        media: OutgoingMedia,
        caption: FormattedText = FormattedText(""),
        replyTo: Int? = null,
        topicId: Int? = null,
        randomId: Long = Random.nextLong(),
        options: SendOptions = SendOptions.None,
    ): Flow<DownloadState.Progress> = followed(1) { queueFile(dialogKey, media, caption, replyTo, topicId, randomId, options) }.map { it.progress }

    suspend fun queueFile(
        dialogKey: String,
        media: OutgoingMedia,
        caption: FormattedText = FormattedText(""),
        replyTo: Int? = null,
        topicId: Int? = null,
        randomId: Long = Random.nextLong(),
        options: SendOptions = SendOptions.None,
    ): Long = queue.file(dialogKey, media, caption, replyTo, topicId, options, randomId)

    fun sendAlbum(
        dialogKey: String,
        items: List<OutgoingMedia>,
        caption: FormattedText = FormattedText(""),
        replyTo: Int? = null,
        topicId: Int? = null,
        randomIds: List<Long> = items.map { Random.nextLong() },
        options: SendOptions = SendOptions.None,
    ): Flow<AlbumProgress> = followed(items.size) { queueAlbum(dialogKey, items, caption, replyTo, topicId, randomIds, options) }

    suspend fun queueAlbum(
        dialogKey: String,
        items: List<OutgoingMedia>,
        caption: FormattedText = FormattedText(""),
        replyTo: Int? = null,
        topicId: Int? = null,
        randomIds: List<Long> = items.map { Random.nextLong() },
        options: SendOptions = SendOptions.None,
    ): Long {
        require(items.size in 2..AttachmentDraft.MAX_ITEMS && randomIds.size == items.size) { "an album has 2 to ${AttachmentDraft.MAX_ITEMS} items" }
        return queue.album(dialogKey, items, caption, replyTo, topicId, options, randomIds)
    }

    fun uploadKindOf(mimeType: String, size: Long, asFile: Boolean = false): UploadKind = uploadKindOfFile(mimeType, size, asFile)

    fun sendVoice(
        dialogKey: String,
        recording: File,
        duration: Int,
        waveform: List<Int>,
        topicId: Int? = null,
        randomId: Long = Random.nextLong(),
        replyTo: Int? = null,
        options: SendOptions = SendOptions.None,
    ): Flow<DownloadState.Progress> = followed(1) { queueVoice(dialogKey, recording, duration, waveform, topicId, randomId, replyTo, options) }.map { it.progress }

    suspend fun queueVoice(
        dialogKey: String,
        recording: File,
        duration: Int,
        waveform: List<Int>,
        topicId: Int? = null,
        randomId: Long = Random.nextLong(),
        replyTo: Int? = null,
        options: SendOptions = SendOptions.None,
    ): Long = queue.voice(dialogKey, recording, duration, waveform, replyTo, topicId, options, randomId)

    private fun followed(count: Int, enqueue: suspend () -> Long): Flow<AlbumProgress> = channelFlow {
        val batch = enqueue()
        val keys = List(count) { OutgoingQueue.uploadKey(batch + it) }
        val progress = launch {
            queue.progress.collect { all -> keys.forEachIndexed { index, key -> all[key]?.let { send(AlbumProgress(index, it)) } } }
        }
        try {
            queue.await(batch)
        } finally {
            progress.cancel()
        }
    }.distinctUntilChanged()
}

internal fun replyToOf(replyTo: Int?, topicId: Int?): InputReplyToMessage? {
    val topic = topicId?.takeIf { it != ForumTopic.GENERAL_TOPIC }
    return when {
        replyTo != null -> InputReplyToMessage(replyToMsgId = replyTo, topMsgId = topic?.takeIf { it != replyTo })
        topic != null -> InputReplyToMessage(replyToMsgId = topic)
        else -> null
    }
}

private val PHOTO_TYPES = setOf("image/jpeg", "image/png", "image/webp")
private const val MAX_PHOTO_SIZE = 10L * 1024 * 1024

internal fun uploadKindOfFile(mimeType: String, size: Long, asFile: Boolean): UploadKind = when {
    mimeType in PHOTO_TYPES && !asFile && size <= MAX_PHOTO_SIZE -> UploadKind.Photo
    mimeType.startsWith("video/") && !asFile -> UploadKind.Video
    else -> UploadKind.File
}

internal fun inputMediaOf(media: OutgoingMedia, file: TypeInputFile): TypeInputMedia {
    if (uploadKindOfFile(media.mimeType, media.source.size, media.asFile) == UploadKind.Photo) return InputMediaUploadedPhoto(spoiler = spoilerOf(media), file = file)
    val attributes = when {
        media.asFile -> listOf(DocumentAttributeFilename(media.name))
        videoOf(media) -> listOf(
            DocumentAttributeVideo(supportsStreaming = true, duration = media.info.duration, w = media.info.width, h = media.info.height),
            DocumentAttributeFilename(media.name),
        )
        media.mimeType.startsWith("audio/") -> listOf(
            DocumentAttributeAudio(duration = media.info.duration.toInt(), title = media.info.title, performer = media.info.performer),
            DocumentAttributeFilename(media.name),
        )
        else -> listOf(DocumentAttributeFilename(media.name))
    }
    return InputMediaUploadedDocument(forceFile = media.asFile, spoiler = spoilerOf(media), file = file, mimeType = media.mimeType, attributes = attributes)
}

internal fun videoOf(media: OutgoingMedia) = media.mimeType.startsWith("video/") && !media.asFile

private fun spoilerOf(media: OutgoingMedia) = media.spoiler && !media.asFile && (media.mimeType in PHOTO_TYPES || media.mimeType.startsWith("video/"))
