package app.miogram

import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.messages.DeleteScheduledMessages
import cn.ellerotta.ktproto.api.functions.messages.EditMessage
import cn.ellerotta.ktproto.api.functions.messages.GetScheduledHistory
import cn.ellerotta.ktproto.api.functions.messages.SendMultiMedia
import cn.ellerotta.ktproto.api.functions.messages.SendScheduledMessages
import cn.ellerotta.ktproto.api.types.InputDocument
import cn.ellerotta.ktproto.api.types.InputMediaDocument
import cn.ellerotta.ktproto.api.types.InputMediaPhoto
import cn.ellerotta.ktproto.api.types.InputPhoto
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageMediaDocument
import cn.ellerotta.ktproto.api.types.MessageMediaPhoto
import cn.ellerotta.ktproto.api.types.MessageReplyHeader
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.TypeInputReplyTo
import cn.ellerotta.ktproto.api.types.TypeMessageEntity
import cn.ellerotta.ktproto.api.types.TypeMessageMedia
import cn.ellerotta.ktproto.api.types.TypePeer
import cn.ellerotta.ktproto.api.types.UpdateDeleteScheduledMessages
import cn.ellerotta.ktproto.api.types.UpdateNewScheduledMessage
import cn.ellerotta.ktproto.api.types.UpdateShort
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.messages.Messages
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.CopyOnWriteArrayList

class FakeSending(private val fake: FakeTelegram) {
    private val held = CopyOnWriteArrayList<Message>()
    private var nextScheduledId = 10

    val scheduled: List<Message> get() = held.sortedWith(compareBy({ it.date }, { it.id }))

    val albums: MutableList<SendMultiMedia> = CopyOnWriteArrayList()

    val sentNow: MutableList<Int> = CopyOnWriteArrayList()
    val deleted: MutableList<Int> = CopyOnWriteArrayList()

    @Volatile var failAlbums = 0

    suspend fun schedule(message: (id: Int) -> Message) {
        val made = message(nextScheduledId++)
        held += made
        fake.cluster.updates.push(UpdateShort(UpdateNewScheduledMessage(made), NOW))
    }

    fun hold(text: String, date: Int) {
        held += Message(out = true, id = nextScheduledId++, peerId = PeerUser(fake.selfId), date = date, message = text)
    }

    suspend fun scheduleText(text: String, date: Int, entities: List<TypeMessageEntity>? = null) =
        schedule { id -> Message(out = true, id = id, peerId = PeerUser(fake.selfId), date = date, message = text, entities = entities) }

    suspend fun pushDeleted(ids: List<Int>) {
        held.removeAll { it.id in ids }
        fake.cluster.updates.push(UpdateShort(UpdateDeleteScheduledMessages(peer = PeerUser(fake.selfId), messages = ids), NOW))
    }

    fun edit(call: EditMessage): FakeAnswer? {
        val index = held.indexOfFirst { it.id == call.id }
        if (index < 0) return null
        val old = held[index]
        held[index] = Message(
            out = old.out,
            id = old.id,
            peerId = old.peerId,
            date = call.scheduleDate ?: old.date,
            message = call.message ?: old.message,
            entities = call.entities?.map(fake::storedEntity) ?: old.entities,
            editDate = if (call.message != null) NOW else old.editDate,
            replyTo = old.replyTo,
            media = old.media,
        )
        return FakeAnswer.Result(Updates(emptyList(), emptyList(), emptyList(), NOW, 0))
    }

    private fun replyOf(replyTo: TypeInputReplyTo?) = (replyTo as? InputReplyToMessage)?.let { MessageReplyHeader(replyToMsgId = it.replyToMsgId) }

