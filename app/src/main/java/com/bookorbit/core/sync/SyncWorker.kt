package com.bookorbit.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bookorbit.feature.bookdetail.BookDetailRepository
import com.bookorbit.feature.player.AudioProgressRepository
import com.bookorbit.feature.reader.ReaderProgressRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Flushes every queued offline write (reader/audio progress, rating, read-status) once network
 * constraints are met. Retries (via WorkManager backoff) as long as anything remains pending.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val readerProgressRepository: ReaderProgressRepository,
    private val audioProgressRepository: AudioProgressRepository,
    private val bookDetailRepository: BookDetailRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val stillPending = readerProgressRepository.flushPending() ||
            audioProgressRepository.flushPending() ||
            bookDetailRepository.flushPendingRatings() ||
            bookDetailRepository.flushPendingReadStatus()
        return if (stillPending) Result.retry() else Result.success()
    }
}
