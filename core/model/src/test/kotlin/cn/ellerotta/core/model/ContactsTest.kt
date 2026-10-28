package cn.ellerotta.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ContactsTest {
    private fun contact(name: String, presence: Presence) = ContactItem("u${name.hashCode()}", name.hashCode().toLong(), name, presence)

    @Test
    fun onlineComesFirstThenLastSeenThenTheApproximateStatuses() {
        val list = listOf(
            contact("Month", Presence.LastMonth),
            contact("Old", Presence.Offline(1_000)),
            contact("Bea", Presence.Online),
            contact("Recent", Presence.Recently),
            contact("New", Presence.Offline(2_000)),
            contact("Ann", Presence.Online),
            contact("Gone", Presence.LongAgo),
            contact("Week", Presence.LastWeek),
        )
        assertEquals(listOf("Ann", "Bea", "New", "Old", "Recent", "Week", "Month", "Gone"), list.sortedByPresence().map { it.name })
    }

    @Test
    fun searchFindsWordsUsernamesAndNumbers() {
        val carol = ContactItem("u3", 3, "Carol Writer", phone = "15550001111", username = "carol_writer")
        assertTrue(carol.matches("wri"))
        assertTrue(carol.matches("Carol W"))
        assertTrue(carol.matches("@carol_"))
        assertTrue(carol.matches("+1 555"))
        assertTrue(carol.matches(" "))
        assertFalse(carol.matches("rol"))
        assertFalse(carol.matches("55"))
    }
}
