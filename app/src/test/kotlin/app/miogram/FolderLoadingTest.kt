package app.miogram

import app.miogram.data.AvatarStore
import app.miogram.data.DialogsState
import app.miogram.data.DialogsStore
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.ktproto.api.types.DialogFilter
import cn.ellerotta.ktproto.api.types.DialogFilterChatlist
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.TextWithEntities
import cn.ellerotta.ktproto.api.types.TypeDialogFilter
import cn.ellerotta.ktproto.api.types.TypeInputPeer
import cn.ellerotta.ktproto.api.types.User
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

class FolderLoadingTest {
    private fun named(id: Long) = InputPeerUser(id, id * 3)

    private fun title(text: String) = TextWithEntities(text, emptyList())

    private fun byType(id: Int, vararg flags: String, excludeArchived: Boolean = false, include: List<TypeInputPeer> = emptyList()): TypeDialogFilter = DialogFilter(
        contacts = "contacts" in flags,
        nonContacts = "nonContacts" in flags,
        bots = "bots" in flags,
        excludeArchived = excludeArchived,
        id = id,
        title = title("folder $id"),
        pinnedPeers = emptyList(),
        includePeers = include,
        excludePeers = emptyList(),
    )

    private fun stranger(id: Long) = User(id = id, accessHash = id * 3, firstName = "Nora")

    @Test
    fun aFolderByTypeReadsOnThroughTheMainListsPages(): Unit = scenario(
        setup = {
            listed += (1000L until 1230L).map { id -> if (id == 1150L || id == 1160L) stranger(id) else cluster.updates.user(id) }
            filters += byType(3, "nonContacts")
        },
    ) { fake, store ->
        val folder = store.state.value.folders.single { it.id == 3 }
        val first = store.state.value
        assertEquals(listOf("u501"), first.chatsOf(folder).map { it.key })
        assertTrue(first.hasMore(folder))
        assertEquals(2, fake.dialogsCalls.get())

        store.loadMore(3)
        val done = store.state.value
        assertEquals(listOf("u501", "u1150", "u1160"), done.chatsOf(folder).map { it.key })
        assertFalse(done.hasMore(folder))
        assertEquals(4, fake.dialogsCalls.get())
        store.loadMore(3)
        assertEquals(4, fake.dialogsCalls.get())
    }

    @Test
    fun aFolderWithoutMatchesIsEmptyOnlyOnceTelegramHasNoMore(): Unit = scenario(
        setup = {
            listed += (1000L until 1230L).map(cluster.updates::user)
            filters += byType(7, "bots")
        },
    ) { fake, store ->
        val folder = store.state.value.folders.single { it.id == 7 }
        assertEquals(emptyList(), store.state.value.chatsOf(folder))
        assertTrue(store.state.value.hasMore(folder))
        store.loadMore(7)
        assertEquals(emptyList(), store.state.value.chatsOf(folder))
        assertFalse(store.state.value.hasMore(folder))
        assertEquals(4, fake.dialogsCalls.get())
    }

    @Test
    fun aFolderAsksForMoreAtItsEndOnePageAtATime(): Unit = scenario(
        setup = { listed += (1000L until 1230L).map(cluster.updates::user) },
    ) { fake, store ->
        val contacts = store.state.value.folders.single { it.id == 2 }
        assertEquals(99, store.state.value.chatsOf(contacts).size)
        store.loadMore(2)
        assertEquals(199, store.state.value.chatsOf(contacts).size)
        assertTrue(store.state.value.hasMore(contacts))
        assertEquals(3, fake.dialogsCalls.get())
        store.loadMore(ChatFolder.ALL)
        assertEquals(4, fake.dialogsCalls.get())
        assertFalse(store.state.value.hasMore(store.state.value.folders.first { it.isAll }))
    }

    @Test
    fun namedChatsOutsideThePagesAreFetchedTogetherInTheirOrder(): Unit = scenario(
        setup = {
            listed += (1000L until 1230L).map(cluster.updates::user)
            filters += DialogFilter(
                id = 3,
                title = title("Picks"),
                pinnedPeers = listOf(named(1229), named(1100)),
                includePeers = listOf(named(1228)),
                excludePeers = emptyList(),
            )
            filters += DialogFilterChatlist(id = 4, title = title("Shared"), pinnedPeers = emptyList(), includePeers = listOf(named(1227)))
        },
    ) { fake, store ->
        val state = store.until { it.loaded && it.wanted(it.folders).isEmpty() }
        val picks = state.folders.single { it.id == 3 }
        val shared = state.folders.single { it.id == 4 }
        assertEquals(listOf("u1229", "u1100", "u1228"), state.chatsOf(picks).map { it.key })
        assertEquals(listOf("u1227"), state.chatsOf(shared).map { it.key })
        assertFalse(state.hasMore(picks))
        assertFalse(state.hasMore(shared))
        assertEquals(listOf(listOf("u1229", "u1100", "u1228", "u1227")), fake.peerDialogsCalls)
        assertEquals(2, fake.dialogsCalls.get())
        store.loadMore(3)
        store.loadMore(4)
        assertEquals(1, fake.peerDialogsCalls.size)
        assertEquals(2, fake.dialogsCalls.get())
        assertFalse(state.dialogs.any { it.key == "u1229" })
        assertTrue(state.pulled.any { it.key == "u1229" })
    }

