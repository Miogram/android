package app.miogram.push

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun CoroutineScope.awaitWithin(millis: Long, block: suspend () -> Unit): Boolean {
    val done = CountDownLatch(1)
    val job = launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
    job.invokeOnCompletion { done.countDown() }
    if (done.await(millis, TimeUnit.MILLISECONDS)) return true
    job.cancel()
    return false
}
