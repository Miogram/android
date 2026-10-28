package cn.ellerotta.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class ChatCreationTest {
    @Test
    fun usernamesAreCheckedBeforeTelegramIsAsked() {
        assertNull(usernameProblem("kotlin_devs"))
        assertNull(usernameProblem("a123"))
        assertEquals(UsernameProblem.TooShort, usernameProblem("abc"))
        assertEquals(UsernameProblem.TooShort, usernameProblem(""))
        assertEquals(UsernameProblem.TooLong, usernameProblem("a".repeat(33)))
        assertNull(usernameProblem("a".repeat(32)))
        assertEquals(UsernameProblem.BadStart, usernameProblem("1abc"))
        assertEquals(UsernameProblem.BadStart, usernameProblem("_abcd"))
        assertEquals(UsernameProblem.BadCharacter, usernameProblem("ab-cd"))
        assertEquals(UsernameProblem.BadCharacter, usernameProblem("abcдe"))
        assertEquals(UsernameProblem.BadEnd, usernameProblem("abcd_"))
    }

    @Test
    fun titlesAreTrimmedAndCut() {
        assertEquals("Book club", chatTitleOf("  Book club \n"))
        assertEquals(CHAT_TITLE_MAX, chatTitleOf("x".repeat(300)).length)
    }

    @Test
    fun theSelectionTogglesInPickOrder() {
        assertEquals(listOf("u1", "u2"), toggled(toggled(emptyList(), "u1"), "u2"))
        assertEquals(listOf("u2"), toggled(listOf("u1", "u2"), "u1"))
    }

    @Test
    fun pickableContactsFollowTheSearch() {
        val people = listOf(ContactItem("u1", 1, "Alice Smith"), ContactItem("u2", 2, "Bob Jones", username = "bobby"), ContactItem("u3", 3, "Alina"))
        assertEquals(listOf("u1", "u3"), pickableContacts(people, "ali").map { it.key })
        assertEquals(listOf("u2"), pickableContacts(people, "@bob").map { it.key })
        assertEquals(listOf("u1", "u2", "u3"), pickableContacts(people, "").map { it.key })
    }
}
