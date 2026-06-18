package com.bookorbit.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local audiobook listening position per book id (local-first write + dirty flag + flush). */
@Entity(tableName = "audio_progress")
data class AudioProgressEntity(
    @PrimaryKey val bookId: Int,
    val currentFileId: Int,
    val positionSeconds: Double,
    val percentage: Double,
    val updatedAt: Long,
    val dirty: Boolean,
)
