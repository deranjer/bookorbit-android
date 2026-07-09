package com.bookorbit.feature.reader.pdf

import android.graphics.RectF
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.feature.bookdetail.BookDetailRepository
import com.bookorbit.feature.downloads.DownloadsRepository
import com.bookorbit.feature.reader.ReaderProgressRepository
import com.bookorbit.feature.reader.ReaderSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val PROGRESS_THROTTLE_MS = 2_000L

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepo: BookDetailRepository,
    private val downloads: DownloadsRepository,
    private val source: ReaderSource,
    private val progress: ReaderProgressRepository,
    private val settingsStore: PdfReaderSettingsStore,
) : ViewModel() {

    val bookId: Int = savedStateHandle.get<Int>("id") ?: 0

    /** A single search match: which page it is on, and its bounds in page-point space. */
    data class SearchHit(val page: Int, val rect: RectF)

    data class SearchState(
        val query: String = "",
        val running: Boolean = false,
        val hits: List<SearchHit> = emptyList(),
        val activeIndex: Int = -1,
    ) {
        val active: SearchHit? get() = hits.getOrNull(activeIndex)
    }

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val title: String? = null,
        val core: PdfRenderCore? = null,
        val pageCount: Int = 0,
        val initialPage: Int = 0,
        val currentPage: Int = 0, // 0-based
        val settings: PdfReaderSettings = PdfReaderSettings(),
        val hasTextLayer: Boolean = Build.VERSION.SDK_INT >= 35,
        val search: SearchState? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    private var fileId: Int = 0
    private var lastSaved = 0L
    private var pendingPage: Int? = null
    private var searchJob: kotlinx.coroutines.Job? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val settings = settingsStore.load()
            _ui.update { it.copy(settings = settings) }

            // Fall back to the offline-downloaded copy when the network is unavailable.
            val book = runCatching { bookRepo.detail(bookId) }.getOrNull() ?: downloads.cachedBook(bookId)
            if (book == null) {
                _ui.update { it.copy(loading = false, error = "Failed to load book") }
                return@launch
            }
            _ui.update { it.copy(title = book.title) }

            try {
                val resolved = source.resolvePdf(book)
                fileId = resolved.fileId
                val core = PdfRenderCore.open(resolved.file)
                val initial = progress.resolveInitial(resolved.fileId)
                val page = PdfProgress.fractionToPage(initial.fraction, core.pageCount)
                _ui.update {
                    it.copy(
                        loading = false,
                        core = core,
                        pageCount = core.pageCount,
                        initialPage = page,
                        currentPage = page,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Could not open this PDF.") }
            }
        }
    }

    fun onPageChanged(pageIndex: Int) {
        val total = _ui.value.pageCount
        if (total <= 0) return
        val clamped = pageIndex.coerceIn(0, total - 1)
        if (clamped == _ui.value.currentPage) return
        _ui.update { it.copy(currentPage = clamped) }
        report(clamped)
    }

    fun updateSettings(settings: PdfReaderSettings) {
        _ui.update { it.copy(settings = settings) }
        viewModelScope.launch { settingsStore.save(settings) }
    }

    // ---- Search (API 35+) ----

    fun search(query: String) {
        val core = _ui.value.core ?: return
        searchJob?.cancel()
        if (query.isBlank()) {
            _ui.update { it.copy(search = null) }
            return
        }
        _ui.update { it.copy(search = SearchState(query = query, running = true)) }
        searchJob = viewModelScope.launch {
            val hits = withContext(Dispatchers.IO) {
                buildList {
                    for (p in 0 until core.pageCount) {
                        core.searchPage(p, query).forEach { match ->
                            match.rects.forEach { add(SearchHit(p, it)) }
                        }
                    }
                }
            }
            _ui.update {
                it.copy(search = SearchState(query, running = false, hits = hits, activeIndex = if (hits.isEmpty()) -1 else 0))
            }
        }
    }

    fun nextMatch() = stepMatch(1)

    fun prevMatch() = stepMatch(-1)

    private fun stepMatch(delta: Int) {
        val s = _ui.value.search ?: return
        if (s.hits.isEmpty()) return
        val next = (s.activeIndex + delta + s.hits.size) % s.hits.size
        _ui.update { it.copy(search = s.copy(activeIndex = next)) }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _ui.update { it.copy(search = null) }
    }

    private fun report(pageIndex: Int) {
        pendingPage = pageIndex
        if (System.currentTimeMillis() - lastSaved >= PROGRESS_THROTTLE_MS) flush()
    }

    fun flush() {
        val page = pendingPage ?: return
        val total = _ui.value.pageCount
        if (fileId == 0 || total <= 0) return
        pendingPage = null
        lastSaved = System.currentTimeMillis()
        val percentage = PdfProgress.pageToPercentage(page, total)
        val pageNumber = PdfProgress.pageNumber(page)
        viewModelScope.launch { progress.report(fileId, cfi = null, percentage = percentage, pageNumber = pageNumber) }
    }

    override fun onCleared() {
        flush()
        _ui.value.core?.close()
        super.onCleared()
    }
}
