package app.miogram

import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.SharedTab
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class SharedMediaRepositoryTest {
    private fun run(block: suspend (TestRepositories, FakeTelegram) -> Unit): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                repos.dialogs.dialogs()
                block(repos, fake)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun countsHideEmptyTabsAndPagesFollowOffsets(): Unit = run { repos, fake ->
        val key = "u${fake.alice.id}"
        val shared = fake.sharedMedia
        shared.messages[key] = mutableListOf(
            shared.photo(key, age = 300), shared.video(key, age = 200), shared.photo(key, age = 100),
            shared.file(key, "plan.pdf"),
            shared.voice(key), shared.round(key),
        )
        assertEquals(mapOf(SharedTab.Media to 3, SharedTab.Files to 1, SharedTab.Voice to 2), repos.shared.counts(key))

        val first = repos.shared.page(key, SharedTab.Media, limit = 2)
        assertEquals(2, first.size)
        assertTrue(first[0].id > first[1].id)
        val second = repos.shared.page(key, SharedTab.Media, offsetId = first.last().id, limit = 2)
        assertEquals(1, second.size)
        assertTrue(second.single().id < first.last().id)
        assertEquals(first.last().id, shared.searchCalls.last().offsetId)
    }

    @Test
    fun commonChatsPageAfterTheLastId(): Unit = run { repos, fake ->
        val devs = fake.groups.devs()
        fake.sharedMedia.common[fake.alice.id] = listOf(devs)
        val key = "u${fake.alice.id}"
        val chats = repos.shared.common(key, limit = 1)
        assertEquals(1, chats.size)
        assertEquals(devs.title, chats.single().title)
        assertTrue(repos.shared.common(key, maxId = devs.id).isEmpty())
    }
}
