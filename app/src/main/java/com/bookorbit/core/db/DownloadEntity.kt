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
    /** Human-readable reason for the most recent [status] == FAILED, surfaced in the UI. */
    val lastError: String? = null,
    /** Exact selected server file IDs; null only for legacy download plans. */
    val selectedFileIdsJson: String? = null,
    /** Generation token: obsolete workers cannot publish into a replaced or deleted plan. */
    @androidx.room.ColumnInfo(defaultValue = "''") val planId: String = "",
    /** Stable across additive updates/retries so interrupted bytes can be resumed in place. */
    @androidx.room.ColumnInfo(defaultValue = "''") val storageId: String = "",
)
