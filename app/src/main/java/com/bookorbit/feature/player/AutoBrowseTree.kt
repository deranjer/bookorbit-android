package com.bookorbit.feature.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.bookorbit.core.db.AudioProgressEntity
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.feature.downloads.DownloadStatus
import java.io.File

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

    private const val BOOK_PREFIX = "book/"

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
    )

    /** Top-level shelves shown under the root. */
    fun rootChildren(): List<BrowseEntry> = listOf(
        BrowseEntry(CONTINUE_ID, "Continue listening", null, null, isPlayable = false),
        BrowseEntry(DOWNLOADS_ID, "Downloaded", null, null, isPlayable = false),
    )

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
                .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                .apply { entry.coverPath?.let { setArtworkUri(Uri.fromFile(File(it))) } }
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
        get() = isAudiobook && status == DownloadStatus.COMPLETE.name

    private fun DownloadEntity.toBrowseEntry(): BrowseEntry = BrowseEntry(
        mediaId = bookMediaId(bookId),
        title = title ?: "Audiobook",
        subtitle = narrators.ifBlank { authors }.ifBlank { null },
        coverPath = coverLocalPath,
        isPlayable = true,
    )
}
