package com.bookorbit.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("UPDATE downloads SET progress = :progress WHERE bookId = :bookId")
    suspend fun updateProgress(bookId: Int, progress: Float)

    @Query("UPDATE downloads SET status = :status WHERE bookId = :bookId")
    suspend fun updateStatus(bookId: Int, status: String)

    @Query("DELETE FROM downloads WHERE bookId = :bookId")
    suspend fun delete(bookId: Int)
}
