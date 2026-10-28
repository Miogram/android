package cn.ellerotta.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "dialogs")
data class DialogRow(
    @PrimaryKey val dialog: String,
    val folder: Int,
    val position: Int,
    val pinned: Boolean,
    val topMessage: Int,
    val unread: Int,
    val mentions: Int,
    val unreadMark: Boolean,
    val muteUntil: Int,
    val readOutbox: Int,
    val message: ByteArray?,
    val draft: ByteArray?,
    val peers: String,
    val written: Long,
    val layer: Int,
) {
    companion object {
        const val FOLLOWS_DEFAULT = -1
    }
}

@Entity(tableName = "messages", primaryKeys = ["dialog", "topic", "id"], indices = [Index("id")])
data class MessageRow(
    val dialog: String,
    val topic: Int,
    val id: Int,
    val tl: ByteArray,
    val peers: String,
    val layer: Int,
    val reactions: ByteArray? = null,
)

@Entity(tableName = "histories", primaryKeys = ["dialog", "topic"])
data class HistoryRow(val dialog: String, val topic: Int, val complete: Boolean, val stale: Boolean, val used: Long)

@Entity(tableName = "peers", indices = [Index("seen")])
data class PeerRow(@PrimaryKey val peer: String, val name: String, val min: Boolean, val tl: ByteArray, val seen: Long, val layer: Int)

@Entity(tableName = "folders")
data class FolderRow(@PrimaryKey val position: Int, val tl: ByteArray, val layer: Int)

@Entity(tableName = "meta")
data class MetaRow(@PrimaryKey val name: String, val value: String)

@Entity(tableName = "profiles", indices = [Index("seen")])
data class ProfileRow(
    @PrimaryKey val peer: String,
    val full: ByteArray?,
    val post: ByteArray?,
    val subscribers: Int?,
    val gifts: ByteArray?,
    val photos: ByteArray?,
    val seen: Long,
    val layer: Int,
)

@Entity(tableName = "custom_emoji")
data class CustomEmojiRow(@PrimaryKey val id: Long, val tl: ByteArray, val layer: Int)

@Entity(tableName = "contacts")
data class ContactRow(@PrimaryKey val peer: String, val mutual: Boolean, val layer: Int)

@Entity(tableName = "stories")
data class StoryRow(@PrimaryKey val peer: String, val hidden: Boolean, val maxRead: Int, val tl: ByteArray, val layer: Int)

data class Span(val low: Int?, val high: Int?, val count: Int)

data class HistoryKey(val dialog: String, val topic: Int)

data class StoredPage(val rows: List<MessageRow>, val complete: Boolean)
