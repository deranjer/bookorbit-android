package com.bookorbit.feature.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

/**
 * The ongoing "Downloading…" notification that lets [DownloadWorker] run as a foreground service.
 * Without it the download is an ordinary background job, which Android (and Samsung's app freezer in
 * particular) stops as soon as the app leaves the screen.
 */
object DownloadNotifications {
    private const val CHANNEL_ID = "downloads"
    private const val NOTIFICATION_ID_BASE = 40_000

    fun foregroundInfo(context: Context, bookId: Int, title: String, progress: Float?): ForegroundInfo {
        ensureChannel(context)
        val percent = progress?.let { (it * 100).toInt().coerceIn(0, 100) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(percent?.let { "Downloading · $it%" } ?: "Downloading")
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        val id = NOTIFICATION_ID_BASE + bookId
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress of books downloading for offline use"
            },
        )
    }
}
