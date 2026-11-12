package com.bookorbit.feature.player

import android.content.Context
import com.bookorbit.R
import dagger.hilt.android.qualifiers.ApplicationContext
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
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.bookorbit.feature.downloads.DownloadsRepository
import com.bookorbit.feature.cast.CastProxyServer
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/**
 * Serves the Android Auto browse tree and turns a tapped book into a playable queue. The tree is the
 * offline catalog ([AutoBrowseTree]); resolution reuses the same [PlayerRepository] + resume logic
 * the in-app player uses, then hands the active book to [PlayerManager] so progress sync covers
 * car-initiated playback too.
 */
@UnstableApi
@Singleton
class MediaLibraryCallback @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloads: DownloadsRepository,
    private val playbackResolver: AutoPlaybackResolver,
    private val playerManager: PlayerManager,
    private val castProxyServer: CastProxyServer,
    private val artwork: AutoArtwork,
    private val autoCatalog: AutoCatalog,
    private val search: AutoSearch,
) : MediaLibrarySession.Callback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queueRequestGeneration = 0L
    private data class SearchSnapshot(val query: String, val entries: List<AutoBrowseTree.BrowseEntry>)
    private val searchResults = ConcurrentHashMap<ControllerInfo, SearchSnapshot>()

    override fun onDisconnected(session: MediaSession, controller: ControllerInfo) {
        searchResults.remove(controller)
    }

    override fun onConnect(session: MediaSession, controller: ControllerInfo): MediaSession.ConnectionResult {
        val base = super<MediaLibrarySession.Callback>.onConnect(session, controller)
        if (base.isAccepted) artwork.grantTo(controller.packageName)
        if (!base.isAccepted || controller.packageName != context.packageName) return base
        return MediaSession.ConnectionResult.accept(
            base.availableSessionCommands.buildUpon().add(BookQueueCommand.command).build(),
            base.availablePlayerCommands,
        )
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: ControllerInfo,
        command: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (command != BookQueueCommand.command || controller.packageName != context.packageName) {
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
        val generation = ++queueRequestGeneration
        return future {
            val load = try { BookQueueCommand.decode(args) } catch (_: IllegalArgumentException) {
                return@future SessionResult(SessionError.ERROR_BAD_VALUE)
            }
            withContext(Dispatchers.Main.immediate) {
                if (generation != queueRequestGeneration) return@withContext SessionResult(SessionResult.RESULT_SUCCESS)
                val routed = routeQueue(MediaItemsWithStartPosition(load.items, load.index, load.positionMs))
                BookQueueCommand.apply(session.player, load.copy(items = routed.mediaItems))
                SessionResult(SessionResult.RESULT_SUCCESS)
            }
        }
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = future {
        LibraryResult.ofItem(AutoBrowseTree.rootMediaItem(labels()), contentStyledParams(params))
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
            AutoBrowseTree.ROOT_ID -> AutoBrowseTree.rootChildren(current(), labels())
            AutoBrowseTree.NOW_PLAYING_CHAPTERS_ID ->
                current()?.let { AutoBrowseTree.chapterEntries(it) } ?: emptyList()
            AutoBrowseTree.DOWNLOADS_ID -> AutoBrowseTree.downloadedAudiobooks(catalog(), labels())
            AutoBrowseTree.CONTINUE_ID -> autoCatalog.continueListening(labels())
            else -> AutoBrowseTree.parseLibraryPage(parentId)?.let { page ->
                val result = autoCatalog.page(page, labels())
                result.entries + if ((page.toLong() + 1) * AutoCatalog.PAGE_SIZE < result.total) {
                    listOf(AutoBrowseTree.BrowseEntry(AutoBrowseTree.libraryPageId(page + 1), labels().moreAudiobooks,
                        null, null, false))
                } else emptyList()
            } ?: emptyList()
        }
        val items = ImmutableList.copyOf(entries.map { mediaItem(it) })
        LibraryResult.ofItemList(items, params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = future {
        val bookId = AutoBrowseTree.parseBookId(mediaId)
        val chapterRef = AutoBrowseTree.parseChapterId(mediaId)
        if (chapterRef != null) {
            val entry = current()?.let { AutoBrowseTree.chapterEntries(it) }?.find { it.mediaId == mediaId }
            if (entry != null) LibraryResult.ofItem(mediaItem(entry), null)
            else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        } else if (bookId != null) {
            val entry = autoCatalog.item(bookId, labels())
            if (entry != null) LibraryResult.ofItem(mediaItem(entry), null)
            else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        } else {
            val node = AutoBrowseTree.rootChildren(current(), labels()).find { it.mediaId == mediaId }
            if (node != null) LibraryResult.ofItem(mediaItem(node), null)
            else LibraryResult.ofItem(AutoBrowseTree.rootMediaItem(labels()), null)
        }
    }

    override fun onSearch(
        session: MediaLibrarySession, browser: ControllerInfo, query: String, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = future {
        val results = search.find(query, labels(), current()?.bookId)
        searchResults[browser] = SearchSnapshot(query, results)
        withContext(Dispatchers.Main.immediate) {
            session.notifySearchResultChanged(browser, query, results.size, params)
        }
        LibraryResult.ofVoid(params)
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession, browser: ControllerInfo, query: String,
        page: Int, pageSize: Int, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
        val results = searchResults[browser]?.takeIf { it.query == query }?.entries
            ?: search.find(query, labels(), current()?.bookId)
        val offset = (page.toLong().coerceAtLeast(0) * pageSize.coerceAtLeast(0)).coerceAtMost(results.size.toLong()).toInt()
        LibraryResult.ofItemList(ImmutableList.copyOf(results.drop(offset).take(pageSize.coerceAtLeast(0)).map(::mediaItem)), params)
    }

    /** Tapping a book in the car: expand its `book/<id>` media id into the full file queue + resume. */
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaItemsWithStartPosition> = future {
        val query = mediaItems.getOrNull(startIndex)?.requestMetadata?.searchQuery
            ?: mediaItems.firstOrNull()?.requestMetadata?.searchQuery
        if (query != null) {
            val resolved = if (query.isBlank()) playbackResolver.resume() else {
                search.find(query, labels(), current()?.bookId).firstOrNull()
                    ?.mediaId?.let(AutoBrowseTree::parseBookId)?.let { playbackResolver.resolve(it) }
            }
            return@future routeQueue(resolved?.let { adopt(mediaSession, it) }
                ?: MediaItemsWithStartPosition(emptyList(), 0, 0))
        }
        val chapterRef = mediaItems.firstNotNullOfOrNull { AutoBrowseTree.parseChapterId(it.mediaId) }
        if (chapterRef != null) {
            val (chapterBookId, index) = chapterRef
            return@future routeQueue(resolveQueue(mediaSession, chapterBookId, startAtChapterIndex = index)
                ?: MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
        }
        val bookId = mediaItems.firstNotNullOfOrNull { AutoBrowseTree.parseBookId(it.mediaId) }
        routeQueue(bookId?.let { resolveQueue(mediaSession, it) }
            ?: MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
    }

    /** Pressing play with an empty queue (e.g. "resume" from the car): replay the last book. */
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: ControllerInfo,
    ): ListenableFuture<MediaItemsWithStartPosition> = future {
        routeQueue(playbackResolver.resume()?.let { adopt(mediaSession, it) }
            ?: MediaItemsWithStartPosition(emptyList(), 0, 0))
    }

    /** Queue replacement follows the active output, including a second book in the same session. */
    private suspend fun routeQueue(queue: MediaItemsWithStartPosition): MediaItemsWithStartPosition =
        withContext(Dispatchers.Main.immediate) {
            if (castProxyServer.isRunning) {
                MediaItemsWithStartPosition(
                    castProxyServer.rewriteForCast(queue.mediaItems, queue.startIndex, queue.startPositionMs),
                    queue.startIndex,
                    queue.startPositionMs,
                )
            } else queue
        }

    /** Built on each request, so the car follows the device language even if it changes while connected. */
    private fun labels() = AutoBrowseTree.Labels(
        appName = context.getString(R.string.app_name),
        continueListening = context.getString(R.string.auto_continue_listening),
        downloaded = context.getString(R.string.auto_downloaded),
        audiobook = context.getString(R.string.player_audiobook),
        chaptersOf = { title -> context.getString(R.string.auto_chapters_of, title) },
        audiobooks = context.getString(R.string.auto_audiobooks),
        moreAudiobooks = context.getString(R.string.auto_more_audiobooks),
    )

    private fun current() = AutoBrowseTree.currentBookInfo(playerManager.state.value, labels())

    private suspend fun resolveQueue(session: MediaSession, bookId: Int, startAtChapterIndex: Int? = null): MediaItemsWithStartPosition? {
        return playbackResolver.resolve(bookId, startAtChapterIndex)?.let { adopt(session, it) }
    }

    private suspend fun adopt(session: MediaSession, resolved: AutoPlaybackResolver.Resolved): MediaItemsWithStartPosition =
        withContext(Dispatchers.Main.immediate) {
            session.player.setPlaybackSpeed(resolved.speed)
            playerManager.adoptExternalQueue(resolved.data.book, resolved.data.files, resolved.speed, resolved.speedIsPerBook)
            MediaItemsWithStartPosition(resolved.data.mediaItems, resolved.index, resolved.positionMs)
        }

    private suspend fun catalog() = downloads.downloads.first()

    private fun mediaItem(entry: AutoBrowseTree.BrowseEntry): MediaItem {
        val id = AutoBrowseTree.parseBookId(entry.mediaId) ?: AutoBrowseTree.parseChapterId(entry.mediaId)?.first
        return AutoBrowseTree.toMediaItem(entry, id?.let { artwork.uri(it) })
    }

    /** Apply Auto's list/list content styling (see developer.android.com/training/cars/media). */
    private fun contentStyledParams(params: LibraryParams?): LibraryParams {
        val extras = Bundle(params?.extras ?: Bundle.EMPTY).apply {
            putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
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
