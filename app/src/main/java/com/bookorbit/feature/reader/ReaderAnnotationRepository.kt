package com.bookorbit.feature.reader

import com.bookorbit.core.model.BookAnnotation
import com.bookorbit.core.model.CreateAnnotation
import com.bookorbit.core.network.ApiService
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Highlights and notes for a book. Online-only for now: failures surface to the caller. */
@Singleton
class ReaderAnnotationRepository @Inject constructor(
    private val api: ApiService,
) {
    /** Only CFI-anchored annotations are usable in the EPUB-family reader (PDF ones carry a page rect). */
    suspend fun list(bookId: Int): List<BookAnnotation> =
        api.getAnnotations(bookId).filter { it.cfi != null }

    suspend fun create(
        bookId: Int,
        fileId: Int,
        cfi: String,
        text: String,
        color: String,
        note: String?,
        chapterTitle: String?,
    ): BookAnnotation = api.createAnnotation(
        bookId,
        CreateAnnotation(
            cfi = cfi,
            bookFileId = fileId,
            text = text,
            color = color,
            style = DEFAULT_STYLE,
            note = note,
            chapterTitle = chapterTitle,
        ),
    )

    suspend fun setColor(bookId: Int, id: Int, color: String): BookAnnotation =
        api.updateAnnotation(bookId, id, buildJsonObject { put("color", JsonPrimitive(color)) })

    /** A blank note clears it (sent as an explicit null, which the server accepts). */
    suspend fun setNote(bookId: Int, id: Int, note: String): BookAnnotation =
        api.updateAnnotation(
            bookId,
            id,
            buildJsonObject { put("note", if (note.isBlank()) JsonNull else JsonPrimitive(note)) },
        )

    suspend fun delete(bookId: Int, id: Int) = api.deleteAnnotation(bookId, id)

    companion object {
        const val DEFAULT_STYLE = "highlight"

        /** Same hues as the web reader's default highlight (`#FACC15`) and its siblings. */
        val COLORS: List<Pair<String, String>> = listOf(
            "yellow" to "#FACC15",
            "green" to "#4ADE80",
            "blue" to "#38BDF8",
            "pink" to "#F472B6",
            "orange" to "#FB923C",
        )
    }
}
