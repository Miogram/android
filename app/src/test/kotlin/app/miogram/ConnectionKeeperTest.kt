package app.miogram

import app.miogram.connection.ConnectionKeeper
import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.SignInResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectionKeeperTest {
    private class Setup(val fake: FakeTelegram, val repos: TestRepositories, val scheduler: TestCoroutineScheduler, val music: MutableStateFlow<Boolean>, val keeper: ConnectionKeeper) {
        fun connections() = fake.cluster.servers.sumOf { it.openConnections() }

        suspend fun until(condition: () -> Boolean) {
            withTimeout(20_000) {
                while (true) {
                    scheduler.runCurrent()
                    if (condition()) break
                    delay(20)
                }
            }
        }

        fun pass(millis: Long) {
            scheduler.advanceTimeBy(millis)
            scheduler.runCurrent()
        }

        suspend fun closed() = until { !repos.telegram.connected && connections() == 0 }
    }

    private fun test(block: suspend Setup.() -> Unit): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        FakeTelegram().use { fake ->
            val repos = TestRepositories(dir, fake)
            val scheduler = TestCoroutineScheduler()
            val scope = CoroutineScope(StandardTestDispatcher(scheduler))
            val music = MutableStateFlow(false)
            val keeper = ConnectionKeeper(repos.telegram, music, scope, ConnectionKeeper.IDLE_MILLIS) { scheduler.currentTime }
            withTimeout(90_000) {
                keeper.start()
                keeper.foreground(true)
                scheduler.runCurrent()
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("+999 66 1 4242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "11111")).profile.id
                repos.updates.updateState.first { it != null }
                Setup(fake, repos, scheduler, music, keeper).block()
            }
            scope.cancel()
            repos.telegram.close()
        }
        dir.deleteRecursively()
    }

    @Test
    fun aMinuteInTheBackgroundClosesTheNetworkAndTheForegroundCatchesUp() = test {
        keeper.foreground(false)
        pass(59_000)
        assertTrue(repos.telegram.connected, "still connected before the minute is over")
        assertTrue(connections() > 0)

        pass(1_000)
        closed()
        val missed = CoroutineScope(Dispatchers.Default).async { repos.updates.events().filterIsInstance<ChatEvent.NewMessage>().first { it.message.text == "while away" } }
        fake.cluster.updates.message(501, "while away", push = false)
        delay(500)
        assertEquals(0, connections(), "nothing connects by itself in the background")

        keeper.foreground(true)
        until { repos.telegram.connected }
        assertEquals("u501", withTimeout(20_000) { missed.await() }.key)
    }

    @Test
    fun musicKeepsTheNetworkAndAPushHoldOpensItOnlyWhileItRuns() = test {
        music.value = true
        keeper.foreground(false)
        pass(5 * 60_000)
        assertTrue(repos.telegram.connected, "music plays: the network stays")

        music.value = false
        scheduler.runCurrent()
        closed()

        val me = keeper.hold {
            scheduler.runCurrent()
            repos.profiles.me()
        }
        assertEquals(fake.selfId, me.id)
        closed()
        assertFalse(keeper.foreground.value)
    }
}
