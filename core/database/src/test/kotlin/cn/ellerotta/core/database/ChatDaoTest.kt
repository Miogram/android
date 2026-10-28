package cn.ellerotta.core.database

import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatDaoTest {
    private val db = ChatDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val dao = db.chats()

    @After
    fun close() = db.close()

    private fun dialog(key: String, position: Int, written: Long = 1, layer: Int = LAYER) = DialogRow(
        dialog = key, folder = 0, position = position, pinned = false, topMessage = 1, unread = 0, mentions = 0, unreadMark = false,
        muteUntil = 0, readOutbox = 0, message = byteArrayOf(1), draft = null, peers = key, written = written, layer = layer,
    )

    private fun messages(ids: IntRange, dialog: String = "u1", topic: Int = 0, layer: Int = LAYER) =
        ids.map { MessageRow(dialog, topic, it, byteArrayOf(it.toByte()), "u1", layer) }

    private suspend fun ids(dialog: String = "u1", topic: Int = 0) = dao.messages(dialog, topic, Int.MAX_VALUE, 10_000, LAYER).map { it.id }.reversed()

    @Test
    fun dialogsKeepTheirPlaceAndTheListDropsWhatItLeftOut(): Unit = runBlocking {
        dao.putDialogs(listOf(dialog("u1", 0), dialog("u2", 1), dialog("u3", 2)))
        dao.keepDialogs(gone = listOf("u2"), before = 10) { rows -> rows.filter { it.dialog != "u2" }.map { it.copy(position = if (it.dialog == "u3") 0 else 1, unread = 4) } }
        assertEquals(listOf("u3" to 4, "u1" to 4), dao.dialogs(LAYER).map { it.dialog to it.unread })

        dao.putDialogs(listOf(dialog("u1", 99, written = 20).copy(unread = 7), dialog("u4", 5, written = 20), dialog("u5", 6, written = 1)))
        dao.keepDialogs(gone = listOf("u4"), before = 15) { rows -> rows.filter { it.dialog != "u4" && it.dialog != "u5" } }
        assertEquals(listOf("u3" to 0, "u1" to 1, "u4" to 5, "u5" to 6), dao.dialogs(LAYER).map { it.dialog to it.position })
        assertEquals(7, dao.dialogsOf(listOf("u1")).single().unread)

        assertEquals(1, dao.newTop("u1", 5, byteArrayOf(5), "u1 u9"))
        assertEquals(0, dao.newTop("u1", 4, byteArrayOf(4), "u1"))
        assertEquals(1, dao.editedTop("u1", 5, byteArrayOf(6), "u1"))
        dao.readOutbox("u1", 3)
        dao.readOutbox("u1", 2)
        val u1 = dao.dialogsOf(listOf("u1")).single()
        assertEquals(5, u1.topMessage)
        assertEquals(6, u1.message?.single()?.toInt())
        assertEquals(3, u1.readOutbox)
    }

    @Test
    fun aNewestPageJoinsTheStoredMessagesOrReplacesThem(): Unit = runBlocking {
        dao.putNewest("u1", 0, messages(51..100), limit = 50, keep = 500, now = 1)
        dao.putNewest("u1", 0, messages(81..130).filter { it.id != 90 }, limit = 49, keep = 500, now = 2)
        assertEquals((51..130).filter { it != 90 }, ids())
        assertFalse(dao.history("u1", 0)!!.complete)

        dao.putNewest("u1", 0, messages(200..249), limit = 50, keep = 500, now = 3)
        assertEquals((200..249).toList(), ids())

        dao.staleAll()
        dao.putNewest("u1", 0, messages(230..279), limit = 50, keep = 500, now = 4)
        assertEquals((230..279).toList(), ids())
        assertFalse(dao.history("u1", 0)!!.stale)

        dao.putNewest("u1", 0, messages(1..20), limit = 50, keep = 500, now = 5)
        assertEquals((1..20).toList(), ids())
        assertTrue(dao.history("u1", 0)!!.complete)
        assertEquals(StoredPage(emptyList(), true), dao.older("u1", 0, 1, 50, LAYER, 6))
    }

    @Test
    fun olderPagesAreKeptOnlyRightBelowTheStoredOnes(): Unit = runBlocking {
        dao.putNewest("u1", 0, messages(101..150), limit = 50, keep = 120, now = 1)
        assertTrue(dao.putOlder("u1", 0, before = 101, rows = messages(51..100), limit = 50, keep = 120, now = 2))
        assertEquals((51..150).toList(), ids())
        val page = dao.older("u1", 0, before = 101, limit = 30, layer = LAYER, now = 3)!!
        assertEquals((100 downTo 71).toList(), page.rows.map { it.id })
        assertFalse(page.complete)
        assertNull(dao.older("u1", 0, before = 51, limit = 30, layer = LAYER, now = 4))
        assertNull(dao.older("u1", 0, before = 400, limit = 30, layer = LAYER, now = 4))
        assertFalse(dao.putOlder("u1", 0, before = 400, rows = messages(300..349), limit = 50, keep = 120, now = 4))

        assertTrue(dao.putOlder("u1", 0, before = 51, rows = messages(1..50), limit = 60, keep = 120, now = 5))
        assertEquals((31..150).toList(), ids())
        assertFalse(dao.history("u1", 0)!!.complete)
        assertFalse(dao.putOlder("u1", 0, before = 31, rows = messages(1..30), limit = 60, keep = 120, now = 6))

        dao.putNewest("u1", 7, messages(140..150, topic = 7), limit = 50, keep = 120, now = 6)
        dao.append(messages(151..151) + messages(151..151, topic = 7) + messages(151..151, dialog = "u9"), keep = 120)
        assertEquals(151, ids().last())
        assertEquals(32, ids().first())
        assertEquals((140..151).toList(), ids(topic = 7))
        assertEquals(emptyList(), ids("u9"))
        assertEquals(2, dao.edit("u1", 151, byteArrayOf(9), "u1 u2"))
        assertEquals(2, dao.react("u1", 151, byteArrayOf(3)))
        assertEquals(listOf("u1 u2", "u1 u2"), dao.message("u1", 151).map { it.peers })
        dao.putNewest("ch5", 0, messages(150..151, dialog = "ch5"), limit = 50, keep = 120, now = 7)
        assertEquals(4, dao.deleteCommon(listOf(150, 151)))
        assertEquals(listOf(150, 151), ids("ch5"))
        assertEquals(2, dao.delete("ch5", listOf(150, 151)))

        dao.stale("u1")
        assertEquals(149, dao.older("u1", 0, Int.MAX_VALUE, 10, LAYER, now = 8)!!.rows.first().id)
        assertNull(dao.older("u1", 0, before = 100, limit = 10, layer = LAYER, now = 8))
    }

    @Test
    fun limitsLayersMinPeersAndClearing(): Unit = runBlocking {
        (1..5).forEach { dao.putNewest("u$it", 0, messages(1..3, dialog = "u$it"), limit = 50, keep = 500, now = it.toLong()) }
        dao.older("u1", 0, Int.MAX_VALUE, 50, LAYER, now = 10)
        dao.pruneHistories(keep = 3)
        val kept = listOf("u1", "u2", "u3", "u4", "u5").mapNotNull { dao.history(it, 0) }
        assertEquals(listOf("u1", "u5", "u4"), kept.sortedByDescending { it.used }.map { it.dialog })
        assertEquals(emptyList(), ids("u2"))

        val full = PeerRow("u7", "Alice Example", min = false, tl = byteArrayOf(1), seen = 1, layer = LAYER)
        dao.putPeers(listOf(full))
        dao.putPeers(listOf(full.copy(name = "Alice", min = true, tl = byteArrayOf(2), seen = 2), PeerRow("u8", "Bob", min = true, tl = byteArrayOf(3), seen = 3, layer = LAYER)))
        assertEquals(listOf("Alice Example", "Bob"), dao.peers(listOf("u7", "u8"), LAYER).sortedBy { it.peer }.map { it.name })
        dao.prunePeers(keep = 1)
        assertEquals(listOf("u8"), dao.peers(listOf("u7", "u8"), LAYER).map { it.peer })

        dao.append(messages(4..4, dialog = "u1", layer = LAYER - 1), keep = 500)
        dao.putDialogs(listOf(dialog("u1", 0), dialog("u2", 1, layer = LAYER - 1)))
        dao.putFolders(listOf(FolderRow(0, byteArrayOf(1), LAYER - 1), FolderRow(1, byteArrayOf(2), LAYER)))
        dao.dropOtherLayers(LAYER)
        assertNull(dao.history("u1", 0))
        assertEquals((1..3).toList(), ids("u5"))
        assertEquals(listOf("u1"), dao.dialogs(LAYER).map { it.dialog })
        assertEquals(listOf(1), dao.folders(LAYER).map { it.position })

        dao.putMeta(MetaRow("owner", "42"))
        dao.clear()
        assertEquals(emptyList(), dao.dialogs(LAYER))
        assertEquals(emptyList(), ids("u5"))
        assertNull(dao.history("u5", 0))
        assertEquals(emptyList(), dao.peers(listOf("u8"), LAYER))
        assertEquals(emptyList(), dao.folders(LAYER))
        assertNull(dao.meta("owner"))
    }

    @Test
    fun aDialogsEveryHistoryGoesWithItsTopicsAndTheOthersStay(): Unit = runBlocking {
        dao.putNewest("u1", 0, messages(1..5), limit = 50, keep = 500, now = 1)
        dao.putNewest("u1", 7, messages(10..12, topic = 7), limit = 50, keep = 500, now = 1)
        dao.putNewest("u2", 0, messages(1..3, dialog = "u2"), limit = 50, keep = 500, now = 1)
        dao.dropDialogHistory("u1")
        assertEquals(emptyList(), ids("u1"))
        assertEquals(emptyList(), ids("u1", topic = 7))
        assertNull(dao.history("u1", 0))
        assertNull(dao.history("u1", 7))
        assertEquals(listOf(1, 2, 3), ids("u2"))
        assertTrue(dao.history("u2", 0)!!.complete)
    }

    private companion object {
        const val LAYER = 229
    }
}
