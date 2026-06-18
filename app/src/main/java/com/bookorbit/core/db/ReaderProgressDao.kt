package com.bookorbit.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReaderProgressDao {
    @Query("SELECT * FROM reader_progress WHERE fileId = :fileId")
    suspend fun get(fileId: Int): ReaderProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReaderProgressEntity)

    @Query("SELECT * FROM reader_progress WHERE dirty = 1")
    suspend fun dirtyEntries(): List<ReaderProgressEntity>

    @Query("UPDATE reader_progress SET dirty = 0 WHERE fileId = :fileId")
    suspend fun markSynced(fileId: Int)
}
