package app.miogram

import androidx.test.core.app.ApplicationProvider
import app.miogram.data.AvatarStore
import app.miogram.data.DialogsStore
import app.miogram.data.telegram.ChatMutes
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.TelegramClientHolder
import cn.ellerotta.core.database.ChatDatabase
import cn.ellerotta.core.model.NotifyScope
import cn.ellerotta.ktproto.api.types.NotifyBroadcasts
import cn.ellerotta.ktproto.api.types.PeerNotifySettings
import cn.ellerotta.ktproto.api.types.UpdateNotifySettings
import cn.ellerotta.ktproto.api.types.UpdateShort
import cn.ellerotta.ktproto.client.InMemorySessionStorage
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChannelMutesStoredTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val blog = "ch${FakeDiscussion.BLOG_ID}"

    @Test
    fun aChannelMutedByTheDefaultIsNotStoredAsMutedOnItsOwn(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val session = InMemorySessionStorage()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        FakeTelegram().apply { groups.discussion.inDialogs = true; defaultMutes["broadcasts"] = Int.MAX_VALUE }.use { fake ->
            withTimeout(90_000) {
                val database = ChatDatabase.open(context, File(dir, "chats.db"))
                val telegram = TelegramClientHolder(dir, database = database, chatMutes = ChatMutes(context)) { id, hash, logger -> fake.config(id, hash, logger, session) }
                val repos = TestRepositories(dir, fake, telegram)
                telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                repos.updates.updateState.first { it != null }
                val store = DialogsStore(repos.dialogs, repos.updates, AvatarStore(repos.media, scope), scope)
                store.start()
                store.state.first { it.loaded }
                assertTrue(store.state.value.dialogs.first { it.key == blog }.muted)

                val shown = store.state.value
                repos.dialogs.keep(shown.dialogs, shown.archived, since = 0)
                assertEquals(-1, telegram.cache?.muteUntil(blog), "the row keeps no mute_until of its own")
                assertTrue(repos.dialogs.stored()?.dialogs.orEmpty().first { it.key == blog }.muted, "the stored list is muted by the default")

                fake.defaultMutes["broadcasts"] = 0
                fake.cluster.updates.push(UpdateShort(UpdateNotifySettings(NotifyBroadcasts, PeerNotifySettings(muteUntil = 0)), FakeTelegram.NOW))
                withTimeout(20_000) { store.state.first { state -> !state.dialogs.first { it.key == blog }.muted } }
                val after = store.state.value
                repos.dialogs.keep(after.dialogs, after.archived, since = 0)
                assertEquals(-1, telegram.cache?.muteUntil(blog))
                assertFalse(assertNotNull(repos.dialogs.stored()).dialogs.first { it.key == blog }.muted)
                assertEquals(0, ChatMutes(context).default(NotifyScope.Broadcasts), "the next process knows the default")
                telegram.close()
            }
        }
        scope.cancel()
        dir.deleteRecursively()
    }
}
