package com.bookorbit.core.model

import kotlinx.serialization.Serializable

/**
 * `GET /audiobooks/{bookId}/manifest` (server 3.0+). Only the asset list and revision are read:
 * since 3.0 audio bytes are served per asset (`/audiobooks/{bookId}/assets/{assetId}/content`) and
 * `/books/files/{fileId}/serve` 404s for audio formats. [revision] must accompany playback-state
 * writes.
 */
@Serializable
data class AudiobookManifest(
    val revision: String = "",
    val assets: List<AudiobookManifestAsset> = emptyList(),
)

@Serializable
data class AudiobookManifestAsset(
    val assetId: String,
    val sequence: Int,
    val format: String,
    val durationMs: Long? = null,
    val sizeBytes: Long? = null,
    val fileId: Int? = null,
)

/** `GET/PUT /audiobooks/{bookId}/playback-state` response. [capturedAt] is the writer's clock. */
@Serializable
data class AudiobookPlaybackState(
    val assetId: String,
    val positionMs: Long,
    val percentage: Double = 0.0,
    val capturedAt: String? = null,
    val revision: Int = 0,
)

/**
 * `PUT /audiobooks/{bookId}/playback-state`: optimistic concurrency on [baseRevision] (0 creates;
 * a stale value gets 409) and [manifestRevision] (a changed manifest gets 412).
 */
@Serializable
data class PutAudiobookPlaybackState(
    val assetId: String,
    val positionMs: Long,
    val capturedAt: String,
    val operationId: String,
    val baseRevision: Int,
    val manifestRevision: String,
)

object AudiobookAssets {
    /**
     * Exact server file IDs win. Older manifests without IDs may use unique format/size pairs,
     * but ambiguous tracks must never be guessed from their order.
     */
    fun match(files: List<BookFileRef>, assets: List<AudiobookManifestAsset>): Map<Int, String> {
        val result = linkedMapOf<Int, String>()
        val remainingFiles = files.toMutableList()
        val remainingAssets = assets.sortedBy { it.sequence }.toMutableList()

        fun sameFormat(file: BookFileRef, asset: AudiobookManifestAsset) =
            file.format?.lowercase() == asset.format.lowercase()

        for (asset in remainingAssets.toList()) {
            val id = asset.fileId ?: continue
            val file = remainingFiles.singleOrNull { it.id == id && sameFormat(it, asset) }
            if (file != null && assets.count { it.fileId == id } == 1) {
                result[file.id] = asset.assetId
                remainingFiles.remove(file)
            }
            // A supplied ID is authoritative, even when it is outside the requested set.
            remainingAssets.remove(asset)
        }

        for (asset in remainingAssets.toList()) {
            val size = asset.sizeBytes?.takeIf { it > 0 } ?: continue
            val candidates = remainingFiles.filter { it.sizeBytes == size && sameFormat(it, asset) }
            val assetsWithSameKey = remainingAssets.count { it.sizeBytes == size && it.format.equals(asset.format, true) }
            if (candidates.size == 1 && assetsWithSameKey == 1) {
                result[candidates[0].id] = asset.assetId
                remainingFiles.remove(candidates[0])
                remainingAssets.remove(asset)
            }
        }
        for (asset in remainingAssets) {
            val candidates = remainingFiles.filter { sameFormat(it, asset) }
            if (candidates.size == 1 && remainingAssets.count { it.format.equals(asset.format, true) } == 1) {
                result[candidates.single().id] = asset.assetId
            }
        }
        return result
    }
}
