package com.bookorbit.feature.player

import android.content.Context
import com.bookorbit.R
import dagger.hilt.android.qualifiers.ApplicationContext
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import com.bookorbit.core.auth.SessionManager
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
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
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
    private val autoLibrary: AutoLibraryRepository,
    private val search: AutoSearch,
    private val sessionManager: SessionManager,
) : MediaLibrarySession.Callback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queueRequestGeneration = 0L
    private data class SearchSnapshot(val query: String, val scope: String, val entries: List<AutoBrowseTree.BrowseEntry>)
    private val searchResults = ConcurrentHashMap<ControllerInfo, SearchSnapshot>()
    private val subscriptions = ConcurrentHashMap<ControllerInfo, Set<String>>()

    override fun onDisconnected(session: MediaSession, controller: ControllerInfo) {
        searchResults.remove(controller)
        subscriptions.remove(controller)
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
    ): ListenableFuture<LibraryResult<MediaItem>> = libraryFuture {
        LibraryResult.ofItem(AutoBrowseTree.rootMediaItem(labels()), contentStyledParams(params))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = libraryFuture {
        val entries = children(parentId)
        artwork.grantTo(browser.packageName)
        val items = ImmutableList.copyOf(entries.map { mediaItem(it) })
        LibraryResult.ofItemList(items, params)
    }

    override fun onSubscribe(
        session: MediaLibrarySession, browser: ControllerInfo, parentId: String, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = libraryFuture {
        val count = children(parentId).size
        subscriptions.compute(browser) { _, parents -> parents.orEmpty() + parentId }
        withContext(Dispatchers.Main.immediate) { session.notifyChildrenChanged(browser, parentId, count, params) }
        LibraryResult.ofVoid(params)
    }

    override fun onUnsubscribe(
        session: MediaLibrarySession, browser: ControllerInfo, parentId: String,
    ): ListenableFuture<LibraryResult<Void>> {
        subscriptions.computeIfPresent(browser) { _, parents -> parents - parentId }
        return Futures.immediateFuture(LibraryResult.ofVoid(null))
    }

    fun refresh(session: MediaLibrarySession, shelves: Set<String>) {
        scope.launch {
            val parents = subscriptions.values.flatMap { it }.toSet().filter {
                (AutoBrowseTree.parseGroup(it)?.parent ?: it) in shelves ||
                    (AutoBrowseTree.DOWNLOADS_ID in shelves &&
                        (it.startsWith("${AutoBrowseTree.LIBRARY_ID}/") || AutoLibraryTree.parseGroup(it) != null))
            }
            for (parent in parents) {
                try {
                    val count = children(parent).size
                    withContext(Dispatchers.Main.immediate) { session.notifyChildrenChanged(parent, count, null) }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { /* Keep the existing shelf during a transient server outage. */ }
            }
        }
    }

    private suspend fun children(parentId: String): List<AutoBrowseTree.BrowseEntry> {
        val group = AutoBrowseTree.parseGroup(parentId)
        val parent = group?.parent ?: parentId
        val entries = when (parent) {
            AutoBrowseTree.ROOT_ID -> return AutoBrowseTree.rootChildren(current(), labels())
            AutoBrowseTree.NOW_PLAYING_CHAPTERS_ID -> current()?.let(AutoBrowseTree::chapterEntries).orEmpty()
            AutoBrowseTree.DOWNLOADS_ID -> AutoBrowseTree.downloadedAudiobooks(catalog(), labels())
            AutoBrowseTree.CONTINUE_ID -> autoCatalog.continueListening(labels())
            else -> return autoLibrary.children(parent, labels())
        }
        return AutoBrowseTree.grouped(parent, entries, labels(), group?.start ?: 0, group?.end ?: entries.size)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = libraryFuture {
        val bookId = AutoBrowseTree.parseBookId(mediaId)
        val chapterRef = AutoBrowseTree.parseChapterId(mediaId)
        if (chapterRef != null) {
            val entry = current()?.let { AutoBrowseTree.chapterEntries(it) }?.find { it.mediaId == mediaId }
            if (entry != null) LibraryResult.ofItem(mediaItem(entry), null)
            else throw AutoRequestException(AutoFailure.UNAVAILABLE)
        } else if (bookId != null) {
            val entry = autoCatalog.item(bookId, labels())
            if (entry != null) LibraryResult.ofItem(mediaItem(entry), null)
            else throw AutoRequestException(AutoFailure.UNAVAILABLE)
        } else {
            val node = AutoBrowseTree.rootChildren(current(), labels()).find { it.mediaId == mediaId }
            val group = AutoBrowseTree.parseGroup(mediaId)
            val folder = when {
                node != null -> node
                group != null -> run {
                    // Resolve the range's actual titles, including nested local shelf groups.
                    val all = when (group.parent) {
                        AutoBrowseTree.DOWNLOADS_ID -> AutoBrowseTree.downloadedAudiobooks(catalog(), labels())
                        AutoBrowseTree.CONTINUE_ID -> autoCatalog.continueListening(labels())
                        else -> current()?.let(AutoBrowseTree::chapterEntries).orEmpty()
                    }
                    all.getOrNull(group.start)?.let { first -> all.getOrNull(group.end - 1)?.let { last ->
                        AutoBrowseTree.BrowseEntry(mediaId, labels().titleRange(first.title, last.title), null, null, false)
                    } }
                }
                mediaId == AutoBrowseTree.ROOT_ID -> null
                else -> autoLibrary.item(mediaId, labels())
            }
            when {
                mediaId == AutoBrowseTree.ROOT_ID -> LibraryResult.ofItem(AutoBrowseTree.rootMediaItem(labels()), null)
                folder != null -> LibraryResult.ofItem(mediaItem(folder), null)
                else -> throw AutoRequestException(AutoFailure.INVALID_REQUEST)
            }
        }
    }

    override fun onSearch(
        session: MediaLibrarySession, browser: ControllerInfo, query: String, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = libraryFuture {
        val results = search.find(query, labels(), current()?.bookId)
        searchResults[browser] = SearchSnapshot(query, dataScope(), results)
        withContext(Dispatchers.Main.immediate) {
            session.notifySearchResultChanged(browser, query, results.size, params)
        }
        LibraryResult.ofVoid(params)
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession, browser: ControllerInfo, query: String,
        page: Int, pageSize: Int, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = libraryFuture {
        val results = searchResults[browser]?.takeIf { it.query == query && it.scope == dataScope() }?.entries
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
    ): ListenableFuture<MediaItemsWithStartPosition> = playbackFuture(mediaSession) {
        if (mediaItems.isEmpty()) return@playbackFuture MediaItemsWithStartPosition(emptyList(), 0, 0)
        val selected = mediaItems.getOrNull(startIndex) ?: mediaItems.first()
        val query = selected.requestMetadata.searchQuery
        if (query != null) {
            val resolved = if (query.isBlank()) playbackResolver.resume() else {
                val match = search.find(query, labels(), current()?.bookId).firstOrNull()
                    ?: throw AutoRequestException(AutoFailure.NO_RESULTS)
                match.mediaId.let(AutoBrowseTree::parseBookId)?.let { playbackResolver.resolve(it) }
                    ?: throw AutoRequestException(AutoFailure.UNAVAILABLE)
            }
            if (resolved == null) throw AutoRequestException(if (query.isBlank()) AutoFailure.NO_HISTORY else AutoFailure.NO_RESULTS)
            return@playbackFuture routeQueue(adopt(mediaSession, resolved))
        }
        val chapterRef = AutoBrowseTree.parseChapterId(selected.mediaId)
        if (chapterRef != null) {
            val (chapterBookId, index) = chapterRef
            return@playbackFuture routeQueue(resolveQueue(mediaSession, chapterBookId, startAtChapterIndex = index)
                ?: throw AutoRequestException(AutoFailure.UNAVAILABLE))
        }
        val bookId = AutoBrowseTree.parseBookId(selected.mediaId)
        if (bookId != null) return@playbackFuture routeQueue(resolveQueue(mediaSession, bookId)
            ?: throw AutoRequestException(AutoFailure.UNAVAILABLE))
        if (controller.packageName == context.packageName && mediaItems.all { it.localConfiguration != null }) {
            return@playbackFuture routeQueue(MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
        }
        throw AutoRequestException(AutoFailure.INVALID_REQUEST)
    }

    /** Pressing play with an empty queue (e.g. "resume" from the car): replay the last book. */
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: ControllerInfo,
    ): ListenableFuture<MediaItemsWithStartPosition> = playbackFuture(mediaSession) {
        routeQueue(playbackResolver.resume()?.let { adopt(mediaSession, it) }
            ?: throw AutoRequestException(AutoFailure.NO_HISTORY))
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
        byTitle = context.getString(R.string.auto_by_title),
        byAuthor = context.getString(R.string.auto_by_author),
        bySeries = context.getString(R.string.auto_by_series),
        unknownAuthor = context.getString(R.string.auto_unknown_author),
        bookCount = { count -> context.resources.getQuantityString(R.plurals.auto_book_count, count, count) },
        titleRange = { first, last -> context.getString(R.string.auto_title_range, first, last) },
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
        return LibraryParams.Builder().setExtras(extras)
            .setRecent(params?.isRecent ?: false).setOffline(params?.isOffline ?: false)
            .setSuggested(params?.isSuggested ?: false).build()
    }

    private fun dataScope() = "${sessionManager.serverUrl}|${sessionManager.currentUser?.id}"

    private fun error(error: Throwable): SessionError {
        val reason = if (sessionManager.accessToken == null && error is java.io.IOException) AutoFailure.SIGN_IN else AutoFailure.from(error)
        val (code, message) = when (reason) {
            AutoFailure.SIGN_IN -> SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED to R.string.auto_err_sign_in
            AutoFailure.NETWORK -> SessionError.ERROR_IO to R.string.auto_err_network
            AutoFailure.NO_RESULTS -> SessionError.ERROR_BAD_VALUE to R.string.auto_err_no_results
            AutoFailure.NO_HISTORY -> SessionError.ERROR_SESSION_END_OF_PLAYLIST to R.string.auto_err_no_history
            AutoFailure.INVALID_REQUEST -> SessionError.ERROR_BAD_VALUE to R.string.auto_err_invalid
            AutoFailure.UNAVAILABLE -> SessionError.ERROR_BAD_VALUE to R.string.auto_err_unavailable
        }
        return SessionError(code, context.getString(message), Bundle.EMPTY)
    }

    private fun <T : Any> libraryFuture(block: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> = future {
        try { block() }
        catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            LibraryResult.ofError<T>(error(e))
        } catch (e: Exception) { LibraryResult.ofError<T>(error(e)) }
    }

    private fun playbackFuture(session: MediaSession, block: suspend () -> MediaItemsWithStartPosition): ListenableFuture<MediaItemsWithStartPosition> = future {
        try { block() }
        catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            val failure = error(e)
            withContext(Dispatchers.Main.immediate) { session.sendError(failure) }
            throw PlaybackException(failure.message, e, failure.code)
        } catch (e: Exception) {
            val failure = error(e)
            withContext(Dispatchers.Main.immediate) { session.sendError(failure) }
            throw PlaybackException(failure.message, e, failure.code)
        }
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val settable = SettableFuture.create<T>()
        val job = scope.launch {
            try { settable.set(block()) }
            catch (e: CancellationException) { settable.cancel(false) }
            catch (e: Exception) { settable.setException(e) }
        }
        settable.addListener({ if (settable.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
        return settable
    }
}