    fun register(server: FakeTelegramServer) {
        server.rpc.on(SendMultiMedia.CONSTRUCTOR_ID) { request ->
            val call = SendMultiMedia.decode(request)
            if (failAlbums > 0) {
                failAlbums--
                return@on FakeAnswer.Error(403, "CHAT_SEND_MEDIA_FORBIDDEN")
            }
            if (call.multiMedia.any { !fake.rememberRandomId(it.randomId) }) return@on FakeAnswer.Error(500, "RANDOM_ID_DUPLICATE")
            albums += call
            val grouped = FakeTelegram.ALBUM_ID + albums.size
            val made = call.multiMedia.map { single ->
                val media: TypeMessageMedia = when (val input = single.media) {
                    is InputMediaDocument -> fake.articles.uploadedDocuments[(input.id as? InputDocument)?.id]?.let { MessageMediaDocument(document = it) }
                    is InputMediaPhoto -> fake.articles.uploadedPhotos[(input.id as? InputPhoto)?.id]?.let { MessageMediaPhoto(photo = it) }
                    else -> null
                } ?: return@on FakeAnswer.Error(400, "MEDIA_INVALID")
                val make: (Int, TypePeer, TypePeer?, Int) -> Message = { id, peer, from, date ->
                    Message(
                        out = true,
                        silent = call.silent,
                        id = id,
                        fromId = from,
                        peerId = peer,
                        date = date,
                        message = single.message,
                        entities = single.entities?.map(fake::storedEntity),
                        media = media,
                        replyTo = replyOf(call.replyTo),
                        groupedId = grouped,
                    )
                }
                make to single.randomId
            }
            if (call.scheduleDate != null) {
                made.forEach { (make, _) -> schedule { id -> make(id, PeerUser(fake.selfId), null, call.scheduleDate!!) } }
                return@on FakeAnswer.Result(Updates(emptyList(), emptyList(), emptyList(), NOW, 0))
            }
            val sent = made.map { (make, randomId) ->
                val id = fake.saveMessage { id -> make(id, PeerUser(fake.selfId), null, NOW) }
                make(id, fake.peerOf(call.peer), PeerUser(fake.selfId), fake.sentDate()) to randomId
            }
            FakeAnswer.Result(fake.sentUpdates(sent))
        }
        server.rpc.on(GetScheduledHistory.CONSTRUCTOR_ID) { _ ->
            FakeAnswer.Result(Messages(scheduled, emptyList(), emptyList(), emptyList()))
        }
        server.rpc.on(SendScheduledMessages.CONSTRUCTOR_ID) { request ->
            val call = SendScheduledMessages.decode(request)
            val sent = held.filter { it.id in call.id }.sortedBy { it.id }
            val messages = sent.map { old ->
                held.remove(old)
                sentNow += old.id
                val id = fake.saveMessage { id -> Message(out = true, id = id, peerId = old.peerId, date = NOW, message = old.message, entities = old.entities, media = old.media, replyTo = old.replyTo, groupedId = old.groupedId) }
                Message(
                    out = true, fromScheduled = true, id = id, fromId = PeerUser(fake.selfId), peerId = old.peerId, date = fake.sentDate(), message = old.message, entities = old.entities,
                    media = old.media, replyTo = old.replyTo, groupedId = old.groupedId,
                ) to null
            }
            val deleted = UpdateDeleteScheduledMessages(peer = PeerUser(fake.selfId), messages = sent.map { it.id }, sentMessages = messages.map { it.first.id })
            FakeAnswer.Result(fake.sentUpdates(messages, listOf(deleted)))
        }
        server.rpc.on(DeleteScheduledMessages.CONSTRUCTOR_ID) { request ->
            val call = DeleteScheduledMessages.decode(request)
            held.removeAll { it.id in call.id }
            deleted += call.id
            fake.cluster.updates.push(UpdateShort(UpdateDeleteScheduledMessages(peer = PeerUser(fake.selfId), messages = call.id), NOW))
            FakeAnswer.Result(Updates(emptyList(), emptyList(), emptyList(), NOW, 0))
        }
    }
}
