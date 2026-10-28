package cn.ellerotta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmojiInputTest {
    @Test
    fun theBundledListHasEveryCategoryInOrder() {
        val groups = EmojiCatalog.groups
        assertEquals(EmojiCategory.entries, groups.map { it.category })
        assertEquals("😀", groups.first().emoji.first())
        assertTrue("🇺🇦" in groups.last().emoji)
        assertTrue(groups.flatMap { it.emoji }.none { emoji -> emoji.codePoints().anyMatch { it in 0x1F3FB..0x1F3FF } })
    }

    @Test
    fun parsingSkipsCommentsAndUnknownCategories() {
        val groups = parseEmojiCatalog("# header\npeople\t😀 😃\nplants\t🌵\nflags\t🏁\n")
        assertEquals(listOf(EmojiGroup(EmojiCategory.People, listOf("😀", "😃")), EmojiGroup(EmojiCategory.Flags, listOf("🏁"))), groups)
    }

    @Test
    fun theBundledTonesCoverHandsAndPeopleOnly() {
        val tones = EmojiCatalog.tones
        val all = EmojiCatalog.groups.flatMap { it.emoji }.toSet()
        assertEquals(listOf("👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"), tones["👍"])
        assertEquals("☝🏽", tones.getValue("☝️")[2])
        assertEquals("🧑🏿‍🤝‍🧑🏿", tones.getValue("🧑‍🤝‍🧑")[4])
        assertTrue(tones.keys.all { it in all })
        assertTrue(tones.values.all { it.size == SKIN_TONES })
        assertTrue("😀" !in tones && "🐶" !in tones)
        assertEquals("👍", EmojiCatalog.baseOf("👍🏾"))
        assertEquals("😀", EmojiCatalog.baseOf("😀"))
    }

    @Test
    fun pickedTonesParseRoundTripAndReset() {
        assertEquals(mapOf("👋" to listOf("1", "2", "3", "4", "5")), parseEmojiTones("# c\n👋\t1 2 3 4 5\n✋\t1 2\n\n"))
        val tones = emojiTonesWith(emojiTonesWith(emptyMap(), "👍", "👍🏽"), "👋", "👋🏿")
        assertEquals(mapOf("👍" to "👍🏽", "👋" to "👋🏿"), tones)
        assertEquals(tones, decodeEmojiTones(encodeEmojiTones(tones)))
        assertEquals(mapOf("👋" to "👋🏿"), emojiTonesWith(tones, "👍", "👍"))
        assertEquals(emptyMap(), decodeEmojiTones("broken\n\tx\n"))
    }

    @Test
    fun recentEmojiMoveToTheFrontOnceAndRoundTrip() {
        val heart = PickedEmoji("❤️")
        val star = PickedEmoji("⭐", documentId = 42)
        val list = recentEmoji(recentEmoji(recentEmoji(emptyList(), heart), star), heart)
        assertEquals(listOf(heart, star), list)
        assertEquals(2, recentEmoji(listOf(heart, star), PickedEmoji("🙂"), max = 2).size)
        assertEquals(list, decodeRecentEmoji(encodeRecentEmoji(list)))
        assertEquals(emptyList(), decodeRecentEmoji("broken\n\n"))
    }

    @Test
    fun anEmojiReplacesTheSelectionAndMovesEntities() {
        val text = FormattedText("hi there", listOf(TextEntity(0, 8, EntityType.Bold), TextEntity(3, 5, EntityType.Italic)))
        val (inserted, cursor) = text.withEmoji(PickedEmoji("😀"), 2, 2)
        assertEquals("hi😀 there", inserted.text)
        assertEquals(4, cursor)
        assertEquals(listOf(TextEntity(0, 10, EntityType.Bold), TextEntity(5, 5, EntityType.Italic)), inserted.entities)
    }

    @Test
    fun aCustomEmojiCarriesItsEntity() {
        val (inserted, cursor) = FormattedText("a").withEmoji(PickedEmoji("⭐", 42), 1, 1)
        assertEquals("a⭐", inserted.text)
        assertEquals(2, cursor)
        assertEquals(listOf(TextEntity(1, 1, EntityType.CustomEmoji(42))), inserted.entities)
        val (twice, _) = inserted.withEmoji(PickedEmoji("⭐", 42), 1, 1)
        assertEquals(listOf(TextEntity(1, 1, EntityType.CustomEmoji(42)), TextEntity(2, 1, EntityType.CustomEmoji(42))), twice.entities)
    }

    @Test
    fun backspaceDeletesWholeEmoji() {
        fun back(text: String) = FormattedText(text).deletedBackwards(text.length, text.length).first.text
        assertEquals("a", back("a👨‍👩‍👧"))
        assertEquals("a", back("a👍🏽"))
        assertEquals("a", back("a1️⃣"))
        assertEquals("a🇺🇦", back("a🇺🇦🇩🇪"))
        assertEquals("a", back("a❤️"))
        assertEquals("ab", back("abc"))
        assertEquals("", back(""))
    }

    @Test
    fun backspaceDeletesACustomEmojiOrTheSelection() {
        val text = FormattedText("x🙂y", listOf(TextEntity(1, 2, EntityType.CustomEmoji(7)), TextEntity(0, 4, EntityType.Bold)))
        val (deleted, cursor) = text.deletedBackwards(3, 3)
        assertEquals("xy", deleted.text)
        assertEquals(1, cursor)
        assertEquals(listOf(TextEntity(0, 2, EntityType.Bold)), deleted.entities)
        assertEquals("x" to 1, text.deletedBackwards(1, 4).let { it.first.text to it.second })
    }
}
