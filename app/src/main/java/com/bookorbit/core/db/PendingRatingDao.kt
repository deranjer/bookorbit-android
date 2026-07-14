package com.bookorbit.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingRatingDao {
    @Query("SELECT * FROM pending_ratings WHERE bookId = :bookId")
    suspend fun get(bookId: Int): PendingRatingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingRatingEntity)

    @Query("SELECT * FROM pending_ratings")
    suspend fun all(): List<PendingRatingEntity>

    @Query("DELETE FROM pending_ratings WHERE bookId = :bookId")
    suspend fun delete(bookId: Int)
}
