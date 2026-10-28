package cn.ellerotta.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
abstract class ChatDao {
    @Query("SELECT * FROM dialogs WHERE layer = :layer ORDER BY folder, position, dialog")
    abstract suspend fun dialogs(layer: Int): List<DialogRow>

    @Query("SELECT * FROM dialogs WHERE dialog IN (:dialogs)")
    abstract suspend fun dialogsOf(dialogs: List<String>): List<DialogRow>

    @Query("SELECT * FROM dialogs")
    abstract suspend fun allDialogs(): List<DialogRow>

    @Upsert
    abstract suspend fun upsertDialogs(rows: List<DialogRow>)

    @Query("DELETE FROM dialogs WHERE dialog IN (:gone) AND written < :before")
    abstract suspend fun dropDialogs(gone: List<String>, before: Long)

    @Transaction
    open suspend fun putDialogs(rows: List<DialogRow>) {
        val stored = dialogsOf(rows.map { it.dialog }).associateBy { it.dialog }
        upsertDialogs(rows.map { row -> stored[row.dialog]?.let { row.copy(position = it.position) } ?: row })
    }

    @Transaction
    open suspend fun keepDialogs(gone: List<String>, before: Long, change: (List<DialogRow>) -> List<DialogRow>) {
        val rows = change(allDialogs())
        upsertDialogs(rows)
        dropDialogs(gone - rows.mapTo(HashSet()) { it.dialog }, before)
    }

    @Query("UPDATE dialogs SET message = :message, topMessage = :id, peers = :peers WHERE dialog = :dialog AND topMessage <= :id")
    abstract suspend fun newTop(dialog: String, id: Int, message: ByteArray, peers: String): Int

    @Query("UPDATE dialogs SET message = :message, peers = :peers WHERE dialog = :dialog AND topMessage = :id")
    abstract suspend fun editedTop(dialog: String, id: Int, message: ByteArray, peers: String): Int

    @Query("UPDATE dialogs SET readOutbox = MAX(readOutbox, :maxId) WHERE dialog = :dialog")
    abstract suspend fun readOutbox(dialog: String, maxId: Int): Int

    @Query("UPDATE dialogs SET draft = :draft WHERE dialog = :dialog")
    abstract suspend fun draft(dialog: String, draft: ByteArray?): Int

    @Query("SELECT * FROM messages WHERE dialog = :dialog AND topic = :topic AND id < :before AND layer = :layer ORDER BY id DESC LIMIT :limit")
    abstract suspend fun messages(dialog: String, topic: Int, before: Int, limit: Int, layer: Int): List<MessageRow>

    @Query("SELECT MIN(id) AS low, MAX(id) AS high, COUNT(*) AS count FROM messages WHERE dialog = :dialog AND topic = :topic")
    abstract suspend fun span(dialog: String, topic: Int): Span

    @Query("SELECT * FROM messages WHERE dialog = :dialog AND id = :id")
    abstract suspend fun message(dialog: String, id: Int): List<MessageRow>

    @Upsert
    abstract suspend fun upsertMessages(rows: List<MessageRow>)

    @Query("DELETE FROM messages WHERE dialog = :dialog AND topic = :topic AND id BETWEEN :low AND :high AND id NOT IN (:keep)")
    abstract suspend fun dropBetween(dialog: String, topic: Int, low: Int, high: Int, keep: List<Int>)

    @Query("DELETE FROM messages WHERE dialog = :dialog AND topic = :topic")
    abstract suspend fun dropMessages(dialog: String, topic: Int)

    @Query(
        "DELETE FROM messages WHERE dialog = :dialog AND topic = :topic AND id < " +
            "(SELECT id FROM messages WHERE dialog = :dialog AND topic = :topic ORDER BY id DESC LIMIT 1 OFFSET :keep - 1)",
    )
    abstract suspend fun prune(dialog: String, topic: Int, keep: Int): Int

    @Query("UPDATE messages SET tl = :tl, peers = :peers, reactions = NULL WHERE dialog = :dialog AND id = :id")
    abstract suspend fun edit(dialog: String, id: Int, tl: ByteArray, peers: String): Int

