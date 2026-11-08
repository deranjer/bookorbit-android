package com.bookorbit.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.feature.bookdetail.BookDetailRepository
import com.bookorbit.feature.player.AudioProgressRepository
import com.bookorbit.feature.reader.ReaderAnnotationRepository
import com.bookorbit.feature.reader.ReaderProgressRepository
import com.bookorbit.feature.sessions.ReadingSessionTracker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Flushes every queued offline write (reader/audio progress, rating, read-status) once network
 * constraints are met. Retries (via WorkManager backoff) as long as anything remains pending.
 *
 * Signed out, it sends nothing: every write stays queued, and signing in schedules a new run.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val readerProgressRepository: ReaderProgressRepository,
    private val audioProgressRepository: AudioProgressRepository,
    private val bookDetailRepository: BookDetailRepository,
    private val readingSessionTracker: ReadingSessionTracker,
    private val annotationRepository: ReaderAnnotationRepository,
    private val session: SessionManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (session.currentUser == null) return Result.success()
        // Run every flush (no short-circuiting `||`): one stuck queue must not starve the others.
        val pending = listOf(
            readingSessionTracker.flushPending(),
            annotationRepository.flushPending(),
            readerProgressRepository.flushPending(),
            audioProgressRepository.flushPending(),
            bookDetailRepository.flushPendingRatings(),
            bookDetailRepository.flushPendingReadStatus(),
        )
        val stillPending = pending.any { it }
        return if (stillPending) Result.retry() else Result.success()
    }
}
