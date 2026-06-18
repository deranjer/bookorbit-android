package com.bookorbit.feature.player

import com.bookorbit.core.db.AudioProgressDao
import com.bookorbit.core.db.AudioProgressEntity
import com.bookorbit.core.model.AudioProgress
import com.bookorbit.core.network.ApiService
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
            api.saveAudioProgress(bookId, AudioProgress(currentFileId, positionSeconds, percentage))
            dao.markSynced(bookId)
        }
    }

    suspend fun flushPending(): Int {
        var synced = 0
        for (e in dao.dirtyEntries()) {
            val ok = runCatching {
                api.saveAudioProgress(e.bookId, AudioProgress(e.currentFileId, e.positionSeconds, e.percentage))
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

    /** Resume position: unsynced local wins, else server, else null. */
    suspend fun resolveResume(bookId: Int): AudioProgress? {
        val local = dao.get(bookId)
        val server = runCatching { api.getAudioProgress(bookId) }.getOrNull()
        return if (local != null && (local.dirty || server == null)) {
            AudioProgress(local.currentFileId, local.positionSeconds, local.percentage)
        } else {
            server
        }
    }
}
