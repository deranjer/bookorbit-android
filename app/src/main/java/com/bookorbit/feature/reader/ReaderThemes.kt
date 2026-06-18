package com.bookorbit.feature.reader

import androidx.compose.ui.graphics.Color

/**
 * Theme swatches for the settings UI. Names + colors mirror the web client and the THEMES table
 * inside assets/reader/bridge.js (which actually styles the text). Keep these three in sync (the
 * three-way sync noted in CLAUDE.md).
 */
data class ReaderTheme(
    val name: String,
    val label: String,
    val lightFg: Long,
    val lightBg: Long,
    val darkFg: Long,
    val darkBg: Long,
)

val READER_THEMES = listOf(
    ReaderTheme("default", "Default", 0xFF000000, 0xFFFFFFFF, 0xFFE0E0E0, 0xFF222222),
    ReaderTheme("gray", "Gray", 0xFF222222, 0xFFE0E0E0, 0xFFC6C6C6, 0xFF444444),
    ReaderTheme("sepia", "Sepia", 0xFF5B4636, 0xFFF1E8D0, 0xFFFFD595, 0xFF342E25),
    ReaderTheme("crimson", "Crimson", 0xFF2F1F25, 0xFFFDF1F4, 0xFFF3DBE2, 0xFF3A252D),
    ReaderTheme("meadow", "Meadow", 0xFF232C16, 0xFFD7DBBD, 0xFFD8DEBA, 0xFF333627),
    ReaderTheme("rosewood", "Rosewood", 0xFF4E1609, 0xFFF0D1D5, 0xFFE5C4C8, 0xFF462F32),
    ReaderTheme("azure", "Azure", 0xFF262D48, 0xFFCEDEF5, 0xFFBABEE1, 0xFF282E47),
    ReaderTheme("dawnlight", "Dawnlight", 0xFF586E75, 0xFFFDF6E3, 0xFF93A1A1, 0xFF002B36),
    ReaderTheme("ember", "Ember", 0xFF3C3836, 0xFFFBF1C7, 0xFFEBDBB2, 0xFF282828),
    ReaderTheme("aurora", "Aurora", 0xFF2E3440, 0xFFECEFF4, 0xFFD8DEE9, 0xFF2E3440),
    ReaderTheme("ocean", "Ocean", 0xFF0A4D4D, 0xFFE0F7FA, 0xFFB2DFDB, 0xFF263238),
    ReaderTheme("mist", "Mist", 0xFF4A148C, 0xFFF3E5F5, 0xFFC7B6DD, 0xFF3A3150),
    ReaderTheme("amoled", "AMOLED", 0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000),
)

private val THEME_BY_NAME = READER_THEMES.associateBy { it.name }

/** Background color a theme renders for the given mode — used to tint the reader surface. */
fun themeBackgroundColor(themeName: String, isDark: Boolean): Color {
    val theme = THEME_BY_NAME[themeName] ?: READER_THEMES.first()
    return Color(if (isDark) theme.darkBg else theme.lightBg)
}
