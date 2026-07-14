package com.bookorbit.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A read-status change made while offline (or that failed to reach the server), queued for retry. */
@Entity(tableName = "pending_read_status")
data class PendingReadStatusEntity(
    @PrimaryKey val bookId: Int,
    val status: String,
    val updatedAt: Long,
)
