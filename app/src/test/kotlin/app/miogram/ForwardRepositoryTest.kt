package app.miogram

import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.ForwardInfo
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class ForwardRepositoryTest {
    @Test
    fun forwardsAndNamesTheOrigin(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                val profile = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile
                fake.selfId = profile.id
                fake.addSavedMessages(1)
                val post = fake.addForwardedMessage()
                val saved = repos.dialogs.dialogs().dialogs.first { it.isSaved }.key

                assertEquals(ForwardInfo("Kotlin News", "https://t.me/kotlinnews/5"), repos.messages.history(saved).single { it.id == post }.forward)

                repos.messages.forward(saved, listOf(10), saved)
                val call = assertNotNull(fake.lastForward)
                assertEquals(listOf(10), call.id)
                assertEquals(1, call.randomId.size)
                assertTrue(!call.dropAuthor && !call.dropMediaCaptions)
                assertEquals(ForwardInfo("Test User", "tg://user?id=${profile.id}"), repos.messages.history(saved).last().forward)
                repos.messages.forward(saved, listOf(10), saved, dropCaptions = true)
                assertTrue(fake.lastForward!!.dropAuthor && fake.lastForward!!.dropMediaCaptions)
                assertNull(repos.messages.history(saved).last().forward)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}
