package com.bookorbit.feature.reader

import android.util.Log
import com.bookorbit.core.auth.SessionManager
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
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
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
    private val api: ApiService,
    private val ops: PendingAnnotationOpDao,
    private val cache: AnnotationCacheDao,
    private val json: Json,
    private val syncScheduler: SyncScheduler,
    private val session: SessionManager,
) {
    private val listSerializer = ListSerializer(BookAnnotation.serializer())

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
    private val createdIds = mutableMapOf<Int, Int>()

    private val currentUserId: Int? get() = session.currentUser?.id

    private fun PendingAnnotationOpEntity.ownedBy(user: Int?) = this.userId == null || this.userId == user

    /** Highlights now: the server copy (fresh if reachable, else the cached one) plus queued changes. */
    suspend fun load(bookId: Int): List<BookAnnotation> {
        val fresh = runCatching { api.getAnnotations(bookId) }.getOrNull()
        if (fresh != null) cache.put(AnnotationCacheEntity(bookId, json.encodeToString(listSerializer, fresh)))
        return view(bookId, fresh ?: cached(bookId))
    }

    /** Highlights without touching the network: the cached server copy plus queued changes. */
    suspend fun local(bookId: Int): List<BookAnnotation> = view(bookId, cached(bookId))

    private suspend fun cached(bookId: Int): List<BookAnnotation> =
        cache.get(bookId)?.let { runCatching { json.decodeFromString(listSerializer, it.json) }.getOrNull() }.orEmpty()

    /** Only CFI-anchored annotations are usable in the EPUB-family reader (PDF ones carry a page rect). */
    private suspend fun view(bookId: Int, base: List<BookAnnotation>): List<BookAnnotation> =
        overlayPendingOps(base.filter { it.cfi != null }, ops.forBook(bookId).filter { it.ownedBy(currentUserId) })

    suspend fun create(
        bookId: Int,
        fileId: Int,
        cfi: String,
        text: String,
        color: String,
        note: String?,
        chapterTitle: String?,
    ) {
        queueLock.withLock {
            // Placeholder ids are negative and descending so they never clash with a server id.
            val tempId = minOf(ops.minCreateId() ?: 0, createdIds.keys.minOrNull() ?: 0, 0) - 1
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
                    userId = currentUserId,
                ),
            )
        }
        syncScheduler.schedule()
    }

    suspend fun setColor(bookId: Int, id: Int, color: String) {
        patch(bookId, id) { it.copy(color = color) }
    }

    /** A blank note clears it. */
    suspend fun setNote(bookId: Int, id: Int, note: String) {
        patch(bookId, id) { it.copy(note = note, noteSet = true) }
    }

    private suspend fun patch(bookId: Int, id: Int, change: (PendingAnnotationOpEntity) -> PendingAnnotationOpEntity) {
        queueLock.withLock {
            val create = if (id < 0) ops.find(AnnotationOp.CREATE, id) else null
            if (create != null) {
                // Not on the server yet: fold the edit into the queued create.
                ops.update(change(create))
            } else {
                serverId(id)?.let { queueUpdate(bookId, it, currentUserId, change) }
            }
        }
        syncScheduler.schedule()
    }

    suspend fun delete(bookId: Int, id: Int) {
        queueLock.withLock {
            if (id < 0 && ops.find(AnnotationOp.CREATE, id) != null) {
                // Never reached the server: just cancel the create.
                ops.deleteFor(id, listOf(AnnotationOp.CREATE))
            } else {
                serverId(id)?.let { queueDelete(bookId, it, currentUserId) }
            }
        }
        syncScheduler.schedule()
    }

    /** The server id for an id the reader shows: a placeholder maps to its highlight once created. */
    private fun serverId(id: Int): Int? = if (id < 0) createdIds[id] else id

    private suspend fun queueUpdate(
        bookId: Int,
        id: Int,
        userId: Int?,
        change: (PendingAnnotationOpEntity) -> PendingAnnotationOpEntity,
    ) {
        val existing = ops.find(AnnotationOp.UPDATE, id)
        if (existing != null) ops.update(change(existing))
        else ops.insert(change(PendingAnnotationOpEntity(bookId = bookId, kind = AnnotationOp.UPDATE, annotationId = id, userId = userId)))
    }

    private suspend fun queueDelete(bookId: Int, id: Int, userId: Int?) {
        ops.deleteFor(id, listOf(AnnotationOp.UPDATE))
        if (ops.find(AnnotationOp.DELETE, id) == null) {
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
        val user = currentUserId ?: return@withLock false
        var op = mine(user).firstOrNull()
        while (op != null) {
            try {
                val sent = send(op)
                settle(op, if (op.kind == AnnotationOp.CREATE) sent?.id else null)
                cacheSent(op, sent)
            } catch (e: HttpException) {
                // A permanent rejection won't succeed on retry; 404 means it's already gone.
                if (isPermanentRejection(e.code())) {
                    Log.w(TAG, "dropping ${op.kind} ${op.annotationId}: HTTP ${e.code()}")
                    ops.delete(op.id)
                    if (e.code() == 404 && op.kind != AnnotationOp.CREATE) dropFromCache(op.bookId, op.annotationId)
                } else {
                    return@withLock true
                }
            } catch (e: Exception) {
                // Offline (or server down): leave the rest queued, in order.
                return@withLock true
            }
            op = mine(user).firstOrNull()
        }
        false
    }

    private suspend fun mine(user: Int) = ops.all().filter { it.ownedBy(user) }

    /**
     * [op] has reached the server; [createdId] is the id of the highlight it created, if it was a create.
     * The row is dropped, unless the highlight was edited or deleted while the request was in flight:
     * that change was merged into the row after [op] was read, so it hasn't been sent yet. An edited
     * update stays queued and goes out again with its new values. A create's later changes become an
     * update, or a delete, of the highlight the server has just made.
     */
    private suspend fun settle(op: PendingAnnotationOpEntity, createdId: Int?) = queueLock.withLock {
        val now = ops.get(op.id)
        if (op.kind == AnnotationOp.CREATE && createdId != null) {
            createdIds[op.annotationId] = createdId
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
    private suspend fun cacheSent(op: PendingAnnotationOpEntity, sent: BookAnnotation?) {
        when (op.kind) {
            AnnotationOp.CREATE, AnnotationOp.UPDATE -> if (sent != null) {
                updateCache(op.bookId) { list ->
                    if (list.any { it.id == sent.id }) list.map { if (it.id == sent.id) sent else it } else list + sent
                }
            }
            AnnotationOp.DELETE -> dropFromCache(op.bookId, op.annotationId)
        }
    }

    private suspend fun dropFromCache(bookId: Int, id: Int) = updateCache(bookId) { list -> list.filterNot { it.id == id } }

    private suspend fun updateCache(bookId: Int, change: (List<BookAnnotation>) -> List<BookAnnotation>) {
        runCatching {
            val list = cached(bookId)
            val updated = change(list)
            if (updated != list) cache.put(AnnotationCacheEntity(bookId, json.encodeToString(listSerializer, updated)))
        }.onFailure { Log.w(TAG, "cached highlights of book $bookId not updated", it) }
    }

    /** Sends [op]. Returns the server's copy of the highlight it created or changed (null for a delete). */
    private suspend fun send(op: PendingAnnotationOpEntity): BookAnnotation? = when (op.kind) {
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
        )
        AnnotationOp.UPDATE -> api.updateAnnotation(
            op.bookId,
            op.annotationId,
            buildJsonObject {
                op.color?.let { put("color", JsonPrimitive(it)) }
                // A blank note is sent as an explicit null, which the server accepts to clear it.
                if (op.noteSet) put("note", if (op.note.isNullOrBlank()) JsonNull else JsonPrimitive(op.note))
            },
        )
        AnnotationOp.DELETE -> {
            api.deleteAnnotation(op.bookId, op.annotationId)
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
