package com.bookorbit.feature.downloads

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.model.BookFiles

enum class DownloadStatus { DOWNLOADING, COMPLETE, COMPLETE_FALLBACK, FAILED }

/** True for a usable, fully-downloaded record — [DownloadStatus.COMPLETE] and the internal-storage
 * fallback case ([DownloadStatus.COMPLETE_FALLBACK]) are equally usable, they only differ in where
 * the bytes ended up. */
fun String.isCompleteDownloadStatus(): Boolean =
    this == DownloadStatus.COMPLETE.name || this == DownloadStatus.COMPLETE_FALLBACK.name

/** Android Auto must not lose an offline audiobook while an additional format is downloading. */
fun DownloadEntity.hasPublishedAudio(): Boolean = isAudiobook &&
    (status.isCompleteDownloadStatus() || runCatching {
        offlineJournalJson.decodeFromString<List<DownloadedFile>>(filesJson).any { it.format?.lowercase() in BookFiles.AUDIO_FORMATS }
    }.getOrDefault(false))

private val offlineJournalJson = Json { ignoreUnknownKeys = true }

/** A downloaded book file on disk (serialized into the download record). */
@Serializable
data class DownloadedFile(
    val id: Int,
    val localPath: String,
    val filename: String? = null,
    val format: String? = null,
    val durationSeconds: Double? = null,
)
