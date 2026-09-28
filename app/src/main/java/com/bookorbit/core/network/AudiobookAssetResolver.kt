package com.bookorbit.core.network

import com.bookorbit.core.model.AudiobookAssets
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.core.model.BookFiles
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps an audiobook's files onto the server's audiobook API. Server 3.0+ serves audio and keeps
 * listening position only through that API, keyed by manifest assetIds (`/books/files/{id}/serve`
 * 404s for audio); older servers have no manifest endpoint and use the per-file/per-book routes.
 * Results are cached per book for the process so frequent callers (progress reports) don't
 * re-fetch the manifest.
 */
@Singleton
class AudiobookAssetResolver @Inject constructor(private val api: ApiService) {

    sealed interface Sources {
        /** Pre-3.0 server: `/books/files/{fileId}/serve` and `/books/{id}/audio-progress`. */
        data object Legacy : Sources

        /** 3.0+ server: fileId -> assetId, plus what playback-state writes need. */
        data class Assets(
            val assetIds: Map<Int, String>,
            val manifestRevision: String,
            val assetDurationsMs: Map<String, Long?> = emptyMap(),
        ) : Sources {
            fun fileIdFor(assetId: String): Int? = assetIds.entries.firstOrNull { it.value == assetId }?.key
        }
    }

    private val cache = ConcurrentHashMap<Int, Sources>()

    /**
     * Resolves [files] against the book's manifest (always re-fetched) and caches the result. A 404
     * from the manifest means an older server ([Sources.Legacy]); any other failure is thrown so
     * callers can retry or surface it.
     */
    suspend fun resolve(bookId: Int, files: List<BookFileRef>): Sources {
        val manifest = try {
            api.getAudiobookManifest(bookId)
        } catch (e: HttpException) {
            if (e.code() == 404) return Sources.Legacy.also { cache[bookId] = it }
            throw e
        }
        return Sources.Assets(
            assetIds = AudiobookAssets.match(files, manifest.assets),
            manifestRevision = manifest.revision,
            assetDurationsMs = manifest.assets.associate { it.assetId to it.durationMs },
        ).also { cache[bookId] = it }
    }

    /** Cached sources for [bookId], resolving from the server's book detail on a miss or [refresh]. */
    suspend fun forBook(bookId: Int, refresh: Boolean = false): Sources {
        if (!refresh) cache[bookId]?.let { return it }
        return resolve(bookId, BookFiles.audioFiles(api.getBookDetail(bookId)))
    }

    /** Path (relative to the server's `/api/v1`) that serves [fileId]'s bytes. */
    fun path(bookId: Int, fileId: Int, sources: Sources): String {
        val assetId = (sources as? Sources.Assets)?.assetIds?.get(fileId)
        return if (assetId != null) "audiobooks/$bookId/assets/$assetId/content"
        else "books/files/$fileId/serve"
    }
}
