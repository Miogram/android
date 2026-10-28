package cn.ellerotta.core.model

data class MessageTranslation(val language: String, val text: FormattedText = FormattedText(""), val article: ArticleMessage? = null) {
    val isEmpty: Boolean get() = text.text.isEmpty() && article == null
}

fun MessageItem.translated(): MessageItem {
    val translation = translation?.takeIf { !it.isEmpty } ?: return this
    return if (translation.article != null) copy(article = translation.article) else copy(text = translation.text.text, entities = translation.text.entities)
}

val MessageItem.translatable: Boolean
    get() = !pending && !streaming && ephemeral == null && (text.isNotBlank() || article != null)
