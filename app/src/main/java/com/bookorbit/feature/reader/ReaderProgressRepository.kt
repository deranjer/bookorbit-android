package com.bookorbit.feature.reader

import com.bookorbit.core.db.ReaderProgressDao
import com.bookorbit.core.db.ReaderProgressEntity
import com.bookorbit.core.model.SaveFileProgress
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class InitialProgress(val cfi: String?, val fraction: Double?)

/** A position to save: where in [fileId] the reader is, as the server records it. */
data class SavedPosition(val fileId: Int, val cfi: String?, val percentage: Double, val pageNumber: Int? = null)

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
    /** Outlives any one reader, so the save made as a reader closes isn't cancelled with it. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Saves go out one at a time, in order, so an older position never lands after a newer one. */
    private val saving = Mutex()

    private val _changes = MutableStateFlow(0)

    /**
     * Counts saved positions. Screens that show reading progress watch it while they're on screen
     * and reload when it moves, so coming back from a reader shows where the reader got to.
     */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    /** [report] in the background, after any save still going out. */
    fun save(position: SavedPosition) {
        scope.launch { saving.withLock { report(position.fileId, position.cfi, position.percentage, position.pageNumber) } }
    }

    /**
     * Save locally, then attempt to push to the server (clearing dirty on success). [pageNumber] is
     * forwarded to the server for cross-client parity (used by the PDF reader); the local Room store
     * keeps only cfi + percentage, from which a stable PDF page count is recoverable on resume.
     */
    suspend fun report(fileId: Int, cfi: String?, percentage: Double, pageNumber: Int? = null) {
        dao.upsert(ReaderProgressEntity(fileId, cfi, percentage, System.currentTimeMillis(), dirty = true))
        val ok = runCatching {
            api.saveFileProgress(fileId, SaveFileProgress(cfi, percentage, pageNumber))
        }.isSuccess
        if (ok) dao.markSynced(fileId) else syncScheduler.schedule()
        _changes.update { it + 1 }
    }

    /** Push every dirty position to the server. Returns true if anything is still dirty afterward. */
    suspend fun flushPending(): Boolean {
        for (entry in dao.dirtyEntries()) {
            val ok = runCatching {
                api.saveFileProgress(entry.fileId, SaveFileProgress(entry.cfi, entry.percentage))
            }.isSuccess
            if (ok) dao.markSynced(entry.fileId)
        }
        return dao.dirtyEntries().isNotEmpty()
    }

    /** Do not let a web reader start behind a pending offline position for this file. */
    suspend fun syncBeforeWeb(fileId: Int) {
        val local = dao.get(fileId) ?: return
        if (!local.dirty) return
        api.saveFileProgress(fileId, SaveFileProgress(local.cfi, local.percentage))
        dao.markSynced(fileId)
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
