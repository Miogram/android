package app.miogram

import androidx.test.core.app.ApplicationProvider
import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.feature.chat.ChatPins
import cn.ellerotta.core.database.ChatDatabase
import cn.ellerotta.core.model.PinnedMessages
import cn.ellerotta.ktproto.client.InMemorySessionStorage
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PinsHiddenTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun pinsOf(repos: TestRepositories, scope: CoroutineScope, saved: String) =
        ChatPins(scope, saved, null, repos.pins, { block -> scope.launch { block() } }, {}).also { it.load() }

    private suspend fun ChatPins.until(check: (PinnedMessages) -> Boolean): PinnedMessages = withTimeout(10_000) { ui.first(check) }

    @Test
    fun aHiddenBarStaysHiddenUntilANewerPin(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val file = File(dir, "chats.db")
        val session = InMemorySessionStorage()
        FakeTelegram().use { fake ->
            withTimeout(90_000) {
                var database = ChatDatabase.open(context, file)
                var telegram = TelegramClientHolder(dir, database = database) { id, hash, logger -> fake.config(id, hash, logger, session) }
                var repos = TestRepositories(dir, fake, telegram)
                telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                fake.addSavedMessages(4)
                val saved = repos.dialogs.dialogs().dialogs.first { it.isSaved }.key
                val ids = repos.messages.history(saved).filter { it.text.startsWith("note ") }.map { it.id }.sorted()
                fake.pins.pinned += listOf(ids[0], ids[1])
                var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

                val first = pinsOf(repos, scope, saved)
                assertEquals(ids[1], first.until { it.shown != null }.shown?.id)
                first.hide()
                assertNull(first.ui.value.shown)
                while (repos.pins.hiddenUpTo(saved) != ids[1]) delay(20)

                val again = pinsOf(repos, scope, saved)
                val reopened = again.until { it.messages.isNotEmpty() }
                assertEquals(true, reopened.hidden)
                assertNull(reopened.shown)

                scope.cancel()
                telegram.close()
                database.close()
                database = ChatDatabase.open(context, file)
                telegram = TelegramClientHolder(dir, database = database) { id, hash, logger -> fake.config(id, hash, logger, session) }
                repos = TestRepositories(dir, fake, telegram)
                telegram.connect(12345, "hash")
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val restarted = pinsOf(repos, scope, saved)
                assertNull(restarted.until { it.messages.isNotEmpty() }.shown)

                fake.pins.pinned += ids[3]
                restarted.apply(ChatEvent.MessagesPinned(saved, listOf(ids[3]), pinned = true))
                assertEquals(ids[3], restarted.until { it.shown != null }.shown?.id)
                scope.cancel()
                telegram.close()
                database.close()
            }
        }
        dir.deleteRecursively()
    }
}
