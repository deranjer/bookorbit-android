package com.bookorbit.feature.reader

import kotlinx.serialization.Serializable

/**
 * Render settings for the foliate reader. Field names MUST match what the WebView bridge
 * (assets/reader/bridge.js) reads, since this is serialized straight into the begin/applyStyles
 * payloads. Mirrors the web client's ReaderState.
 */
@Serializable
data class ReaderSettings(
    val fontSize: Int = 16,
    val lineHeight: Double = 1.5,
    val fontFamily: String? = null,
    val maxColumnCount: Int = 2,
    val gap: Double = 0.05,
    val maxInlineSize: Int = 720,
    val maxBlockSize: Int = 1440,
    val justify: Boolean = true,
    val hyphenate: Boolean = true,
    val isDark: Boolean = true,
    val themeName: String = "default",
    val flow: String = "paginated", // "paginated" | "scrolled"
)

val FONT_SIZE_RANGE = 10..32
val LINE_HEIGHT_RANGE = 0.8..3.0

data class FontFamilyOption(val label: String, val value: String?)

val FONT_FAMILIES = listOf(
    FontFamilyOption("Original", null),
    FontFamilyOption("Serif", "Georgia, \"Times New Roman\", serif"),
    FontFamilyOption("Sans", "-apple-system, \"Helvetica Neue\", Arial, sans-serif"),
    FontFamilyOption("Monospace", "Menlo, Consolas, monospace"),
)

fun clampFontSize(v: Int): Int = v.coerceIn(FONT_SIZE_RANGE.first, FONT_SIZE_RANGE.last)

fun clampLineHeight(v: Double): Double =
    (Math.round(v * 10) / 10.0).coerceIn(LINE_HEIGHT_RANGE.start, LINE_HEIGHT_RANGE.endInclusive)
