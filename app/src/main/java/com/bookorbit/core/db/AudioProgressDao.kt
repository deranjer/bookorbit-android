package com.bookorbit.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AudioProgressDao {
    @Query("SELECT * FROM audio_progress WHERE bookId = :bookId")
    suspend fun get(bookId: Int): AudioProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AudioProgressEntity)

    @Query("SELECT * FROM audio_progress WHERE dirty = 1")
    suspend fun dirtyEntries(): List<AudioProgressEntity>

    /** Most recently played first — drives the Android Auto "Continue listening" shelf. */
    @Query("SELECT * FROM audio_progress ORDER BY updatedAt DESC")
    suspend fun recent(): List<AudioProgressEntity>

    @Query("UPDATE audio_progress SET dirty = 0 WHERE bookId = :bookId")
    suspend fun markSynced(bookId: Int)
}
