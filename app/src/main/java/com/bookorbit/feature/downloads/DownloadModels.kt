package com.bookorbit.feature.downloads

import kotlinx.serialization.Serializable

enum class DownloadStatus { DOWNLOADING, COMPLETE, COMPLETE_FALLBACK, FAILED }

/** True for a usable, fully-downloaded record — [DownloadStatus.COMPLETE] and the internal-storage
 * fallback case ([DownloadStatus.COMPLETE_FALLBACK]) are equally usable, they only differ in where
 * the bytes ended up. */
fun String.isCompleteDownloadStatus(): Boolean =
    this == DownloadStatus.COMPLETE.name || this == DownloadStatus.COMPLETE_FALLBACK.name

/** A downloaded book file on disk (serialized into the download record). */
@Serializable
data class DownloadedFile(
    val id: Int,
    val localPath: String,
    val filename: String? = null,
    val format: String? = null,
    val durationSeconds: Double? = null,
)
