package com.bookorbit.feature.reader

import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.auth.SessionOwner
import com.bookorbit.core.db.AnnotationCacheDao
import com.bookorbit.core.db.AnnotationCacheEntity
import com.bookorbit.core.db.AnnotationOp
import com.bookorbit.core.db.PendingAnnotationOpDao
import com.bookorbit.core.db.PendingAnnotationOpEntity
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.model.BookAnnotation
import com.bookorbit.core.model.CreateAnnotation
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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
    override suspend fun firstOwned(userId: Int) = all().firstOrNull { it.userId == userId && it.kind !in listOf(AnnotationOp.CREATE_UNCERTAIN, AnnotationOp.CREATE_UNCERTAIN_DELETE) }
    override suspend fun forBook(bookId: Int) = all().filter { it.bookId == bookId }
    override suspend fun forOwnedBook(bookId: Int, userId: Int) = all().filter { it.bookId == bookId && it.userId == userId }
    override suspend fun unownedCount(bookId: Int) = rows.count { it.bookId == bookId && it.userId == null }
    override suspend fun find(kind: String, annotationId: Int) =
        rows.firstOrNull { it.kind == kind && it.annotationId == annotationId }

    override suspend fun findOwned(kind: String, annotationId: Int, userId: Int) =
        rows.firstOrNull { it.kind == kind && it.annotationId == annotationId && it.userId == userId }

    override suspend fun get(id: Long) = rows.firstOrNull { it.id == id }

    override suspend fun delete(id: Long) {
        rows.removeAll { it.id == id }
    }

    override suspend fun deleteFor(annotationId: Int, kinds: List<String>) {
        rows.removeAll { it.annotationId == annotationId && it.kind in kinds }
    }

    override suspend fun deleteOwned(annotationId: Int, kinds: List<String>, userId: Int) {
        rows.removeAll { it.annotationId == annotationId && it.kind in kinds && it.userId == userId }
    }

    override suspend fun minCreateId() = rows.filter { it.kind in listOf(AnnotationOp.CREATE, AnnotationOp.CREATE_UNCERTAIN, AnnotationOp.CREATE_UNCERTAIN_DELETE) }.minOfOrNull { it.annotationId }
}

/** In-memory stand-in for the cached server copy of each book's highlights. */
private class FakeCacheDao : AnnotationCacheDao {
    val rows = mutableMapOf<Int, AnnotationCacheEntity>()

    override suspend fun put(entity: AnnotationCacheEntity) {
        rows[entity.bookId] = entity
    }

    override suspend fun get(bookId: Int) = rows[bookId]
}

class ReaderAnnotationRepositoryTest {
    private lateinit var api: ApiService
    private lateinit var dao: FakeOpDao
    private lateinit var cache: FakeCacheDao
    private lateinit var repo: ReaderAnnotationRepository
    private lateinit var session: SessionManager
    private var generation = 0L
    private var wrongDatabaseScope = false

    private val me = AuthUser(id = 1, username = "me")
    private val someoneElse = AuthUser(id = 2, username = "someone-else")

    /** Who [SessionManager.currentUser] reports; null means signed out. */
    private var signedIn: AuthUser? = me
        set(value) { field = value; generation++ }

    @Before
    fun setUp() {
        api = mockk(relaxed = true)
        dao = FakeOpDao()
        cache = FakeCacheDao()
        session = mockk<SessionManager>()
        every { session.currentUser } answers { signedIn }
        every { session.owner() } answers { signedIn?.let { SessionOwner("https://books.example", it.id, generation) } }
        every { session.owns(any()) } answers { firstArg<SessionOwner>() == session.owner() }
        every { session.dataScopeChanged() } answers { wrongDatabaseScope }
        coEvery { api.createAnnotation(any(), any(), any()) } coAnswers {
            val body = secondArg<CreateAnnotation>()
            BookAnnotation(7, firstArg(), body.cfi, body.text, body.color, body.style, body.note, body.chapterTitle)
        }
        coEvery { api.updateAnnotation(any(), any(), any(), any()) } coAnswers { onServer(secondArg()).copy(bookId = firstArg()) }
        repo = ReaderAnnotationRepository(api, dao, cache, Json, mockk<SyncScheduler>(relaxed = true), session)
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
        coVerify(exactly = 1) { api.createAnnotation(1, any<CreateAnnotation>(), any()) }
        coVerify(exactly = 1) { api.updateAnnotation(1, 42, any(), any()) }
    }

