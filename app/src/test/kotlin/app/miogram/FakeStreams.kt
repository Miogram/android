package app.miogram

import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.help.GetAppConfig
import cn.ellerotta.ktproto.api.types.JsonNumber
import cn.ellerotta.ktproto.api.types.JsonObject
import cn.ellerotta.ktproto.api.types.JsonObjectValue
import cn.ellerotta.ktproto.api.types.JsonString
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.PageBlockParagraph
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.RichMessage
import cn.ellerotta.ktproto.api.types.SendMessageRichMessageDraftAction
import cn.ellerotta.ktproto.api.types.SendMessageTextDraftAction
import cn.ellerotta.ktproto.api.types.TextPlain
import cn.ellerotta.ktproto.api.types.TextWithEntities
import cn.ellerotta.ktproto.api.types.TypeSendMessageAction
import cn.ellerotta.ktproto.api.types.UpdateChannelUserTyping
import cn.ellerotta.ktproto.api.types.UpdateNewMessage
import cn.ellerotta.ktproto.api.types.UpdateShort
import cn.ellerotta.ktproto.api.types.UpdateShortMessage
import cn.ellerotta.ktproto.api.types.UpdateUserTyping
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.help.AppConfig
import cn.ellerotta.ktproto.api.types.help.AppConfigNotModified
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.CopyOnWriteArrayList

class FakeStreams(private val fake: FakeTelegram) {
    @Volatile var draftTtl = 30

    val appConfigHashes: MutableList<Int> = CopyOnWriteArrayList()

    val numbers: MutableMap<String, Int> = java.util.concurrent.ConcurrentHashMap()

    private fun hash() = CONFIG_HASH + draftTtl + fake.articles.configHash() + numbers.hashCode()

    suspend fun text(botId: Long, randomId: Long, text: String, canStop: Boolean = false, keepOnStop: Boolean = false) =
        push(botId, SendMessageTextDraftAction(canStop = canStop, keepOnStop = keepOnStop, randomId = randomId, text = TextWithEntities(text, emptyList())))

    suspend fun rich(botId: Long, randomId: Long, text: String, canStop: Boolean = false) =
        push(botId, SendMessageRichMessageDraftAction(canStop = canStop, randomId = randomId, richMessage = RichMessage(blocks = listOf(PageBlockParagraph(TextPlain(text))), photos = emptyList(), documents = emptyList())))

    suspend fun inGroup(channelId: Long, botId: Long, randomId: Long, text: String) = fake.cluster.updates.push(
        UpdateShort(UpdateChannelUserTyping(channelId = channelId, fromId = PeerUser(botId), action = SendMessageTextDraftAction(randomId = randomId, text = TextWithEntities(text, emptyList()))), NOW),
    )

    private suspend fun push(botId: Long, action: TypeSendMessageAction) = fake.cluster.updates.push(UpdateShort(UpdateUserTyping(userId = botId, action = action), NOW))

    suspend fun botMessage(botId: Long, id: Int, text: String): Message {
        fake.cluster.updates.message(botId, text, push = false)
        val pts = (fake.cluster.updates.last as UpdateShortMessage).pts
        val message = Message(id = id, fromId = PeerUser(botId), peerId = PeerUser(botId), date = NOW, message = text)
        fake.cluster.updates.push(Updates(listOf(UpdateNewMessage(message, pts, 1)), emptyList(), emptyList(), NOW, 0))
        return message
    }

    fun register(server: FakeTelegramServer) {
        server.rpc.on(GetAppConfig.CONSTRUCTOR_ID) { request ->
            val asked = GetAppConfig.decode(request).hash
            appConfigHashes += asked
            if (asked == hash()) return@on FakeAnswer.Result(AppConfigNotModified)
            val config = JsonObject(
                listOf(
                    JsonObjectValue("message_typing_draft_ttl", JsonNumber(draftTtl.toDouble())),
                    JsonObjectValue("rich_message_posting", JsonString(fake.articles.posting)),
                ) + (fake.articles.limits + numbers).map { (key, value) -> JsonObjectValue(key, JsonNumber(value.toDouble())) },
            )
            FakeAnswer.Result(AppConfig(hash(), config))
        }
    }

    private companion object {
        const val CONFIG_HASH = 2_290
    }
}
