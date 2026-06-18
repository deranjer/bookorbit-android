package com.bookorbit.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A downloaded (or downloading) book. The full BookDetail is persisted as [bookJson] so the player
 * and downloads list render entirely offline. [filesJson] holds the on-disk file list
 * (List<DownloadedFile>).
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val bookId: Int,
    val title: String?,
    val authors: String,
    val narrators: String,
    val isAudiobook: Boolean,
    val format: String?,
    val sizeBytes: Long,
    val downloadedAt: Long,
    val coverLocalPath: String?,
    val bookJson: String,
    val filesJson: String,
    val status: String,
    val progress: Float,
)
