package com.bookorbit.feature.player

import com.bookorbit.core.model.AudioProgress
import com.bookorbit.core.model.ScrollerType
import com.bookorbit.feature.browse.BrowseRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks which audiobook to reopen on launch ([PlayerManager.restoreLastBook]): this device's most
 * recent unfinished book plus the first few of the server's Continue Listening (whose order isn't a
 * timestamp), ranked by when each was last listened to anywhere. Every network lookup is time-boxed
 * and falls back to local data, so a slow or absent network only narrows the choice.
 */
@Singleton
class RestoreCandidateFinder @Inject constructor(
    private val audioProgress: AudioProgressRepository,
    private val browse: BrowseRepository,
) {
    /** Unfinished books to try, most recently played first, each with its resume position. */
    suspend fun find(): List<Pair<Int, AudioProgress>> = coroutineScope {
        runCatching { withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { audioProgress.flushPending() } }

        val serverIds = async {
            withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
                runCatching { browse.scroller(ScrollerType.CONTINUE_LISTENING, limit = SERVER_CANDIDATES) }.getOrNull()
            }.orEmpty().map { it.id }
        }
        val localId = audioProgress.recent()
            .firstOrNull { it.percentage < ResumeCandidates.FINISHED_PERCENT }?.bookId
        val local = localId?.let { id -> async { id to point(id) } }
        val server = serverIds.await().filter { it != localId }.map { id -> async { id to point(id) } }

        val points = (listOfNotNull(local) + server).awaitAll()
        val byId = points.toMap()
        val ranked = ResumeCandidates.rank(
            points.mapNotNull { (id, p) -> p?.let { ResumeCandidate(id, it.progress.percentage, it.lastActivityMillis) } },
        )
        ranked.mapNotNull { id -> byId[id]?.let { id to it.progress } }
    }

    private suspend fun point(bookId: Int): ResumePoint? =
        withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { audioProgress.resumePoint(bookId, flush = false) }
            ?: audioProgress.localResumePoint(bookId)

    companion object {
        /** Upper bound for each launch-restore network lookup. */
        const val LOOKUP_TIMEOUT_MS = 4_000L
        /** How many of the server's Continue Listening books to compare. */
        const val SERVER_CANDIDATES = 3
    }
}
