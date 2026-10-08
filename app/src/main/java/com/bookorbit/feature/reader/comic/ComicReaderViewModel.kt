package com.bookorbit.feature.reader.comic

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
    private val settings: AppSettingsStore,
) : ViewModel() {

    val bookId: Int = savedStateHandle.get<Int>("id") ?: 0

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
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

    private var lastSaved = 0L
    private var pendingPage: Int? = null

    init {
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val book = runCatching { bookRepo.detail(bookId) }.getOrNull() ?: downloads.cachedBook(bookId)
            val file = book?.let(BookFiles::comicFile)
            if (book == null || file == null) {
                _ui.update { it.copy(loading = false, error = "Couldn't load this comic.") }
                return@launch
            }
            val count = runCatching { api.getComicPageCount(file.id).pageCount }.getOrNull()
            if (count == null || count <= 0) {
                _ui.update {
                    it.copy(
                        loading = false,
                        title = book.title,
                        error = "Couldn't read this comic. CBR and CB7 comics need a connection to your server.",
                    )
                }
                return@launch
            }
            val initial = progress.resolveInitial(file.id)
            val page = ComicPaging.pageForFraction(initial.fraction, count)
            sessions.beginReading(file.id, ComicPaging.percentage(page, count))
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
        sessions.onReadingProgress(ComicPaging.percentage(page, s.pageCount))
        if (System.currentTimeMillis() - lastSaved >= PROGRESS_THROTTLE_MS) flush()
    }

    fun setRtl(value: Boolean) {
        viewModelScope.launch { settings.setComicRtl(value) }
    }

    fun flush() {
        val page = pendingPage ?: return
        val s = _ui.value
        if (s.fileId == 0 || s.pageCount <= 0) return
        pendingPage = null
        lastSaved = System.currentTimeMillis()
        viewModelScope.launch {
            progress.report(s.fileId, cfi = null, percentage = ComicPaging.percentage(page, s.pageCount), pageNumber = ComicPaging.pageNumber(page))
        }
    }

    override fun onCleared() {
        flush()
        sessions.end()
        super.onCleared()
    }
}
