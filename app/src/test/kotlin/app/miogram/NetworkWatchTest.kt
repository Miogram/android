package app.miogram

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.TelegramClientHolder
import cn.ellerotta.ktproto.android.watchNetwork
import cn.ellerotta.ktproto.client.InMemorySessionStorage
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworkWatchTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val connectivity = shadowOf(context.getSystemService(ConnectivityManager::class.java))

    private suspend fun until(condition: () -> Boolean) {
        while (!condition()) delay(20)
    }

    @Test
    fun watchesWhileLoggedInAndReconnectsOnANewNetwork(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val session = InMemorySessionStorage()
            val telegram = TelegramClientHolder(dir, networkWatcher = { it.watchNetwork(context) }) { id, hash, logger -> fake.config(id, hash, logger, session) }
            val repos = TestRepositories(dir, fake, telegram)
            withTimeout(90_000) {
                telegram.connect(12345, "hash")
                assertTrue(connectivity.networkCallbacks.isEmpty())
                val request = repos.auth.sendCode("9996624242")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "22222")).profile.id
                until { connectivity.networkCallbacks.size == 1 }

                val mobile = ShadowNetwork.newInstance(4242)
                val internet = ShadowNetworkCapabilities.newInstance().also { shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) }
                connectivity.networkCallbacks.single().onCapabilitiesChanged(mobile, internet)
                until { telegram.log.value.any { "reconnecting now" in it.text } }
                assertEquals(fake.selfId, repos.profiles.me().id)

                assertTrue(telegram.prepare(12345, "hash"))
                assertTrue(connectivity.networkCallbacks.isEmpty())
                telegram.connect()
                until { connectivity.networkCallbacks.size == 1 }
                telegram.disconnect()
                assertTrue(connectivity.networkCallbacks.isEmpty(), "closing the network stops watching it")
                telegram.reconnect()
                until { connectivity.networkCallbacks.size == 1 }
                telegram.close()
                assertTrue(connectivity.networkCallbacks.isEmpty())

                telegram.prepare(12345, "hash")
                telegram.connect()
                until { connectivity.networkCallbacks.size == 1 }
                repos.auth.logOut()
                until { connectivity.networkCallbacks.isEmpty() }
            }
            telegram.close()
            dir.deleteRecursively()
        }
    }
}
