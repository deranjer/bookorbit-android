package com.bookorbit.feature.reader

import com.bookorbit.core.db.ReaderProgressDao
import com.bookorbit.core.db.ReaderProgressEntity
import com.bookorbit.core.model.SaveFileProgress
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class InitialProgress(val cfi: String?, val fraction: Double?, val pageNumber: Int? = null)

/**
 * Offline-first reading-position store keyed by server file id, backed by Room: every position is
 * written locally first (so it survives offline + restart) and kept dirty until the server accepts
 * it.
 */
@Singleton
class ReaderProgressRepository @Inject constructor(
    private val dao: ReaderProgressDao,
    private val api: ApiService,
    private val syncScheduler: SyncScheduler,
) {
    private val syncMutex = Mutex()
    suspend fun mostRecentFileId(fileIds: List<Int>): Int? = fileIds.distinct().mapNotNull { dao.get(it) }.maxByOrNull { it.updatedAt }?.fileId
    /**
     * Save locally, then attempt to push to the server (clearing dirty on success). [pageNumber] is
     * persisted alongside CFI + percentage so offline retries retain PDF/comic coordinates.
     */
    suspend fun report(fileId: Int, cfi: String?, percentage: Double, pageNumber: Int? = null) {
        val entry = ReaderProgressEntity(fileId, cfi, percentage, System.currentTimeMillis(), dirty = true,
            pageNumber = pageNumber, writeId = UUID.randomUUID().toString())
        dao.upsert(entry)
        syncMutex.withLock {
            if (dao.get(fileId)?.writeId != entry.writeId) return@withLock
            if (!push(entry) || dao.markSynced(fileId, entry.writeId) == 0) syncScheduler.schedule()
        }
    }

    /** Push every dirty position to the server. Returns true if anything is still dirty afterward. */
    suspend fun flushPending(): Boolean = syncMutex.withLock {
        for (entry in dao.dirtyEntries()) {
            if (push(entry)) dao.markSynced(entry.fileId, entry.writeId)
        }
        dao.dirtyEntries().isNotEmpty()
    }

    private suspend fun push(entry: ReaderProgressEntity): Boolean = try {
        api.saveFileProgress(entry.fileId, SaveFileProgress(entry.cfi, entry.percentage, entry.pageNumber))
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /** Do not let a web reader start behind a pending offline position for this file. */
    suspend fun syncBeforeWeb(fileId: Int) = syncMutex.withLock {
        val local = dao.get(fileId) ?: return@withLock
        if (!local.dirty) return@withLock
        api.saveFileProgress(fileId, SaveFileProgress(local.cfi, local.percentage, local.pageNumber))
        if (dao.markSynced(fileId, local.writeId) == 0) {
            syncScheduler.schedule()
            error("Reading position changed during handoff. Try again.")
        }
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
        val pageNumber: Int?
        if (local != null && (local.dirty || server == null)) {
            cfi = local.cfi
            percentage = local.percentage
            pageNumber = local.pageNumber
        } else if (server != null) {
            cfi = server.cfi
            percentage = server.percentage
            pageNumber = server.pageNumber
        } else {
            return InitialProgress(null, null)
        }
        return InitialProgress(cfi, percentage?.let { it / 100.0 }, pageNumber)
    }
}
