package com.bookorbit.feature.player

import com.bookorbit.core.db.AudioProgressDao
import com.bookorbit.core.db.AudioProgressEntity
import com.bookorbit.core.model.AudioProgress
import com.bookorbit.core.model.SaveAudioProgress
import com.bookorbit.core.network.ApiService
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first audiobook position store keyed by book id, backed by Room (local-first write +
 * dirty flag + flush).
 */
@Singleton
class AudioProgressRepository @Inject constructor(
    private val dao: AudioProgressDao,
    private val api: ApiService,
) {
    suspend fun report(bookId: Int, currentFileId: Int, positionSeconds: Double, percentage: Double) {
        dao.upsert(
            AudioProgressEntity(bookId, currentFileId, positionSeconds, percentage, System.currentTimeMillis(), dirty = true),
        )
        runCatching {
            api.saveAudioProgress(bookId, SaveAudioProgress(currentFileId, positionSeconds, percentage))
            dao.markSynced(bookId)
        }
    }

    suspend fun flushPending(): Int {
        var synced = 0
        for (e in dao.dirtyEntries()) {
            val ok = runCatching {
                api.saveAudioProgress(e.bookId, SaveAudioProgress(e.currentFileId, e.positionSeconds, e.percentage))
            }.isSuccess
            if (ok) {
                dao.markSynced(e.bookId)
                synced++
            }
        }
        return synced
    }

    /** Saved positions, most recently played first (Android Auto "Continue listening" shelf). */
    suspend fun recent(): List<AudioProgressEntity> = dao.recent()

    /**
     * Resume position: retries any pending local write first, then picks whichever of local/server
     * is actually newer by timestamp (true last-write-wins, matching the server's own merge logic),
     * falling back to local if the server is unreachable and to server if there's no local row.
     */
    suspend fun resolveResume(bookId: Int): AudioProgress? {
        runCatching { flushPending() }
        val local = dao.get(bookId)
        val server = runCatching { api.getAudioProgress(bookId) }.getOrNull()
        if (local == null) return server
        if (server == null) return local.toAudioProgress()
        val serverMillis = server.updatedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        return if (serverMillis != null && serverMillis > local.updatedAt) server else local.toAudioProgress()
    }

    private fun AudioProgressEntity.toAudioProgress() =
        AudioProgress(currentFileId, positionSeconds, percentage, updatedAt = null)
}
