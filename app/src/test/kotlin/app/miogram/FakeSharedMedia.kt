package app.miogram

import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.messages.GetCommonChats
import cn.ellerotta.ktproto.api.functions.messages.GetSearchCounters
import cn.ellerotta.ktproto.api.functions.messages.Search
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.DocumentAttributeAnimated
import cn.ellerotta.ktproto.api.types.DocumentAttributeAudio
import cn.ellerotta.ktproto.api.types.DocumentAttributeFilename
import cn.ellerotta.ktproto.api.types.DocumentAttributeVideo
import cn.ellerotta.ktproto.api.types.InputMessagesFilterDocument
import cn.ellerotta.ktproto.api.types.InputMessagesFilterGif
import cn.ellerotta.ktproto.api.types.InputMessagesFilterMusic
import cn.ellerotta.ktproto.api.types.InputMessagesFilterPhotoVideo
import cn.ellerotta.ktproto.api.types.InputMessagesFilterRoundVoice
import cn.ellerotta.ktproto.api.types.InputMessagesFilterUrl
import cn.ellerotta.ktproto.api.types.InputPeerChannel
import cn.ellerotta.ktproto.api.types.InputPeerChat
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageEntityTextUrl
import cn.ellerotta.ktproto.api.types.MessageEntityUrl
import cn.ellerotta.ktproto.api.types.MessageMediaDocument
import cn.ellerotta.ktproto.api.types.MessageMediaPhoto
import cn.ellerotta.ktproto.api.types.MessageMediaWebPage
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.PhotoSize
import cn.ellerotta.ktproto.api.types.PhotoStrippedSize
import cn.ellerotta.ktproto.api.types.TypeChat
import cn.ellerotta.ktproto.api.types.TypeInputPeer
import cn.ellerotta.ktproto.api.types.TypeMessagesFilter
import cn.ellerotta.ktproto.api.types.WebPage
import cn.ellerotta.ktproto.api.types.messages.Chats
import cn.ellerotta.ktproto.api.types.messages.Messages
import cn.ellerotta.ktproto.api.types.messages.MessagesSlice
import cn.ellerotta.ktproto.api.types.messages.SearchCounter
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeCluster
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import cn.ellerotta.ktproto.tl.TlWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FakeSharedMedia(private val fake: FakeTelegram) {
    val searchCalls: MutableList<Search> = CopyOnWriteArrayList()
    val counterCalls: MutableList<GetSearchCounters> = CopyOnWriteArrayList()
    val commonCalls: MutableList<GetCommonChats> = CopyOnWriteArrayList()

    val messages: MutableMap<String, MutableList<Message>> = ConcurrentHashMap()

    val common: MutableMap<Long, List<TypeChat>> = ConcurrentHashMap()

    val deleted: MutableList<Int> = CopyOnWriteArrayList()

    private var nextId = 1_000

    private fun keyOf(peer: TypeInputPeer): String = when (peer) {
        is InputPeerUser -> "u${peer.userId}"
        is InputPeerChat -> "c${peer.chatId}"
        is InputPeerChannel -> "ch${peer.channelId}"
        InputPeerSelf -> "self"
        else -> ""
    }

    private fun peerId(key: String) = when {
        key == "self" -> PeerUser(fake.selfId)
        key.startsWith("ch") -> PeerChannel(key.drop(2).toLong())
        else -> PeerUser(key.drop(1).toLong())
    }

    fun photo(key: String, age: Int = 0): Message = Message(id = nextId++, peerId = peerId(key), date = NOW - age, message = "", media = MessageMediaPhoto(photo = fake.photo))

    fun video(key: String, age: Int = 0): Message =
        Message(id = nextId++, peerId = peerId(key), date = NOW - age, message = "", media = MessageMediaDocument(video = true, document = fake.videoDocument()))

    fun file(key: String, name: String, age: Int = 0): Message = Message(
        id = nextId++,
        peerId = peerId(key),
        date = NOW - age,
        message = "",
        media = MessageMediaDocument(
            document = Document(
                id = FakeCluster.FILE_ID, accessHash = 1, fileReference = byteArrayOf(5), date = NOW, mimeType = "application/pdf", size = fake.cluster.fileBytes.size.toLong(),
                dcId = fake.cluster.fileDcId, attributes = listOf(DocumentAttributeFilename(name)),
            ),
        ),
    )

    fun link(key: String, text: String, url: String, title: String? = null, age: Int = 0): Message {
        val at = text.indexOf(url)
        return Message(
            id = nextId++,
            peerId = peerId(key),
            date = NOW - age,
            message = text,
            entities = listOf(MessageEntityUrl(at, url.length)),
            media = title?.let { MessageMediaWebPage(webpage = WebPage(id = 70L + nextId, url = url, displayUrl = url, hash = 0, siteName = "Example", title = it, description = "About $it")) },
        )
    }

    fun song(key: String, title: String, age: Int = 0): Message = Message(
        id = nextId++,
        peerId = peerId(key),
        date = NOW - age,
        message = "",
        media = MessageMediaDocument(
            document = Document(
                id = 9_000L + nextId, accessHash = 1, fileReference = byteArrayOf(3), date = NOW, mimeType = "audio/mpeg", size = 4_000_000, dcId = 2,
                attributes = listOf(DocumentAttributeAudio(duration = 185, title = title, performer = "Band"), DocumentAttributeFilename("$title.mp3")),
            ),
        ),
    )

    fun voice(key: String, age: Int = 0, from: Long? = null): Message = Message(
        id = nextId++,
        peerId = peerId(key),
        fromId = from?.let(::PeerUser),
        date = NOW - age,
        message = "",
        media = MessageMediaDocument(
            voice = true,
            document = Document(
                id = 9_500L + nextId, accessHash = 1, fileReference = byteArrayOf(4), date = NOW, mimeType = "audio/ogg", size = 20_000, dcId = 2,
                attributes = listOf(DocumentAttributeAudio(voice = true, duration = 12, waveform = byteArrayOf(1, 20, 40, 8, 90, 3))),
            ),
        ),
    )

    fun round(key: String, age: Int = 0): Message = Message(
        id = nextId++,
        peerId = peerId(key),
        date = NOW - age,
        message = "",
        media = MessageMediaDocument(
            round = true,
            document = Document(
                id = 9_800L + nextId, accessHash = 1, fileReference = byteArrayOf(6), date = NOW, mimeType = "video/mp4", size = 90_000, dcId = 2,
                thumbs = listOf(PhotoStrippedSize("i", fake.strippedBytes), PhotoSize("m", 240, 240, 100)),
                attributes = listOf(DocumentAttributeVideo(roundMessage = true, duration = 7.0, w = 240, h = 240)),
            ),
        ),
    )

    fun gif(key: String, age: Int = 0): Message = Message(
        id = nextId++,
        peerId = peerId(key),
        date = NOW - age,
        message = "",
        media = MessageMediaDocument(
            document = Document(
                id = 9_900L + nextId, accessHash = 1, fileReference = byteArrayOf(7), date = NOW, mimeType = "video/mp4", size = 50_000, dcId = 2,
                attributes = listOf(DocumentAttributeAnimated, DocumentAttributeVideo(duration = 2.0, w = 320, h = 240), DocumentAttributeFilename("loop.mp4")),
            ),
        ),
    )

    private fun matches(message: Message, filter: TypeMessagesFilter): Boolean {
        val media = message.media
        val document = (media as? MessageMediaDocument)?.document as? Document
        val audio = document?.attributes?.filterIsInstance<DocumentAttributeAudio>()?.firstOrNull()
        val video = document?.attributes?.filterIsInstance<DocumentAttributeVideo>()?.firstOrNull()
        val animated = document?.attributes?.any { it is DocumentAttributeAnimated } == true
        return when (filter) {
            InputMessagesFilterPhotoVideo -> media is MessageMediaPhoto || (video != null && !animated && !video.roundMessage)
            InputMessagesFilterDocument -> document != null && audio == null && video == null && !animated
            InputMessagesFilterUrl -> message.entities.orEmpty().any { it is MessageEntityUrl || it is MessageEntityTextUrl } || media is MessageMediaWebPage
            InputMessagesFilterMusic -> audio != null && !audio.voice
            InputMessagesFilterRoundVoice -> (audio != null && audio.voice) || video?.roundMessage == true
            InputMessagesFilterGif -> animated
            else -> false
        }
    }

    fun search(call: Search): FakeAnswer? {
        val all = messages[keyOf(call.peer)] ?: return null
        searchCalls += call
        val found = all.filter { matches(it, call.filter) }.sortedByDescending { it.id }
        val rest = found.filter { call.offsetId == 0 || it.id < call.offsetId }
        val page = rest.take(call.limit)
        return FakeAnswer.Result(
            MessagesSlice(count = found.size, messages = page, topics = emptyList(), chats = listOf(fake.groups.devs()), users = listOf(fake.alice, fake.bob)),
        )
    }

    fun delete(ids: List<Int>) {
        messages.values.forEach { list -> if (list.removeAll { it.id in ids }) deleted += ids }
    }

    fun register(server: FakeTelegramServer) {
        server.rpc.on(GetSearchCounters.CONSTRUCTOR_ID) { request ->
            fake.answerLate("counters")
            val call = GetSearchCounters.decode(request)
            counterCalls += call
            val all = messages[keyOf(call.peer)].orEmpty()
            val writer = TlWriter()
            writer.writeVector(call.filters.map { SearchCounter(filter = it, count = all.count { message -> matches(message, it) }) }) { it.serialize(this) }
            FakeAnswer.Raw(writer.toByteArray())
        }
        server.rpc.on(GetCommonChats.CONSTRUCTOR_ID) { request ->
            fake.answerLate("common")
            val call = GetCommonChats.decode(request)
            commonCalls += call
            val all = common[(call.userId as? cn.ellerotta.ktproto.api.types.InputUser)?.userId].orEmpty()
            val after = if (call.maxId == 0L) 0 else all.indexOfFirst { chatId(it) == call.maxId } + 1
            FakeAnswer.Result(Chats(all.drop(after).take(call.limit)))
        }
    }

    private fun chatId(chat: TypeChat): Long = when (chat) {
        is cn.ellerotta.ktproto.api.types.Channel -> chat.id
        is cn.ellerotta.ktproto.api.types.Chat -> chat.id
        else -> 0L
    }
}
