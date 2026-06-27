package com.bookorbit.feature.reader

import com.bookorbit.core.db.ReaderProgressDao
import com.bookorbit.core.db.ReaderProgressEntity
import com.bookorbit.core.model.SaveFileProgress
import com.bookorbit.core.network.ApiService
import javax.inject.Inject
import javax.inject.Singleton

data class InitialProgress(val cfi: String?, val fraction: Double?)

/**
 * Offline-first reading-position store keyed by server file id, backed by Room: every position is
 * written locally first (so it survives offline + restart) and kept dirty until the server accepts
 * it.
 */
@Singleton
class ReaderProgressRepository @Inject constructor(
    private val dao: ReaderProgressDao,
    private val api: ApiService,
) {
    /**
     * Save locally, then attempt to push to the server (clearing dirty on success). [pageNumber] is
     * forwarded to the server for cross-client parity (used by the PDF reader); the local Room store
     * keeps only cfi + percentage, from which a stable PDF page count is recoverable on resume.
     */
    suspend fun report(fileId: Int, cfi: String?, percentage: Double, pageNumber: Int? = null) {
        dao.upsert(ReaderProgressEntity(fileId, cfi, percentage, System.currentTimeMillis(), dirty = true))
        runCatching {
            api.saveFileProgress(fileId, SaveFileProgress(cfi, percentage, pageNumber))
            dao.markSynced(fileId)
        }
    }

    /** Push every pending position to the server; entries that fail stay dirty. Returns count synced. */
    suspend fun flushPending(): Int {
        var synced = 0
        for (entry in dao.dirtyEntries()) {
            val ok = runCatching {
                api.saveFileProgress(entry.fileId, SaveFileProgress(entry.cfi, entry.percentage))
            }.isSuccess
            if (ok) {
                dao.markSynced(entry.fileId)
                synced++
            }
        }
        return synced
    }

    /**
     * Where to resume: a not-yet-synced local position wins (most recent reading), otherwise the
     * server's, otherwise the start.
     */
    suspend fun resolveInitial(fileId: Int): InitialProgress {
        val local = dao.get(fileId)
        val server = runCatching { api.getFileProgress(fileId) }.getOrNull()

        val cfi: String?
        val percentage: Double?
        if (local != null && (local.dirty || server == null)) {
            cfi = local.cfi
            percentage = local.percentage
        } else if (server != null) {
            cfi = server.cfi
            percentage = server.percentage
        } else {
            return InitialProgress(null, null)
        }
        return InitialProgress(cfi, percentage?.let { it / 100.0 })
    }
}
