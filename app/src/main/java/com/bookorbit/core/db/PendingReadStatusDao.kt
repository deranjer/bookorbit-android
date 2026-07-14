package com.bookorbit.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingReadStatusDao {
    @Query("SELECT * FROM pending_read_status WHERE bookId = :bookId")
    suspend fun get(bookId: Int): PendingReadStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingReadStatusEntity)

    @Query("SELECT * FROM pending_read_status")
    suspend fun all(): List<PendingReadStatusEntity>

    @Query("DELETE FROM pending_read_status WHERE bookId = :bookId")
    suspend fun delete(bookId: Int)
}
