package cn.ellerotta.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class MentionsTest {
    private val alice = ChatMember("u501", "Alice Example", firstName = "Alice")
    private val carol = ChatMember("u503", "Carol Writer", username = "carol_writer", firstName = "Carol")
    private val bot = ChatMember("u504", "Helper", username = "helperbot", firstName = "Helper", bot = true)
    private val me = ChatMember("u1", "Test User", username = "tester", self = true)

    @Test
    fun aMentionIsAnAtAfterASpaceUpToTheCursor() {
        assertEquals(TypedMention(0, 1, ""), mentionAt("@", 1))
        assertEquals(TypedMention(3, 7, "al"), mentionAt("hi @ali", 6))
        assertEquals(TypedMention(0, 4, "Але"), mentionAt("@Але", 4))
        assertNull(mentionAt("mail@ex", 7))
        assertNull(mentionAt("@al ", 4))
        assertNull(mentionAt("hi", 2))
        assertNull(mentionAt("@al", 0))
    }

    @Test
    fun suggestionsMatchNamesAndUsernamesSpeakersFirst() {
        val members = listOf(me, alice, carol, bot, alice)
        assertEquals(listOf(alice, carol, bot), mentionSuggestions(members, "", emptyList()))
        assertEquals(listOf(bot, carol, alice), mentionSuggestions(members, "", listOf("u504", "u503")))
        assertEquals(listOf(carol), mentionSuggestions(members, "wri", emptyList()))
        assertEquals(listOf(carol), mentionSuggestions(members, "CAROL_", emptyList()))
        assertEquals(listOf(bot), mentionSuggestions(members, "help", emptyList()))
        assertEquals(emptyList(), mentionSuggestions(members, "test", emptyList()))
    }

    @Test
    fun aPickPutsTheUsernameOrAMentionByName() {
        val typed = assertNotNullMention("hi @ca", 6)
        assertEquals(FormattedText("hi @carol_writer ") to 17, FormattedText("hi @ca").withMention(typed, carol))

        val (text, cursor) = FormattedText("@al how are you").withMention(assertNotNullMention("@al how are you", 3), alice)
        assertEquals("Alice how are you", text.text)
        assertEquals(listOf(TextEntity(0, 5, EntityType.MentionName(501))), text.entities)
        assertEquals(6, cursor)
    }

    private fun assertNotNullMention(text: String, cursor: Int): TypedMention = mentionAt(text, cursor) ?: error("no mention in $text")
}
