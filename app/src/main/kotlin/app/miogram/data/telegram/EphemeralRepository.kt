package app.miogram.data.telegram

import cn.ellerotta.core.model.CallbackAnswer
import cn.ellerotta.core.model.EphemeralInfo
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.ktproto.api.functions.ephemeral.DeleteMessage
import cn.ellerotta.ktproto.api.functions.ephemeral.GetCallbackAnswer
import cn.ellerotta.ktproto.api.functions.ephemeral.SendMessage
import cn.ellerotta.ktproto.api.types.InputReplyToEphemeralMessage
import cn.ellerotta.ktproto.api.types.InputUserSelf
import cn.ellerotta.ktproto.session.RpcException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class EphemeralRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    suspend fun send(dialogKey: String, botKey: String, text: String, entities: List<TextEntity> = emptyList(), replyToEphemeral: Int? = null, replyTo: Int? = null, topicId: Int? = null) {
        val reply = replyToEphemeral?.let(::InputReplyToEphemeralMessage) ?: replyToOf(replyTo, topicId)
        telegram.client().invoke(
            SendMessage(
                peer = peerOf(dialogKey, botKey),
                receiverId = registry.user(botKey),
                message = text,
                entities = registry.inputEntitiesOf(entities).ifEmpty { null },
                randomId = Random.nextLong(),
                replyTo = reply,
            ),
        )
    }

    suspend fun delete(dialogKey: String, message: EphemeralInfo, out: Boolean) {
        telegram.client().invoke(DeleteMessage(peer = peerOf(dialogKey, message.botKey), receiverId = if (out) registry.user(message.botKey) else InputUserSelf, id = message.id))
    }

    suspend fun callback(dialogKey: String, id: Int, data: ByteArray): CallbackAnswer {
        val answer = try {
            telegram.client().invoke(GetCallbackAnswer(peer = registry.peer(dialogKey), id = id, data = data))
        } catch (e: RpcException) {
            if (e.errorMessage == "BOT_RESPONSE_TIMEOUT") return CallbackAnswer()
            throw e
        }
        return CallbackAnswer(answer.message?.ifEmpty { null }, answer.alert, answer.url?.ifEmpty { null })
    }

    private fun peerOf(dialogKey: String, botKey: String) = if (dialogKey == botKey) null else registry.peer(dialogKey)
}
