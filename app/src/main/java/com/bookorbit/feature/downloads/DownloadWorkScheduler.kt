package com.bookorbit.feature.downloads

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.await
import com.bookorbit.core.settings.AppSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettingsStore,
) {
    suspend fun enqueue(bookId: Int, planId: String) {
        val network = if (settings.wifiOnlyDownloads.first()) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_BOOK_ID to bookId, DownloadWorker.KEY_PLAN_ID to planId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag(DownloadWorker.tag(bookId))
            .build()
        // A generation gets its own work name; a finishing older worker cannot swallow this request.
        WorkManager.getInstance(context).enqueueUniqueWork("${DownloadWorker.tag(bookId)}-$planId", ExistingWorkPolicy.KEEP, request).await()
    }

    suspend fun cancel(bookId: Int) {
        WorkManager.getInstance(context).cancelAllWorkByTag(DownloadWorker.tag(bookId)).await()
    }
}