    @Query("UPDATE messages SET reactions = :reactions WHERE dialog = :dialog AND id = :id")
    abstract suspend fun react(dialog: String, id: Int, reactions: ByteArray): Int

    @Query("DELETE FROM messages WHERE dialog = :dialog AND id IN (:ids)")
    abstract suspend fun delete(dialog: String, ids: List<Int>): Int

    @Query("DELETE FROM messages WHERE dialog NOT LIKE 'ch%' AND id IN (:ids)")
    abstract suspend fun deleteCommon(ids: List<Int>): Int

    @Query("SELECT * FROM histories WHERE dialog = :dialog AND topic = :topic")
    abstract suspend fun history(dialog: String, topic: Int): HistoryRow?

    @Upsert
    abstract suspend fun upsertHistory(row: HistoryRow)

    @Query("UPDATE histories SET stale = 1")
    abstract suspend fun staleAll()

    @Query("UPDATE histories SET stale = 1 WHERE dialog = :dialog")
    abstract suspend fun stale(dialog: String)

    @Query("UPDATE histories SET complete = 0 WHERE dialog = :dialog AND topic = :topic")
    abstract suspend fun incomplete(dialog: String, topic: Int)

    @Query("SELECT dialog, topic FROM histories ORDER BY used DESC LIMIT -1 OFFSET :keep")
    abstract suspend fun unused(keep: Int): List<HistoryKey>

    @Query("DELETE FROM histories WHERE dialog = :dialog AND topic = :topic")
    abstract suspend fun dropHistoryRow(dialog: String, topic: Int)

    @Transaction
    open suspend fun putNewest(dialog: String, topic: Int, rows: List<MessageRow>, limit: Int, keep: Int, now: Long) {
        val history = history(dialog, topic)
        val low = rows.minOfOrNull { it.id }
        val whole = rows.size < limit
        val checked = history?.takeIf { !it.stale }
        val joins = low != null && checked != null && (span(dialog, topic).high ?: Int.MIN_VALUE) >= low
        if (low == null || whole || !joins) dropMessages(dialog, topic) else dropBetween(dialog, topic, low, Int.MAX_VALUE, rows.map { it.id })
        upsertMessages(rows)
        val pruned = prune(dialog, topic, keep) > 0
        upsertHistory(HistoryRow(dialog, topic, complete = !pruned && (whole || joins && checked?.complete == true), stale = false, used = now))
    }

    @Transaction
    open suspend fun putOlder(dialog: String, topic: Int, before: Int, rows: List<MessageRow>, limit: Int, keep: Int, now: Long): Boolean {
        val history = history(dialog, topic) ?: return false
        val span = span(dialog, topic)
        val low = span.low ?: return false
        val high = span.high ?: return false
        if (history.stale || before < low || before > high || span.count >= keep) return false
        rows.minOfOrNull { it.id }?.let { dropBetween(dialog, topic, it, before - 1, rows.map { row -> row.id }) }
        upsertMessages(rows)
        val pruned = prune(dialog, topic, keep) > 0
        upsertHistory(history.copy(complete = !pruned && (history.complete || rows.size < limit), used = now))
        return true
    }

    @Transaction
    open suspend fun append(rows: List<MessageRow>, keep: Int) {
        val known = rows.map { HistoryKey(it.dialog, it.topic) }.distinct().filter { history(it.dialog, it.topic) != null }.toSet()
        upsertMessages(rows.filter { HistoryKey(it.dialog, it.topic) in known })
        known.forEach { if (prune(it.dialog, it.topic, keep) > 0) incomplete(it.dialog, it.topic) }
    }

    @Transaction
    open suspend fun older(dialog: String, topic: Int, before: Int, limit: Int, layer: Int, now: Long): StoredPage? {
        val history = history(dialog, topic) ?: return null
        val high = span(dialog, topic).high
        if (before != Int.MAX_VALUE && (history.stale || high == null || before > high)) return null
        val rows = messages(dialog, topic, before, limit, layer)
        if (rows.isEmpty() && !history.complete) return null
        upsertHistory(history.copy(used = now))
        return StoredPage(rows, history.complete && rows.size < limit)
    }

