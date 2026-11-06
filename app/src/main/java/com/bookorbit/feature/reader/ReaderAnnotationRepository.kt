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
 * offline too.
 *
 * Queued changes are coalesced so the server sees as few calls as possible: edits to a highlight that
 * hasn't been created yet go into its create; deleting it cancels the create; repeated edits merge.
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
        // Placeholder ids are negative and descending so they never clash with a server id.
        val tempId = (ops.minCreateId() ?: 0).coerceAtMost(0) - 1
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
        if (id < 0) {
            // Not on the server yet: fold the edit into the queued create.
            ops.find(AnnotationOp.CREATE, id)?.let { ops.update(change(it)) }
        } else {
            val existing = ops.find(AnnotationOp.UPDATE, id)
            if (existing != null) ops.update(change(existing))
            else ops.insert(change(PendingAnnotationOpEntity(bookId = bookId, kind = AnnotationOp.UPDATE, annotationId = id, userId = currentUserId)))
        }
        syncScheduler.schedule()
    }

    suspend fun delete(bookId: Int, id: Int) {
        if (id < 0) {
            // Never reached the server: just cancel the create.
            ops.deleteFor(id, listOf(AnnotationOp.CREATE))
        } else {
            ops.deleteFor(id, listOf(AnnotationOp.UPDATE))
            if (ops.find(AnnotationOp.DELETE, id) == null) {
                ops.insert(PendingAnnotationOpEntity(bookId = bookId, kind = AnnotationOp.DELETE, annotationId = id, userId = currentUserId))
            }
        }
        syncScheduler.schedule()
    }

    /**
     * Replay the signed-in account's queued changes in order. Returns true if any of them remain (so the
     * caller retries later). Changes of an account that isn't signed in wait for its next sign-in, which
     * schedules a new flush, so they don't count.
     */
    suspend fun flushPending(): Boolean = flushLock.withLock {
        val user = currentUserId ?: return@withLock false
        for (op in mine(user)) {
            try {
                send(op)
                ops.delete(op.id)
            } catch (e: HttpException) {
                // A permanent rejection won't succeed on retry; 404 means it's already gone.
                if (isPermanentRejection(e.code())) {
                    Log.w(TAG, "dropping ${op.kind} ${op.annotationId}: HTTP ${e.code()}")
                    ops.delete(op.id)
                } else {
                    return@withLock true
                }
            } catch (e: Exception) {
                // Offline (or server down): leave the rest queued, in order.
                return@withLock true
            }
        }
        mine(user).isNotEmpty()
    }

    private suspend fun mine(user: Int) = ops.all().filter { it.ownedBy(user) }

    private suspend fun send(op: PendingAnnotationOpEntity) {
        when (op.kind) {
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
            AnnotationOp.DELETE -> api.deleteAnnotation(op.bookId, op.annotationId)
        }
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
