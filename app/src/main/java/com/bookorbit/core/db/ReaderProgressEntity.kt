package com.bookorbit.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local reading position per server file id: written locally first and kept [dirty] until the
 * server accepts it.
 */
@Entity(tableName = "reader_progress")
data class ReaderProgressEntity(
    @PrimaryKey val fileId: Int,
    val cfi: String?,
    val percentage: Double,
    val updatedAt: Long,
    val dirty: Boolean,
)
