package com.bookorbit.feature.reader

import com.bookorbit.feature.reader.comic.ComicPaging
import org.junit.Assert.*
import org.junit.Test

class ComicOcrSelectionTest {
    @Test fun `sentence selection respects punctuation and bubble boundaries`() {
        val words = listOf("Hello", "world!", "Next", "sentence.", "Another", "bubble")
            .mapIndexed { i, text -> OcrWord(text, i.toFloat(), 0f, i + 1f, 1f, if (i < 4) 0 else 1) }
        val page = OcrPage(100, 100, words)
        assertEquals(0..1, page.sentenceAt(0))
        assertEquals(2..3, page.sentenceAt(3))
        assertEquals(4..5, page.sentenceAt(5))
        assertEquals(IntRange.EMPTY, page.sentenceAt(20))
    }
    @Test fun `legacy CBZ CFI migrates to the same image page`() {
        assertEquals(2, ComicPaging.pageForCheckpoint("epubcfi(/6/6!/4/2)", null, 0.0, 10))
        assertEquals(2, ComicPaging.pageForCheckpoint(ComicPaging.cfiForPage(2), null, 0.0, 10))
        assertEquals(4, ComicPaging.pageForCheckpoint(null, 5, 0.0, 10))
        assertEquals(9, ComicPaging.pageForCheckpoint(null, 500, null, 10))
    }
}
