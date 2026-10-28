package cn.ellerotta.core.ui

import cn.ellerotta.core.ui.components.common.PlaybackSlots
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Test

class PlaybackSlotsTest {
    private suspend fun until(condition: () -> Boolean) = withTimeout(5_000) { while (!condition()) yield() }

    @Test
    fun atMostCapacityPlayAndWaitersFollowInOrder(): Unit = runBlocking {
        val slots = PlaybackSlots(2)
        val events = mutableListOf<String>()
        val done = List(4) { CompletableDeferred<Unit>() }
        val jobs = List(4) { i ->
            launch { slots.play(waiting = { events += "still $i" }) { events += "play $i"; done[i].await() } }
        }
        until { events.size == 4 }
        assertEquals(listOf("play 0", "play 1", "still 2", "still 3"), events)
        assertEquals(2, slots.playing)

        jobs[3].cancel()
        done[0].complete(Unit)
        until { "play 2" in events }
        assertEquals(2, slots.playing)

        jobs[1].cancel()
        jobs[1].join()
        assertEquals(1, slots.playing)
        assertTrue("play 3" !in events)
        done[2].complete(Unit)
        jobs[2].join()
        assertEquals(0, slots.playing)
    }
}
