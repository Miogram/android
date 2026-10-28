package cn.ellerotta.core.ui.components.common

import kotlinx.coroutines.sync.Semaphore

class PlaybackSlots(val capacity: Int) {
    private val semaphore = Semaphore(capacity)

    val playing: Int get() = capacity - semaphore.availablePermits

    suspend fun <T> play(waiting: suspend () -> Unit, block: suspend () -> T): T {
        if (!semaphore.tryAcquire()) {
            waiting()
            semaphore.acquire()
        }
        try {
            return block()
        } finally {
            semaphore.release()
        }
    }
}
