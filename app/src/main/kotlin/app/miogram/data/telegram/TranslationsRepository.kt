package app.miogram.data.telegram

import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.MessageTranslation
import cn.ellerotta.ktproto.api.functions.messages.TranslateRichMessage
import cn.ellerotta.ktproto.api.functions.messages.TranslateText
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationsRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    suspend fun translate(dialogKey: String, id: Int, toLang: String): MessageTranslation {
        val result = telegram.client().invoke(TranslateText(peer = registry.peer(dialogKey), id = listOf(id), toLang = toLang))
        val text = result.result.firstOrNull() ?: return MessageTranslation(toLang)
        return MessageTranslation(toLang, FormattedText(text.text, entitiesOf(text.text, text.entities)))
    }

    suspend fun translateRich(dialogKey: String, id: Int, toLang: String, bot: String?): MessageTranslation {
        val result = telegram.client().invoke(TranslateRichMessage(peer = registry.peer(dialogKey), id = listOf(id), toLang = toLang))
        val rich = result.result.firstOrNull() ?: return MessageTranslation(toLang)
        return MessageTranslation(toLang, article = articleOf(rich, registry, RichMessageOrigin(dialogKey, id), bot))
    }

    fun botOf(dialogKey: String, senderId: Long, viaBot: String?): String? =
        viaBot ?: registry.usernames[senderId] ?: dialogKey.takeIf { it.startsWith("u") }?.drop(1)?.toLongOrNull()?.let { registry.usernames[it] }
}
