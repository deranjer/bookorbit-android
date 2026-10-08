package com.bookorbit.feature.player

import com.bookorbit.core.model.AudiobookBookmark
import com.bookorbit.core.model.CreateAudiobookBookmark
import com.bookorbit.core.network.ApiService
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Audiobook bookmarks, kept in sync with the server so they appear on the web and other clients. */
@Singleton
class AudiobookBookmarkRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun list(bookId: Int): List<AudiobookBookmark> =
        api.getAudiobookBookmarks(bookId).sortedBy { it.positionMs }

    suspend fun add(bookId: Int, positionSec: Double, title: String): AudiobookBookmark =
        api.createAudiobookBookmark(
            bookId,
            CreateAudiobookBookmark(
                clientId = UUID.randomUUID().toString(),
                positionMs = (positionSec * 1000).toLong().coerceAtLeast(0),
                title = title,
            ),
        )

    suspend fun delete(bookId: Int, bookmarkId: String) = api.deleteAudiobookBookmark(bookId, bookmarkId)
}
