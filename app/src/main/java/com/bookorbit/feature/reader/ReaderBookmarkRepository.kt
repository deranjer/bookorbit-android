package com.bookorbit.feature.reader

import com.bookorbit.core.model.BookBookmark
import com.bookorbit.core.model.CreateBookBookmark
import com.bookorbit.core.network.ApiService
import javax.inject.Inject
import javax.inject.Singleton

/** Ebook bookmarks, shared with the web reader and other clients. */
@Singleton
class ReaderBookmarkRepository @Inject constructor(
    private val api: ApiService,
) {
    /** Only CFI bookmarks apply to the reader; the same endpoint also returns audiobook ones. */
    suspend fun list(bookId: Int): List<BookBookmark> = api.getBookmarks(bookId).filter { it.cfi != null }

    suspend fun add(bookId: Int, cfi: String, title: String): BookBookmark =
        api.createBookmark(bookId, CreateBookBookmark(cfi = cfi, title = title))

    suspend fun delete(bookId: Int, id: Int) = api.deleteBookmark(bookId, id)
}

object BookmarkNames {
    /** "Chapter 4 · 37%", or just "37%" when the book has no chapter titles. */
    fun default(chapterTitle: String?, percent: Int): String {
        val chapter = chapterTitle?.trim()?.takeIf { it.isNotEmpty() } ?: return "$percent%"
        return "$chapter · $percent%"
    }
}
