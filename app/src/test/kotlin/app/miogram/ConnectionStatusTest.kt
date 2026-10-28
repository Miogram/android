package app.miogram

import android.app.Application
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import app.miogram.connection.ConnectionStatus
import app.miogram.connection.NetworkMonitor
import app.miogram.data.AvatarStore
import app.miogram.data.DialogsStore
import app.miogram.data.telegram.SignInResult
import java.nio.file.Files
import kotlin.test.assertIs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import app.miogram.connection.statusOf
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectionStatusTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val connectivity = shadowOf(manager)

    @Test
    fun theNetworkComesFirstThenTheClientThenTheListsLoading() {
        assertEquals(ChatsStatus.WaitingForNetwork, statusOf(online = false, ready = true, loaded = true, refreshing = true))
        assertEquals(ChatsStatus.WaitingForNetwork, statusOf(online = false, ready = false, loaded = false, refreshing = false))
        assertEquals(ChatsStatus.Connecting, statusOf(online = true, ready = false, loaded = true, refreshing = true))
        assertEquals(ChatsStatus.Connecting, statusOf(online = true, ready = true, loaded = false, refreshing = false))
        assertEquals(ChatsStatus.Updating, statusOf(online = true, ready = true, loaded = true, refreshing = true))
        assertEquals(ChatsStatus.Ready, statusOf(online = true, ready = true, loaded = true, refreshing = false))
    }

    @Test
    fun aChatBeforeTheListLoadedFollowsTheClientsConnection(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            withTimeout(90_000) {
                val network = NetworkMonitor(context)
                repos.telegram.prepare(12345, "hash")
                val store = DialogsStore(repos.dialogs, repos.updates, AvatarStore(repos.media, scope), scope)
                val connection = ConnectionStatus(network, repos.telegram, store)
                assertEquals(ChatsStatus.Connecting, connection.chat.first())
                assertEquals(ChatsStatus.Connecting, connection.status.first())

                repos.telegram.connect()
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("9996624242"), "22222")).profile.id
                assertFalse(store.state.value.loaded)
                assertEquals(ChatsStatus.Ready, connection.chat.first { it == ChatsStatus.Ready })
                assertEquals(ChatsStatus.Ready, connection.chatNow)
                assertEquals(ChatsStatus.Connecting, connection.status.first())

                repos.telegram.disconnect()
                assertEquals(ChatsStatus.Connecting, connection.chat.first { it != ChatsStatus.Ready })
                repos.telegram.reconnect()
                assertEquals(ChatsStatus.Ready, connection.chat.first { it == ChatsStatus.Ready })
                shadowOf(manager).networkCallbacks.single().onLost(manager.activeNetwork!!)
                assertEquals(ChatsStatus.WaitingForNetwork, connection.chat.first { it != ChatsStatus.Ready })
            }
            scope.cancel()
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun theMonitorFollowsTheDefaultNetwork() {
        val monitor = NetworkMonitor(context)
        assertTrue(monitor.online.value)
        val callback = connectivity.networkCallbacks.single()
        val first = manager.activeNetwork!!

        callback.onLost(first)
        assertFalse(monitor.online.value)
        val second = ShadowNetwork.newInstance(4242)
        callback.onAvailable(second)
        assertTrue(monitor.online.value)

        val third = ShadowNetwork.newInstance(4343)
        callback.onAvailable(third)
        callback.onLost(second)
        assertTrue(monitor.online.value)
        callback.onLost(first)
        assertTrue(monitor.online.value)
        callback.onLost(third)
        assertFalse(monitor.online.value)
    }
}
