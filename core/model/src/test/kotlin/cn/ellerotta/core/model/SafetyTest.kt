package cn.ellerotta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SafetyTest {
    private fun user(id: Int) = BlockedUser("u$id", "User $id")

    @Test
    fun pagesAddUpAndStopAtTheTotal() {
        val first = BlockedList(listOf(user(1), user(2)), total = 5, read = 2, loaded = true)
        assertTrue(first.more)
        val second = first.plus(BlockedList(listOf(user(3), user(4)), total = 5, read = 2, loaded = true))
        assertEquals(listOf("u1", "u2", "u3", "u4"), second.users.map { it.key })
        assertEquals(4, second.read)
        assertTrue(second.more)
        val last = second.plus(BlockedList(listOf(user(5)), total = 5, read = 1, loaded = true))
        assertFalse(last.more)
    }

    @Test
    fun entriesThatAreNotUsersStillCountAsRead() {
        val page = BlockedList(listOf(user(1), user(2)), total = 4, read = 3, loaded = true)
        assertEquals(3, page.read)
        assertTrue(page.more)
    }

    @Test
    fun aPageRepeatingAUserKeepsItOnce() {
        val list = BlockedList(listOf(user(1), user(2)), total = 3, read = 2, loaded = true).plus(BlockedList(listOf(user(2), user(3)), total = 3, read = 2, loaded = true))
        assertEquals(listOf("u1", "u2", "u3"), list.users.map { it.key })
    }

    @Test
    fun unblockingTakesTheRowAndOneOffTheCounts() {
        val list = BlockedList(listOf(user(1), user(2)), total = 2, read = 2, loaded = true)
        val rest = list.without("u1")
        assertEquals(listOf("u2"), rest.users.map { it.key })
        assertEquals(1, rest.total)
        assertEquals(1, rest.read)
        assertFalse(rest.more)
        assertSame(rest, rest.without("u9"))
    }

    @Test
    fun theBarShowsWhenAnyButtonIsOffered() {
        assertFalse(PeerBar().shown)
        assertTrue(PeerBar(reportSpam = true).shown)
        assertTrue(PeerBar(addContact = true).shown)
        assertTrue(PeerBar(block = true).shown)
    }
}