    @Query("DELETE FROM messages WHERE dialog = :dialog")
    abstract suspend fun dropDialogMessages(dialog: String)

    @Query("DELETE FROM histories WHERE dialog = :dialog")
    abstract suspend fun dropDialogHistories(dialog: String)

    @Transaction
    open suspend fun dropDialogHistory(dialog: String) {
        dropDialogMessages(dialog)
        dropDialogHistories(dialog)
    }

    @Transaction
    open suspend fun dropHistory(dialog: String, topic: Int) {
        dropMessages(dialog, topic)
        dropHistoryRow(dialog, topic)
    }

    @Transaction
    open suspend fun pruneHistories(keep: Int) {
        unused(keep).forEach { dropHistory(it.dialog, it.topic) }
    }

    @Upsert
    abstract suspend fun upsertPeers(rows: List<PeerRow>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPeers(rows: List<PeerRow>)

    @Transaction
    open suspend fun putPeers(rows: List<PeerRow>) {
        upsertPeers(rows.filter { !it.min })
        insertPeers(rows.filter { it.min })
    }

    @Query("SELECT * FROM peers WHERE peer IN (:peers) AND layer = :layer")
    abstract suspend fun peers(peers: List<String>, layer: Int): List<PeerRow>

    @Query("DELETE FROM peers WHERE peer NOT IN (SELECT peer FROM peers ORDER BY seen DESC LIMIT :keep) AND peer NOT IN (SELECT peer FROM contacts) AND peer NOT IN (SELECT peer FROM stories)")
    abstract suspend fun prunePeers(keep: Int)

    @Query("SELECT * FROM folders WHERE layer = :layer ORDER BY position")
    abstract suspend fun folders(layer: Int): List<FolderRow>

    @Query("DELETE FROM folders")
    abstract suspend fun dropFolders()

    @Insert
    abstract suspend fun insertFolders(rows: List<FolderRow>)

    @Transaction
    open suspend fun putFolders(rows: List<FolderRow>) {
        dropFolders()
        insertFolders(rows)
    }

    @Query("SELECT value FROM meta WHERE name = :name")
    abstract suspend fun meta(name: String): String?

    @Upsert
    abstract suspend fun putMeta(row: MetaRow)

    @Query("SELECT DISTINCT dialog, topic FROM messages WHERE layer != :layer")
    abstract suspend fun otherLayerHistories(layer: Int): List<HistoryKey>

    @Query("DELETE FROM dialogs WHERE layer != :layer")
    abstract suspend fun dropOtherLayerDialogs(layer: Int)

    @Query("DELETE FROM peers WHERE layer != :layer")
    abstract suspend fun dropOtherLayerPeers(layer: Int)

    @Query("DELETE FROM folders WHERE layer != :layer")
    abstract suspend fun dropOtherLayerFolders(layer: Int)

    @Query("DELETE FROM profiles WHERE layer != :layer")
    abstract suspend fun dropOtherLayerProfiles(layer: Int)

    @Query("DELETE FROM custom_emoji WHERE layer != :layer")
    abstract suspend fun dropOtherLayerCustomEmoji(layer: Int)

    @Query("DELETE FROM contacts WHERE layer != :layer")
    abstract suspend fun dropOtherLayerContacts(layer: Int)

    @Query("DELETE FROM stories WHERE layer != :layer")
    abstract suspend fun dropOtherLayerStories(layer: Int)

    @Transaction
    open suspend fun dropOtherLayers(layer: Int) {
        otherLayerHistories(layer).forEach { dropHistory(it.dialog, it.topic) }
        dropOtherLayerDialogs(layer)
        dropOtherLayerPeers(layer)
        dropOtherLayerFolders(layer)
        dropOtherLayerProfiles(layer)
        dropOtherLayerCustomEmoji(layer)
        dropOtherLayerContacts(layer)
        dropOtherLayerStories(layer)
    }

    @Query("SELECT * FROM profiles WHERE peer = :peer AND layer = :layer")
    abstract suspend fun profile(peer: String, layer: Int): ProfileRow?

    @Upsert
    abstract suspend fun upsertProfile(row: ProfileRow)

    @Query("DELETE FROM profiles WHERE peer NOT IN (SELECT peer FROM profiles ORDER BY seen DESC LIMIT :keep)")
    abstract suspend fun pruneProfiles(keep: Int)

    @Transaction
    open suspend fun changeProfile(peer: String, layer: Int, seen: Long, keep: Int, change: (ProfileRow) -> ProfileRow) {
        val stored = profile(peer, layer) ?: ProfileRow(peer, null, null, null, null, null, seen, layer)
        upsertProfile(change(stored).copy(seen = seen))
        pruneProfiles(keep)
    }

    @Query("SELECT * FROM custom_emoji WHERE id IN (:ids) AND layer = :layer")
    abstract suspend fun customEmoji(ids: List<Long>, layer: Int): List<CustomEmojiRow>

    @Upsert
    abstract suspend fun putCustomEmoji(rows: List<CustomEmojiRow>)

    @Query("SELECT * FROM contacts WHERE layer = :layer")
    abstract suspend fun contacts(layer: Int): List<ContactRow>

    @Query("DELETE FROM contacts")
    abstract suspend fun dropAllContacts()

    @Insert
    abstract suspend fun insertContacts(rows: List<ContactRow>)

    @Transaction
    open suspend fun putContacts(rows: List<ContactRow>, peers: List<PeerRow>, savedCountKey: String, savedCount: Int) {
        dropAllContacts()
        insertContacts(rows)
        putPeers(peers)
        putMeta(MetaRow(savedCountKey, savedCount.toString()))
    }

    @Query("SELECT * FROM stories WHERE layer = :layer")
    abstract suspend fun stories(layer: Int): List<StoryRow>

    @Query("SELECT * FROM stories WHERE peer = :peer AND layer = :layer")
    abstract suspend fun storiesOf(peer: String, layer: Int): StoryRow?

    @Upsert
    abstract suspend fun upsertStories(rows: List<StoryRow>)

    @Query("DELETE FROM stories WHERE peer = :peer")
    abstract suspend fun dropStories(peer: String)

    @Query("DELETE FROM stories WHERE hidden = :hidden")
    abstract suspend fun dropStoryList(hidden: Boolean)

    @Query("UPDATE stories SET maxRead = MAX(maxRead, :maxId) WHERE peer = :peer")
    abstract suspend fun readStories(peer: String, maxId: Int): Int

    @Query("UPDATE stories SET hidden = :hidden WHERE peer = :peer")
    abstract suspend fun hideStories(peer: String, hidden: Boolean): Int

    @Transaction
    open suspend fun putStories(hidden: Boolean, rows: List<StoryRow>, peers: List<PeerRow>, replace: Boolean) {
        if (replace) dropStoryList(hidden)
        upsertStories(rows)
        putPeers(peers)
    }

    @Transaction
    open suspend fun changeStories(peer: String, layer: Int, change: (StoryRow?) -> StoryRow?) {
        val row = change(storiesOf(peer, layer))
        if (row == null) dropStories(peer) else upsertStories(listOf(row))
    }

    @Query("DELETE FROM stories")
    abstract suspend fun dropAllStories()

    @Query("DELETE FROM dialogs")
    abstract suspend fun dropAllDialogs()

    @Query("DELETE FROM messages")
    abstract suspend fun dropAllMessages()

    @Query("DELETE FROM histories")
    abstract suspend fun dropAllHistories()

    @Query("DELETE FROM peers")
    abstract suspend fun dropAllPeers()

    @Query("DELETE FROM meta")
    abstract suspend fun dropMeta()

    @Query("DELETE FROM profiles")
    abstract suspend fun dropAllProfiles()

    @Query("DELETE FROM custom_emoji")
    abstract suspend fun dropAllCustomEmoji()

    @Transaction
    open suspend fun clear() {
        dropAllDialogs()
        dropAllMessages()
        dropAllHistories()
        dropAllPeers()
        dropFolders()
        dropMeta()
        dropAllProfiles()
        dropAllCustomEmoji()
        dropAllContacts()
        dropAllStories()
    }
}