    @Test
    fun `unconfirmed create is preserved without an automatic retry`() = runTest {
        createOne()
        repo.delete(1, 42)
        coEvery { api.createAnnotation(any(), any(), any()) } throws IOException("offline")
        assertFalse(repo.flushPending())
        assertEquals(AnnotationOp.CREATE_UNCERTAIN, dao.rows.single().kind)
        assertFalse(repo.flushPending())
        coVerify(exactly = 1) { api.createAnnotation(any(), any(), any()) }
    }

    @Test
    fun `flush drops an op the server permanently rejects`() = runTest {
        repo.delete(1, 42)
        coEvery { api.deleteAnnotation(any(), any(), any()) } throws HttpException(Response.error<Any>(404, "".toResponseBody()))
        assertFalse(repo.flushPending())
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `flush retries later on a server error`() = runTest {
        repo.delete(1, 42)
        coEvery { api.deleteAnnotation(any(), any(), any()) } throws HttpException(Response.error<Any>(503, "".toResponseBody()))
        assertTrue(repo.flushPending())
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `flush keeps a change queued when the session has expired`() = runTest {
        repo.delete(1, 42)
        coEvery { api.deleteAnnotation(any(), any(), any()) } throws HttpException(Response.error<Any>(401, "".toResponseBody()))
        assertTrue(repo.flushPending())
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `flush sends nothing while signed out and keeps the queue`() = runTest {
        createOne()
        signedIn = null
        assertFalse(repo.flushPending())
        assertEquals(1, dao.rows.size)
        coVerify(exactly = 0) { api.createAnnotation(any(), any(), any()) }
    }

    @Test
    fun `a change waits for the account that made it`() = runTest {
        createOne()
        signedIn = someoneElse
        assertFalse(repo.flushPending())
        assertTrue(repo.local(1, 9).isEmpty())
        coVerify(exactly = 0) { api.createAnnotation(any(), any(), any()) }

        signedIn = me
        assertEquals(1, repo.local(1, 9).size)
        assertFalse(repo.flushPending())
        coVerify(exactly = 1) { api.createAnnotation(1, any<CreateAnnotation>(), any()) }
    }

    @Test
    fun `an unowned legacy change stays intact and is not assigned to whoever signs in`() = runTest {
        dao.insert(PendingAnnotationOpEntity(bookId = 1, kind = AnnotationOp.DELETE, annotationId = 42))
        signedIn = someoneElse
        assertFalse(repo.flushPending())
        assertEquals(1, dao.rows.size)
        coVerify(exactly = 0) { api.deleteAnnotation(any(), any(), any()) }
    }

    @Test
    fun `two flushes at the same time send a queued highlight once`() = runTest {
        createOne()
        coEvery { api.createAnnotation(any(), any(), any()) } coAnswers {
            delay(100)
            BookAnnotation(id = 7, bookId = 1, cfi = "epubcfi(/6/2)", text = "a line")
        }
        val reader = async { repo.flushPending() }
        val worker = async { repo.flushPending() }
        assertFalse(reader.await())
        assertFalse(worker.await())
        coVerify(exactly = 1) { api.createAnnotation(1, any<CreateAnnotation>(), any()) }
    }

    private fun onServer(id: Int) = BookAnnotation(id = id, bookId = 1, cfi = "epubcfi(/6/2)", text = "a line")

    @Test
    fun `an edit made while its highlight is being created goes out as an update of the new highlight`() = runTest {
        createOne()
        coEvery { api.createAnnotation(any(), any(), any()) } coAnswers {
            repo.setColor(1, -1, "#4ADE80")
            repo.setNote(1, -1, "remember this")
            onServer(7)
        }
        val bodies = mutableListOf<JsonObject>()
        coEvery { api.updateAnnotation(1, 7, capture(bodies), any()) } returns onServer(7)
        assertFalse(repo.flushPending())
        assertTrue(dao.rows.isEmpty())
        assertEquals(listOf(buildJsonObject { put("color", "#4ADE80"); put("note", "remember this") }), bodies)
    }

    @Test
    fun `deleting a highlight while it is being created deletes it on the server`() = runTest {
        createOne()
        coEvery { api.createAnnotation(any(), any(), any()) } coAnswers {
            repo.delete(1, -1)
            onServer(7)
        }
        assertFalse(repo.flushPending())
        assertTrue(dao.rows.isEmpty())
        coVerify(exactly = 1) { api.deleteAnnotation(1, 7, any()) }
    }

    @Test
    fun `an edit made while an update is being sent goes out after it`() = runTest {
        repo.setColor(1, 42, "#4ADE80")
        val bodies = mutableListOf<JsonObject>()
        coEvery { api.updateAnnotation(1, 42, capture(bodies), any()) } coAnswers {
            if (bodies.size == 1) repo.setNote(1, 42, "n")
            onServer(42)
        }
        assertFalse(repo.flushPending())
        assertTrue(dao.rows.isEmpty())
        assertEquals(2, bodies.size)
        assertEquals(JsonPrimitive("n"), bodies[1]["note"])
    }

    @Test
    fun `the placeholder id still on screen after the create was sent reaches the new highlight`() = runTest {
        createOne()
        coEvery { api.createAnnotation(any(), any(), any()) } returns onServer(7)
        assertFalse(repo.flushPending())

        // The reader hasn't reloaded yet, so it still shows the highlight under its placeholder id.
        repo.setColor(1, -1, "#F472B6")
        assertEquals(listOf(AnnotationOp.UPDATE to 7), dao.rows.map { it.kind to it.annotationId })
        repo.delete(1, -1)
        assertEquals(listOf(AnnotationOp.DELETE to 7), dao.rows.map { it.kind to it.annotationId })
        assertFalse(repo.flushPending())
        coVerify(exactly = 1) { api.deleteAnnotation(1, 7, any()) }
    }

    @Test
    fun `a placeholder id isn't given out again once its highlight is created`() = runTest {
        createOne()
        coEvery { api.createAnnotation(any(), any(), any()) } returns onServer(7)
        assertFalse(repo.flushPending())
        createOne("another")
        assertEquals(listOf(-2), dao.rows.map { it.annotationId })
    }

    private fun cacheOf(vararg annotations: BookAnnotation) {
        val snapshot = ScopedAnnotationCache(scopes = mapOf(annotationScope(session.owner()!!, 9) to annotations.toList()))
        cache.rows[1] = AnnotationCacheEntity(1, Json.encodeToString(ScopedAnnotationCache.serializer(), snapshot))
    }

    @Test
    fun `a sent highlight goes into the cached copy, so it still shows if the reload fails`() = runTest {
        cacheOf(onServer(3))
        createOne()
        coEvery { api.createAnnotation(any(), any(), any()) } returns onServer(7)
        assertFalse(repo.flushPending())
        assertEquals(listOf(3, 7), repo.local(1, 9).map { it.id })
    }

    @Test
    fun `a sent highlight shows even before the book's highlights were ever loaded`() = runTest {
        createOne()
        coEvery { api.createAnnotation(any(), any(), any()) } returns onServer(7)
        assertFalse(repo.flushPending())
        assertEquals(listOf(7), repo.local(1, 9).map { it.id })
    }

    @Test
    fun `a sent edit goes into the cached copy`() = runTest {
        cacheOf(onServer(42))
        repo.setColor(1, 42, "#4ADE80")
        coEvery { api.updateAnnotation(1, 42, any(), any()) } returns onServer(42).copy(color = "#4ADE80")
        assertFalse(repo.flushPending())
        assertTrue(dao.rows.isEmpty())
        assertEquals("#4ADE80", repo.local(1, 9).single().color)
    }

    @Test
    fun `a sent delete leaves the cached copy`() = runTest {
        cacheOf(onServer(42))
        repo.delete(1, 42)
        assertFalse(repo.flushPending())
        assertTrue(repo.local(1, 9).isEmpty())
    }

    @Test
    fun `a highlight already gone on the server leaves the cached copy`() = runTest {
        cacheOf(onServer(42))
        repo.delete(1, 42)
        coEvery { api.deleteAnnotation(any(), any(), any()) } throws HttpException(Response.error<Any>(404, "".toResponseBody()))
        assertFalse(repo.flushPending())
        assertTrue(repo.local(1, 9).isEmpty())
    }

    @Test fun `cache is private to both account and reader edition`() = runTest {
        val owner = session.owner()!!
        coEvery { api.getAnnotations(1, 9, any()) } returns listOf(onServer(42).copy(cfi = "edition-nine", jumpFileId = 9))
        coEvery { api.getAnnotations(1, 10, any()) } returns listOf(onServer(42).copy(cfi = "edition-ten", jumpFileId = 10))
        repo.load(1, 9); repo.load(1, 10)
        assertEquals("edition-nine", repo.local(1, 9).single().cfi)
        assertEquals("edition-ten", repo.local(1, 10).single().cfi)
        coVerify { api.getAnnotations(1, 9, owner) }
        signedIn = someoneElse
        assertTrue(repo.local(1, 9).isEmpty())
        signedIn = me
        assertEquals("edition-nine", repo.local(1, 9).single().cfi)
    }

    @Test fun `legacy unowned cache is preserved verbatim and never claimed by the next account`() = runTest {
        val legacy = Json.encodeToString(ListSerializer(BookAnnotation.serializer()), listOf(onServer(42)))
        cache.rows[1] = AnnotationCacheEntity(1, legacy)
        assertTrue(repo.local(1, 9).isEmpty())
        assertTrue(repo.hasUnownedData(1))
        coEvery { api.getAnnotations(1, 9, any()) } returns listOf(onServer(7))
        assertEquals(listOf(7), repo.load(1, 9).map { it.id })
        assertEquals(legacy, decodeAnnotationCache(cache.rows.getValue(1).json, Json).legacyJson)
        signedIn = someoneElse
        assertTrue(repo.local(1, 9).isEmpty())
    }

    @Test fun `late load cannot publish or cache highlights for a replaced account`() = runTest {
        coEvery { api.getAnnotations(1, 9, any()) } coAnswers { signedIn = someoneElse; listOf(onServer(42)) }
        assertTrue(repo.load(1, 9).isEmpty())
        assertTrue(cache.rows.isEmpty())
    }

    @Test fun `pending creates are not overlaid onto another file`() = runTest {
        createOne()
        assertEquals(1, repo.local(1, 9).size)
        assertTrue(repo.local(1, 10).isEmpty())
    }

    @Test fun `recolouring a projected highlight cannot replace its CFI with canonical coordinates`() = runTest {
        cacheOf(onServer(42).copy(cfi = "projected-nine", jumpFileId = 9))
        repo.setColor(1, 42, "pink")
        coEvery { api.updateAnnotation(1, 42, any(), any()) } returns onServer(42).copy(cfi = "canonical-ten", jumpFileId = 10, color = "pink")
        repo.flushPending()
        val cached = repo.local(1, 9).single()
        assertEquals("projected-nine", cached.cfi)
        assertEquals("pink", cached.color)
    }

    @Test fun `another account cannot coalesce or delete the first account queued operation`() = runTest {
        repo.setNote(1, 42, "first account note")
        signedIn = someoneElse
        repo.setColor(1, 42, "pink")
        assertEquals(2, dao.rows.size)
        repo.delete(1, 42)
        assertEquals("first account note", dao.rows.single { it.userId == me.id }.note)
        assertEquals(AnnotationOp.DELETE, dao.rows.single { it.userId == someoneElse.id }.kind)
    }

    @Test fun `flush stops on an ownership change instead of sending the next old account operation`() = runTest {
        createOne(); repo.delete(1, 42)
        coEvery { api.createAnnotation(any(), any(), any()) } coAnswers { signedIn = someoneElse; onServer(7) }
        assertFalse(repo.flushPending())
        assertEquals(1, dao.rows.size)
        assertEquals(me.id, dao.rows.single().userId)
        assertTrue(repo.local(1, 9).isEmpty())
        coVerify(exactly = 0) { api.deleteAnnotation(any(), any(), any()) }
    }

    @Test fun `changed database server scope blocks local mutations and flushing`() = runTest {
        createOne()
        wrongDatabaseScope = true
        assertTrue(repo.local(1, 9).isEmpty())
        assertFalse(repo.flushPending())
        assertTrue(runCatching { createOne() }.isFailure)
        assertEquals(1, dao.rows.size)
        coVerify(exactly = 0) { api.createAnnotation(any(), any(), any()) }
    }

    @Test fun `cancelled loads and flushes are not converted into stale success`() = runTest {
        coEvery { api.getAnnotations(1, 9, any()) } throws kotlinx.coroutines.CancellationException()
        assertTrue(runCatching { repo.load(1, 9) }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        createOne()
        coEvery { api.createAnnotation(any(), any(), any()) } throws kotlinx.coroutines.CancellationException()
        assertTrue(runCatching { repo.flushPending() }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertEquals(1, dao.rows.size)
    }

    @Test fun `a GET started before a successful delete cannot reinsert its stale highlight`() = runTest {
        cacheOf(onServer(42))
        coEvery { api.getAnnotations(1, 9, any()) } coAnswers {
            repo.delete(1, 42)
            repo.flushPending()
            listOf(onServer(42))
        }
        assertTrue(repo.load(1, 9).isEmpty())
        assertTrue(repo.local(1, 9).isEmpty())
    }

    @Test fun `a mismatched mutation response cannot settle another book or annotation`() = runTest {
        repo.setColor(1, 42, "pink")
        coEvery { api.updateAnnotation(1, 42, any(), any()) } returns onServer(7)
        assertTrue(repo.flushPending())
        assertEquals(42, dao.rows.single().annotationId)
        coEvery { api.updateAnnotation(1, 42, any(), any()) } returns onServer(42).copy(bookId = 2)
        assertTrue(repo.flushPending())
        assertEquals(1, dao.rows.size)
    }

    @Test fun `lost create acknowledgement is not replayed after repository reopen or relogin`() = runTest {
        createOne()
        coEvery { api.createAnnotation(any(), any(), any()) } throws IOException("response lost after acceptance")
        assertFalse(repo.flushPending())
        val reopened = ReaderAnnotationRepository(api, dao, cache, Json, mockk(relaxed = true), session)
        signedIn = me
        repeat(3) { assertFalse(reopened.flushPending()) }
        coVerify(exactly = 1) { api.createAnnotation(any(), any(), any()) }
        val row = reopened.local(1, 9).single()
        assertEquals("a line", row.text)
        assertEquals("delivery-uncertain", row.positionStatus)
        assertNull(row.cfi)
        createOne()
        assertEquals(listOf(-1, -2), dao.rows.map { it.annotationId })
    }

    @Test fun `deletion during an unacknowledged create preserves source and deletion intent`() = runTest {
        createOne()
        coEvery { api.createAnnotation(any(), any(), any()) } coAnswers {
            repo.delete(1, -1)
            throw IOException("response lost")
        }
        assertFalse(repo.flushPending())
        assertEquals(AnnotationOp.CREATE_UNCERTAIN_DELETE, dao.rows.single().kind)
        assertEquals("a line", dao.rows.single().text)
        assertEquals("delivery-uncertain-delete", repo.local(1, 9).single().positionStatus)
        assertFalse(repo.flushPending())
        coVerify(exactly = 1) { api.createAnnotation(any(), any(), any()) }
        coVerify(exactly = 0) { api.deleteAnnotation(any(), any(), any()) }
    }

    @Test fun `explicit unauthorised create rejection can retry with renewed authorization`() = runTest {
        createOne()
        coEvery { api.createAnnotation(any(), any(), any()) } throws HttpException(Response.error<Any>(401, "".toResponseBody()))
        assertTrue(repo.flushPending())
        assertEquals(AnnotationOp.CREATE, dao.rows.single().kind)
        coEvery { api.createAnnotation(any(), any(), any()) } returns onServer(7)
        assertFalse(repo.flushPending())
        assertTrue(dao.rows.isEmpty())
    }

    @Test fun `reader projection and owner are passed as query and local tag without extra server DTO fields`() = kotlinx.coroutines.runBlocking {
        okhttp3.mockwebserver.MockWebServer().use { server ->
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("[]"))
            val captured = java.util.concurrent.atomic.AtomicReference<okhttp3.Request>()
            val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain -> captured.set(chain.request()); chain.proceed(chain.request()) }.build()
            val network = retrofit2.Retrofit.Builder().baseUrl(server.url("/api/v1/")).client(client)
                .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build().create(ApiService::class.java)
            val owner = session.owner()!!
            assertTrue(network.getAnnotations(1, 9, owner).isEmpty())
            assertEquals("/api/v1/books/1/annotations?readerFileId=9", server.takeRequest().path)
            assertEquals(owner, captured.get().tag(SessionOwner::class.java))
        }
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
