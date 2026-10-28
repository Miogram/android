package app.miogram

import app.miogram.data.AvatarStore
import app.miogram.data.DialogsState
import app.miogram.data.DialogsStore
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.ktproto.api.types.DialogPeer
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.UpdatePinnedDialogs
import cn.ellerotta.ktproto.api.types.UpdateShort
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class DialogsKeysTest {
    @Test
    fun aChatIsOneRowAcrossTheListAndTheArchive(): Unit = runBlocking {
        withStore(prepare = { fake ->
            fake.listed += listOf(601L, 602L).map(fake.cluster.updates::user)
            fake.afterDialogs = { folder ->
                if (folder != 1) {
                    fake.archived += 601L
                    fake.afterDialogs = null
                }
            }
        }) { fake, _, store ->
            val state = store.state.value
            assertDistinct(state)
            assertEquals(listOf("u502", "u601"), state.archived.map { it.key }.sorted())
            assertTrue(state.dialogs.none { it.archived })
            assertEquals(listOf("u501", "u602", "u${fake.selfId}").sorted(), state.dialogs.map { it.key }.sorted())
        }
    }

    @Test
    fun aRowAPageRepeatsIsOneRow(): Unit = runBlocking {
        withStore(prepare = { fake -> fake.repeated += listOf(501L, 502L) }) { fake, _, store ->
            val state = store.state.value
            assertEquals(listOf("u501", "u${fake.selfId}").sorted(), state.dialogs.map { it.key }.sorted())
            assertEquals(listOf("u502"), state.archived.map { it.key })
        }
    }

    @Test
    fun aPinnedOrderNamingAChatTwicePinsItOnce(): Unit = runBlocking {
        withStore(prepare = {}) { fake, _, store ->
            val order = listOf(PeerUser(501), PeerUser(501), PeerUser(fake.selfId)).map(::DialogPeer)
            fake.cluster.updates.push(UpdateShort(UpdatePinnedDialogs(order = order), FakeTelegram.NOW))
            val pinned = withTimeout(15_000) { store.state.first { s -> s.dialogs.firstOrNull()?.pinned == true } }
            assertEquals(listOf("u501", "u${fake.selfId}"), pinned.dialogs.map { it.key })
        }
    }

    @Test
    fun aFolderPinningAChatTwiceListsItOnce(): Unit = runBlocking {
        withStore(prepare = {}) { fake, repos, _ ->
            fake.contactsPins = listOf(InputPeerSelf, InputPeerUser(fake.selfId, 1))
            assertEquals(listOf("u${fake.selfId}"), repos.dialogs.folders().single { it.id == 2 }.pinned)
        }
    }

    private fun assertDistinct(state: DialogsState) {
        val keys = (state.dialogs + state.archived).map { it.key }
        assertEquals(keys.distinct(), keys, "a key in more than one row")
    }

    private suspend fun withStore(prepare: (FakeTelegram) -> Unit, block: suspend (FakeTelegram, TestRepositories, DialogsStore) -> Unit) {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                withTimeout(60_000) {
                    repos.telegram.connect(12345, "hash")
                    fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                    repos.updates.updateState.first { it != null }
                    prepare(fake)
                    val store = DialogsStore(repos.dialogs, repos.updates, AvatarStore(repos.media, scope), scope)
                    store.start()
                    store.state.first { it.loaded }
                    delay(300)
                    block(fake, repos, store)
                }
            } finally {
                scope.cancel()
                repos.telegram.close()
                dir.deleteRecursively()
            }
        }
    }
}
