package com.bookorbit.feature.player

import com.bookorbit.R
import com.bookorbit.ui.UiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.model.AudiobookBookmark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Thin ViewModel exposing the shared [PlayerManager] to the player screen, mini-player, and detail. */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val manager: PlayerManager,
    private val bookmarkRepo: AudiobookBookmarkRepository,
) : ViewModel() {
    val state = manager.state

    private val _bookmarks = MutableStateFlow<List<AudiobookBookmark>>(emptyList())
    val bookmarks = _bookmarks.asStateFlow()

    /** One-shot message for the screen to show, cleared with [consumeMessage]. */
    private val _message = MutableStateFlow<UiText?>(null)
    val message = _message.asStateFlow()

    fun loadAndPlay(bookId: Int) = manager.loadAndPlay(bookId)
    fun restoreLastBook() = manager.restoreLastBook()
    fun refreshIfStale() = manager.refreshIfStale()
    fun togglePlay() = manager.togglePlay()
    fun skipBack() = manager.skipBack()
    fun skipForward() = manager.skipForward()
    fun seekToAbsolute(absoluteSec: Double) = manager.seekToAbsolute(absoluteSec)
    fun setSpeed(value: Float, forThisBook: Boolean = false) = manager.setSpeed(value, forThisBook)
    fun setSleepTimer(minutes: Int) = manager.setSleepTimer(minutes)
    fun setSleepTimerEndOfChapter() = manager.setSleepTimerEndOfChapter()
    fun cancelSleepTimer() = manager.cancelSleepTimer()
    fun stop() = manager.stop()

    fun consumeMessage() = _message.update { null }

    /** Load the current book's bookmarks from the server (kept as-is if the call fails, e.g. offline). */
    fun loadBookmarks() {
        val bookId = state.value.currentBook?.id ?: return
        viewModelScope.launch {
            runCatching { bookmarkRepo.list(bookId) }.onSuccess { list -> _bookmarks.value = list }
        }
    }

    /** Bookmark the current position in the whole book, named after the chapter and time. */
    fun addBookmark() {
        val s = state.value
        val bookId = s.currentBook?.id ?: return
        val chapter = PlaybackQueue.currentChapterIndex(s.chapters, s.positionSec).let { s.chapters.getOrNull(it)?.title }
        val title = BookmarkTitles.default(chapter, s.positionSec)
        viewModelScope.launch {
            runCatching { bookmarkRepo.add(bookId, s.positionSec, title) }
                .onSuccess { created -> _bookmarks.update { (it + created).sortedBy { b -> b.positionMs } } }
                .onFailure { _message.value = UiText.of(R.string.player_bookmark_err_save) }
        }
    }

    fun deleteBookmark(id: String) {
        val bookId = state.value.currentBook?.id ?: return
        viewModelScope.launch {
            runCatching { bookmarkRepo.delete(bookId, id) }
                .onSuccess { _bookmarks.update { list -> list.filterNot { it.id == id } } }
                .onFailure { _message.value = UiText.of(R.string.player_bookmark_err_delete) }
        }
    }
}
