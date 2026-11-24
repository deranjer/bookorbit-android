package com.bookorbit.feature.reader.comic

import com.bookorbit.R
import com.bookorbit.ui.UiText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.model.BookFiles
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.settings.AppSettingsStore
import com.bookorbit.feature.bookdetail.BookDetailRepository
import com.bookorbit.feature.downloads.DownloadsRepository
import com.bookorbit.feature.reader.ReaderProgressRepository
import com.bookorbit.feature.sessions.ReadingSessionTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.bookorbit.feature.reader.ReaderSource
import com.bookorbit.core.storage.LocalRef
import com.bookorbit.core.storage.openInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val PROGRESS_THROTTLE_MS = 2_000L

/** Pages through a CBR/CB7 comic whose pages the server renders as images. */
@HiltViewModel
class ComicReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepo: BookDetailRepository,
    private val downloads: DownloadsRepository,
    private val api: ApiService,
    private val progress: ReaderProgressRepository,
    private val sessions: ReadingSessionTracker,
    private val session: com.bookorbit.core.auth.SessionManager,
    private val settings: AppSettingsStore,
    private val source: ReaderSource,
    @ApplicationContext private val context: Context,
    val ocr: com.bookorbit.feature.reader.ComicOcr,
    val readingTools: com.bookorbit.feature.reader.ReadingToolsRepository,
    readerSettings: com.bookorbit.feature.reader.ReaderSettingsStore,
    private val lastEditions: com.bookorbit.feature.reader.LastReadEditionStore,
) : ViewModel() {

    val bookId: Int = savedStateHandle.get<Int>("id") ?: 0
    private val selectedFileId = checkNotNull(savedStateHandle.get<Int>("fileId"))
    private val activityOwner = session.owner()
    private var readingHandle: com.bookorbit.feature.sessions.ReadingHandle? = null

    data class UiState(
        val loading: Boolean = true,
        val error: UiText? = null,
        val title: String? = null,
        val fileId: Int = 0,
        val pageCount: Int = 0,
        val initialPage: Int = 0,
        val currentPage: Int = 0,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    val rtl: StateFlow<Boolean> = settings.comicRtl
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val readerSettings = readerSettings.observe().stateIn(viewModelScope, SharingStarted.Eagerly, com.bookorbit.feature.reader.ReaderSettings())

    private var lastSaved = 0L
    private var pendingPage: Int? = null
    private var archive: ComicArchive? = null
    private var cbz = false

    init {
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            if (activityOwner == null || !session.owns(activityOwner)) return@launch
            val book = runCatching { bookRepo.detail(bookId) }.getOrNull() ?: downloads.cachedBook(bookId)
            if (!session.owns(activityOwner)) return@launch
            val file = book?.let { (BookFiles.readingTarget(it, selectedFileId) as? BookFiles.ReadingTarget.Comic)?.file }
            if (book == null || file == null) {
                _ui.update { it.copy(loading = false, error = UiText.of(R.string.comic_err_load)) }
                return@launch
            }
            cbz = file.format.equals("cbz", ignoreCase = true)
            val count = runCatching {
                if (cbz) {
                    val resolved = source.resolveFile(book, file)
                    archive = withContext(Dispatchers.IO) {
                        val serverKey = java.security.MessageDigest.getInstance("SHA-256").digest(activityOwner?.serverUrl.orEmpty().toByteArray()).joinToString("") { "%02x".format(it) }
                        val cache = File(context.cacheDir, "comic-pages/$serverKey/${activityOwner?.userId}/${file.id}")
                        val local = when (val ref = resolved.ref) {
                            is LocalRef.PlainFile -> ref.file
                            is LocalRef.ContentDoc -> {
                                cache.mkdirs()
                                File(cache, "archive.cbz").also { copy -> ref.openInputStream(context).use { input -> copy.outputStream().use { input.copyTo(it) } } }
                            }
                        }
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                        local.inputStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
                        }
                        val revision = digest.digest().joinToString("") { "%02x".format(it) }
                        ComicArchive(local, File(cache, revision))
                    }
                    archive!!.pages.size
                } else api.getComicPageCount(file.id).pageCount
            }.getOrNull()
            if (!session.owns(activityOwner)) return@launch
            if (count == null || count <= 0) {
                _ui.update {
                    it.copy(
                        loading = false,
                        title = book.title,
                        error = UiText.of(R.string.comic_err_read),
                    )
                }
                return@launch
            }
            val initial = progress.resolveInitial(file.id)
            if (!session.owns(activityOwner)) return@launch
            activityOwner?.let { lastEditions.record(it, bookId, file.id) }
            val stored = ComicPaging.pageForCheckpoint(initial.cfi, initial.pageNumber, initial.fraction, count)
            val oldPage = ComicPaging.legacyCfiPage(initial.cfi)
            val page = if (cbz && initial.pageNumber == null && oldPage != null)
                runCatching { archive!!.legacyPage(oldPage) }.getOrElse {
                    _ui.update { state -> state.copy(loading = false, error = UiText.of(R.string.comic_err_read)) }
                    return@launch
                } else stored
            readingHandle = sessions.beginReading(file.id, ComicPaging.percentage(page, count), activityOwner)
            _ui.update {
                it.copy(loading = false, title = book.title, fileId = file.id, pageCount = count, initialPage = page, currentPage = page)
            }
        }
    }

    fun onPageChanged(page: Int) {
        val s = _ui.value
        if (s.pageCount <= 0 || page == s.currentPage) return
        _ui.update { it.copy(currentPage = page) }
        pendingPage = page
        readingHandle?.let { sessions.onReadingProgress(it, ComicPaging.percentage(page, s.pageCount)) }
        if (System.currentTimeMillis() - lastSaved >= PROGRESS_THROTTLE_MS) flush()
    }

    fun setRtl(value: Boolean) {
        viewModelScope.launch { settings.setComicRtl(value) }
    }

    suspend fun localPage(page: Int): File? = archive?.page(page)

    private val ocrCache = android.util.LruCache<String, com.bookorbit.feature.reader.OcrPage>(6)
    suspend fun recognizePage(page: Int, bitmap: android.graphics.Bitmap): com.bookorbit.feature.reader.OcrPage {
        val key = "$page:${bitmap.width}:${bitmap.height}"
        return ocrCache.get(key) ?: ocr.recognize(bitmap).also { ocrCache.put(key, it) }
    }

    fun flush() {
        val page = pendingPage ?: return
        val s = _ui.value
        if (s.fileId == 0 || s.pageCount <= 0) return
        pendingPage = null
        lastSaved = System.currentTimeMillis()
        viewModelScope.launch {
            progress.report(s.fileId, cfi = if (cbz) ComicPaging.cfiForPage(page) else null, percentage = ComicPaging.percentage(page, s.pageCount), pageNumber = ComicPaging.pageNumber(page))
        }
    }

    override fun onCleared() {
        flush()
        readingHandle?.let(sessions::endReading)
        super.onCleared()
    }
}
