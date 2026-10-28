package cn.ellerotta.core.model

import cn.ellerotta.core.model.EntityType.Blockquote
import cn.ellerotta.core.model.EntityType.Bold
import cn.ellerotta.core.model.EntityType.Code
import cn.ellerotta.core.model.EntityType.Italic
import cn.ellerotta.core.model.EntityType.Pre
import cn.ellerotta.core.model.EntityType.TextUrl
import cn.ellerotta.core.model.EntityType.Url
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class EntitiesTest {
    private fun at(text: String, part: String, type: EntityType) = TextEntity(text.indexOf(part), part.length, type)

    @Test
    fun offsetsCountUtf16UnitsAndNeverCutASurrogatePair() {
        val text = "\uD83D\uDE00 bold \uD83D\uDC4D"
        assertEquals(3, text.indexOf("bold"))
        val entities = listOf(
            TextEntity(3, 4, Bold),
            TextEntity(1, 1, Italic),
            TextEntity(9, 1, Code),
            TextEntity(8, 40, EntityType.Underline),
            TextEntity(4, 0, EntityType.Strike),
            TextEntity(-3, 2, EntityType.Spoiler),
        )
        val fixed = normalizeEntities(text, entities)
        assertEquals(listOf(TextEntity(0, 2, Italic), TextEntity(3, 4, Bold), TextEntity(8, 2, Code), TextEntity(8, 2, EntityType.Underline)), fixed)
        assertEquals("\uD83D\uDC4D", fixed.last().part(text))
    }

    @Test
    fun stylesNestButLinksAndBlocksDoNotOverlap() {
        val text = "see example.com and more\npre here\nquote"
        val entities = listOf(
            at(text, "see example.com", Bold),
            at(text, "example.com and", Italic),
            at(text, "example.com", Url),
            at(text, "com and", TextUrl("https://t.me/x")),
            at(text, "pre here", Pre("kotlin")),
            at(text, "here", Bold),
            at(text, "here\nquote", Blockquote()),
        )
        val fixed = normalizeEntities(text, entities)
        assertEquals(listOf(Bold, Italic, Url, Pre("kotlin")), fixed.map { it.type })
        assertEquals("example.com", linkTarget(text, fixed.single { it.type == Url }))
        val smile = "go \uD83D\uDE00 now"
        val emoji = normalizeEntities(
            smile,
            listOf(TextEntity(0, smile.length, TextUrl("https://t.me")), TextEntity(3, 2, EntityType.CustomEmoji(1)), TextEntity(3, 2, EntityType.CustomEmoji(2))),
        )
        assertEquals(listOf(TextUrl("https://t.me"), EntityType.CustomEmoji(1)), emoji.map { it.type })
    }

    @Test
    fun customEmojiIdsAreCollectedOncePerDocument() {
        val messages = listOf(
            MessageItem(1, "\uD83D\uDE00\uD83D\uDE00", out = false, date = 0, entities = listOf(TextEntity(0, 2, EntityType.CustomEmoji(7)), TextEntity(2, 2, EntityType.CustomEmoji(8)))),
            MessageItem(2, "\uD83D\uDE00", out = false, date = 0, entities = listOf(TextEntity(0, 2, EntityType.CustomEmoji(7)), TextEntity(0, 2, Bold))),
            MessageItem(3, "plain", out = false, date = 0),
            MessageItem(4, "menu", out = false, date = 0, buttons = listOf(listOf(BotButton.Url("Site", "https://core.telegram.org", icon = 9), BotButton.Callback("Go", byteArrayOf(1))))),
        )
        assertEquals(setOf(7L, 8L, 9L), customEmojiIds(messages))
    }

    @Test
    fun blocksSplitTheTextAndCarryTheirEntities() {
        val text = "say hi\nval x = 1\nquote line\nend"
        val entities = normalizeEntities(
            text,
            listOf(
                at(text, "say", Bold),
                at(text, "val x = 1\n", Pre("kotlin")),
                at(text, "quote line", Blockquote(collapsed = true)),
                at(text, "quote", Italic),
                at(text, "end", Code),
            ),
        )
        assertEquals(
            listOf(
                TextBlock.Paragraph("say hi", listOf(TextEntity(0, 3, Bold))),
                TextBlock.Pre("val x = 1", "kotlin"),
                TextBlock.Quote("quote line", listOf(TextEntity(0, 5, Italic)), collapsed = true),
                TextBlock.Paragraph("end", listOf(TextEntity(0, 3, Code))),
            ),
            blocksOf(text, entities),
        )
        assertEquals(listOf(TextBlock.Paragraph("plain", emptyList())), blocksOf("plain", emptyList()))
    }

    @Test
    fun tapsOpenWhatTheEntityNames() {
        val text = "@carol a@b.c +1 650 253 x"
        assertEquals("https://t.me/carol", linkTarget(text, at(text, "@carol", EntityType.Mention)))
        assertEquals("mailto:a@b.c", linkTarget(text, at(text, "a@b.c", EntityType.Email)))
        assertEquals("tel:+1650253", linkTarget(text, at(text, "+1 650 253", EntityType.Phone)))
        assertEquals("tg://user?id=501", linkTarget(text, at(text, "x", EntityType.MentionName(501))))
        assertEquals("https://core.telegram.org", linkTarget(text, at(text, "x", TextUrl("https://core.telegram.org"))))
        assertNull(linkTarget(text, at(text, "x", Bold)))
    }

    @Test
    fun editsMoveGrowAndShrinkEntities() {
        val start = FormattedText("hello bold world", listOf(TextEntity(6, 4, Bold)))
        assertEquals(TextEntity(6, 6, Bold), start.edited("hello boXXld world").entities.single())
        assertEquals(TextEntity(6, 4, Bold), start.edited("hello boldX world").entities.single())
        assertEquals(TextEntity(7, 4, Bold), start.edited("hello Xbold world").entities.single())
        assertEquals(TextEntity(3, 4, Bold), start.edited("lo bold world").entities.single())
        assertEquals(TextEntity(6, 2, Bold), start.edited("hello bo world").entities.single())
        assertEquals(TextEntity(6, 2, Bold), start.edited("hello ld world").entities.single())
        assertEquals(emptyList(), start.edited("hello  world").entities)
        assertEquals(TextEntity(8, 4, Bold), start.edited("\uD83D\uDE00hello bold world").entities.single())
    }

    @Test
    fun togglingAddsJoinsAndRemovesStyles() {
        val text = FormattedText("one two three")
        val bold = text.toggled(Bold, 0, 3)
        assertEquals(listOf(TextEntity(0, 3, Bold)), bold.entities)
        assertEquals(listOf(TextEntity(0, 7, Bold)), bold.toggled(Bold, 2, 7).entities)
        val split = bold.toggled(Bold, 0, 7).toggled(Bold, 2, 4)
        assertEquals(listOf(TextEntity(0, 2, Bold), TextEntity(4, 3, Bold)), split.entities)
        val linked = text.toggled(TextUrl("https://a.example"), 4, 7).toggled(TextUrl("https://b.example"), 4, 13)
        assertEquals(listOf(TextEntity(4, 9, TextUrl("https://b.example"))), linked.entities)
        assertEquals(emptyList(), linked.toggled(TextUrl(""), 0, 13).entities)
        val mixed = FormattedText("x \uD83D\uDE00", listOf(TextEntity(0, 4, Italic), TextEntity(2, 2, EntityType.CustomEmoji(5))))
        assertEquals(listOf(TextEntity(2, 2, EntityType.CustomEmoji(5))), mixed.cleared(0, 4).entities)
    }

    @Test
    fun sentTextIsTrimmedAndEntitiesDropTrailingSpace() {
        val text = FormattedText("  bold \n\nnext  ", listOf(TextEntity(2, 7, Bold), TextEntity(9, 6, Italic), TextEntity(0, 2, Code)))
        val sent = text.trimmed()
        assertEquals("bold \n\nnext", sent.text)
        assertEquals(listOf(TextEntity(0, 4, Bold), TextEntity(7, 4, Italic)), sent.entities)
    }
}
