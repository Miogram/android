package cn.ellerotta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchTest {
    @Test
    fun aRecentSearchGoesFirstOnceWhateverItsCaseAndTheListIsCapped() {
        assertEquals(listOf("kotlin", "lunch"), listOf("lunch", "Kotlin").withRecentSearch("  kotlin "))
        assertEquals(listOf("a"), emptyList<String>().withRecentSearch("a"))
        assertEquals(listOf("x"), listOf("x").withRecentSearch("   "))
        val many = (1..RECENT_SEARCHES).map { "q$it" }.withRecentSearch("new")
        assertEquals(RECENT_SEARCHES, many.size)
        assertEquals("new", many.first())
        assertEquals("q${RECENT_SEARCHES - 1}", many.last())
    }
}
