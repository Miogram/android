package cn.ellerotta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslationsTest {
    private val bold = listOf(TextEntity(0, 5, EntityType.Bold))
    private val message = MessageItem(1, "hello world", out = false, date = 0, entities = bold)

    @Test
    fun aTranslationTakesThePlaceOfTheTextOrTheBlocks() {
        assertEquals(message, message.translated())
        val text = message.copy(translation = MessageTranslation("ru", FormattedText("привет мир", listOf(TextEntity(0, 6, EntityType.Bold)))))
        assertEquals("привет мир" to listOf(TextEntity(0, 6, EntityType.Bold)), text.translated().let { it.text to it.entities })
        val article = ArticleMessage(listOf(ArticleBlock.Paragraph(ArticleText.Plain("hallo"))))
        val rich = MessageItem(2, "", out = false, date = 0, article = ArticleMessage(listOf(ArticleBlock.Paragraph(ArticleText.Plain("hello")))))
        assertEquals(article, rich.copy(translation = MessageTranslation("de", article = article)).translated().article)
        assertEquals("hello world", message.copy(translation = MessageTranslation("ru")).translated().text)
    }

    @Test
    fun onlyMessagesTelegramHasWithTextOrBlocksTranslate() {
        assertTrue(message.translatable)
        assertTrue(MessageItem(2, "", out = true, date = 0, article = ArticleMessage(emptyList())).translatable)
        assertFalse(message.copy(text = " ").translatable)
        assertFalse(message.copy(pending = true).translatable)
        assertFalse(message.copy(streaming = true).translatable)
        assertFalse(message.copy(ephemeral = EphemeralInfo(id = 1, botKey = "u9", botUsername = "helper")).translatable)
    }
}
