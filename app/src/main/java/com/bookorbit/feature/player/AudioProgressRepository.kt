package com.bookorbit.feature.player

import android.util.Log
import com.bookorbit.core.db.AudioProgressDao
import com.bookorbit.core.db.AudioProgressEntity
import com.bookorbit.core.model.AudioProgress
import com.bookorbit.core.model.AudiobookPlaybackState
import com.bookorbit.core.model.PutAudiobookPlaybackState
import com.bookorbit.core.model.SaveAudioProgress
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.network.AudiobookAssetResolver
import com.bookorbit.core.network.AudiobookAssetResolver.Sources
import com.bookorbit.core.sync.SyncScheduler
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** A resume position and when it was captured (epoch millis). */
data class ResumePoint(val progress: AudioProgress, val lastActivityMillis: Long)

/**
 * Offline-first audiobook position store keyed by book id, backed by Room (local-first write +
 * dirty flag + flush). Syncs through `/audiobooks/{id}/playback-state` on server 3.0+ (shared with
 * the web player) and the legacy `/books/{id}/audio-progress` on older servers.
 */
@Singleton
class AudioProgressRepository @Inject constructor(
    private val dao: AudioProgressDao,
    private val api: ApiService,
    private val assets: AudiobookAssetResolver,
    private val json: Json,
    private val syncScheduler: SyncScheduler,
) {
    /** Last playback-state revision seen per book - the PUT's optimistic-concurrency base. */
    private val revisions = ConcurrentHashMap<Int, Int>()

    suspend fun report(bookId: Int, currentFileId: Int, positionSeconds: Double, percentage: Double) {
        val entity = AudioProgressEntity(bookId, currentFileId, positionSeconds, percentage, System.currentTimeMillis(), dirty = true)
        dao.upsert(entity)
        if (push(entity)) dao.markSynced(bookId) else syncScheduler.schedule()
    }

    /** Push every dirty position to the server. Returns true if anything is still dirty afterward. */
    suspend fun flushPending(): Boolean {
        for (e in dao.dirtyEntries()) {
            if (push(e)) dao.markSynced(e.bookId)
        }
        return dao.dirtyEntries().isNotEmpty()
    }

    /** Saved positions, most recently played first (Android Auto "Continue listening" shelf). */
    suspend fun recent(): List<AudioProgressEntity> = dao.recent()

    /**
     * Resume position: retries any pending local write first, then picks whichever of local/server
     * is actually newer by timestamp (true last-write-wins, matching the server's own merge logic),
     * falling back to local if the server is unreachable and to server if there's no local row.
     */
    suspend fun resolveResume(bookId: Int): AudioProgress? = resumePoint(bookId)?.progress

    /** This device's saved position only - no network. Fallback when the server is slow or offline. */
    suspend fun localResumePoint(bookId: Int): ResumePoint? =
        dao.get(bookId)?.let { ResumePoint(it.toAudioProgress(), it.updatedAt) }

    /**
     * [resolveResume]'s pick plus when it was captured (epoch millis; 0 when the server gave no
     * timestamp), so callers can tell which of several books was listened to most recently.
     */
    suspend fun resumePoint(bookId: Int): ResumePoint? {
        runCatching { flushPending() }
        val local = dao.get(bookId)
        val server = runCatching { serverProgress(bookId) }.getOrNull()
        val serverMillis = server?.updatedAt?.let(::epochMillis)
        return when {
            local == null -> server?.let { ResumePoint(it, serverMillis ?: 0L) }
            server == null -> ResumePoint(local.toAudioProgress(), local.updatedAt)
            serverMillis != null && serverMillis > local.updatedAt -> ResumePoint(server, serverMillis)
            else -> ResumePoint(local.toAudioProgress(), local.updatedAt)
        }
    }

    /**
     * True when [e] no longer needs sending: saved, or permanently unsendable (unmappable file,
     * rejected position) or superseded by a newer server write - retrying those would keep the
     * row dirty forever. False on transient failures, to retry later.
     */
    private suspend fun push(e: AudioProgressEntity): Boolean = runCatching {
        when (val sources = assets.forBook(e.bookId)) {
            Sources.Legacy -> {
                api.saveAudioProgress(e.bookId, SaveAudioProgress(e.currentFileId, e.positionSeconds, e.percentage))
                true
            }
            is Sources.Assets -> putPlaybackState(e, sources, retriesLeft = 1)
        }
    }.onFailure { Log.w(TAG, "progress sync failed bookId=${e.bookId}: ${it.message}") }.getOrDefault(false)

    private suspend fun putPlaybackState(e: AudioProgressEntity, sources: Sources.Assets, retriesLeft: Int): Boolean {
        val assetId = sources.assetIds[e.currentFileId] ?: run {
            Log.w(TAG, "no manifest asset for bookId=${e.bookId} fileId=${e.currentFileId}; dropping position")
            return true
        }
        val maxMs = sources.assetDurationsMs[assetId] ?: Long.MAX_VALUE
        val body = PutAudiobookPlaybackState(
            assetId = assetId,
            positionMs = (e.positionSeconds * 1000).toLong().coerceIn(0, maxMs),
            capturedAt = Instant.ofEpochMilli(e.updatedAt).toString(),
            operationId = UUID.randomUUID().toString(),
            baseRevision = revisions[e.bookId] ?: 0,
            manifestRevision = sources.manifestRevision,
        )
        try {
            revisions[e.bookId] = api.putPlaybackState(e.bookId, body).revision
            return true
        } catch (ex: HttpException) {
            when (ex.code()) {
                // Stale base revision (another device/web wrote, or first write this session):
                // re-read, and only overwrite if our position is the newer one.
                409 -> {
                    val server = fetchPlaybackState(e.bookId)
                    revisions[e.bookId] = server?.revision ?: 0
                    val serverMillis = server?.capturedAt?.let(::epochMillis)
                    if (serverMillis != null && serverMillis >= e.updatedAt) return true
                }
                // Manifest changed (files added/removed): re-map and retry.
                412 -> {
                    val refreshed = assets.forBook(e.bookId, refresh = true)
                    if (refreshed !is Sources.Assets) return false
                    return retriesLeft > 0 && putPlaybackState(e, refreshed, retriesLeft - 1)
                }
                400, 404 -> {
                    Log.w(TAG, "server rejected position bookId=${e.bookId} (HTTP ${ex.code()}); dropping it")
                    return true
                }
                else -> throw ex
            }
            return retriesLeft > 0 && putPlaybackState(e, sources, retriesLeft - 1)
        }
    }

    private suspend fun serverProgress(bookId: Int): AudioProgress? =
        when (val sources = assets.forBook(bookId)) {
            Sources.Legacy -> api.getAudioProgress(bookId)
            is Sources.Assets -> {
                val state = fetchPlaybackState(bookId)
                state?.revision?.let { revisions[bookId] = it }
                val fileId = state?.let { sources.fileIdFor(it.assetId) }
                if (state == null || fileId == null) null
                else AudioProgress(fileId, state.positionMs / 1000.0, state.percentage, updatedAt = state.capturedAt)
            }
        }

    private suspend fun fetchPlaybackState(bookId: Int): AudiobookPlaybackState? {
        val text = api.getPlaybackState(bookId).use { it.string() }.trim()
        if (text.isEmpty() || text == "null") return null
        return json.decodeFromString(AudiobookPlaybackState.serializer(), text)
    }

    private fun epochMillis(iso: String): Long? = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()

    private fun AudioProgressEntity.toAudioProgress() =
        AudioProgress(currentFileId, positionSeconds, percentage, updatedAt = null)

    private companion object {
        const val TAG = "AudioProgressRepo"
    }
}
