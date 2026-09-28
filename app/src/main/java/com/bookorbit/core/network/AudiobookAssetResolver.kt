package com.bookorbit.core.network

import com.bookorbit.core.model.AudiobookAssets
import com.bookorbit.core.model.BookFileRef
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where to fetch an audiobook's audio bytes. Server 3.0+ serves audio only through the audiobook
 * asset API (`/books/files/{id}/serve` 404s for audio); older servers have no manifest endpoint
 * and still serve audio via `/serve`.
 */
@Singleton
class AudiobookAssetResolver @Inject constructor(private val api: ApiService) {

    sealed interface Sources {
        /** Pre-3.0 server: fetch every file via `/books/files/{fileId}/serve`. */
        data object Legacy : Sources

        /** 3.0+ server: fileId -> assetId for `/audiobooks/{bookId}/assets/{assetId}/content`. */
        data class Assets(val assetIds: Map<Int, String>) : Sources
    }

    /**
     * Resolves [files] against the book's manifest. A 404 from the manifest means an older server
     * ([Sources.Legacy]); any other failure is thrown so callers can retry or surface it.
     */
    suspend fun resolve(bookId: Int, files: List<BookFileRef>): Sources {
        val manifest = try {
            api.getAudiobookManifest(bookId)
        } catch (e: HttpException) {
            if (e.code() == 404) return Sources.Legacy
            throw e
        }
        return Sources.Assets(AudiobookAssets.match(files, manifest.assets))
    }

    /** Path (relative to the server's `/api/v1`) that serves [fileId]'s bytes. */
    fun path(bookId: Int, fileId: Int, sources: Sources): String {
        val assetId = (sources as? Sources.Assets)?.assetIds?.get(fileId)
        return if (assetId != null) "audiobooks/$bookId/assets/$assetId/content"
        else "books/files/$fileId/serve"
    }
}
