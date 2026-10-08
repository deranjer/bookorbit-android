package com.bookorbit.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.model.BookAnnotation
import com.bookorbit.core.storage.LocalRef
import com.bookorbit.feature.bookdetail.BookDetailRepository
import com.bookorbit.feature.downloads.DownloadsRepository
import com.bookorbit.feature.sessions.ReadingSessionTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val sessions: ReadingSessionTracker,
    private val annotationRepo: ReaderAnnotationRepository,
) : ViewModel() {

    val bookId: Int = savedStateHandle.get<Int>("id") ?: 0

    data class ResolvedOpen(
        val ref: LocalRef,
        val format: String,
        val fileId: Int,
        val initial: InitialProgress,
    )

    /** Text the user has selected in the page, not yet saved as a highlight. */
    data class PendingSelection(val text: String, val cfi: String)

    /** What the floating highlight toolbar is acting on: a new selection, or an existing highlight. */
    data class ToolbarTarget(
        val rect: ViewportRect?,
        val selection: PendingSelection? = null,
        val annotationId: Int? = null,
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
        val annotations: List<BookAnnotation> = emptyList(),
        val toolbar: ToolbarTarget? = null,
        /** One-shot message for the screen to toast, then clear via [consumeMessage]. */
        val message: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    private var lastSaved = 0L
    private var pending: Pair<String?, Double>? = null

    init {
        load()
        loadAnnotations()
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
                sessions.beginReading(resolved.fileId, initial.fraction?.let { it * 100 })
                _ui.update {
                    it.copy(
                        loadingFile = false,
                        resolved = ResolvedOpen(resolved.ref, resolved.format, resolved.fileId, initial),
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
        if (cfi != null && fraction != null) {
            report(cfi, fraction * 100)
            sessions.onReadingProgress(fraction * 100)
        }
    }

    // --- highlights & notes ---

    private fun loadAnnotations() {
        viewModelScope.launch {
            runCatching { annotationRepo.list(bookId) }
                .onSuccess { list -> _ui.update { it.copy(annotations = list) } }
        }
    }

    fun onSelection(text: String, cfi: String?, rect: ViewportRect?) {
        if (cfi == null) return
        _ui.update { it.copy(toolbar = ToolbarTarget(rect, selection = PendingSelection(text, cfi))) }
    }

    fun onSelectionCleared() {
        // A selection going away dismisses the new-highlight toolbar, but not one opened on an existing highlight.
        _ui.update { s -> if (s.toolbar?.annotationId == null) s.copy(toolbar = null) else s }
    }

    fun onAnnotationTap(cfi: String, rect: ViewportRect?) {
        val ann = _ui.value.annotations.firstOrNull { it.cfi == cfi } ?: return
        _ui.update { it.copy(toolbar = ToolbarTarget(rect, annotationId = ann.id)) }
    }

    fun dismissToolbar() = _ui.update { it.copy(toolbar = null) }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    /** Highlight the selection in [color], or recolour the highlight the toolbar is open on. */
    fun highlight(color: String) = saveAnnotation(color = color, note = null)

    /** Save a note: on the open highlight, or on the selection as a new (yellow) highlight. */
    fun saveNote(note: String) = saveAnnotation(color = null, note = note)

    private fun saveAnnotation(color: String?, note: String?) {
        val target = _ui.value.toolbar ?: return
        val fileId = _ui.value.resolved?.fileId
        viewModelScope.launch {
            val result = runCatching {
                when {
                    target.annotationId != null -> {
                        var updated: BookAnnotation? = null
                        if (color != null) updated = annotationRepo.setColor(bookId, target.annotationId, color)
                        if (note != null) updated = annotationRepo.setNote(bookId, target.annotationId, note)
                        updated
                    }
                    target.selection != null && fileId != null -> annotationRepo.create(
                        bookId = bookId,
                        fileId = fileId,
                        cfi = target.selection.cfi,
                        text = target.selection.text,
                        color = color ?: ReaderAnnotationRepository.COLORS.first().second,
                        note = note,
                        chapterTitle = _ui.value.chapterTitle,
                    )
                    else -> null
                }
            }
            result.onSuccess { saved ->
                if (saved != null) {
                    _ui.update { s ->
                        val exists = s.annotations.any { it.id == saved.id }
                        s.copy(
                            annotations = if (exists) s.annotations.map { if (it.id == saved.id) saved else it } else s.annotations + saved,
                            toolbar = null,
                        )
                    }
                } else {
                    _ui.update { it.copy(toolbar = null) }
                }
            }.onFailure {
                _ui.update { s -> s.copy(message = "Couldn't save the highlight. Check your connection and try again.") }
            }
        }
    }

    fun deleteAnnotation(id: Int) {
        viewModelScope.launch {
            runCatching { annotationRepo.delete(bookId, id) }
                .onSuccess { _ui.update { s -> s.copy(annotations = s.annotations.filterNot { it.id == id }, toolbar = null) } }
                .onFailure { _ui.update { s -> s.copy(message = "Couldn't delete the highlight.") } }
        }
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
        sessions.end()
        super.onCleared()
    }
}
