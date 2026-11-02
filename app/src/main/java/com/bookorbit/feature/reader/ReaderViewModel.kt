package com.bookorbit.feature.reader

import com.bookorbit.R
import com.bookorbit.ui.UiText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.model.BookAnnotation
import com.bookorbit.core.model.BookBookmark
import com.bookorbit.core.storage.LocalRef
import com.bookorbit.feature.bookdetail.BookDetailRepository
import com.bookorbit.feature.downloads.DownloadsRepository
import com.bookorbit.feature.sessions.ReadingSessionTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepo: BookDetailRepository,
    private val downloads: DownloadsRepository,
    private val source: ReaderSource,
    private val progress: ReaderProgressRepository,
    private val settingsStore: ReaderSettingsStore,
    private val preferences: ReaderPreferencesRepository,
    private val fonts: ReaderFontsRepository,
    private val sessions: ReadingSessionTracker,
    private val annotationRepo: ReaderAnnotationRepository,
    private val bookmarkRepo: ReaderBookmarkRepository,
    private val dictionary: ReaderDictionary,
    private val translator: ReaderTranslator,
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

    /** What the floating selection toolbar is acting on: a new selection, or an existing highlight. */
    data class ToolbarTarget(
        val rect: ViewportRect?,
        val selection: PendingSelection? = null,
        val annotationId: Int? = null,
    )

    /** A word being looked up in the dictionary sheet; neither [result] nor [failed] once done means not found. */
    data class DefineState(
        val word: String,
        val loading: Boolean = true,
        val result: DictionaryResult? = null,
        val failed: Boolean = false,
    )

    /** A selection being translated in the translation sheet, into [target]. */
    data class TranslationState(
        val text: String,
        val target: String,
        val loading: Boolean = true,
        val result: TranslationResult? = null,
        val error: UiText? = null,
    )

    data class UiState(
        val loadingFile: Boolean = true,
        val error: UiText? = null,
        val title: String? = null,
        val resolved: ResolvedOpen? = null,
        val settings: ReaderSettings = ReaderSettings(),
        /** The @font-face rules for an uploaded family in [settings], or "". */
        val fontFaceCss: String = "",
        val pageTurn: PageTurn = PageTurn.SLIDE,
        /** True when this book has settings of its own on top of the defaults. */
        val bookCustomized: Boolean = false,
        /** True when reader settings are kept on the server and shared with the web reader. */
        val syncSettings: Boolean = false,
        val uploadedFonts: List<UploadedFontFamily> = emptyList(),
        val position: ReadingPosition = ReadingPosition(),
        /** Where each spine section starts, 0..1, for the scrubber's chapter ticks. */
        val sectionFractions: List<Double> = emptyList(),
        /** When this reading session began, for the footer's time-read mode. */
        val openedAt: Long = System.currentTimeMillis(),
        val toc: List<TocItem> = emptyList(),
        val chapterTitle: String? = null,
        val percentage: Int = 0,
        val loaded: Boolean = false,
        val showPagingHint: Boolean = false,
        val annotations: List<BookAnnotation> = emptyList(),
        val bookmarks: List<BookBookmark> = emptyList(),
        val search: SearchState? = null,
        val toolbar: ToolbarTarget? = null,
        /** The book's language as its metadata gives it, for dictionary lookups and reading aloud. */
        val language: String? = null,
        val define: DefineState? = null,
        val translation: TranslationState? = null,
        /** One-shot message for the screen to toast, then clear via [consumeMessage]. */
        val message: UiText? = null,
    )

    /** In-book search: the query, matches grouped by chapter as they stream in, and progress. */
    data class SearchState(
        val query: String = "",
        val running: Boolean = false,
        val progress: Double = 0.0,
        val groups: List<SearchGroup> = emptyList(),
        val total: Int = 0,
        val capped: Boolean = false,
        val done: Boolean = false,
    )

    data class SearchGroup(val label: String, val hits: List<SearchHit>)

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    private val saver = ProgressSaver<SavedPosition>(viewModelScope) { progress.save(it) }

    /** Where the reader is now, so a bookmark can be dropped there. */
    private var currentCfi: String? = null

    init {
        load()
        loadAnnotations()
    }

    private fun load() {
        viewModelScope.launch {
            // Device defaults until the book's file is known and its own settings can be read.
            _ui.update { it.copy(settings = settingsStore.load(), pageTurn = settingsStore.loadPageTurn()) }

            // Fall back to the offline-downloaded copy when the network is unavailable.
            val book = runCatching { bookRepo.detail(bookId) }.getOrNull() ?: downloads.cachedBook(bookId)
            if (book == null) {
                _ui.update { it.copy(loadingFile = false, error = UiText.of(R.string.reader_err_load_book)) }
                return@launch
            }
            _ui.update { it.copy(title = book.title) }

            try {
                val resolved = source.resolve(book)
                val loaded = preferences.load(resolved.fileId)
                val fontFace = fonts.fontFaceCss(loaded.settings.fontFamily)
                // Coach the paginated tap-zones once (they're invisible by design).
                val showHint = loaded.settings.flow == "paginated" && !settingsStore.hasSeenPagingHint()
                _ui.update {
                    it.copy(
                        settings = loaded.settings,
                        bookCustomized = loaded.bookCustomized,
                        syncSettings = loaded.sync,
                        fontFaceCss = fontFace,
                        showPagingHint = showHint,
                    )
                }
                val initial = progress.resolveInitial(resolved.fileId)
                sessions.beginReading(resolved.fileId, initial.fraction?.let { it * 100 })
                _ui.update {
                    it.copy(
                        loadingFile = false,
                        resolved = ResolvedOpen(resolved.ref, resolved.format, resolved.fileId, initial),
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loadingFile = false, error = e.message?.let { m -> UiText.raw(m) } ?: UiText.of(R.string.reader_err_open_book)) }
            }
        }
    }

    fun onLoaded(toc: List<TocItem>, title: String?, sectionFractions: List<Double> = emptyList(), language: String? = null) {
        _ui.update {
            it.copy(loaded = true, toc = toc, chapterTitle = it.chapterTitle ?: title, sectionFractions = sectionFractions, language = language)
        }
    }

    /** Keep the last reading position and reset page state before a fresh WebView is created. */
    fun onRendererGone() {
        _ui.update { state ->
            state.copy(
                loaded = false,
                toolbar = null,
                search = null,
                resolved = state.resolved?.let { resolved ->
                    resolved.copy(initial = InitialProgress(
                        currentCfi ?: resolved.initial.cfi,
                        if (state.loaded) state.percentage / 100.0 else resolved.initial.fraction,
                    ))
                },
            )
        }
    }

    fun onRelocate(cfi: String?, fraction: Double?, chapterTitle: String?, position: ReadingPosition = ReadingPosition()) {
        _ui.update {
            it.copy(
                position = position,
                chapterTitle = chapterTitle ?: it.chapterTitle,
                percentage = fraction?.let { f -> (f * 100).roundToInt() } ?: it.percentage,
            )
        }
        if (cfi != null) currentCfi = cfi
        if (cfi != null && fraction != null) {
            report(cfi, fraction * 100)
            sessions.onReadingProgress(fraction * 100)
        }
    }

    // --- in-book search ---

    fun startSearch(query: String) {
        val q = query.trim()
        _ui.update {
            it.copy(search = if (q.isEmpty()) null else SearchState(query = q, running = true))
        }
    }

    fun clearSearch() = _ui.update { it.copy(search = null) }

    fun onSearchResults(label: String, hits: List<SearchHit>) = _ui.update { s ->
        val cur = s.search ?: return@update s
        cur.copy(groups = cur.groups + SearchGroup(label, hits), total = cur.total + hits.size).let { s.copy(search = it) }
    }

    fun onSearchProgress(progress: Double) = _ui.update { s ->
        s.search?.let { s.copy(search = it.copy(progress = progress)) } ?: s
    }

    fun onSearchDone(total: Int, capped: Boolean) = _ui.update { s ->
        s.search?.let { s.copy(search = it.copy(running = false, done = true, total = total, capped = capped, progress = 1.0)) } ?: s
    }

    // --- bookmarks ---

    fun loadBookmarks() {
        viewModelScope.launch {
            runCatching { bookmarkRepo.list(bookId) }.onSuccess { list -> _ui.update { it.copy(bookmarks = list) } }
        }
    }

    /** Bookmark the current page, named after the chapter and how far through the book it is. */
    fun addBookmark() {
        val cfi = currentCfi ?: return
        val s = _ui.value
        val title = BookmarkNames.default(s.chapterTitle, s.percentage)
        viewModelScope.launch {
            runCatching { bookmarkRepo.add(bookId, cfi, title) }
                .onSuccess { created -> _ui.update { it.copy(bookmarks = it.bookmarks + created) } }
                .onFailure { _ui.update { it.copy(message = UiText.of(R.string.reader_err_bookmark_save)) } }
        }
    }

    fun deleteBookmark(id: Int) {
        viewModelScope.launch {
            runCatching { bookmarkRepo.delete(bookId, id) }
                .onSuccess { _ui.update { s -> s.copy(bookmarks = s.bookmarks.filterNot { it.id == id }) } }
                .onFailure { _ui.update { it.copy(message = UiText.of(R.string.reader_err_bookmark_delete)) } }
        }
    }

    // --- highlights & notes ---

    private fun loadAnnotations() {
        viewModelScope.launch {
            // Show what we already have straight away (cached server copy + queued changes), then
            // push anything queued and refresh from the server.
            _ui.update { it.copy(annotations = annotationRepo.local(bookId)) }
            syncAnnotations()
        }
    }

    /** Replay queued changes, then reload so placeholder ids are swapped for the server's. */
    private fun syncAnnotations() {
        viewModelScope.launch {
            annotationRepo.flushPending()
            _ui.update { it.copy(annotations = annotationRepo.load(bookId)) }
        }
    }

    private suspend fun refreshLocalAnnotations() {
        _ui.update { it.copy(annotations = annotationRepo.local(bookId), toolbar = null) }
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

    /** The text the toolbar is open on: the selection, or the highlight's own text. */
    fun selectedText(): String? {
        val s = _ui.value
        val target = s.toolbar ?: return null
        return target.selection?.text ?: target.annotationId?.let { id -> s.annotations.firstOrNull { it.id == id }?.text }
    }

    // --- define & translate ---

    private var defineJob: Job? = null
    private var translateJob: Job? = null

    /** Look the selected word up and show it in the dictionary sheet. */
    fun define() {
        val word = selectedText()?.takeIf { it.isNotBlank() } ?: return
        _ui.update { it.copy(toolbar = null) }
        lookUp(word)
    }

    fun retryDefine() = _ui.value.define?.let { lookUp(it.word) }

    fun closeDefine() {
        defineJob?.cancel()
        _ui.update { it.copy(define = null) }
    }

    private fun lookUp(word: String) {
        defineJob?.cancel()
        _ui.update { it.copy(define = DefineState(word)) }
        defineJob = viewModelScope.launch {
            val outcome = runCatching { dictionary.lookup(word, _ui.value.language) }
            _ui.update { s ->
                s.copy(define = s.define?.copy(loading = false, result = outcome.getOrNull(), failed = outcome.isFailure))
            }
        }
    }

    /** Translate the selection into the last language used and show it in the translation sheet. */
    fun translate() {
        val text = selectedText()?.trim()?.takeIf { it.isNotEmpty() } ?: return
        _ui.update { it.copy(toolbar = null) }
        viewModelScope.launch { runTranslation(text, settingsStore.loadTranslateTarget()) }
    }

    fun setTranslateTarget(code: String) {
        val text = _ui.value.translation?.text ?: return
        viewModelScope.launch { settingsStore.saveTranslateTarget(code) }
        runTranslation(text, code)
    }

    fun retryTranslate() = _ui.value.translation?.let { runTranslation(it.text, it.target) }

    fun closeTranslation() {
        translateJob?.cancel()
        _ui.update { it.copy(translation = null) }
    }

    private fun runTranslation(text: String, target: String) {
        translateJob?.cancel()
        if (text.length > ReaderTranslator.CHAR_LIMIT) {
            val error = UiText.of(R.string.translate_too_long, ReaderTranslator.CHAR_LIMIT)
            _ui.update { it.copy(translation = TranslationState(text, target, loading = false, error = error)) }
            return
        }
        _ui.update { it.copy(translation = TranslationState(text, target)) }
        translateJob = viewModelScope.launch {
            val outcome = runCatching { translator.translate(text, target) }
            _ui.update { s ->
                s.copy(
                    translation = s.translation?.copy(
                        loading = false,
                        result = outcome.getOrNull(),
                        error = if (outcome.isFailure) UiText.of(R.string.translate_failed) else null,
                    ),
                )
            }
        }
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    /** Highlight the selection in [color], or recolour the highlight the toolbar is open on. */
    fun highlight(color: String) = saveAnnotation(color = color, note = null)

    /** Save a note: on the open highlight, or on the selection as a new (yellow) highlight. */
    fun saveNote(note: String) = saveAnnotation(color = null, note = note)

    private fun saveAnnotation(color: String?, note: String?) {
        val target = _ui.value.toolbar ?: return
        val fileId = _ui.value.resolved?.fileId
        viewModelScope.launch {
            runCatching {
                when {
                    target.annotationId != null -> {
                        if (color != null) annotationRepo.setColor(bookId, target.annotationId, color)
                        if (note != null) annotationRepo.setNote(bookId, target.annotationId, note)
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
                }
            }.onSuccess {
                refreshLocalAnnotations()
                syncAnnotations()
            }.onFailure {
                _ui.update { s -> s.copy(message = UiText.of(R.string.reader_err_highlight_save)) }
            }
        }
    }

    fun deleteAnnotation(id: Int) {
        viewModelScope.launch {
            runCatching { annotationRepo.delete(bookId, id) }
                .onSuccess {
                    refreshLocalAnnotations()
                    syncAnnotations()
                }
                .onFailure { _ui.update { s -> s.copy(message = UiText.of(R.string.reader_err_highlight_delete)) } }
        }
    }

    fun onError(message: String) {
        _ui.update { it.copy(error = UiText.raw(message)) }
    }

    /** A change made in the reader: it becomes this book's own setting, and the server's when syncing. */
    fun updateSettings(settings: ReaderSettings) {
        val previous = _ui.value.settings
        val next = settings.sanitized()
        _ui.update { it.copy(settings = next, bookCustomized = true) }
        val fileId = _ui.value.resolved?.fileId ?: return
        viewModelScope.launch {
            if (next.fontFamily != previous.fontFamily) {
                val css = fonts.fontFaceCss(next.fontFamily)
                _ui.update { it.copy(fontFaceCss = css) }
            }
            preferences.updateBook(fileId, previous, next, _ui.value.syncSettings)
        }
    }

    /** How pages turn; a device setting for every book, never sent to the server. */
    fun setPageTurn(pageTurn: PageTurn) {
        _ui.update { it.copy(pageTurn = pageTurn) }
        viewModelScope.launch { settingsStore.savePageTurn(pageTurn) }
    }

    /** Footer: page and percent, then time read and left in the book, then chapter and time left in it. */
    fun cycleFooterMode() = updateSettings(_ui.value.settings.let { it.copy(footerDisplayMode = (it.footerDisplayMode + 1) % 3) })

    /** Back to the defaults for this book. */
    fun resetBookSettings() {
        val fileId = _ui.value.resolved?.fileId ?: return
        viewModelScope.launch {
            val defaults = preferences.resetBook(fileId, _ui.value.syncSettings)
            _ui.update { it.copy(settings = defaults, bookCustomized = false, fontFaceCss = fonts.fontFaceCss(defaults.fontFamily)) }
        }
    }

    /** Makes this book's settings the defaults for every book. */
    fun saveSettingsAsDefaults() {
        val fileId = _ui.value.resolved?.fileId ?: return
        val settings = _ui.value.settings
        viewModelScope.launch {
            preferences.saveAsDefaults(fileId, settings, _ui.value.syncSettings)
            _ui.update { it.copy(bookCustomized = false, message = UiText.of(R.string.reader_saved_as_defaults)) }
        }
    }

    /** Keeps reader settings on the server (shared with the web reader) or on this device only. */
    fun setSettingsSync(enabled: Boolean) {
        viewModelScope.launch {
            if (preferences.setSync(enabled)) {
                // Like the web's switch: nothing is copied now; the next change is the first one shared.
                _ui.update { it.copy(syncSettings = enabled) }
            } else {
                _ui.update { it.copy(message = UiText.of(R.string.reader_sync_failed)) }
            }
        }
    }

    /** The fonts uploaded to the server, for the font picker. */
    fun loadUploadedFonts() {
        viewModelScope.launch { _ui.update { it.copy(uploadedFonts = fonts.families()) } }
    }

    fun dismissPagingHint() {
        _ui.update { it.copy(showPagingHint = false) }
        viewModelScope.launch { settingsStore.markPagingHintSeen() }
    }

    private fun report(cfi: String?, percentage: Double) {
        val fileId = _ui.value.resolved?.fileId ?: return
        saver.offer(SavedPosition(fileId, cfi, percentage))
    }

    /** Save where the reader is now; the screen calls it when the app goes to the background. */
    fun flush() = saver.flush()

    override fun onCleared() {
        flush()
        sessions.end()
        super.onCleared()
    }
}
