package com.bookorbit.feature.downloads

import kotlinx.serialization.Serializable

enum class DownloadStatus { DOWNLOADING, COMPLETE, FAILED }

/** A downloaded book file on disk (serialized into the download record). */
@Serializable
data class DownloadedFile(
    val id: Int,
    val localPath: String,
    val filename: String? = null,
    val format: String? = null,
    val durationSeconds: Double? = null,
)
