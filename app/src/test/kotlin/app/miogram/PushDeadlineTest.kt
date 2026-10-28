package app.miogram

import app.miogram.push.awaitWithin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PushDeadlineTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun stop() = scope.cancel()

    private fun millis(block: () -> Unit): Long {
        val started = System.nanoTime()
        block()
        return (System.nanoTime() - started) / 1_000_000
    }

    @Test
    fun finishedWorkReturnsAtOnce() {
        var ran = false
        var done = false
        val took = millis { done = scope.awaitWithin(5_000) { ran = true } }
        assertTrue(done && ran)
        assertTrue(took < 2_000, "did not wait out the deadline ($took ms)")
    }

    @Test
    fun workThatSuspendsForeverIsCutOffAtTheDeadline() {
        val cancelled = CountDownLatch(1)
        var done = true
        val took = millis {
            done = scope.awaitWithin(300) {
                try {
                    awaitCancellation()
                } finally {
                    cancelled.countDown()
                }
            }
        }
        assertFalse(done)
        assertTrue(took in 250..2_000, "waited the deadline, not longer ($took ms)")
        assertTrue(cancelled.await(5, TimeUnit.SECONDS), "the work was cancelled")
    }

    @Test
    fun workStuckInABlockingCallStillReturnsAtTheDeadline() {
        var done = true
        val took = millis { done = scope.awaitWithin(300) { Thread.sleep(4_000) } }
        assertFalse(done)
        assertTrue(took < 2_000, "a call that does not cancel does not hold the caller ($took ms)")
    }

    @Test
    fun aFailureOfTheWorkIsDroppedAndTheNextPushRuns() {
        assertTrue(scope.awaitWithin(5_000) { error("cannot be shown") })
        var ran = 0
        assertTrue(scope.awaitWithin(5_000) { ran++ })
        assertEquals(1, ran)
    }
}
