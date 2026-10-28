package cn.ellerotta.core.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatDatabaseMigrationTest {
    private fun create(file: File, version: Int) {
        val schema = JSONObject(File("schemas/${ChatDatabase::class.java.name}/$version.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version
        }
    }

    private fun dialog(key: String, muteUntil: Int) = ContentValues().apply {
        put("dialog", key)
        put("folder", 0)
        put("position", 0)
        put("pinned", 0)
        put("topMessage", 7)
        put("unread", 2)
        put("mentions", 0)
        put("unreadMark", 0)
        put("muteUntil", muteUntil)
        put("readOutbox", 0)
        put("message", byteArrayOf(1))
        put("peers", key)
        put("written", 1L)
        put("layer", 1)
    }

    @Test
    fun version1KeepsItsRowsAndUnmutedRowsFollowTheDefault(): Unit = runBlocking {
        val file = File(Files.createTempDirectory("db").toFile(), "chats.db")
        create(file, 1)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.insertOrThrow("dialogs", null, dialog("u1", 0))
            db.insertOrThrow("dialogs", null, dialog("c2", Int.MAX_VALUE))
            db.insertOrThrow("dialogs", null, dialog("ch3", 1_790_000_000))
            db.insertOrThrow("messages", null, ContentValues().apply {
                put("dialog", "u1")
                put("topic", 0)
                put("id", 7)
                put("tl", byteArrayOf(7))
                put("peers", "u1")
                put("layer", 1)
            })
            db.insertOrThrow("meta", null, ContentValues().apply {
                put("name", "owner")
                put("value", "42")
            })
        }

        val database = ChatDatabase.open(ApplicationProvider.getApplicationContext(), file)
        try {
            val dao = database.chats()
            assertEquals(
                mapOf("u1" to DialogRow.FOLLOWS_DEFAULT, "c2" to Int.MAX_VALUE, "ch3" to 1_790_000_000),
                dao.allDialogs().associate { it.dialog to it.muteUntil },
            )
            assertEquals(listOf(7), dao.messages("u1", 0, Int.MAX_VALUE, 10, 1).map { it.id })
            assertEquals("42", dao.meta("owner"))
        } finally {
            database.close()
            file.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun version2KeepsItsRowsAndGetsProfilesAndCustomEmoji(): Unit = runBlocking {
        val file = File(Files.createTempDirectory("db").toFile(), "chats.db")
        create(file, 2)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.insertOrThrow("dialogs", null, dialog("u1", DialogRow.FOLLOWS_DEFAULT))
        }

        val database = ChatDatabase.open(ApplicationProvider.getApplicationContext(), file)
        try {
            val dao = database.chats()
            assertEquals(listOf("u1"), dao.allDialogs().map { it.dialog })
            dao.changeProfile("u1", 1, seen = 5, keep = 10) { it.copy(gifts = byteArrayOf(3)) }
            dao.changeProfile("u1", 1, seen = 6, keep = 10) { it.copy(full = byteArrayOf(1), subscribers = 9) }
            val profile = assertNotNull(dao.profile("u1", 1))
            assertEquals(listOf(1.toByte(), 3.toByte()), listOf(profile.full!!.single(), profile.gifts!!.single()))
            assertEquals(9 to 6L, profile.subscribers to profile.seen)
            dao.putCustomEmoji(listOf(CustomEmojiRow(77, byteArrayOf(7), 1)))
            assertEquals(listOf(77L), dao.customEmoji(listOf(77, 78), 1).map { it.id })
            dao.clear()
            assertEquals(null, dao.profile("u1", 1))
            assertEquals(emptyList(), dao.customEmoji(listOf(77), 1))
        } finally {
            database.close()
            file.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun version3KeepsItsRowsAndGetsContacts(): Unit = runBlocking {
        val file = File(Files.createTempDirectory("db").toFile(), "chats.db")
        create(file, 3)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.insertOrThrow("dialogs", null, dialog("u1", DialogRow.FOLLOWS_DEFAULT))
        }

        val database = ChatDatabase.open(ApplicationProvider.getApplicationContext(), file)
        try {
            val dao = database.chats()
            assertEquals(listOf("u1"), dao.allDialogs().map { it.dialog })
            val user = PeerRow("u2", "Bob", min = false, tl = byteArrayOf(2), seen = 1, layer = 1)
            dao.putContacts(listOf(ContactRow("u2", mutual = true, layer = 1)), listOf(user), "contacts_saved", 3)
            assertEquals(listOf("u2" to true), dao.contacts(1).map { it.peer to it.mutual })
            assertEquals("3", dao.meta("contacts_saved"))
            dao.prunePeers(0)
            assertEquals(listOf("u2"), dao.peers(listOf("u2"), 1).map { it.peer })
            dao.clear()
            assertEquals(emptyList(), dao.contacts(1))
        } finally {
            database.close()
            file.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun version4KeepsItsRowsAndGetsStories(): Unit = runBlocking {
        val file = File(Files.createTempDirectory("db").toFile(), "chats.db")
        create(file, 4)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.insertOrThrow("dialogs", null, dialog("u1", DialogRow.FOLLOWS_DEFAULT))
            db.insertOrThrow("contacts", null, ContentValues().apply {
                put("peer", "u3")
                put("mutual", 0)
                put("layer", 1)
            })
        }

        val database = ChatDatabase.open(ApplicationProvider.getApplicationContext(), file)
        try {
            val dao = database.chats()
            assertEquals(listOf("u1"), dao.allDialogs().map { it.dialog })
            assertEquals(listOf("u3"), dao.contacts(1).map { it.peer })
            val user = PeerRow("u2", "Alice", min = false, tl = byteArrayOf(2), seen = 1, layer = 1)
            dao.putStories(hidden = true, listOf(StoryRow("u2", hidden = true, maxRead = 4, tl = byteArrayOf(5), layer = 1)), listOf(user), replace = true)
            assertEquals(listOf(Triple("u2", true, 4)), dao.stories(1).map { Triple(it.peer, it.hidden, it.maxRead) })
            dao.readStories("u2", 6)
            dao.hideStories("u2", false)
            assertEquals(Triple(false, 6, 1), assertNotNull(dao.storiesOf("u2", 1)).let { Triple(it.hidden, it.maxRead, it.layer) })
            dao.prunePeers(0)
            assertEquals(listOf("u2"), dao.peers(listOf("u2"), 1).map { it.peer })
            dao.dropOtherLayers(2)
            assertEquals(emptyList(), dao.stories(1))
            dao.putStories(hidden = false, listOf(StoryRow("u2", hidden = false, maxRead = 0, tl = byteArrayOf(5), layer = 1)), emptyList(), replace = false)
            dao.clear()
            assertEquals(emptyList(), dao.stories(1))
        } finally {
            database.close()
            file.parentFile?.deleteRecursively()
        }
    }
}
