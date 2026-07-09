package com.bookorbit.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.feature.bookdetail.BookDetailRepository
import com.bookorbit.feature.downloads.DownloadsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

private const val PROGRESS_THROTTLE_MS = 2_000L

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepo: BookDetailRepository,
    private val downloads: DownloadsRepository,
    private val source: ReaderSource,
    private val progress: ReaderProgressRepository,
    private val settingsStore: ReaderSettingsStore,
) : ViewModel() {

    val bookId: Int = savedStateHandle.get<Int>("id") ?: 0

    data class ResolvedOpen(
        val file: File,
        val format: String,
        val fileId: Int,
        val initial: InitialProgress,
    )

    data class UiState(
        val loadingFile: Boolean = true,
        val error: String? = null,
        val title: String? = null,
        val resolved: ResolvedOpen? = null,
        val settings: ReaderSettings = ReaderSettings(),
        val toc: List<TocItem> = emptyList(),
        val chapterTitle: String? = null,
        val percentage: Int = 0,
        val loaded: Boolean = false,
        val showPagingHint: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    private var lastSaved = 0L
    private var pending: Pair<String?, Double>? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val settings = settingsStore.load()
            // Coach the paginated tap-zones once (they're invisible by design).
            val showHint = settings.flow == "paginated" && !settingsStore.hasSeenPagingHint()
            _ui.update { it.copy(settings = settings, showPagingHint = showHint) }

            // Fall back to the offline-downloaded copy when the network is unavailable.
            val book = runCatching { bookRepo.detail(bookId) }.getOrNull() ?: downloads.cachedBook(bookId)
            if (book == null) {
                _ui.update { it.copy(loadingFile = false, error = "Failed to load book") }
                return@launch
            }
            _ui.update { it.copy(title = book.title) }

            try {
                val resolved = source.resolve(book)
                val initial = progress.resolveInitial(resolved.fileId)
                _ui.update {
                    it.copy(
                        loadingFile = false,
                        resolved = ResolvedOpen(resolved.file, resolved.format, resolved.fileId, initial),
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loadingFile = false, error = e.message ?: "Could not open this book.") }
            }
        }
    }

    fun onLoaded(toc: List<TocItem>, title: String?) {
        _ui.update { it.copy(loaded = true, toc = toc, chapterTitle = it.chapterTitle ?: title) }
    }

    fun onRelocate(cfi: String?, fraction: Double?, chapterTitle: String?) {
        _ui.update {
            it.copy(
                chapterTitle = chapterTitle ?: it.chapterTitle,
                percentage = fraction?.let { f -> (f * 100).roundToInt() } ?: it.percentage,
            )
        }
        if (cfi != null && fraction != null) report(cfi, fraction * 100)
    }

    fun onError(message: String) {
        _ui.update { it.copy(error = message) }
    }

    fun updateSettings(settings: ReaderSettings) {
        _ui.update { it.copy(settings = settings) }
        viewModelScope.launch { settingsStore.save(settings) }
    }

    fun dismissPagingHint() {
        _ui.update { it.copy(showPagingHint = false) }
        viewModelScope.launch { settingsStore.markPagingHintSeen() }
    }

    private fun report(cfi: String?, percentage: Double) {
        pending = cfi to percentage
        if (System.currentTimeMillis() - lastSaved >= PROGRESS_THROTTLE_MS) flush()
    }

    fun flush() {
        val fileId = _ui.value.resolved?.fileId ?: return
        val p = pending ?: return
        pending = null
        lastSaved = System.currentTimeMillis()
        viewModelScope.launch { progress.report(fileId, p.first, p.second) }
    }

    override fun onCleared() {
        flush()
        super.onCleared()
    }
}
