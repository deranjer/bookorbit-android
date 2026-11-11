package com.bookorbit.feature.player

import android.content.ComponentName
import android.media.browse.MediaBrowser as PlatformBrowser
import android.media.session.MediaController as PlatformController
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bookorbit.app.BookOrbitApplication
import com.bookorbit.core.db.*
import com.bookorbit.core.model.*
import com.bookorbit.feature.downloads.DownloadedFile
import kotlinx.coroutines.runBlocking
import dagger.hilt.android.EntryPointAccessors
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Exercises the real service and legacy voice bridge without launching a phone activity. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class AutoMediaSessionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun <T> main(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }
    private fun await(label: String, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(50)
        }
        fail("Timed out: $label")
    }

    @Test fun offlineSearchVoiceResumeChaptersAndLiveDownloads() = runBlocking {
        val id = 1_900_000_001
        val otherId = id + 1
        val dependencies = EntryPointAccessors.fromApplication(context, AutoTestDependencies::class.java)
        val database = dependencies.database()
        val dao = database.downloadDao()
        val progress = database.audioProgressDao()
        val previous = dao.get(id)
        val otherPrevious = dao.get(otherId)
        val previousProgress = progress.get(id)
        val settings = AudioSettingsStore(context)
        val oldSettings = settings.load()
        val oldBookSpeed = settings.bookSpeed(id)
        val files = (0..1).map { index -> writeWav(File.createTempFile("auto-session-$index", ".wav", context.cacheDir), 10) }
        val refs = files.mapIndexed { index, file -> BookFileRef(id + 10 + index, "wav", "primary", durationSeconds = 10.0, filename = file.name) }
        val book = BookDetail(id, 1, "Test", "", "", title = "Auto Session Fixture",
            files = refs, audioMetadata = AudioMetadata(chapters = listOf(
                AudiobookChapter("First", 0), AudiobookChapter("Second", 5_000), AudiobookChapter("Third", 15_000))))
        val json = Json { encodeDefaults = true }
        val record = DownloadEntity(id, book.title, "Test Author", "Test Narrator", true, "wav", 0, System.currentTimeMillis(),
            null, json.encodeToString(book), json.encodeToString(refs.mapIndexed { i, ref ->
                DownloadedFile(ref.id, Uri.fromFile(files[i]).toString(), files[i].name, "wav", 10.0)
            }), "COMPLETE", 1f)
        var browser: MediaBrowser? = null
        var legacy: PlatformBrowser? = null
        val notifications = AtomicInteger()
        val failure = AtomicReference<SessionError?>()
        try {
            dao.upsert(record)
            progress.upsert(AudioProgressEntity(id, refs[0].id, 2.0, 10.0, System.currentTimeMillis(), false))
            settings.saveBookSpeed(id, 1.5f)
            settings.saveProgressBarMode(ProgressBarMode.BOOK)
            val listener = object : MediaBrowser.Listener {
                override fun onChildrenChanged(browser: MediaBrowser, parentId: String, itemCount: Int, params: LibraryParams?) {
                    if (parentId == AutoBrowseTree.DOWNLOADS_ID) notifications.incrementAndGet()
                }
                override fun onError(controller: MediaController, sessionError: SessionError) { failure.set(sessionError) }
            }
            val client = main {
                MediaBrowser.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
                    .setListener(listener).buildAsync()
            }.get(15, TimeUnit.SECONDS)
            browser = client
            val root = main { client.getLibraryRoot(null) }.get(10, TimeUnit.SECONDS)
            assertTrue(root.params!!.extras.getBoolean("android.media.browse.SEARCH_SUPPORTED"))
            val shelves = main { client.getChildren(AutoBrowseTree.ROOT_ID, 0, Int.MAX_VALUE, null) }.get(10, TimeUnit.SECONDS).value!!
            assertEquals(AutoBrowseTree.CONTINUE_ID, shelves.first().mediaId)
            main { client.subscribe(AutoBrowseTree.DOWNLOADS_ID, null) }.get(10, TimeUnit.SECONDS)
            val before = notifications.get()
            dao.upsert(record.copy(bookId = otherId, title = "Other fixture"))
            await("download shelf refresh") { notifications.get() > before }
            assertTrue(main { client.getChildren(AutoBrowseTree.DOWNLOADS_ID, 0, 40, null) }
                .get(10, TimeUnit.SECONDS).value!!.any { it.mediaId == AutoBrowseTree.bookMediaId(otherId) })

            val found = main { client.search("Auto Session Fixture", null) }.get(10, TimeUnit.SECONDS)
            assertEquals(0, found.resultCode)
            val results = main { client.getSearchResult("Auto Session Fixture", 0, 40, null) }.get(10, TimeUnit.SECONDS).value!!
            assertTrue(results.any { it.mediaId == AutoBrowseTree.bookMediaId(id) })

            // Android Auto uses the platform/legacy transport path for play-from-search.
            val connected = CountDownLatch(1)
            val platform = main {
                PlatformBrowser(context, ComponentName(context, PlaybackService::class.java),
                    object : PlatformBrowser.ConnectionCallback() {
                        override fun onConnected() { connected.countDown() }
                    }, null).also { it.connect() }
            }
            legacy = platform
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            main { PlatformController(context, platform.sessionToken).transportControls.playFromSearch("Auto Session Fixture", null) }
            await("voice playback and saved speed") {
                main { client.currentMediaItem?.mediaId == refs[0].id.toString() && client.isPlaying && client.playbackParameters.speed == 1.5f }
            }
            main { client.pause() }
            assertTrue(main { client.currentPosition >= 1_900 })
            val chapters = main { client.getChildren(AutoBrowseTree.NOW_PLAYING_CHAPTERS_ID, 0, 40, null) }.get(10, TimeUnit.SECONDS).value!!
            assertEquals(3, chapters.size)
            main { client.setMediaItem(MediaItem.Builder().setMediaId(AutoBrowseTree.chapterMediaId(id, 2)).build()); client.prepare() }
            await("chapter spanning file boundaries") {
                main { client.currentMediaItem?.mediaId == refs[1].id.toString() && client.currentPosition >= 4_900 }
            }
            main { client.setMediaItem(MediaItem.Builder().setMediaId("book/0").build()) }
            await("friendly selection error") { failure.get() != null }
            assertTrue(failure.get()!!.message.isNotBlank())
            await("invalid selection retains the current queue") {
                main { dependencies.activeBookPlayer().current?.currentMediaItem?.mediaId == refs[1].id.toString() }
            }

            main { client.clearMediaItems(); client.play() }
            await("empty-queue resumption") {
                main { client.currentMediaItem?.mediaId == refs[0].id.toString() && client.isPlaying }
            }
            main { client.pause() }
        } finally {
            dependencies.playerManager().stop().join()
            main { browser?.stop(); browser?.clearMediaItems(); browser?.release(); legacy?.disconnect() }
            if (previous == null) dao.delete(id) else dao.upsert(previous)
            if (otherPrevious == null) dao.delete(otherId) else dao.upsert(otherPrevious)
            if (previousProgress != null) progress.upsert(previousProgress)
            else database.openHelper.writableDatabase.execSQL("DELETE FROM audio_progress WHERE bookId = ?", arrayOf(id))
            database.pendingReadingSessionDao().all().filter { it.fileId == refs[0].id }.forEach {
                database.pendingReadingSessionDao().deleteIfUnchanged(it.sessionId, it.endedAt)
            }
            settings.saveBookSpeed(id, oldBookSpeed)
            settings.saveProgressBarMode(oldSettings.progressBarMode)
            files.forEach { it.delete() }
        }
    }

    private fun writeWav(file: File, seconds: Int): File {
        val length = 8_000 * 2 * seconds
        RandomAccessFile(file, "rw").use { out ->
            fun int(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun short(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            out.writeBytes("RIFF"); int(36 + length); out.writeBytes("WAVEfmt "); int(16); short(1); short(1)
            int(8_000); int(16_000); short(2); short(16); out.writeBytes("data"); int(length); out.write(ByteArray(length))
        }
        return file
    }
}
