package com.bookorbit.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues a background flush of any pending offline writes (progress, rating, read-status) once
 * connectivity returns. [SyncWorker] re-reads whatever is actually pending at run time, so a
 * duplicate [schedule] call while one is already queued is a cheap no-op ([ExistingWorkPolicy.KEEP]).
 */
@Singleton
class SyncScheduler internal constructor(private val enqueue: () -> Unit) {
    @Inject constructor(@ApplicationContext context: Context) : this({
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
    })

    fun schedule() = enqueue()

    companion object {
        const val TAG = "sync-pending-writes"
    }
}
