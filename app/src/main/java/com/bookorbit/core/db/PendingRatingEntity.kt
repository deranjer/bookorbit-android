package com.bookorbit.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A rating change made while offline (or that failed to reach the server), queued for retry. */
@Entity(tableName = "pending_ratings")
data class PendingRatingEntity(
    @PrimaryKey val bookId: Int,
    val rating: Int?,
    val updatedAt: Long,
)
