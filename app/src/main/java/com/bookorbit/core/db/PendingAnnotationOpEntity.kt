package com.bookorbit.core.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

/** What a queued highlight change does. */
object AnnotationOp {
    const val CREATE = "create"
    const val UPDATE = "update"
    const val DELETE = "delete"
}

/**
 * A highlight change made while offline (or that failed to reach the server), replayed in order by
 * the sync worker. Ops are coalesced as they are queued (see [ReaderAnnotationRepository]), so at most
 * one create, one update and one delete exist per highlight.
 *
 * For a CREATE, [annotationId] is a negative placeholder id used by the UI until the server assigns a
 * real one; for UPDATE and DELETE it is the server id.
 */
@Entity(tableName = "pending_annotation_ops")
data class PendingAnnotationOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Int,
    val kind: String,
    val annotationId: Int,
    val cfi: String? = null,
    val text: String? = null,
    val color: String? = null,
    /** New note text. Only meaningful when [noteSet] (a blank note clears it). */
    val note: String? = null,
    val noteSet: Boolean = false,
    val chapterTitle: String? = null,
    val bookFileId: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface PendingAnnotationOpDao {
    @Insert
    suspend fun insert(op: PendingAnnotationOpEntity): Long

    @Update
    suspend fun update(op: PendingAnnotationOpEntity)

    @Query("SELECT * FROM pending_annotation_ops ORDER BY id ASC")
    suspend fun all(): List<PendingAnnotationOpEntity>

    @Query("SELECT * FROM pending_annotation_ops WHERE bookId = :bookId ORDER BY id ASC")
    suspend fun forBook(bookId: Int): List<PendingAnnotationOpEntity>

    @Query("SELECT * FROM pending_annotation_ops WHERE kind = :kind AND annotationId = :annotationId LIMIT 1")
    suspend fun find(kind: String, annotationId: Int): PendingAnnotationOpEntity?

    @Query("DELETE FROM pending_annotation_ops WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_annotation_ops WHERE annotationId = :annotationId AND kind IN (:kinds)")
    suspend fun deleteFor(annotationId: Int, kinds: List<String>)

    /** The lowest placeholder id in use, so the next one can go below it. */
    @Query("SELECT MIN(annotationId) FROM pending_annotation_ops WHERE kind = 'create'")
    suspend fun minCreateId(): Int?
}

/** Last server copy of a book's highlights, so they still show (and can be extended) offline. */
@Entity(tableName = "annotation_cache")
data class AnnotationCacheEntity(
    @PrimaryKey val bookId: Int,
    val json: String,
)

@Dao
interface AnnotationCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: AnnotationCacheEntity)

    @Query("SELECT * FROM annotation_cache WHERE bookId = :bookId")
    suspend fun get(bookId: Int): AnnotationCacheEntity?
}
