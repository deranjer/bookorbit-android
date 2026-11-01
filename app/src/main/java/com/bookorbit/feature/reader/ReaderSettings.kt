package com.bookorbit.feature.reader

import androidx.annotation.StringRes
import com.bookorbit.R
import kotlinx.serialization.Serializable

/**
 * Render settings for the foliate reader. Field names and values MUST match the web client's
 * `EpubReaderSettings` (packages/types/src/reader-settings.ts at v3.6.0): this is serialized
 * straight into the bridge's begin/applyStyles payloads, and field by field into the server's
 * `reader/preferences` rows, which the web reader reads back.
 */
@Serializable
data class ReaderSettings(
    val fontSize: Int = 16,
    val lineHeight: Double = 1.5,
    /** null (the book's own), a generic family the web also offers, a bundled face, or an uploaded one. */
    val fontFamily: String? = null,
    val fontWeight: Int = 400,
    val fontStyle: String = "normal", // "normal" | "italic"
    /** Space after each paragraph, in em; 0 leaves the book's own. */
    val paragraphSpacing: Double = 0.0,
    /** In em; null leaves the book's own. */
    val letterSpacing: Double? = null,
    val wordSpacing: Double? = null,
    val textIndent: Double? = null,
    val maxColumnCount: Int = 2,
    val gap: Double = 0.05,
    val maxInlineSize: Int = 720,
    val maxBlockSize: Int = 1440,
    val justify: Boolean = true,
    val hyphenate: Boolean = true,
    val isDark: Boolean = true,
    val themeName: String = "default",
    val flow: String = "paginated", // "paginated" | "scrolled"
    /** What the footer shows: 0 page and percent, 1 time read and left in the book, 2 chapter and time left in it. */
    val footerDisplayMode: Int = 0,
)

val FONT_SIZE_RANGE = 6..32
val LINE_HEIGHT_RANGE = 0.8..3.0
val PARAGRAPH_SPACING_RANGE = 0.0..2.0
val LETTER_SPACING_RANGE = 0.0..0.2
val WORD_SPACING_RANGE = 0.0..0.5
val TEXT_INDENT_RANGE = 0.0..4.0
val PAGE_WIDTH_RANGE = 400..1600
val COLUMN_COUNT_RANGE = 1..3
val COLUMN_GAP_RANGE = 0.0..0.2

/**
 * A choice in the font picker. [value] is what is stored and synced. [css] is the stack the page is
 * actually set in on this device: the generic families the web offers are drawn with bundled faces
 * here (Literata for serif, Atkinson Hyperlegible for sans), since Android's own are Noto and Roboto.
 */
data class FontFamilyOption(@StringRes val label: Int, val value: String?, val css: String?)

/** Faces shipped in assets/reader/fonts, declared by @font-face in bridge.js. */
val FONT_FAMILIES = listOf(
    FontFamilyOption(R.string.font_original, null, null),
    FontFamilyOption(R.string.font_serif, "serif", "\"BO Literata\", serif"),
    FontFamilyOption(R.string.font_sans, "sans-serif", "\"BO Atkinson Hyperlegible\", sans-serif"),
    FontFamilyOption(R.string.font_monospace, "monospace", "monospace"),
    // Only on this app: the web reader has no such face and falls back to its default for them.
    FontFamilyOption(R.string.reader_font_bitter, "\"BO Bitter\", serif", "\"BO Bitter\", serif"),
    FontFamilyOption(R.string.reader_font_opendyslexic, "\"BO OpenDyslexic\", sans-serif", "\"BO OpenDyslexic\", sans-serif"),
)

/** The CSS font-family to set the page in for a stored [value]; uploaded fonts are already CSS names. */
fun fontCss(value: String?): String? =
    FONT_FAMILIES.firstOrNull { it.value == value }?.css ?: value?.let { if (it.startsWith("\"")) it else "\"$it\"" }

fun clampFontSize(v: Int): Int = v.coerceIn(FONT_SIZE_RANGE.first, FONT_SIZE_RANGE.last)

fun clampLineHeight(v: Double): Double =
    (Math.round(v * 10) / 10.0).coerceIn(LINE_HEIGHT_RANGE.start, LINE_HEIGHT_RANGE.endInclusive)

/** Pulls every field back into the range the web client accepts, so a synced value can't break either side. */
fun ReaderSettings.sanitized(): ReaderSettings = copy(
    fontSize = clampFontSize(fontSize),
    lineHeight = clampLineHeight(lineHeight),
    fontWeight = fontWeight.coerceIn(100, 900) / 100 * 100,
    fontStyle = if (fontStyle == "italic") "italic" else "normal",
    paragraphSpacing = paragraphSpacing.coerceIn(PARAGRAPH_SPACING_RANGE),
    letterSpacing = letterSpacing?.coerceIn(LETTER_SPACING_RANGE),
    wordSpacing = wordSpacing?.coerceIn(WORD_SPACING_RANGE),
    textIndent = textIndent?.coerceIn(TEXT_INDENT_RANGE),
    maxColumnCount = maxColumnCount.coerceIn(1, 10),
    gap = gap.coerceIn(0.0, 0.5),
    maxInlineSize = maxInlineSize.coerceIn(PAGE_WIDTH_RANGE),
    maxBlockSize = maxBlockSize.coerceIn(600, 2400),
    flow = if (flow == "scrolled") "scrolled" else "paginated",
    footerDisplayMode = footerDisplayMode.coerceIn(0, 2),
)