    @Test
    fun aChatNamedByAFolderMovesIntoAllChatsWhenItWritesAgain(): Unit = scenario(
        setup = {
            listed += (1000L until 1230L).map(cluster.updates::user)
            filters += DialogFilterChatlist(id = 4, title = title("Shared"), pinnedPeers = emptyList(), includePeers = listOf(named(1229)))
        },
    ) { fake, store ->
        store.until { it.pulled.any { row -> row.key == "u1229" } }
        fake.cluster.updates.message(1229, "write")
        fake.cluster.updates.message(1229, "back again")
        val state = store.until { it.dialogs.firstOrNull()?.key == "u1229" && it.dialogs.first().preview.text == "back again" }
        assertTrue(state.pulled.none { it.key == "u1229" })
        assertEquals("back again", state.dialogs.first().preview.text)
    }

    @Test
    fun archivedChatsOfAFolderThatDoesNotLeaveThemOutAreReadFromTheArchive(): Unit = scenario(
        setup = {
            listed += (3000L until 3060L).map(cluster.updates::user)
            val archivedUsers = (4000L until 4150L).map { id -> if (id == 4120L) stranger(id) else cluster.updates.user(id) }
            listed += archivedUsers
            archived += archivedUsers.map { it.id }
            filters += byType(3, "nonContacts")
            filters += byType(5, "nonContacts", excludeArchived = true)
            filters += byType(6, include = listOf(named(4140)))
        },
    ) { fake, store ->
        val state = store.until { it.loaded && it.wanted(it.folders).isEmpty() }
        val leaves = state.folders.single { it.id == 5 }
        val keeps = state.folders.single { it.id == 3 }
        val named = state.folders.single { it.id == 6 }

        assertEquals(listOf("u501"), state.chatsOf(leaves).map { it.key })
        assertFalse(state.hasMore(leaves))
        assertEquals(2, fake.dialogsCalls.get())
        store.loadMore(5)
        assertEquals(2, fake.dialogsCalls.get())

        assertEquals(listOf("u4140"), state.chatsOf(named).map { it.key })
        assertTrue(state.chatsOf(named).single().archived)
        assertEquals(listOf(listOf("u4140")), fake.peerDialogsCalls)
        assertFalse(state.hasMore(named))

        assertEquals(listOf("u501"), state.chatsOf(keeps).map { it.key })
        assertTrue(state.hasMore(keeps))
        store.loadMore(3)
        val done = store.state.value
        assertEquals(listOf("u501", "u4120"), done.chatsOf(keeps).map { it.key })
        assertTrue(done.chatsOf(keeps).last().archived)
        assertFalse(done.hasMore(keeps))
        assertEquals(3, fake.dialogsCalls.get())
        assertTrue(done.archived.any { it.key == "u4120" })
    }

    @Test
    fun theArchiveScreenReadsOnThroughTheArchivesPagesAndStopsAtItsEnd(): Unit = scenario(
        setup = {
            val archivedUsers = (4000L until 4230L).map(cluster.updates::user)
            listed += archivedUsers
            archived += archivedUsers.map { it.id }
        },
    ) { fake, store ->
        val first = store.state.value
        assertEquals(100, first.archived.size)
        assertFalse(first.archiveComplete)
        assertEquals(2, fake.dialogsCalls.get())

        store.loadMoreArchive()
        assertEquals(200, store.state.value.archived.size)
        assertEquals(3, fake.dialogsCalls.get())
        store.loadMoreArchive()
        val done = store.state.value
        assertEquals(231, done.archived.size)
        assertTrue(done.archiveComplete)
        assertTrue(done.archived.any { it.key == "u4229" })
        assertEquals(4, fake.dialogsCalls.get())
        store.loadMoreArchive()
        assertEquals(4, fake.dialogsCalls.get())
    }

    @Test
    fun aFolderAskedForBeforeTheFirstLoadFillsAfterIt(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            fake.staggered = true
            fake.listed += (1000L until 1230L).map { id -> if (id == 1150L) stranger(id) else fake.cluster.updates.user(id) }
            fake.filters += byType(3, "nonContacts")
            withTimeout(120_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                repos.updates.updateState.first { it != null }
                val store = DialogsStore(repos.dialogs, repos.updates, AvatarStore(repos.media, scope), scope)
                store.start()
                store.loadMore(3)
                val state = store.until { s -> s.loaded && s.folders.any { it.id == 3 } && s.complete }
                val folder = state.folders.single { it.id == 3 }
                assertEquals(listOf("u501", "u1150"), state.chatsOf(folder).map { it.key })
            }
            scope.cancel()
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    private fun scenario(setup: FakeTelegram.() -> Unit, check: suspend (FakeTelegram, DialogsStore) -> Unit): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            fake.staggered = true
            fake.setup()
            withTimeout(120_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                repos.updates.updateState.first { it != null }
                val store = DialogsStore(repos.dialogs, repos.updates, AvatarStore(repos.media, scope), scope)
                store.start()
                store.until { it.loaded }
                store.until { !it.refreshing }
                delay(300)
                check(fake, store)
            }
            scope.cancel()
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    private suspend fun DialogsStore.until(condition: (DialogsState) -> Boolean): DialogsState = withTimeout(30_000) { state.first(condition) }
}
