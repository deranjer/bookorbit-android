package com.bookorbit.core.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A reading/listening session (or a checkpoint of one still open) waiting to reach the server.
 * Keyed by the session id, so a later checkpoint of the same session replaces the earlier row.
 */
@Entity(tableName = "pending_reading_sessions")
data class PendingReadingSessionEntity(
    @PrimaryKey val sessionId: String,
    val fileId: Int,
    val sessionType: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Int,
    val progressDelta: Double?,
    val endProgress: Double?,
)

@Dao
interface PendingReadingSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingReadingSessionEntity)

    @Query("SELECT * FROM pending_reading_sessions")
    suspend fun all(): List<PendingReadingSessionEntity>

    /** Only delete the row that was sent: a newer checkpoint of the same session must survive. */
    @Query("DELETE FROM pending_reading_sessions WHERE sessionId = :sessionId AND endedAt = :endedAt")
    suspend fun deleteIfUnchanged(sessionId: String, endedAt: Long)
}
