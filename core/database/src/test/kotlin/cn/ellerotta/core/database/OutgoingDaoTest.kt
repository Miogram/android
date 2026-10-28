package cn.ellerotta.core.database

import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OutgoingDaoTest {
    private val db = ChatDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val dao = db.outgoing()

    @After
    fun close() = db.close()

    private fun row(id: Long, account: Long = 7, file: String? = null, thumb: String? = null) =
        OutgoingRow(id = id, account = account, dialog = "u1", topic = 0, batch = id, date = 1, request = byteArrayOf(id.toByte()), shown = null, file = file, thumb = thumb)

    @Test
    fun rowsKeepTheirOrderPartsAndFailuresPerAccount(): Unit = runBlocking {
        dao.put(listOf(row(5, file = "5.ogg"), row(2), row(9, account = 8, file = "9.mp4", thumb = "9.jpg")))
        assertEquals(listOf(2L, 5L), dao.rows(7).map { it.id })
        assertEquals(listOf(9L), dao.rows(8).map { it.id })

        dao.uploaded(5, 3)
        dao.media(5, byteArrayOf(4))
        dao.failed(listOf(2, 5), "CHAT_WRITE_FORBIDDEN")
        val five = dao.rows(7).single { it.id == 5L }
        assertEquals(3, five.uploaded)
        assertEquals(listOf<Byte>(4), five.media!!.toList())
        assertEquals(listOf("CHAT_WRITE_FORBIDDEN", "CHAT_WRITE_FORBIDDEN"), dao.rows(7).map { it.failed })
        dao.failed(listOf(2), null)
        assertEquals(null, dao.rows(7).first().failed)

        assertEquals(setOf("5.ogg", "9.mp4", "9.jpg"), dao.files().toSet())
        dao.delete(listOf(5, 9))
        assertEquals(listOf(2L), dao.rows(7).map { it.id })
        assertEquals(emptyList(), dao.files())
    }
}
