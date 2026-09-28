package com.bookorbit.feature.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import com.bookorbit.core.db.AudioProgressEntity
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.storage.LocalRef
import com.bookorbit.core.storage.toUri
import com.bookorbit.feature.downloads.isCompleteDownloadStatus

/**
 * Builds the Android Auto browse hierarchy from the offline catalog. The car shows a root with two
 * shelves — "Continue listening" and "Downloaded" — both sourced from completed audiobook downloads
 * so browsing works fully offline (no auth headers needed for cover art over the car connection).
 *
 * The filtering/ordering is kept pure (operating on entities) so it is unit-testable; only
 * [toMediaItem] touches the Android framework ([Uri]).
 */
object AutoBrowseTree {
    const val ROOT_ID = "root"
    const val CONTINUE_ID = "continue"
    const val DOWNLOADS_ID = "downloads"

    const val NOW_PLAYING_CHAPTERS_ID = "now_playing_chapters"

    private const val BOOK_PREFIX = "book/"
    private const val CHAPTER_PREFIX = "chapter/"

    fun bookMediaId(bookId: Int): String = "$BOOK_PREFIX$bookId"

    /** Parses a `book/<id>` media id back to its book id, or null if it isn't a book media id. */
    fun parseBookId(mediaId: String): Int? =
        mediaId.removePrefix(BOOK_PREFIX).takeIf { it != mediaId }?.toIntOrNull()

    /** A browse node or playable book, decoupled from the Android [MediaItem] for testability. */
    data class BrowseEntry(
        val mediaId: String,
        val title: String,
        val subtitle: String?,
        val coverPath: String?,
        val isPlayable: Boolean,
        val completion: CompletionStatus? = null,
    )

    enum class CompletionStatus { NOT_PLAYED, PARTIALLY_PLAYED, FULLY_PLAYED }

    data class CurrentBookInfo(val bookId: Int, val title: String, val ranges: List<ChapterRange>, val positionSec: Double)

    fun currentBookInfo(state: PlayerManager.UiState): CurrentBookInfo? {
        val book = state.currentBook ?: return null
        val ranges = PlaybackQueue.chapterRanges(state.chapters, state.totalDurationSec)
        return CurrentBookInfo(book.id, book.title ?: "Audiobook", ranges, state.positionSec)
    }

    /** Top-level shelves shown under the root. */
    fun rootChildren(current: CurrentBookInfo?): List<BrowseEntry> = buildList {
        if (current != null && current.ranges.size >= 2) {
            add(BrowseEntry(NOW_PLAYING_CHAPTERS_ID, "Chapters · ${current.title}", null, null, isPlayable = false))
        }
        add(BrowseEntry(CONTINUE_ID, "Continue listening", null, null, isPlayable = false))
        add(BrowseEntry(DOWNLOADS_ID, "Downloaded", null, null, isPlayable = false))
    }

    fun chapterMediaId(bookId: Int, index: Int) = "$CHAPTER_PREFIX$bookId/$index"

    fun parseChapterId(mediaId: String): Pair<Int, Int>? {
        if (!mediaId.startsWith(CHAPTER_PREFIX)) return null
        val parts = mediaId.removePrefix(CHAPTER_PREFIX).split("/")
        if (parts.size != 2) return null
        val book = parts[0].toIntOrNull() ?: return null
        val idx = parts[1].toIntOrNull() ?: return null
        return book to idx
    }

    fun chapterEntries(current: CurrentBookInfo): List<BrowseEntry> = current.ranges.map { r ->
        val completion = when {
            r.endSec <= current.positionSec -> CompletionStatus.FULLY_PLAYED
            r.startSec <= current.positionSec -> CompletionStatus.PARTIALLY_PLAYED
            else -> CompletionStatus.NOT_PLAYED
        }
        BrowseEntry(chapterMediaId(current.bookId, r.index), r.title, PlaybackQueue.formatDurationShort(r.lengthSec), null, isPlayable = true, completion = completion)
    }

    /** Completed audiobook downloads as playable items (most recently downloaded first). */
    fun downloadedAudiobooks(downloads: List<DownloadEntity>): List<BrowseEntry> =
        downloads.filter { it.isComplete }
            .sortedByDescending { it.downloadedAt }
            .map { it.toBrowseEntry() }

    /**
     * Downloaded audiobooks that have a saved listening position, ordered by most recently played.
     * The intersection keeps the shelf offline-safe and metadata-complete (title/art come from the
     * download record, not the network).
     */
    fun continueListening(
        downloads: List<DownloadEntity>,
        progress: List<AudioProgressEntity>,
    ): List<BrowseEntry> {
        val byId = downloads.filter { it.isComplete }.associateBy { it.bookId }
        return progress.sortedByDescending { it.updatedAt }
            .mapNotNull { byId[it.bookId]?.toBrowseEntry() }
    }

    /** Builds the browsable root node. */
    fun rootMediaItem(): MediaItem = browsableItem(ROOT_ID, "BookOrbit", null)

    fun toMediaItem(entry: BrowseEntry): MediaItem =
        if (entry.isPlayable) {
            val metadata = MediaMetadata.Builder()
                .setTitle(entry.title)
                .setArtist(entry.subtitle)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(
                    if (entry.mediaId.startsWith(CHAPTER_PREFIX)) MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER
                    else MediaMetadata.MEDIA_TYPE_AUDIO_BOOK,
                )
                .apply {
                    entry.completion?.let { c ->
                        setExtras(
                            Bundle().apply {
                                putInt(
                                    MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                                    when (c) {
                                        CompletionStatus.NOT_PLAYED -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
                                        CompletionStatus.PARTIALLY_PLAYED -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
                                        CompletionStatus.FULLY_PLAYED -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
                                    },
                                )
                            },
                        )
                    }
                }
                .apply { entry.coverPath?.let { setArtworkUri(LocalRef.parse(it).toUri()) } }
                .build()
            MediaItem.Builder().setMediaId(entry.mediaId).setMediaMetadata(metadata).build()
        } else {
            browsableItem(entry.mediaId, entry.title, entry.subtitle)
        }

    private fun browsableItem(id: String, title: String, subtitle: String?): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS)
            .build()
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()
    }

    private val DownloadEntity.isComplete: Boolean
        get() = isAudiobook && status.isCompleteDownloadStatus()

    private fun DownloadEntity.toBrowseEntry(): BrowseEntry = BrowseEntry(
        mediaId = bookMediaId(bookId),
        title = title ?: "Audiobook",
        subtitle = narrators.ifBlank { authors }.ifBlank { null },
        coverPath = coverLocalPath,
        isPlayable = true,
    )
}
