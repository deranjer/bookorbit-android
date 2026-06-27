package com.bookorbit.feature.reader.pdf

import kotlinx.serialization.Serializable

/**
 * Render settings for the native PDF reader. Mirrors the web client's `PdfReaderSettings`
 * (packages/types/src/reader-settings.ts): scroll mode, page spread, zoom mode + custom scale,
 * and rotation. Kept independent from the foliate [com.bookorbit.feature.reader.ReaderSettings].
 */
@Serializable
data class PdfReaderSettings(
    val scrollMode: ScrollMode = ScrollMode.CONTINUOUS,
    val spread: Spread = Spread.NONE,
    val zoomMode: ZoomMode = ZoomMode.FIT_WIDTH,
    val customScale: Double = 1.0,
    val rotation: Int = 0, // 0 | 90 | 180 | 270, clockwise
) {
    enum class ScrollMode { PAGINATED, CONTINUOUS }

    enum class Spread { NONE, ODD, EVEN }

    enum class ZoomMode { FIT_WIDTH, FIT_PAGE, CUSTOM }

    fun rotatedCw(): PdfReaderSettings = copy(rotation = (rotation + 90) % 360)

    fun withCustomScale(scale: Double): PdfReaderSettings =
        copy(zoomMode = ZoomMode.CUSTOM, customScale = clampScale(scale))
}

val PDF_SCALE_RANGE = 0.25..4.0

fun clampScale(v: Double): Double =
    (Math.round(v * 100) / 100.0).coerceIn(PDF_SCALE_RANGE.start, PDF_SCALE_RANGE.endInclusive)
