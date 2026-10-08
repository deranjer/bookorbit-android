package com.bookorbit.feature.reader

import com.bookorbit.core.db.AnnotationOp
import com.bookorbit.core.db.PendingAnnotationOpEntity
import com.bookorbit.core.model.BookAnnotation

/**
 * The highlights to show: the last server copy with every still-queued local change applied on top, in
 * the order they were made. This is what lets a highlight appear instantly, offline or not, and
 * survive an app restart before it has reached the server.
 */
fun overlayPendingOps(base: List<BookAnnotation>, ops: List<PendingAnnotationOpEntity>): List<BookAnnotation> {
    var list = base
    for (op in ops) {
        list = when (op.kind) {
            AnnotationOp.CREATE -> list + BookAnnotation(
                id = op.annotationId,
                bookId = op.bookId,
                cfi = op.cfi,
                text = op.text.orEmpty(),
                color = op.color ?: ReaderAnnotationRepository.COLORS.first().second,
                style = ReaderAnnotationRepository.DEFAULT_STYLE,
                note = op.note?.takeIf { op.noteSet && it.isNotBlank() },
                chapterTitle = op.chapterTitle,
                origin = "web",
            )
            AnnotationOp.UPDATE -> list.map { a ->
                if (a.id != op.annotationId) a
                else a.copy(
                    color = op.color ?: a.color,
                    note = if (op.noteSet) op.note?.takeIf { it.isNotBlank() } else a.note,
                )
            }
            AnnotationOp.DELETE -> list.filterNot { it.id == op.annotationId }
            else -> list
        }
    }
    return list
}
