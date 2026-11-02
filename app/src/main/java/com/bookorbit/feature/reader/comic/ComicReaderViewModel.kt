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
import com.bookorbit.feature.reader.ProgressSaver
import com.bookorbit.feature.reader.ReaderProgressRepository
import com.bookorbit.feature.reader.SavedPosition
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

    private val saver = ProgressSaver<SavedPosition>(viewModelScope) { progress.save(it) }

    init {
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val book = runCatching { bookRepo.detail(bookId) }.getOrNull() ?: downloads.cachedBook(bookId)
            val file = book?.let(BookFiles::comicFile)
            if (book == null || file == null) {
                _ui.update { it.copy(loading = false, error = UiText.of(R.string.comic_err_load)) }
                return@launch
            }
            val count = runCatching { api.getComicPageCount(file.id).pageCount }.getOrNull()
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
        sessions.onReadingProgress(ComicPaging.percentage(page, s.pageCount))
        if (s.fileId != 0) {
            saver.offer(SavedPosition(s.fileId, cfi = null, ComicPaging.percentage(page, s.pageCount), ComicPaging.pageNumber(page)))
        }
    }

    fun setRtl(value: Boolean) {
        viewModelScope.launch { settings.setComicRtl(value) }
    }

    /** Save where the reader is now; the screen calls it when the app goes to the background. */
    fun flush() = saver.flush()

    override fun onCleared() {
        flush()
        sessions.end()
        super.onCleared()
    }
}
