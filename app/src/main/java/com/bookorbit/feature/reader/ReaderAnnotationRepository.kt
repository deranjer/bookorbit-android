package com.bookorbit.feature.reader

import android.util.Log
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.auth.SessionOwner
import com.bookorbit.core.db.AnnotationCacheDao
import com.bookorbit.core.db.AnnotationCacheEntity
import com.bookorbit.core.db.AnnotationOp
import com.bookorbit.core.db.PendingAnnotationOpDao
import com.bookorbit.core.db.PendingAnnotationOpEntity
import com.bookorbit.core.model.BookAnnotation
import com.bookorbit.core.model.CreateAnnotation
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.sync.SyncScheduler
import com.bookorbit.core.sync.isPermanentRejection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Highlights and notes for a book, local-first: every change is written to a Room queue and shown
 * straight away, then replayed to the server by [flushPending] (immediately when online, or from the
 * sync worker once connectivity returns). The last server copy is cached so a book's highlights show
 * offline too, and each change the server accepts goes into it (see [cacheSent]).
 *
 * Queued changes are coalesced so the server sees as few calls as possible: edits to a highlight that
 * hasn't been created yet go into its create; deleting it cancels the create; repeated edits merge.
 * A change made while the row it merges into is being sent isn't lost: see [settle].
 *
 * Each change belongs to the account that made it and waits, without being dropped, while that account
 * is signed out.
 */
