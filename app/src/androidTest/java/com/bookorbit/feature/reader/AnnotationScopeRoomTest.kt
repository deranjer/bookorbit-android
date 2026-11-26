package com.bookorbit.feature.reader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bookorbit.core.auth.SecureStorage
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.db.*
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.network.annotationClient
import com.bookorbit.core.sync.SyncScheduler
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.KeyStore
import java.util.UUID

/** A private Room database and no-op scheduling: never runs the retained fixture SyncWorker. */
class AnnotationScopeRoomTest {
    @Test fun lostCreateResponseCannotReplayAfterDiskDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "annotation-delivery-test-${UUID.randomUUID()}"
        val alias = "$name-key"
        fun open() = Room.databaseBuilder(context, BookOrbitDatabase::class.java, "$name.sqlite").build()
        var db = open()
        try {
            val storage = SecureStorage(context, name, alias, "$name-owner")
            val session = SessionManager(storage, Json).apply {
                setServerUrl("https://fixture.example"); signIn("fake-token", AuthUser(1, "first")); activeDataScope = dataScope
            }
            MockWebServer().use { server ->
                val client = annotationClient(OkHttpClient.Builder().callTimeout(2, java.util.concurrent.TimeUnit.SECONDS).build())
                val api = Retrofit.Builder().baseUrl(server.url("/api/v1/")).client(client)
                    .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build().create(ApiService::class.java)
                fun repository() = ReaderAnnotationRepository(api, db.pendingAnnotationOpDao(), db.annotationCacheDao(), Json, SyncScheduler {}, session)
                repository().create(1, 9, "epubcfi(/6/2)", "saved source", "yellow", "saved note", "chapter")
                server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST))
                assertFalse(repository().flushPending())
                assertNotNull(server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS))
                assertEquals(AnnotationOp.CREATE_UNCERTAIN, db.pendingAnnotationOpDao().forOwnedBook(1, 1).single().kind)
                db.close(); db = open()
                server.enqueue(MockResponse().setResponseCode(503))
                repeat(3) { assertFalse(repository().flushPending()) }
                val saved = repository().local(1, 9).single()
                assertEquals("saved source", saved.text); assertEquals("saved note", saved.note)
                assertNull(saved.cfi); assertEquals("delivery-uncertain", saved.positionStatus)
                assertEquals(1, server.requestCount)
            }
        } finally {
            db.close(); context.deleteDatabase("$name.sqlite")
            context.deleteSharedPreferences(name); context.deleteSharedPreferences("$name-owner")
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }

    @Test fun scopedQueuesAndProjectedCacheSurviveRepositoryReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "annotation-scope-test-${UUID.randomUUID()}"
        val alias = "$name-key"
        val db = Room.inMemoryDatabaseBuilder(context, BookOrbitDatabase::class.java).build()
        try {
            val storage = SecureStorage(context, name, alias, "$name-owner")
            val session = SessionManager(storage, Json).apply {
                setServerUrl("https://fixture.example"); signIn("fake-token", AuthUser(1, "first"))
                activeDataScope = dataScope
            }
            MockWebServer().use { server ->
                val api = Retrofit.Builder().baseUrl(server.url("/api/v1/" )).client(annotationClient(OkHttpClient()))
                    .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build().create(ApiService::class.java)
                var schedules = 0
                fun repository() = ReaderAnnotationRepository(api, db.pendingAnnotationOpDao(), db.annotationCacheDao(), Json,
                    SyncScheduler { schedules++ }, session)
                val repo = repository()
                val legacy = "[{\"id\":99,\"bookId\":1}]"
                db.annotationCacheDao().put(AnnotationCacheEntity(1, legacy))
                db.pendingAnnotationOpDao().insert(PendingAnnotationOpEntity(bookId = 1, kind = AnnotationOp.DELETE, annotationId = 99))
                server.enqueue(MockResponse().setBody("""[{"id":42,"bookId":1,"cfi":"edition-nine","jumpFileId":9}]"""))
                assertEquals("edition-nine", repo.load(1, 9).single().cfi)
                assertEquals("/api/v1/books/1/annotations?readerFileId=9", server.takeRequest().path)
                repo.setNote(1, 42, "first private note")
                session.signIn("other-token", AuthUser(2, "second"))
                assertTrue(repo.local(1, 9).isEmpty())
                repo.setColor(1, 42, "pink")
                repo.delete(1, 42)
                assertEquals(1, db.pendingAnnotationOpDao().forOwnedBook(1, 1).size)
                assertEquals(AnnotationOp.DELETE, db.pendingAnnotationOpDao().firstOwned(2)!!.kind)
                server.enqueue(MockResponse().setResponseCode(204))
                assertFalse(repo.flushPending())
                assertEquals("DELETE", server.takeRequest().method)
                assertEquals(1, db.pendingAnnotationOpDao().unownedCount(1))
                session.signIn("replacement", AuthUser(1, "first"))
                assertEquals("first private note", repository().local(1, 9).single().note)
                assertTrue(repository().local(1, 10).isEmpty())
                assertEquals(legacy, decodeAnnotationCache(db.annotationCacheDao().get(1)!!.json, Json).legacyJson)
                assertEquals(3, schedules)
                assertEquals(2, server.requestCount)
            }
        } finally {
            db.close()
            context.deleteSharedPreferences(name); context.deleteSharedPreferences("$name-owner")
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }
}
