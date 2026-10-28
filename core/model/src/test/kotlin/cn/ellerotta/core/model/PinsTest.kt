package cn.ellerotta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PinsTest {
    private fun message(id: Int) = MessageItem(id, "pin $id", out = false, date = id)

    private val three = PinnedMessages(listOf(message(30), message(20), message(10)))

    @Test
    fun theBarStartsAtTheNewestAndTapsGoOlderThenRoundAgain() {
        assertEquals(30 to 3, three.shown?.id to three.number)
        val second = three.next()
        assertEquals(20 to 2, second.shown?.id to second.number)
        val third = second.next()
        assertEquals(10 to 1, third.shown?.id to third.number)
        assertEquals(30, third.next().shown?.id)
        val one = PinnedMessages(listOf(message(5)))
        assertEquals(one, one.next())
        assertNull(PinnedMessages().shown)
    }

    @Test
    fun unpinsKeepTheShownMessageOrTheSamePlace() {
        val atTwenty = three.next()
        assertEquals(20, atTwenty.without(listOf(30)).shown?.id)
        assertEquals(10, atTwenty.without(listOf(20)).shown?.id)
        assertEquals(20, atTwenty.next().without(listOf(10)).shown?.id)
        assertNull(three.without(listOf(10, 20, 30)).shown)
        assertEquals(setOf(10, 30), three.without(listOf(20)).ids)
    }

    @Test
    fun aReloadShowsANewPinElseStaysOnTheShownOne() {
        val atTen = three.next().next()
        assertEquals(10, atTen.reloaded(three.messages).shown?.id)
        val newer = listOf(message(40)) + three.messages
        val reloaded = atTen.reloaded(newer)
        assertEquals(40 to 4, reloaded.shown?.id to reloaded.number)
        assertEquals(30, atTen.reloaded(listOf(message(30), message(20))).shown?.id)
    }

    @Test
    fun theIndexCountsFromTheOldestAndHiddenPinsComeBackWithANewerOne() {
        assertEquals(2, three.index)
        assertEquals(0, three.next().next().index)
        val hidden = three.hide()
        assertNull(hidden.shown)
        assertEquals(true, hidden.hidden)
        assertNull(hidden.without(listOf(20)).shown)
        assertEquals(40, hidden.reloaded(listOf(message(40)) + three.messages).shown?.id)
    }

    @Test
    fun aMessageOpenedFromTheListIsWhereTheBarStandsAndTapsGoOnFromIt() {
        val atTen = three.showing(10)
        assertEquals(10 to 0, atTen.shown?.id to atTen.index)
        assertEquals(30, atTen.next().shown?.id)
        assertEquals(20, three.showing(20).shown?.id)
        assertEquals(atTen, atTen.showing(99))
    }
}
