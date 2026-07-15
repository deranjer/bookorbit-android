package com.bookorbit.feature.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.bookorbit.core.storage.LocalRef
import com.bookorbit.core.storage.openParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Wraps [android.graphics.pdf.PdfRenderer] over a local file for the native PDF reader. PdfRenderer
 * permits only one open [PdfRenderer.Page] at a time, so every page access is serialized behind a
 * [Mutex] and runs off the main thread. Rendered bitmaps are kept in a small LRU cache keyed by
 * page + width + rotation; evicted bitmaps are left for the GC (they may still be on screen).
 */
class PdfRenderCore private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) {
    private val mutex = Mutex()
    private var closed = false

    val pageCount: Int = renderer.pageCount

    private val cache = LruCache<String, Bitmap>(CACHE_ENTRIES)

    /** Intrinsic page size in PDF points (pre-rotation). Used to lay out placeholders before render. */
    data class PageSize(val width: Int, val height: Int)

    suspend fun pageSize(index: Int): PageSize = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (closed || index !in 0 until pageCount) return@withContext PageSize(1, 1)
            renderer.openPage(index).use { PageSize(it.width.coerceAtLeast(1), it.height.coerceAtLeast(1)) }
        }
    }

    /**
     * Render [index] to a bitmap [targetWidthPx] wide in its displayed orientation (height follows
     * the page aspect ratio), rotated clockwise by [rotationDegrees] (0/90/180/270).
     */
    suspend fun renderPage(index: Int, targetWidthPx: Int, rotationDegrees: Int = 0): Bitmap? {
        if (index !in 0 until pageCount || targetWidthPx <= 0) return null
        val rotate = (((rotationDegrees % 360) + 360) % 360)
        val key = "$index@$targetWidthPx@$rotate"
        cache.get(key)?.let { return it }
        return mutex.withLock {
            cache.get(key)?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                if (closed) return@withContext null
                renderer.openPage(index).use { page ->
                    val bitmap = renderToBitmap(page, targetWidthPx, rotate)
                    cache.put(key, bitmap)
                    bitmap
                }
            }
        }
    }

    private fun renderToBitmap(page: PdfRenderer.Page, targetWidthPx: Int, rotate: Int): Bitmap {
        val (bmpW, bmpH) = PdfLayout.displayedSize(page.width, page.height, targetWidthPx, rotate)
        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        // PDFs assume white paper; the page may be transparent, so paint a white backdrop first.
        bitmap.eraseColor(Color.WHITE)
        val matrix = displayMatrix(page.width, page.height, targetWidthPx, rotate)
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    // ---- API 35+ text layer (search / links / page text). Empty/no-op below API 35. ----

    private val hasTextLayer: Boolean get() = Build.VERSION.SDK_INT >= 35

    suspend fun searchPage(index: Int, query: String): List<PdfTextMatch> = withPage(index) { page ->
        if (!hasTextLayer || query.isBlank()) emptyList() else PdfTextLayer.search(page, query)
    } ?: emptyList()

    suspend fun pageLinks(index: Int): List<PdfLink> = withPage(index) { page ->
        if (!hasTextLayer) emptyList() else PdfTextLayer.links(page)
    } ?: emptyList()

    suspend fun pageText(index: Int): String = withPage(index) { page ->
        if (!hasTextLayer) "" else PdfTextLayer.text(page)
    } ?: ""

    private suspend fun <T> withPage(index: Int, block: (PdfRenderer.Page) -> T): T? {
        if (index !in 0 until pageCount) return null
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                if (closed) null else renderer.openPage(index).use(block)
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        cache.evictAll()
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }

    companion object {
        private const val CACHE_ENTRIES = 6

        /** Transform from page-point space into displayed pixel space (scale → rotate → translate). */
        fun displayMatrix(pageW: Int, pageH: Int, targetWidthPx: Int, rotate: Int): Matrix {
            val (bmpW, bmpH) = PdfLayout.displayedSize(pageW, pageH, targetWidthPx, rotate)
            val swap = rotate == 90 || rotate == 270
            val dispW = if (swap) pageH else pageW
            val scale = targetWidthPx / dispW.toFloat().coerceAtLeast(1f)
            return Matrix().apply {
                postScale(scale, scale)
                postRotate(rotate.toFloat())
                when (rotate) {
                    90 -> postTranslate(bmpW.toFloat(), 0f)
                    180 -> postTranslate(bmpW.toFloat(), bmpH.toFloat())
                    270 -> postTranslate(0f, bmpH.toFloat())
                }
            }
        }

        /** Map a page-space rect (text/link/match bounds) into displayed pixel coordinates. */
        fun mapPageRect(rect: RectF, pageW: Int, pageH: Int, targetWidthPx: Int, rotate: Int): RectF {
            val out = RectF(rect)
            displayMatrix(pageW, pageH, targetWidthPx, rotate).mapRect(out)
            return out
        }

        /** Open a PDF from a local file/SAF document. Throws if it's not a valid/openable PDF. */
        suspend fun open(context: Context, ref: LocalRef): PdfRenderCore = withContext(Dispatchers.IO) {
            val pfd = ref.openParcelFileDescriptor(context, "r")
            val renderer = try {
                PdfRenderer(pfd)
            } catch (e: Throwable) {
                runCatching { pfd.close() }
                throw e
            }
            PdfRenderCore(pfd, renderer)
        }
    }
}
