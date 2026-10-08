package com.bookorbit.feature.reader

import com.bookorbit.core.db.AnnotationCacheDao
import com.bookorbit.core.db.AnnotationOp
import com.bookorbit.core.db.PendingAnnotationOpDao
import com.bookorbit.core.db.PendingAnnotationOpEntity
import com.bookorbit.core.model.BookAnnotation
import com.bookorbit.core.model.CreateAnnotation
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** In-memory stand-in for the Room DAO, so the queue's coalescing rules can be tested on the JVM. */
private class FakeOpDao : PendingAnnotationOpDao {
    val rows = mutableListOf<PendingAnnotationOpEntity>()
    private var nextId = 1L

    override suspend fun insert(op: PendingAnnotationOpEntity): Long {
        val id = nextId++
        rows += op.copy(id = id)
        return id
    }

    override suspend fun update(op: PendingAnnotationOpEntity) {
        val i = rows.indexOfFirst { it.id == op.id }
        if (i >= 0) rows[i] = op
    }

    override suspend fun all() = rows.sortedBy { it.id }
    override suspend fun forBook(bookId: Int) = all().filter { it.bookId == bookId }
    override suspend fun find(kind: String, annotationId: Int) =
        rows.firstOrNull { it.kind == kind && it.annotationId == annotationId }

    override suspend fun delete(id: Long) {
        rows.removeAll { it.id == id }
    }

    override suspend fun deleteFor(annotationId: Int, kinds: List<String>) {
        rows.removeAll { it.annotationId == annotationId && it.kind in kinds }
    }

    override suspend fun minCreateId() = rows.filter { it.kind == AnnotationOp.CREATE }.minOfOrNull { it.annotationId }
}

class ReaderAnnotationRepositoryTest {
    private lateinit var api: ApiService
    private lateinit var dao: FakeOpDao
    private lateinit var repo: ReaderAnnotationRepository

    @Before
    fun setUp() {
        api = mockk(relaxed = true)
        dao = FakeOpDao()
        repo = ReaderAnnotationRepository(api, dao, mockk<AnnotationCacheDao>(relaxed = true), Json, mockk<SyncScheduler>(relaxed = true))
    }

    private suspend fun createOne(text: String = "a line") =
        repo.create(bookId = 1, fileId = 9, cfi = "epubcfi(/6/2)", text = text, color = "#FACC15", note = null, chapterTitle = "I")

    @Test
    fun `a new highlight gets a negative placeholder id and each next one goes lower`() = runTest {
        createOne()
        createOne("another")
        assertEquals(listOf(-1, -2), dao.rows.map { it.annotationId })
    }

    @Test
    fun `editing an unsynced highlight folds into its create`() = runTest {
        createOne()
        repo.setColor(1, -1, "#4ADE80")
        repo.setNote(1, -1, "remember this")
        assertEquals(1, dao.rows.size)
        val op = dao.rows.single()
        assertEquals(AnnotationOp.CREATE, op.kind)
        assertEquals("#4ADE80", op.color)
        assertEquals("remember this", op.note)
        assertTrue(op.noteSet)
    }

    @Test
    fun `deleting an unsynced highlight cancels it entirely`() = runTest {
        createOne()
        repo.delete(1, -1)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `repeated edits to a synced highlight merge into one update`() = runTest {
        repo.setColor(1, 42, "#4ADE80")
        repo.setNote(1, 42, "n")
        assertEquals(1, dao.rows.size)
        val op = dao.rows.single()
        assertEquals(AnnotationOp.UPDATE, op.kind)
        assertEquals("#4ADE80", op.color)
        assertEquals("n", op.note)
    }

    @Test
    fun `deleting a synced highlight drops its pending edits and queues one delete`() = runTest {
        repo.setColor(1, 42, "#4ADE80")
        repo.delete(1, 42)
        repo.delete(1, 42)
        assertEquals(listOf(AnnotationOp.DELETE), dao.rows.map { it.kind })
    }

    @Test
    fun `flush sends queued ops in order and empties the queue`() = runTest {
        createOne()
        repo.setNote(1, 42, "n")
        val stillPending = repo.flushPending()
        assertFalse(stillPending)
        assertTrue(dao.rows.isEmpty())
        coVerify(exactly = 1) { api.createAnnotation(1, any<CreateAnnotation>()) }
        coVerify(exactly = 1) { api.updateAnnotation(1, 42, any()) }
    }

    @Test
    fun `flush keeps everything queued when offline`() = runTest {
        createOne()
        repo.delete(1, 42)
        coEvery { api.createAnnotation(any(), any()) } throws IOException("offline")
        assertTrue(repo.flushPending())
        assertEquals(2, dao.rows.size)
    }

    @Test
    fun `flush drops an op the server permanently rejects`() = runTest {
        repo.delete(1, 42)
        coEvery { api.deleteAnnotation(any(), any()) } throws HttpException(Response.error<Any>(404, "".toResponseBody()))
        assertFalse(repo.flushPending())
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `flush retries later on a server error`() = runTest {
        repo.delete(1, 42)
        coEvery { api.deleteAnnotation(any(), any()) } throws HttpException(Response.error<Any>(503, "".toResponseBody()))
        assertTrue(repo.flushPending())
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `overlay applies creates updates and deletes over the server copy`() {
        val base = listOf(
            BookAnnotation(id = 1, bookId = 1, cfi = "a", text = "one", color = "yellow"),
            BookAnnotation(id = 2, bookId = 1, cfi = "b", text = "two", color = "yellow", note = "old"),
            BookAnnotation(id = 3, bookId = 1, cfi = "c", text = "three"),
        )
        val ops = listOf(
            PendingAnnotationOpEntity(1, 1, AnnotationOp.CREATE, -1, cfi = "d", text = "four", color = "#4ADE80"),
            PendingAnnotationOpEntity(2, 1, AnnotationOp.UPDATE, 1, color = "#F472B6"),
            PendingAnnotationOpEntity(3, 1, AnnotationOp.UPDATE, 2, note = "", noteSet = true),
            PendingAnnotationOpEntity(4, 1, AnnotationOp.DELETE, 3),
        )
        val result = overlayPendingOps(base, ops)
        assertEquals(listOf(1, 2, -1), result.map { it.id })
        assertEquals("#F472B6", result[0].color)
        assertEquals(null, result[1].note)
        assertEquals("four", result[2].text)
    }
}
