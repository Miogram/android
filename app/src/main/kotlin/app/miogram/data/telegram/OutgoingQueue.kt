package app.miogram.data.telegram

import cn.ellerotta.core.database.OutgoingDao
import cn.ellerotta.core.database.OutgoingRow
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.ForumTopic
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.OutgoingLast
import cn.ellerotta.core.model.OutgoingUploads
import cn.ellerotta.core.model.SendOptions
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.ktproto.api.functions.messages.EditMessage
import cn.ellerotta.ktproto.api.functions.messages.ForwardMessages
import cn.ellerotta.ktproto.api.functions.messages.SendMedia
import cn.ellerotta.ktproto.api.functions.messages.SendMessage
import cn.ellerotta.ktproto.api.functions.messages.SendMultiMedia
import cn.ellerotta.ktproto.api.functions.messages.UploadMedia
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.DocumentAttributeAnimated
import cn.ellerotta.ktproto.api.types.DocumentAttributeAudio
import cn.ellerotta.ktproto.api.types.DocumentAttributeFilename
import cn.ellerotta.ktproto.api.types.DocumentAttributeImageSize
import cn.ellerotta.ktproto.api.types.DocumentAttributeSticker
import cn.ellerotta.ktproto.api.types.DocumentAttributeVideo
import cn.ellerotta.ktproto.api.types.GeoPoint
import cn.ellerotta.ktproto.api.types.InputDocument
import cn.ellerotta.ktproto.api.types.InputFile
import cn.ellerotta.ktproto.api.types.InputMediaContact
import cn.ellerotta.ktproto.api.types.InputMediaDocument
import cn.ellerotta.ktproto.api.types.InputMediaGeoPoint
import cn.ellerotta.ktproto.api.types.InputMediaPhoto
import cn.ellerotta.ktproto.api.types.InputMediaPoll
import cn.ellerotta.ktproto.api.types.InputMediaUploadedDocument
import cn.ellerotta.ktproto.api.types.InputMediaUploadedPhoto
import cn.ellerotta.ktproto.api.types.InputMediaVenue
import cn.ellerotta.ktproto.api.types.InputPhoto
import cn.ellerotta.ktproto.api.types.InputGeoPoint
import cn.ellerotta.ktproto.api.types.InputSingleMedia
import cn.ellerotta.ktproto.api.types.InputStickerSetEmpty
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageFwdHeader
import cn.ellerotta.ktproto.api.types.MessageMediaContact
import cn.ellerotta.ktproto.api.types.MessageMediaDocument
import cn.ellerotta.ktproto.api.types.MessageMediaGeo
import cn.ellerotta.ktproto.api.types.MessageMediaPhoto
import cn.ellerotta.ktproto.api.types.MessageMediaPoll
import cn.ellerotta.ktproto.api.types.MessageMediaVenue
import cn.ellerotta.ktproto.api.types.MessageReplyHeader
import cn.ellerotta.ktproto.api.types.Photo
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.PollResults
import cn.ellerotta.ktproto.api.types.TypeDocumentAttribute
import cn.ellerotta.ktproto.api.types.TypeInputFile
import cn.ellerotta.ktproto.api.types.TypeInputMedia
import cn.ellerotta.ktproto.api.types.TypeMessage
import cn.ellerotta.ktproto.api.types.TypeMessageEntity
import cn.ellerotta.ktproto.api.types.TypeMessageMedia
import cn.ellerotta.ktproto.client.TelegramClient
import cn.ellerotta.ktproto.client.UPLOAD_PART_SIZE
import cn.ellerotta.ktproto.client.Upload
import cn.ellerotta.ktproto.client.UploadSource
import cn.ellerotta.ktproto.client.encodeWaveform
import cn.ellerotta.ktproto.client.uploadFile
import cn.ellerotta.ktproto.session.RpcException
import cn.ellerotta.ktproto.tl.TlObject
import cn.ellerotta.ktproto.tl.decode
import cn.ellerotta.ktproto.tl.toByteArray
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class OutgoingQueue(
    private val telegram: TelegramClientHolder,
    private val dao: OutgoingDao?,
    private val folder: File,
    private val scope: CoroutineScope,
) {
    private val registry get() = telegram.peers
    private val lock = Mutex()
    private val rowsState = MutableStateFlow<List<OutgoingRow>>(emptyList())
    private val progressState = MutableStateFlow<Map<Long, DownloadState.Progress>>(emptyMap())
    private val failures = ConcurrentHashMap<Long, Exception>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val sentFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)

    val sent: SharedFlow<String> = sentFlow.asSharedFlow()

    init {
        scope.launch { telegram.authorized.collectLatest { if (it) run() } }
        scope.launch { telegram.accountEnded.collect { forget() } }
    }

    fun shown(dialogKey: String, topicId: Int?): Flow<List<MessageItem>> = rowsState.map { rows ->
        rows.filter { it.dialog == dialogKey && it.topic == (topicId ?: 0) }.mapNotNull { row -> itemOf(row, rows) }
    }.distinctUntilChanged()

    val progress: Flow<Map<String, DownloadState.Progress>> = progressState.map { all -> all.mapKeys { uploadKey(it.key) } }

    val last: Flow<Map<String, OutgoingLast>> = rowsState.map { rows ->
        rows.filter { it.shown != null }.groupBy { it.dialog }.mapValues { (_, own) ->
            val newest = own.maxBy { it.id }
            val message = TypeMessage.decode(newest.shown!!)
            OutgoingLast(previewOf(message, null, registry), (message as? Message)?.date ?: newest.date, newest.failed != null)
        }
    }.distinctUntilChanged()

    val uploads: Flow<OutgoingUploads?> = combine(rowsState, progressState) { rows, progress ->
        val files = rows.filter { it.file != null && it.failed == null && it.media == null }
        if (files.isEmpty()) return@combine null
        val sizes = files.associate { it.id to File(folder, it.file!!).length() }
        OutgoingUploads(files.size, files.sumOf { progress[it.id]?.downloaded ?: 0L }, sizes.values.sum())
    }.distinctUntilChanged()

    suspend fun text(dialogKey: String, text: String, entities: List<TextEntity>, replyTo: Int?, topicId: Int?, options: SendOptions, randomId: Long): Long {
        val sent = registry.inputEntitiesOf(entities).ifEmpty { null }
        val request = SendMessage(silent = options.silent, clearDraft = true, peer = registry.peer(dialogKey), replyTo = replyToOf(replyTo, topicId), message = text, randomId = randomId, entities = sent, scheduleDate = options.scheduleDate)
        return enqueue(listOf(row(dialogKey, topicId, request, shownOf(dialogKey, text, sent, replyTo, topicId, null, options))))
    }

    val busy: Flow<Boolean> = rowsState.map { rows -> rows.any { it.failed == null } }.distinctUntilChanged()

    suspend fun file(dialogKey: String, media: OutgoingMedia, caption: FormattedText, replyTo: Int?, topicId: Int?, options: SendOptions, randomId: Long): Long =
        enqueue(listOf(fileRow(dialogKey, media, caption, replyTo, topicId, options, randomId, null)))

    suspend fun album(dialogKey: String, items: List<OutgoingMedia>, caption: FormattedText, replyTo: Int?, topicId: Int?, options: SendOptions, randomIds: List<Long>): Long {
        val group = Random.nextLong()
        return enqueue(items.mapIndexed { index, item -> fileRow(dialogKey, item, if (index == 0) caption else FormattedText(""), replyTo, topicId, options, randomIds[index], group) })
    }

    suspend fun voice(dialogKey: String, recording: File, duration: Int, waveform: List<Int>, replyTo: Int?, topicId: Int?, options: SendOptions, randomId: Long): Long {
        val name = copyName("ogg")
        withContext(Dispatchers.IO) {
            folder.mkdirs()
            val kept = File(folder, name)
            if (!recording.renameTo(kept)) {
                recording.copyTo(kept, overwrite = true)
                recording.delete()
            }
        }
        val packed = waveform.takeIf { it.isNotEmpty() }?.let { encodeWaveform(it.toIntArray()) }
        val attributes = listOf(DocumentAttributeAudio(voice = true, duration = duration, waveform = packed))
        val media = InputMediaUploadedDocument(file = placeholder("voice.ogg"), mimeType = VOICE_TYPE, attributes = attributes)
        val request = SendMedia(silent = options.silent, peer = registry.peer(dialogKey), replyTo = replyToOf(replyTo, topicId), media = media, message = "", randomId = randomId, scheduleDate = options.scheduleDate)
        val shown = shownOf(dialogKey, "", null, replyTo, topicId, uploadDocument(VOICE_TYPE, File(folder, name).length(), attributes), options)
        return enqueue(listOf(row(dialogKey, topicId, request, shown, file = name)))
    }

    suspend fun document(dialogKey: String, media: MediaItem, replyTo: Int?, topicId: Int?): Long {
        val input = registry.inputDocuments[media.key] ?: error("unknown document ${media.key}")
        val request = SendMedia(peer = registry.peer(dialogKey), replyTo = replyToOf(replyTo, topicId), media = InputMediaDocument(id = input), message = "", randomId = Random.nextLong())
        val attributes: List<TypeDocumentAttribute> = if (media.kind == MediaKind.Gif) {
            listOf(DocumentAttributeAnimated, DocumentAttributeVideo(duration = media.duration.toDouble(), w = media.width, h = media.height))
        } else {
            listOf(DocumentAttributeSticker(alt = media.emoji.orEmpty(), stickerset = registry.stickerSetOf[media.key] ?: InputStickerSetEmpty), DocumentAttributeImageSize(media.width, media.height))
        }
        val document = Document(
            id = input.id, accessHash = input.accessHash, fileReference = input.fileReference, date = now(), mimeType = media.mimeType ?: "image/webp",
            size = media.size ?: 0, dcId = media.dcId, attributes = attributes,
        )
        return enqueue(listOf(row(dialogKey, topicId, request, shownOf(dialogKey, "", null, replyTo, topicId, MessageMediaDocument(document = document), SendOptions.None))))
    }

    suspend fun media(dialogKey: String, media: TypeInputMedia, replyTo: Int?, topicId: Int?): Long {
        val request = SendMedia(peer = registry.peer(dialogKey), replyTo = replyToOf(replyTo, topicId), media = media, message = "", randomId = Random.nextLong())
        return enqueue(listOf(row(dialogKey, topicId, request, shownOf(dialogKey, "", null, replyTo, topicId, shownMediaOf(media), SendOptions.None))))
    }

    suspend fun forward(fromKey: String, ids: List<Int>, toKey: String, dropAuthor: Boolean, dropCaptions: Boolean, topicId: Int?, options: SendOptions = SendOptions.None): Long {
        val sorted = ids.sorted()
        val request = ForwardMessages(
            silent = options.silent,
            dropAuthor = dropAuthor || dropCaptions,
            dropMediaCaptions = dropCaptions,
            fromPeer = registry.peer(fromKey),
            id = sorted,
            randomId = sorted.map { Random.nextLong() },
            toPeer = registry.peer(toKey),
            topMsgId = topicId?.takeIf { it != ForumTopic.GENERAL_TOPIC },
            scheduleDate = options.scheduleDate,
        )
        return enqueue(sorted.map { id ->
            val original = telegram.cache?.message(fromKey, id) as? Message
            val shown = original?.let {
                val header = if (dropAuthor || dropCaptions) null else MessageFwdHeader(fromId = it.fwdFrom?.fromId ?: it.fromId ?: it.peerId, date = it.fwdFrom?.date ?: it.date)
                val text = if (dropCaptions && it.media != null) "" else it.message
                shownOf(toKey, text, it.entities, null, topicId, it.media, options, fwd = header)
            }
            row(toKey, topicId, request, shown)
        })
    }

    suspend fun edit(dialogKey: String, id: Int, text: String, entities: List<TextEntity>): Long =
        enqueue(listOf(row(dialogKey, null, EditMessage(peer = registry.peer(dialogKey), id = id, message = text, entities = registry.inputEntitiesOf(entities)), null)))

    suspend fun await(batch: Long) {
        val rows = rowsState.first { list -> list.none { it.batch == batch && it.failed == null } }
        val failed = rows.firstOrNull { it.batch == batch } ?: return
        throw failures[batch] ?: IllegalStateException(failed.failed)
    }

    fun batchOf(dialogKey: String, messageId: Int): Long? = rowsState.value.firstOrNull { it.dialog == dialogKey && localIdOf(it) == messageId }?.batch

    suspend fun retry(batch: Long) {
        lock.withLock {
            val ids = rowsState.value.filter { it.batch == batch && it.failed != null }.map { it.id }
            if (ids.isEmpty()) return
            failures.remove(batch)
            dao?.failed(ids, null)
            rowsState.update { rows -> rows.map { if (it.id in ids) it.copy(failed = null) else it } }
        }
        wake.trySend(Unit)
    }

    suspend fun drop(batch: Long) = lock.withLock {
        val gone = rowsState.value.filter { it.batch == batch && it.failed != null }
        removeLocked(gone)
    }

    private suspend fun run() {
        val account = telegram.userId ?: return
        lock.withLock {
            val rows = if (dao != null) dao.rows(account) else rowsState.value.filter { it.account == account }
            rowsState.value = rows
            val needed = rows.flatMap { listOfNotNull(it.file, it.thumb) }.toSet() + dao?.files().orEmpty()
            withContext(Dispatchers.IO) { folder.listFiles()?.filter { it.name !in needed }?.forEach { it.delete() } }
        }
        while (true) {
            val next = rowsState.value.firstOrNull { it.failed == null }
            if (next == null) {
                wake.receive()
                continue
            }
            send(rowsState.value.filter { it.batch == next.batch })
        }
    }

    private suspend fun send(rows: List<OutgoingRow>) {
        val first = rows.first()
        try {
            val client = telegram.client()
            when (val request = requestOf(first.request)) {
                is SendMessage -> sentOnce { client.invoke(request) }
                is ForwardMessages -> sentOnce { client.invoke(request) }
                is EditMessage -> try {
                    client.invoke(request)
                } catch (e: RpcException) {
                    if (e.errorMessage != "MESSAGE_NOT_MODIFIED") throw e
                }
                is SendMedia -> if (rows.size == 1) sendSingle(client, first, request) else sendAlbum(client, rows)
                else -> error("cannot send ${request::class.simpleName}")
            }
            lock.withLock { removeLocked(rows) }
            sentFlow.tryEmit(first.dialog)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            progressState.update { it - rows.map { row -> row.id }.toSet() }
            if (e !is RpcException && !telegram.ready.value) {
                telegram.log("outgoing ${first.batch} waits for the network: ${e.message}")
                telegram.ready.first { it }
                return
            }
            telegram.log("outgoing ${first.batch} failed: ${e.message}")
            failures[first.batch] = e
            val reason = (e as? RpcException)?.errorMessage ?: e.message ?: e::class.simpleName ?: "failed"
            lock.withLock {
                dao?.failed(rows.map { it.id }, reason)
                rowsState.update { list -> list.map { if (it.batch == first.batch) it.copy(failed = reason) else it } }
            }
        }
    }

    private suspend fun sendSingle(client: TelegramClient, row: OutgoingRow, request: SendMedia) {
        val document = (request.media as? InputMediaDocument)?.id as? InputDocument
        if (document == null) return sentOnce { client.invoke(request.withMedia(uploaded(client, row, request.media))) }
        val key = "doc${document.id}"
        try {
            sentOnce { client.invoke(request) }
        } catch (e: RpcException) {
            if (!e.errorMessage.startsWith("FILE_REFERENCE_") || !telegram.refreshReference(key)) throw e
            val fresh = registry.inputDocuments[key] ?: throw e
            sentOnce { client.invoke(request.withMedia(InputMediaDocument(id = fresh))) }
        }
    }

    private suspend fun sendAlbum(client: TelegramClient, rows: List<OutgoingRow>) {
        val requests = rows.map { requestOf(it.request) as SendMedia }
        val first = requests.first()
        val singles = rows.mapIndexed { index, row ->
            val request = requests[index]
            val media = row.media?.let { TypeInputMedia.decode(it) } ?: run {
                val uploaded = uploaded(client, row, request.media)
                val made = client.invoke(UploadMedia(peer = first.peer, media = uploaded))
                val referenced = referencedMedia(made, spoilerOf(uploaded)) ?: error("telegram made no media of item $index")
                val bytes = referenced.toByteArray()
                lock.withLock {
                    dao?.media(row.id, bytes)
                    rowsState.update { list -> list.map { if (it.id == row.id) it.copy(media = bytes) else it } }
                }
                referenced
            }
            InputSingleMedia(media = media, randomId = request.randomId, message = request.message, entities = request.entities)
        }
        sentOnce {
            client.invoke(SendMultiMedia(silent = first.silent, peer = first.peer, replyTo = first.replyTo, multiMedia = singles, scheduleDate = first.scheduleDate))
        }
    }

    private suspend fun uploaded(client: TelegramClient, row: OutgoingRow, media: TypeInputMedia): TypeInputMedia {
        val name = row.file ?: return media
        val file = File(folder, name)
        val uploaded = uploadedFile(client, fileNameOf(media), UploadSource.of(file)) { done ->
            progressState.update { it + (row.id to done) }
            val parts = (done.downloaded / UPLOAD_PART_SIZE).toInt()
            if (parts > row.uploaded) dao?.uploaded(row.id, parts)
        }
        val thumb = row.thumb?.let { uploadedFile(client, "thumb.jpg", UploadSource.of(File(folder, it))) {} }
        return when (media) {
            is InputMediaUploadedDocument -> InputMediaUploadedDocument(
                nosoundVideo = media.nosoundVideo, forceFile = media.forceFile, spoiler = media.spoiler, file = uploaded, thumb = thumb ?: media.thumb,
                mimeType = media.mimeType, attributes = media.attributes,
            )
            is InputMediaUploadedPhoto -> InputMediaUploadedPhoto(spoiler = media.spoiler, file = uploaded)
            else -> media
        }
    }

    private suspend fun uploadedFile(client: TelegramClient, name: String, source: UploadSource, onProgress: suspend (DownloadState.Progress) -> Unit): TypeInputFile {
        var uploaded: TypeInputFile? = null
        client.uploadFile(name, source).collect { step ->
            when (step) {
                is Upload.Progress -> onProgress(DownloadState.Progress(step.uploaded, step.total))
                is Upload.Done -> uploaded = step.file
            }
        }
        return uploaded ?: error("the upload ended without a file")
    }

    private suspend fun sentOnce(send: suspend () -> Unit) {
        try {
            send()
        } catch (e: RpcException) {
            if (e.errorMessage != RANDOM_ID_DUPLICATE) throw e
        }
    }

    private suspend fun enqueue(rows: List<OutgoingRow>): Long {
        val account = telegram.userId ?: error("not logged in")
        val batch = lock.withLock {
            var next = (rowsState.value.maxOfOrNull { it.id } ?: -1) + 1
            val numbered = rows.map { it.copy(id = next++, account = account) }
            val batch = numbered.first().id
            val kept = numbered.map { it.copy(batch = batch) }
            dao?.put(kept)
            rowsState.update { it + kept }
            batch
        }
        wake.trySend(Unit)
        return batch
    }

    private suspend fun removeLocked(rows: List<OutgoingRow>) {
        if (rows.isEmpty()) return
        val ids = rows.map { it.id }.toSet()
        dao?.delete(ids.toList())
        rowsState.update { list -> list.filter { it.id !in ids } }
        progressState.update { it - ids }
        rows.forEach { failures.remove(it.batch) }
        withContext(Dispatchers.IO) { rows.flatMap { listOfNotNull(it.file, it.thumb) }.forEach { File(folder, it).delete() } }
    }

    private suspend fun forget() = lock.withLock {
        rowsState.value = emptyList()
        progressState.value = emptyMap()
        failures.clear()
        withContext(Dispatchers.IO) { folder.deleteRecursively() }
    }

    private suspend fun fileRow(dialogKey: String, media: OutgoingMedia, caption: FormattedText, replyTo: Int?, topicId: Int?, options: SendOptions, randomId: Long, group: Long?): OutgoingRow {
        val name = copyName("bin")
        val thumbName = media.thumb?.takeIf { videoOf(media) }?.let { copyName("jpg") }
        withContext(Dispatchers.IO) {
            folder.mkdirs()
            File(folder, name).outputStream().use { out ->
                var offset = 0L
                while (offset < media.source.size) {
                    val length = minOf(UPLOAD_PART_SIZE.toLong(), media.source.size - offset).toInt()
                    out.write(media.source.read(offset, length))
                    offset += length
                }
            }
            thumbName?.let { File(folder, it).writeBytes(media.thumb) }
        }
        val entities = registry.inputEntitiesOf(caption.entities).ifEmpty { null }
        val input = inputMediaOf(media, placeholder(media.name))
        val request = SendMedia(
            silent = options.silent, peer = registry.peer(dialogKey), replyTo = replyToOf(replyTo, topicId), media = input, message = caption.text,
            randomId = randomId, entities = entities, scheduleDate = options.scheduleDate,
        )
        val document = uploadDocument(media.mimeType, media.source.size, listOf(DocumentAttributeFilename(media.name)))
        val shown = shownOf(dialogKey, caption.text, entities, replyTo, topicId, document, options, group = group)
        return row(dialogKey, topicId, request, shown, file = name, thumb = thumbName)
    }

    private fun row(dialogKey: String, topicId: Int?, request: TlObject, shown: ByteArray?, file: String? = null, thumb: String? = null) =
        OutgoingRow(id = 0, account = 0, dialog = dialogKey, topic = topicId ?: 0, batch = 0, date = now(), request = request.toByteArray(), shown = shown, file = file, thumb = thumb)

    private fun shownOf(
        dialogKey: String,
        text: String,
        entities: List<TypeMessageEntity>?,
        replyTo: Int?,
        topicId: Int?,
        media: TypeMessageMedia?,
        options: SendOptions,
        group: Long? = null,
        fwd: MessageFwdHeader? = null,
    ): ByteArray? {
        if (options.scheduleDate != null) return null
        val topic = topicId?.takeIf { it != ForumTopic.GENERAL_TOPIC }
        val header = when {
            topic != null -> MessageReplyHeader(forumTopic = true, replyToMsgId = replyTo ?: topic, replyToTopId = topic.takeIf { replyTo != null && replyTo != topic })
            replyTo != null -> MessageReplyHeader(replyToMsgId = replyTo)
            else -> null
        }
        return Message(
            out = true, id = 0, fromId = telegram.userId?.let(::PeerUser), peerId = peerOfKey(dialogKey) ?: PeerUser(telegram.userId ?: 0), date = now(),
            message = text, media = media, entities = entities, replyTo = header, groupedId = group, fwdFrom = fwd,
        ).toByteArray()
    }

    private fun itemOf(row: OutgoingRow, rows: List<OutgoingRow>): MessageItem? {
        val message = TypeMessage.decode(row.shown ?: return null)
        val item = registry.itemOf(message, row.dialog) ?: return null
        val media = item.media?.let { media ->
            if (row.file == null) media else MediaItem(
                key = uploadKey(row.id), fileName = media.fileName, size = media.size, dcId = 0, isPhoto = false,
                kind = if (media.kind == MediaKind.Voice) MediaKind.Voice else MediaKind.File, duration = media.duration, waveform = media.waveform,
            )
        }
        val group = item.groupedId?.takeIf { rows.count { it.batch == row.batch } > 1 }
        return item.copy(id = localIdOf(row), pending = true, failed = row.failed != null, media = media, groupedId = group, read = false)
    }

    private fun copyName(extension: String) = "${Random.nextLong().toULong().toString(16)}.$extension"

    private fun requestOf(bytes: ByteArray): TlObject = when (ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int) {
        SendMessage.CONSTRUCTOR_ID -> SendMessage.decode(bytes)
        SendMedia.CONSTRUCTOR_ID -> SendMedia.decode(bytes)
        ForwardMessages.CONSTRUCTOR_ID -> ForwardMessages.decode(bytes)
        EditMessage.CONSTRUCTOR_ID -> EditMessage.decode(bytes)
        else -> error("unknown outgoing request")
    }

    private fun SendMedia.withMedia(media: TypeInputMedia) = SendMedia(
        silent = silent, clearDraft = clearDraft, peer = peer, replyTo = replyTo, media = media, message = message, randomId = randomId, entities = entities, scheduleDate = scheduleDate,
    )

    private fun placeholder(name: String) = InputFile(0, 0, name, "")

    private fun fileNameOf(media: TypeInputMedia): String = when (media) {
        is InputMediaUploadedDocument -> (media.file as? InputFile)?.name
        is InputMediaUploadedPhoto -> (media.file as? InputFile)?.name
        else -> null
    } ?: "file"

    private fun uploadDocument(mimeType: String, size: Long, attributes: List<TypeDocumentAttribute>) =
        MessageMediaDocument(document = Document(id = 0, accessHash = 0, fileReference = ByteArray(0), date = now(), mimeType = mimeType, size = size, dcId = 0, attributes = attributes))

    private fun shownMediaOf(media: TypeInputMedia): TypeMessageMedia? = when (media) {
        is InputMediaPoll -> MessageMediaPoll(poll = media.poll, results = PollResults())
        is InputMediaGeoPoint -> (media.geoPoint as? InputGeoPoint)?.let { MessageMediaGeo(GeoPoint(it.long, it.lat, 0)) }
        is InputMediaVenue -> (media.geoPoint as? InputGeoPoint)?.let { MessageMediaVenue(GeoPoint(it.long, it.lat, 0), media.title, media.address, media.provider, media.venueId, media.venueType) }
        is InputMediaContact -> MessageMediaContact(media.phoneNumber, media.firstName, media.lastName, media.vcard, 0)
        else -> null
    }

    private fun referencedMedia(made: TypeMessageMedia, spoiler: Boolean): TypeInputMedia? = when (made) {
        is MessageMediaPhoto -> (made.photo as? Photo)?.let { InputMediaPhoto(spoiler = spoiler, id = InputPhoto(it.id, it.accessHash, it.fileReference)) }
        is MessageMediaDocument -> (made.document as? Document)?.let { InputMediaDocument(spoiler = spoiler, id = InputDocument(it.id, it.accessHash, it.fileReference)) }
        else -> null
    }

    private fun spoilerOf(media: TypeInputMedia) = when (media) {
        is InputMediaUploadedDocument -> media.spoiler
        is InputMediaUploadedPhoto -> media.spoiler
        else -> false
    }

    private fun now() = (System.currentTimeMillis() / 1000).toInt()

    companion object {
        const val VOICE_TYPE = "audio/ogg"
        const val RANDOM_ID_DUPLICATE = "RANDOM_ID_DUPLICATE"

        fun localIdOf(row: OutgoingRow): Int = Int.MAX_VALUE - row.id.toInt()

        fun uploadKey(id: Long) = "upload:$id"
    }
}
