package cn.ellerotta.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
abstract class OutgoingDao {
    @Query("SELECT * FROM outgoing WHERE account = :account ORDER BY id")
    abstract suspend fun rows(account: Long): List<OutgoingRow>

    @Query("SELECT file FROM outgoing WHERE file IS NOT NULL UNION SELECT thumb FROM outgoing WHERE thumb IS NOT NULL")
    abstract suspend fun files(): List<String>

    @Upsert
    abstract suspend fun put(rows: List<OutgoingRow>)

    @Query("UPDATE outgoing SET uploaded = :parts WHERE id = :id")
    abstract suspend fun uploaded(id: Long, parts: Int)

    @Query("UPDATE outgoing SET media = :media WHERE id = :id")
    abstract suspend fun media(id: Long, media: ByteArray?)

    @Query("UPDATE outgoing SET failed = :failed WHERE id IN (:ids)")
    abstract suspend fun failed(ids: List<Long>, failed: String?)

    @Query("DELETE FROM outgoing WHERE id IN (:ids)")
    abstract suspend fun delete(ids: List<Long>)
}
