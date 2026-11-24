package com.bookorbit.feature.reader

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class OcrWord(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float, val paragraph: Int)
data class OcrPage(val width: Int, val height: Int, val words: List<OcrWord>)

/** Offline, bundled English/Russian models. Native instances stay on one worker at a time. */
@Singleton
class ComicOcr @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()

    suspend fun recognize(bitmap: Bitmap): OcrPage = withContext(Dispatchers.Default) { mutex.withLock {
        val directory = File(context.filesDir, "ocr-v1")
        val data = File(directory, "tessdata").apply { mkdirs() }
        for (language in listOf("eng", "rus")) {
            val dest = File(data, "$language.traineddata")
            if (!dest.exists()) {
                val part = File(data, "$language.part")
                try {
                    context.assets.open("ocr/$language.traineddata").use { input -> part.outputStream().use { input.copyTo(it) } }
                    check(part.renameTo(dest))
                } finally { part.delete() }
            }
        }
        currentCoroutineContext().ensureActive()
        val factor = minOf(1.0, 2400.0 / maxOf(bitmap.width, bitmap.height))
        val software = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val image = if (factor < 1.0) Bitmap.createScaledBitmap(software, (bitmap.width * factor).toInt().coerceAtLeast(1),
            (bitmap.height * factor).toInt().coerceAtLeast(1), true) else software
        val api = TessBaseAPI()
        try {
            check(api.init(directory.absolutePath, "eng+rus", TessBaseAPI.OEM_LSTM_ONLY))
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
            api.setImage(image)
            api.getUTF8Text()
            currentCoroutineContext().ensureActive()
            val words = mutableListOf<OcrWord>()
            val iterator = api.resultIterator
            if (iterator != null) try {
                var paragraph = -1
                iterator.begin()
                do {
                    if (iterator.isAtBeginningOf(TessBaseAPI.PageIteratorLevel.RIL_PARA)) paragraph++
                    val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)?.trim().orEmpty()
                    val box = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    val confidence = iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    if (acceptOcrWord(text, confidence, box.width(), box.height())) words += OcrWord(text,
                        box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat(), paragraph)
                } while (words.size < 10_000 && iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
            } finally { iterator.delete() }
            OcrPage(image.width, image.height, words)
        } finally {
            api.recycle()
            if (image !== software) image.recycle()
            if (software !== bitmap) software.recycle()
        }
    } }
}

/** Reject weak artwork guesses, punctuation-only fragments and degenerate boxes. */
internal fun acceptOcrWord(text: String, confidence: Float, width: Int, height: Int): Boolean =
    confidence.isFinite() && confidence >= 65f && text.any(Char::isLetterOrDigit) &&
        width >= 3 && height >= 5 && width.toFloat() / height in 0.08f..30f

/** Conservative spatial component: nearby words/lines, never every index between two endpoints.
 * This is not a bubble detector; ambiguous neighbouring bubbles still need artwork acceptance.
 */
fun OcrPage.textGroupAt(index: Int): Set<Int> {
    if (index !in words.indices) return emptySet()
    val selected = linkedSetOf(index)
    val queue = ArrayDeque<Int>().apply { add(index) }
    while (queue.isNotEmpty()) {
        val a = words[queue.removeFirst()]
        val ah = a.bottom - a.top
        for ((i, b) in words.withIndex()) {
            if (i in selected) continue
            val bh = b.bottom - b.top
            val h = minOf(ah, bh)
            if (h <= 0 || maxOf(ah, bh) > h * 2) continue
            val verticalOverlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            val horizontalOverlap = minOf(a.right, b.right) - maxOf(a.left, b.left)
            val horizontalGap = maxOf(a.left, b.left) - minOf(a.right, b.right)
            val verticalGap = maxOf(a.top, b.top) - minOf(a.bottom, b.bottom)
            val sameLine = verticalOverlap > h * 0.5f && horizontalGap < h * 0.9f
            val nextLine = horizontalOverlap > 0 && verticalGap in 0f..(h * 0.8f)
            if (sameLine || nextLine) { selected.add(i); queue.add(i) }
        }
    }
    return selected
}

fun OcrPage.selectionBetween(anchor: Int, end: Int): Set<Int> {
    val group = textGroupAt(anchor)
    if (end !in group) return setOf(anchor).intersect(group)
    return group.filterTo(linkedSetOf()) { it in minOf(anchor, end)..maxOf(anchor, end) }
}

fun OcrPage.sentenceWordsAt(index: Int): Set<Int> {
    val group = textGroupAt(index).sorted()
    val position = group.indexOf(index)
    if (position < 0) return emptySet()
    fun ends(i: Int) = words[i].text.trimEnd('"', '\'', '»', '”', ')').lastOrNull() in listOf('.', '!', '?', '。', '！', '？')
    var first = position
    var last = position
    while (first > 0 && !ends(group[first - 1])) first--
    while (last < group.lastIndex && !ends(group[last])) last++
    return group.subList(first, last + 1).toSet()
}

/** A sentence stays inside its detected paragraph/bubble; unknown punctuation selects the paragraph. */
fun OcrPage.sentenceAt(index: Int): IntRange {
    val word = words.getOrNull(index) ?: return IntRange.EMPTY
    fun endsSentence(text: String) = text.trimEnd('"', '\'', '»', '”', ')').lastOrNull() in listOf('.', '!', '?', '。', '！', '？')
    var first = index
    var last = index
    while (first > 0 && words[first - 1].paragraph == word.paragraph && !endsSentence(words[first - 1].text)) first--
    while (last < words.lastIndex && words[last + 1].paragraph == word.paragraph && !endsSentence(words[last].text)) last++
    return first..last
}
