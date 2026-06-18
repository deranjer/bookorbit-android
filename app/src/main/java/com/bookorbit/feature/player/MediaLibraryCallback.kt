package com.bookorbit.feature.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionError
import com.bookorbit.feature.downloads.DownloadsRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serves the Android Auto browse tree and turns a tapped book into a playable queue. The tree is the
 * offline catalog ([AutoBrowseTree]); resolution reuses the same [PlayerRepository] + resume logic
 * the in-app player uses, then hands the active book to [PlayerManager] so progress sync covers
 * car-initiated playback too.
 */
@UnstableApi
@Singleton
class MediaLibraryCallback @Inject constructor(
    private val downloads: DownloadsRepository,
    private val playerRepo: PlayerRepository,
    private val audioProgress: AudioProgressRepository,
    private val playerManager: PlayerManager,
) : MediaLibrarySession.Callback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = future {
        LibraryResult.ofItem(AutoBrowseTree.rootMediaItem(), contentStyledParams(params))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
        val entries = when (parentId) {
            AutoBrowseTree.ROOT_ID -> AutoBrowseTree.rootChildren()
            AutoBrowseTree.DOWNLOADS_ID -> AutoBrowseTree.downloadedAudiobooks(catalog())
            AutoBrowseTree.CONTINUE_ID ->
                AutoBrowseTree.continueListening(catalog(), audioProgress.recent())
            else -> emptyList()
        }
        val items = ImmutableList.copyOf(entries.map { AutoBrowseTree.toMediaItem(it) })
        LibraryResult.ofItemList(items, params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = future {
        val bookId = AutoBrowseTree.parseBookId(mediaId)
        if (bookId != null) {
            val entry = AutoBrowseTree.downloadedAudiobooks(catalog()).find { it.mediaId == mediaId }
            if (entry != null) LibraryResult.ofItem(AutoBrowseTree.toMediaItem(entry), null)
            else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        } else {
            val node = AutoBrowseTree.rootChildren().find { it.mediaId == mediaId }
            if (node != null) LibraryResult.ofItem(AutoBrowseTree.toMediaItem(node), null)
            else LibraryResult.ofItem(AutoBrowseTree.rootMediaItem(), null)
        }
    }

    /** Tapping a book in the car: expand its `book/<id>` media id into the full file queue + resume. */
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaItemsWithStartPosition> = future {
        val bookId = mediaItems.firstNotNullOfOrNull { AutoBrowseTree.parseBookId(it.mediaId) }
        bookId?.let { resolveQueue(it) }
            ?: MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
    }

    /** Pressing play with an empty queue (e.g. "resume" from the car): replay the last book. */
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: ControllerInfo,
    ): ListenableFuture<MediaItemsWithStartPosition> = future {
        audioProgress.recent().firstNotNullOfOrNull { resolveQueue(it.bookId) }
            ?: MediaItemsWithStartPosition(emptyList(), 0, 0)
    }

    private suspend fun resolveQueue(bookId: Int): MediaItemsWithStartPosition? {
        val data = playerRepo.resolve(bookId) ?: return null
        val resume = audioProgress.resolveResume(bookId)
        val index = resume
            ?.let { data.files.indexOfFirst { f -> f.id == it.currentFileId } }
            ?.coerceAtLeast(0) ?: 0
        val positionMs = ((resume?.positionSeconds ?: 0.0) * 1000).toLong()
        // Seed the in-app player state so the shared progress poller reports car-started playback.
        playerManager.adoptExternalQueue(data.book, data.files)
        return MediaItemsWithStartPosition(data.mediaItems, index, positionMs)
    }

    private suspend fun catalog() = downloads.downloads.first()

    /** Apply Auto's list/list content styling (see developer.android.com/training/cars/media). */
    private fun contentStyledParams(params: LibraryParams?): LibraryParams {
        val extras = Bundle(params?.extras ?: Bundle.EMPTY).apply {
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            )
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            )
        }
        return LibraryParams.Builder().setExtras(extras).build()
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val settable = SettableFuture.create<T>()
        scope.launch {
            runCatching { block() }
                .onSuccess { settable.set(it) }
                .onFailure { settable.setException(it) }
        }
        return settable
    }
}
