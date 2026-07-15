package com.bookorbit.feature.player

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.core.storage.LocalRef
import com.bookorbit.core.storage.exists
import com.bookorbit.core.storage.toUri
import com.bookorbit.feature.bookdetail.BookDetailRepository
import com.bookorbit.feature.downloads.DownloadsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Resolves an audiobook to a Media3 queue, preferring offline downloads for bytes + lock-screen art. */
@Singleton
class PlayerRepository @Inject constructor(
    private val bookRepo: BookDetailRepository,
    private val downloads: DownloadsRepository,
    private val session: SessionManager,
    private val json: Json,
    @ApplicationContext private val context: Context,
) {
    data class PlayerData(
        val book: BookDetail,
        val files: List<BookFileRef>,
        val mediaItems: List<MediaItem>,
    )

    @OptIn(UnstableApi::class)
    suspend fun resolve(bookId: Int): PlayerData? {
        // Use the offline copy's persisted BookDetail when present (works fully offline).
        val download = downloads.get(bookId)?.takeIf { it.isAudiobook }
        val book = if (download != null) {
            runCatching { json.decodeFromString(BookDetail.serializer(), download.bookJson) }.getOrNull()
        } else {
            runCatching { bookRepo.detail(bookId) }.getOrNull()
        } ?: return null

        val files = PlaybackQueue.audioFiles(book)
        if (files.isEmpty()) return null
        val base = session.serverUrl

        val localFiles = downloads.localFiles(bookId)
        val coverPath = downloads.coverPath(bookId)
        val performer = PlaybackQueue.performerLabel(book)
        val chapterStarts = PlaybackQueue.resolveChapters(book).map { it.startSec }.toDoubleArray()

        val items = files.map { file ->
            val localRef = localFiles[file.id]?.let(LocalRef::parse)?.takeIf { it.exists(context) }
            val uri = when {
                localRef != null -> localRef.toUri().toString()
                base != null -> "$base/api/v1/books/files/${file.id}/serve"
                else -> return null
            }
            // Extras carry data BookAggregatingPlayer needs to present a whole-book timeline to the
            // MediaSession (Bluetooth/Android Auto) instead of ExoPlayer's real per-file position/duration.
            val extras = Bundle().apply {
                putDouble(BookAggregatingPlayer.EXTRA_DURATION_SEC, file.durationSeconds ?: 0.0)
                if (chapterStarts.size >= 2) putDoubleArray(BookAggregatingPlayer.EXTRA_CHAPTER_STARTS_SEC, chapterStarts)
            }
            val metadata = MediaMetadata.Builder()
                .setTitle(book.title ?: "Audiobook")
                .setArtist(performer)
                .setExtras(extras)
                .apply {
                    // Lock-screen art only for local covers (the remote endpoint needs auth headers).
                    if (localRef != null && coverPath != null) setArtworkUri(LocalRef.parse(coverPath).toUri())
                }
                .build()
            MediaItem.Builder().setMediaId(file.id.toString()).setUri(uri).setMediaMetadata(metadata).build()
        }
        return PlayerData(book, files, items)
    }
}
