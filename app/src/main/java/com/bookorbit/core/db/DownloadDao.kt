package com.bookorbit.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE bookId = :bookId")
    fun observe(bookId: Int): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE bookId = :bookId")
    suspend fun get(bookId: Int): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    /** Reserve a plan atomically; concurrent taps must not replace an active selection. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlan(entity: DownloadEntity): Long

    /** Compare the whole snapshot inside a Room transaction, retaining completed file references. */
    @Transaction
    suspend fun replacePlan(expected: DownloadEntity, updated: DownloadEntity): Boolean {
        require(expected.bookId == updated.bookId)
        if (get(expected.bookId) != expected) return false
        upsert(updated)
        return true
    }

    @Query("UPDATE downloads SET filesJson = :filesJson, sizeBytes = :sizeBytes, progress = :progress WHERE bookId = :bookId AND planId = :planId")
    suspend fun checkpoint(bookId: Int, planId: String, filesJson: String, sizeBytes: Long, progress: Float): Int

    @Query("UPDATE downloads SET progress = :progress WHERE bookId = :bookId AND planId = :planId")
    suspend fun progressForPlan(bookId: Int, planId: String, progress: Float): Int

    @Query("UPDATE downloads SET status = :status, lastError = :error WHERE bookId = :bookId AND planId = :planId")
    suspend fun failPlan(bookId: Int, planId: String, status: String, error: String): Int

    @Query("UPDATE downloads SET status = :status, progress = 1.0, lastError = NULL, coverLocalPath = :cover WHERE bookId = :bookId AND planId = :planId")
    suspend fun finishPlan(bookId: Int, planId: String, status: String, cover: String?): Int

    @Query("UPDATE downloads SET progress = :progress WHERE bookId = :bookId")
    suspend fun updateProgress(bookId: Int, progress: Float)

    @Query("UPDATE downloads SET status = :status WHERE bookId = :bookId")
    suspend fun updateStatus(bookId: Int, status: String)

    /** Terminal failure: records why, so the UI can show it instead of silently reverting to "Download". */
    @Query("UPDATE downloads SET status = :status, lastError = :lastError WHERE bookId = :bookId")
    suspend fun updateFailure(bookId: Int, status: String, lastError: String)

    @Query("DELETE FROM downloads WHERE bookId = :bookId")
    suspend fun delete(bookId: Int)
}