@Singleton
class ReaderAnnotationRepository @Inject constructor(
    @com.bookorbit.core.network.AnnotationApi private val api: ApiService,
    private val ops: PendingAnnotationOpDao,
    private val cache: AnnotationCacheDao,
    private val json: Json,
    private val syncScheduler: SyncScheduler,
    private val session: SessionManager,
) {
    private val cacheLock = Mutex()
    private val cacheEpochs = mutableMapOf<Pair<Int, String>, Long>()

    /**
     * The reader and the sync worker both flush. One flush at a time, so two of them can't read the
     * same queued create and send it twice (which would make a duplicate highlight on the server).
     */
    private val flushLock = Mutex()

    /**
     * Changes to the queue, and the bookkeeping after each send, go through this lock. A flush doesn't
     * hold it while waiting for the server, so the reader can keep queueing edits during a send.
     */
    private val queueLock = Mutex()

    /**
     * Placeholder id -> server id, for the highlights this process has created on the server. The
     * reader shows the placeholder until it reloads, so an edit or a delete made in between is sent to
     * the new highlight. Guarded by [queueLock].
     */
    private data class Placeholder(val server: String, val userId: Int, val id: Int)
    private val createdIds = mutableMapOf<Placeholder, Int>()

    private fun owner(expected: SessionOwner? = null): SessionOwner? = (expected ?: session.owner())
        ?.takeIf { session.owns(it) && !session.dataScopeChanged() }

    /** Highlights now: the server copy (fresh if reachable, else the cached one) plus queued changes. */
    suspend fun load(bookId: Int, fileId: Int, expectedOwner: SessionOwner? = null): List<BookAnnotation> {
        require(fileId > 0)
        val owner = owner(expectedOwner) ?: return emptyList()
        val key = bookId to annotationOwnerPrefix(owner)
        val epoch = cacheLock.withLock { cacheEpochs[key] ?: 0L }
        val fresh = try { api.getAnnotations(bookId, fileId, owner).also { rows -> require(rows.all { it.bookId == bookId }) } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
        if (this.owner(owner) == null) return emptyList()
        val stored = if (fresh != null) changeCache(bookId, expectedEpoch = key.second to epoch) { snapshot ->
            snapshot.copy(scopes = snapshot.scopes + (annotationScope(owner, fileId) to fresh))
        } else false
        return view(bookId, fileId, owner, if (fresh != null && stored == null) fresh else cached(bookId, fileId, owner))
    }

    /** Highlights without touching the network: the cached server copy plus queued changes. */
    suspend fun local(bookId: Int, fileId: Int, expectedOwner: SessionOwner? = null): List<BookAnnotation> {
        require(fileId > 0)
        val owner = owner(expectedOwner) ?: return emptyList()
        return view(bookId, fileId, owner, cached(bookId, fileId, owner))
    }

    suspend fun hasUnownedData(bookId: Int): Boolean = ops.unownedCount(bookId) > 0 ||
        decodeAnnotationCache(cache.get(bookId)?.json, json).legacyJson != null

    private suspend fun cached(bookId: Int, fileId: Int, owner: SessionOwner): List<BookAnnotation> =
        decodeAnnotationCache(cache.get(bookId)?.json, json).scopes[annotationScope(owner, fileId)].orEmpty()

    /** Only CFI-anchored annotations are usable in the EPUB-family reader (PDF ones carry a page rect). */
    private suspend fun view(bookId: Int, fileId: Int, owner: SessionOwner, base: List<BookAnnotation>): List<BookAnnotation> {
        val pending = ops.forOwnedBook(bookId, owner.userId).filter { it.kind != AnnotationOp.CREATE || it.bookFileId == fileId }
        if (this.owner(owner) == null) return emptyList()
        return overlayPendingOps(base.filter { it.bookId == bookId && it.cfi != null && it.positionStatus != "failed" &&
            (it.jumpFileId == null || it.jumpFileId == fileId) }, pending)
    }

    suspend fun create(
        bookId: Int,
        fileId: Int,
        cfi: String,
        text: String,
        color: String,
        note: String?,
        chapterTitle: String?,
        expectedOwner: SessionOwner? = null,
    ) {
        val owner = requireNotNull(owner(expectedOwner))
        queueLock.withLock {
            require(this.owner(owner) != null)
            // Placeholder ids are negative and descending so they never clash with a server id.
            val tempId = minOf(ops.minCreateId() ?: 0, createdIds.keys.minOfOrNull { it.id } ?: 0, 0) - 1
            ops.insert(
                PendingAnnotationOpEntity(
                    bookId = bookId,
                    kind = AnnotationOp.CREATE,
                    annotationId = tempId,
                    cfi = cfi,
                    text = text,
                    color = color,
                    note = note,
                    noteSet = note != null,
                    chapterTitle = chapterTitle,
                    bookFileId = fileId,
                    userId = owner.userId,
                ),
            )
        }
        syncScheduler.schedule()
    }

    suspend fun setColor(bookId: Int, id: Int, color: String, expectedOwner: SessionOwner? = null) {
        patch(bookId, id, expectedOwner) { it.copy(color = color) }
    }

    /** A blank note clears it. */
    suspend fun setNote(bookId: Int, id: Int, note: String, expectedOwner: SessionOwner? = null) {
        patch(bookId, id, expectedOwner) { it.copy(note = note, noteSet = true) }
    }

    private suspend fun patch(bookId: Int, id: Int, expectedOwner: SessionOwner?, change: (PendingAnnotationOpEntity) -> PendingAnnotationOpEntity) {
        val owner = requireNotNull(owner(expectedOwner))
        queueLock.withLock {
            require(this.owner(owner) != null)
            val create = if (id < 0) ops.findOwned(AnnotationOp.CREATE, id, owner.userId)?.takeIf { it.bookId == bookId } else null
            if (create != null) {
                // Not on the server yet: fold the edit into the queued create.
                ops.update(change(create))
            } else {
                serverId(id, owner)?.let { queueUpdate(bookId, it, owner.userId, change) }
            }
        }
        syncScheduler.schedule()
    }

    suspend fun delete(bookId: Int, id: Int, expectedOwner: SessionOwner? = null) {
        val owner = requireNotNull(owner(expectedOwner))
        queueLock.withLock {
            require(this.owner(owner) != null)
            if (id < 0 && ops.findOwned(AnnotationOp.CREATE, id, owner.userId)?.bookId == bookId) {
                // Never reached the server: just cancel the create.
                ops.deleteOwned(id, listOf(AnnotationOp.CREATE), owner.userId)
            } else {
                serverId(id, owner)?.let { queueDelete(bookId, it, owner.userId) }
            }
        }
        syncScheduler.schedule()
    }

    /** The server id for an id the reader shows: a placeholder maps to its highlight once created. */
    private fun serverId(id: Int, owner: SessionOwner): Int? = if (id < 0) createdIds[Placeholder(owner.serverUrl, owner.userId, id)] else id

    private suspend fun queueUpdate(
        bookId: Int,
        id: Int,
        userId: Int?,
        change: (PendingAnnotationOpEntity) -> PendingAnnotationOpEntity,
    ) {
        if (userId == null) return
        val existing = ops.findOwned(AnnotationOp.UPDATE, id, userId)
        if (existing != null) ops.update(change(existing))
        else ops.insert(change(PendingAnnotationOpEntity(bookId = bookId, kind = AnnotationOp.UPDATE, annotationId = id, userId = userId)))
    }

    private suspend fun queueDelete(bookId: Int, id: Int, userId: Int?) {
        if (userId == null) return
        ops.deleteOwned(id, listOf(AnnotationOp.UPDATE), userId)
        if (ops.findOwned(AnnotationOp.DELETE, id, userId) == null) {
            ops.insert(PendingAnnotationOpEntity(bookId = bookId, kind = AnnotationOp.DELETE, annotationId = id, userId = userId))
        }
    }

    /**
     * Replay the signed-in account's queued changes in order. Returns true if any of them remain (so the
     * caller retries later). Changes of an account that isn't signed in wait for its next sign-in, which
     * schedules a new flush, so they don't count. The queue is read again after each send, so changes
     * made during the flush go out in it too.
     */
    suspend fun flushPending(): Boolean = flushLock.withLock {
        val owner = owner() ?: return@withLock false
        val user = owner.userId
        var op = ops.firstOwned(user)
        while (op != null) {
            if (this.owner(owner) == null) return@withLock false
            try {
                val sent = send(op, owner)
                if (op.kind == AnnotationOp.CREATE || op.kind == AnnotationOp.UPDATE) {
                    require(sent != null && sent.bookId == op.bookId && sent.id > 0)
                    if (op.kind == AnnotationOp.UPDATE) require(sent.id == op.annotationId)
                }
                settle(op, if (op.kind == AnnotationOp.CREATE) sent?.id else null, owner)
                cacheSent(op, sent, owner)
            } catch (error: CancellationException) {
                throw error
            } catch (e: HttpException) {
                if (this.owner(owner) == null) return@withLock false
                // A permanent rejection won't succeed on retry; 404 means it's already gone.
                if (isPermanentRejection(e.code())) {
                    Log.w(TAG, "dropping ${op.kind} ${op.annotationId}: HTTP ${e.code()}")
                    ops.delete(op.id)
                    if (e.code() == 404 && op.kind != AnnotationOp.CREATE) dropFromCache(op.bookId, op.annotationId, owner)
                } else {
                    return@withLock true
                }
            } catch (e: Exception) {
                // Offline (or server down): leave the rest queued, in order.
                return@withLock this.owner(owner) != null
            }
            op = ops.firstOwned(user)
        }
        false
    }

    /**
     * [op] has reached the server; [createdId] is the id of the highlight it created, if it was a create.
     * The row is dropped, unless the highlight was edited or deleted while the request was in flight:
     * that change was merged into the row after [op] was read, so it hasn't been sent yet. An edited
     * update stays queued and goes out again with its new values. A create's later changes become an
     * update, or a delete, of the highlight the server has just made.
     */
    private suspend fun settle(op: PendingAnnotationOpEntity, createdId: Int?, owner: SessionOwner) = queueLock.withLock {
        val now = ops.get(op.id)
        if (op.kind == AnnotationOp.CREATE && createdId != null) {
            createdIds[Placeholder(owner.serverUrl, owner.userId, op.annotationId)] = createdId
            if (now == null) {
                queueDelete(op.bookId, createdId, op.userId)
                return@withLock
            }
            ops.delete(op.id)
            val colorChanged = now.color != op.color
            val noteChanged = now.noteSet != op.noteSet || now.note != op.note
            if (colorChanged || noteChanged) {
                queueUpdate(op.bookId, createdId, op.userId) {
                    it.copy(
                        color = if (colorChanged) now.color else it.color,
                        note = if (noteChanged) now.note else it.note,
                        noteSet = it.noteSet || noteChanged,
                    )
                }
            }
        } else if (now == op) {
            ops.delete(op.id)
        }
    }

    /**
     * Once [op] has reached the server, its row leaves the queue, so the reader's view (cached server copy
     * plus queued changes) would lose it until the next successful [load]: a sent highlight would vanish
     * offline, a sent edit would revert. So the cached copy takes what the server answered. Best effort: a
     * failure here must not make a successful send look failed (it would be sent again).
     */
    private suspend fun cacheSent(op: PendingAnnotationOpEntity, sent: BookAnnotation?, owner: SessionOwner) {
        when (op.kind) {
            AnnotationOp.CREATE -> if (sent != null && op.bookFileId != null && sent.bookId == op.bookId) {
                changeCache(op.bookId, mutationOwner = owner) { snapshot ->
                    val key = annotationScope(owner, op.bookFileId)
                    val list = snapshot.scopes[key].orEmpty()
                    snapshot.copy(scopes = snapshot.scopes + (key to (list.filterNot { it.id == sent.id } + sent)))
                }
            }
            AnnotationOp.UPDATE -> if (sent != null && sent.bookId == op.bookId) updateOwnedCache(op.bookId, owner) { list ->
                // A PATCH response has canonical coordinates, not each reader edition's projection.
                list.map { if (it.id == sent.id) it.copy(color = sent.color, note = sent.note, style = sent.style) else it }
            }
            AnnotationOp.DELETE -> dropFromCache(op.bookId, op.annotationId, owner)
        }
    }

    private suspend fun dropFromCache(bookId: Int, id: Int, owner: SessionOwner) = updateOwnedCache(bookId, owner) { list -> list.filterNot { it.id == id } }

    private suspend fun updateOwnedCache(bookId: Int, owner: SessionOwner, change: (List<BookAnnotation>) -> List<BookAnnotation>) = changeCache(bookId, mutationOwner = owner) { snapshot ->
        val prefix = annotationOwnerPrefix(owner)
        snapshot.copy(scopes = snapshot.scopes.mapValues { (key, rows) -> if (key.startsWith(prefix)) change(rows) else rows })
    }

    private suspend fun changeCache(bookId: Int, mutationOwner: SessionOwner? = null, expectedEpoch: Pair<String, Long>? = null,
        change: (ScopedAnnotationCache) -> ScopedAnnotationCache): Boolean? {
        return try {
            cacheLock.withLock {
                if (expectedEpoch != null && (cacheEpochs[bookId to expectedEpoch.first] ?: 0L) != expectedEpoch.second) return@withLock false
                mutationOwner?.let { owner ->
                    val key = bookId to annotationOwnerPrefix(owner)
                    cacheEpochs[key] = (cacheEpochs[key] ?: 0) + 1
                }
                val current = decodeAnnotationCache(cache.get(bookId)?.json, json)
                val updated = change(current)
                if (updated != current) cache.put(AnnotationCacheEntity(bookId, json.encodeToString(ScopedAnnotationCache.serializer(), updated)))
                true
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { Log.w(TAG, "cached highlights of book $bookId not updated", error); null }
    }

    /** Sends [op]. Returns the server's copy of the highlight it created or changed (null for a delete). */
    private suspend fun send(op: PendingAnnotationOpEntity, owner: SessionOwner): BookAnnotation? = when (op.kind) {
        AnnotationOp.CREATE -> api.createAnnotation(
            op.bookId,
            CreateAnnotation(
                cfi = requireNotNull(op.cfi),
                bookFileId = requireNotNull(op.bookFileId),
                text = op.text.orEmpty(),
                color = op.color ?: COLORS.first().second,
                style = DEFAULT_STYLE,
                note = op.note?.takeIf { op.noteSet && it.isNotBlank() },
                chapterTitle = op.chapterTitle,
            ),
            owner,
        )
        AnnotationOp.UPDATE -> api.updateAnnotation(
            op.bookId,
            op.annotationId,
            buildJsonObject {
                op.color?.let { put("color", JsonPrimitive(it)) }
                // A blank note is sent as an explicit null, which the server accepts to clear it.
                if (op.noteSet) put("note", if (op.note.isNullOrBlank()) JsonNull else JsonPrimitive(op.note))
            },
            owner,
        )
        AnnotationOp.DELETE -> {
            api.deleteAnnotation(op.bookId, op.annotationId, owner)
            null
        }
        else -> null
    }

    companion object {
        private const val TAG = "Annotations"
        const val DEFAULT_STYLE = "highlight"

        /** Same hues as the web reader's default highlight (`#FACC15`) and its siblings. */
        val COLORS: List<Pair<String, String>> = listOf(
            "yellow" to "#FACC15",
            "green" to "#4ADE80",
            "blue" to "#38BDF8",
            "pink" to "#F472B6",
            "orange" to "#FB923C",
        )
    }
}
