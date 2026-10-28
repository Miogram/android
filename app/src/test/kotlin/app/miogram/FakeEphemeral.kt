package app.miogram

import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.ephemeral.DeleteMessage
import cn.ellerotta.ktproto.api.functions.ephemeral.GetCallbackAnswer
import cn.ellerotta.ktproto.api.functions.ephemeral.SendMessage
import cn.ellerotta.ktproto.api.types.EphemeralMessage
import cn.ellerotta.ktproto.api.types.InlineButtonTypeCallback
import cn.ellerotta.ktproto.api.types.InputReplyToEphemeralMessage
import cn.ellerotta.ktproto.api.types.KeyboardInlineButton
import cn.ellerotta.ktproto.api.types.KeyboardInlineButtonRow
import cn.ellerotta.ktproto.api.types.MessageReplyHeader
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.ReplyInlineMarkup
import cn.ellerotta.ktproto.api.types.TypePeer
import cn.ellerotta.ktproto.api.types.TypeUpdate
import cn.ellerotta.ktproto.api.types.UpdateDeleteEphemeralMessages
import cn.ellerotta.ktproto.api.types.UpdateEditEphemeralMessage
import cn.ellerotta.ktproto.api.types.UpdateNewEphemeralMessage
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.messages.BotCallbackAnswer
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class FakeEphemeral(private val fake: FakeTelegram) {
    val sendCalls: MutableList<SendMessage> = CopyOnWriteArrayList()

    val deleteCalls: MutableList<DeleteMessage> = CopyOnWriteArrayList()

    val callbackCalls: MutableList<GetCallbackAnswer> = CopyOnWriteArrayList()

    private val nextId = AtomicInteger(FIRST_ID)

    suspend fun botSays(text: String, peer: TypePeer? = null, button: String? = null, replyTo: Int? = null): Int {
        val id = nextId.getAndIncrement()
        push(UpdateNewEphemeralMessage(message(id, text, peer, button, replyTo)))
        return id
    }

    suspend fun edit(id: Int, text: String, peer: TypePeer? = null) = push(UpdateEditEphemeralMessage(message(id, text, peer)))

    suspend fun delete(ids: List<Int>, peer: TypePeer = PeerUser(fake.helperBot.id)) = push(UpdateDeleteEphemeralMessages(peer, ids))

    private fun message(id: Int, text: String, peer: TypePeer?, button: String? = null, replyTo: Int? = null) = EphemeralMessage(
        id = id,
        fromId = PeerUser(fake.helperBot.id),
        peerId = peer,
        receiverId = fake.selfId,
        date = NOW,
        message = text,
        replyMarkup = button?.let { ReplyInlineMarkup(rows = listOf(KeyboardInlineButtonRow(listOf(KeyboardInlineButton(text = it, type = InlineButtonTypeCallback(data = "secret".toByteArray())))))) },
        replyTo = replyTo?.let { MessageReplyHeader(replyToEphemeral = true, replyToMsgId = it) },
    )

    private suspend fun push(update: TypeUpdate) = fake.cluster.updates.push(Updates(listOf(update), listOf(fake.helperBot), emptyList(), NOW, 0))

    fun register(server: FakeTelegramServer) {
        server.rpc.on(SendMessage.CONSTRUCTOR_ID) { request ->
            val call = SendMessage.decode(request)
            sendCalls += call
            val reply = (call.replyTo as? InputReplyToEphemeralMessage)?.let { MessageReplyHeader(replyToEphemeral = true, replyToMsgId = it.id) }
            val own = EphemeralMessage(
                out = true,
                id = nextId.getAndIncrement(),
                fromId = PeerUser(fake.selfId),
                receiverId = fake.helperBot.id,
                date = NOW,
                message = call.message,
                replyTo = reply,
            )
            FakeAnswer.Result(Updates(listOf(UpdateNewEphemeralMessage(own)), listOf(fake.helperBot), emptyList(), NOW, 0))
        }
        server.rpc.on(DeleteMessage.CONSTRUCTOR_ID) { request ->
            deleteCalls += DeleteMessage.decode(request)
            BOOL_TRUE
        }
        server.rpc.on(GetCallbackAnswer.CONSTRUCTOR_ID) { request ->
            val call = GetCallbackAnswer.decode(request)
            callbackCalls += call
            if (call.data?.decodeToString() != "secret") return@on FakeAnswer.Error(400, "BOT_RESPONSE_TIMEOUT")
            FakeAnswer.Result(BotCallbackAnswer(message = "Secret pong", cacheTime = 0))
        }
    }

    companion object {
        const val FIRST_ID = 40

        private val BOOL_TRUE = FakeAnswer.Raw(byteArrayOf(0xb5.toByte(), 0x75, 0x72, 0x99.toByte()))
    }
}
