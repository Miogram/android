package app.miogram

import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.messages.TranslateRichMessage
import cn.ellerotta.ktproto.api.functions.messages.TranslateText
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageEntityBold
import cn.ellerotta.ktproto.api.types.PageBlockParagraph
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.RichMessage
import cn.ellerotta.ktproto.api.types.TextPlain
import cn.ellerotta.ktproto.api.types.TextWithEntities
import cn.ellerotta.ktproto.api.types.messages.TranslateResult
import cn.ellerotta.ktproto.api.types.messages.TranslatedRichMessage
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FakeTranslations(private val fake: FakeTelegram) {
    val textCalls: MutableList<TranslateText> = CopyOnWriteArrayList()
    val richCalls: MutableList<TranslateRichMessage> = CopyOnWriteArrayList()

    @Volatile var quota = Int.MAX_VALUE

    private val texts = ConcurrentHashMap<Int, String>()
    private val articles = ConcurrentHashMap<Int, List<String>>()

    fun addText(text: String): Int = fake.addSaved { id ->
        texts[id] = text
        Message(id = id, fromId = PeerUser(fake.alice.id), peerId = PeerUser(fake.selfId), date = NOW, message = text, entities = listOf(MessageEntityBold(0, text.substringBefore(' ').length)))
    }

    fun addRich(paragraphs: List<String>): Int = fake.addSaved { id ->
        articles[id] = paragraphs
        Message(
            id = id, fromId = PeerUser(fake.alice.id), peerId = PeerUser(fake.selfId), date = NOW, message = "",
            richMessage = RichMessage(blocks = paragraphs.map { PageBlockParagraph(TextPlain(it)) }, photos = emptyList(), documents = emptyList()),
        )
    }

    private fun refused(lang: String): FakeAnswer.Error? = when {
        !Regex("[a-z]{2}").matches(lang) -> FakeAnswer.Error(400, "TO_LANG_INVALID")
        textCalls.size + richCalls.size > quota -> FakeAnswer.Error(400, "QUOTA_EXCEEDED")
        else -> null
    }

    fun register(server: FakeTelegramServer) {
        server.rpc.on(TranslateText.CONSTRUCTOR_ID) { request ->
            val call = TranslateText.decode(request)
            textCalls += call
            refused(call.toLang)?.let { return@on it }
            val result = call.id.orEmpty().mapNotNull { texts[it] }.map { text ->
                val prefix = "${call.toLang}: "
                TextWithEntities(prefix + text, listOf(MessageEntityBold(prefix.length, text.substringBefore(' ').length)))
            }
            FakeAnswer.Result(TranslateResult(result))
        }
        server.rpc.on(TranslateRichMessage.CONSTRUCTOR_ID) { request ->
            val call = TranslateRichMessage.decode(request)
            richCalls += call
            refused(call.toLang)?.let { return@on it }
            val result = call.id.orEmpty().mapNotNull { articles[it] }.map { paragraphs ->
                RichMessage(blocks = paragraphs.map { PageBlockParagraph(TextPlain("${call.toLang}: $it")) }, photos = emptyList(), documents = emptyList())
            }
            FakeAnswer.Result(TranslatedRichMessage(result))
        }
    }
}
