package com.bookorbit.core.model

import kotlinx.serialization.Serializable

/**
 * `GET /audiobooks/{bookId}/manifest` (server 3.0+). Only the asset list is read: since 3.0 audio
 * bytes are served per asset (`/audiobooks/{bookId}/assets/{assetId}/content`) and
 * `/books/files/{fileId}/serve` 404s for audio formats.
 */
@Serializable
data class AudiobookManifest(
    val assets: List<AudiobookManifestAsset> = emptyList(),
)

@Serializable
data class AudiobookManifestAsset(
    val assetId: String,
    val sequence: Int,
    val format: String,
    val durationMs: Long? = null,
    val sizeBytes: Long? = null,
)

object AudiobookAssets {
    /**
     * Maps each audio [BookFileRef] id to its manifest assetId. The manifest keys assets by the
     * file's publicId, which book detail doesn't expose, so files are matched on format + size
     * first (unique pairs only), then any leftovers are paired in order - the server sorts assets
     * by sortOrder/filename, which may differ from book-detail order, hence size wins when it can.
     * Files with no match are simply absent from the result.
     */
    fun match(files: List<BookFileRef>, assets: List<AudiobookManifestAsset>): Map<Int, String> {
        val result = linkedMapOf<Int, String>()
        val remainingFiles = files.toMutableList()
        val remainingAssets = assets.sortedBy { it.sequence }.toMutableList()

        fun sameFormat(file: BookFileRef, asset: AudiobookManifestAsset) =
            file.format?.lowercase() == asset.format.lowercase()

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
        remainingFiles.zip(remainingAssets).forEach { (file, asset) -> result[file.id] = asset.assetId }
        return result
    }
}
