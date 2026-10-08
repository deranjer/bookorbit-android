package com.bookorbit.feature.reader

import androidx.annotation.StringRes
import com.bookorbit.R
import androidx.compose.ui.graphics.Color

/**
 * Theme swatches for the settings UI. Names + colors mirror the web client and the THEMES table
 * inside assets/reader/bridge.js (which actually styles the text). Keep these three in sync (the
 * three-way sync noted in CLAUDE.md).
 */
data class ReaderTheme(
    val name: String,
    @StringRes val label: Int,
    val lightFg: Long,
    val lightBg: Long,
    val darkFg: Long,
    val darkBg: Long,
)

val READER_THEMES = listOf(
    ReaderTheme("default", R.string.reader_theme_default, 0xFF000000, 0xFFFFFFFF, 0xFFE0E0E0, 0xFF222222),
    ReaderTheme("gray", R.string.reader_theme_gray, 0xFF222222, 0xFFE0E0E0, 0xFFC6C6C6, 0xFF444444),
    ReaderTheme("sepia", R.string.reader_theme_sepia, 0xFF5B4636, 0xFFF1E8D0, 0xFFFFD595, 0xFF342E25),
    ReaderTheme("crimson", R.string.reader_theme_crimson, 0xFF2F1F25, 0xFFFDF1F4, 0xFFF3DBE2, 0xFF3A252D),
    ReaderTheme("meadow", R.string.reader_theme_meadow, 0xFF232C16, 0xFFD7DBBD, 0xFFD8DEBA, 0xFF333627),
    ReaderTheme("rosewood", R.string.reader_theme_rosewood, 0xFF4E1609, 0xFFF0D1D5, 0xFFE5C4C8, 0xFF462F32),
    ReaderTheme("azure", R.string.reader_theme_azure, 0xFF262D48, 0xFFCEDEF5, 0xFFBABEE1, 0xFF282E47),
    ReaderTheme("dawnlight", R.string.reader_theme_dawnlight, 0xFF586E75, 0xFFFDF6E3, 0xFF93A1A1, 0xFF002B36),
    ReaderTheme("ember", R.string.reader_theme_ember, 0xFF3C3836, 0xFFFBF1C7, 0xFFEBDBB2, 0xFF282828),
    ReaderTheme("aurora", R.string.reader_theme_aurora, 0xFF2E3440, 0xFFECEFF4, 0xFFD8DEE9, 0xFF2E3440),
    ReaderTheme("ocean", R.string.reader_theme_ocean, 0xFF0A4D4D, 0xFFE0F7FA, 0xFFB2DFDB, 0xFF263238),
    ReaderTheme("mist", R.string.reader_theme_mist, 0xFF4A148C, 0xFFF3E5F5, 0xFFC7B6DD, 0xFF3A3150),
    ReaderTheme("amoled", R.string.reader_theme_amoled, 0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000),
)

private val THEME_BY_NAME = READER_THEMES.associateBy { it.name }

/** Background color a theme renders for the given mode — used to tint the reader surface. */
fun themeBackgroundColor(themeName: String, isDark: Boolean): Color {
    val theme = THEME_BY_NAME[themeName] ?: READER_THEMES.first()
    return Color(if (isDark) theme.darkBg else theme.lightBg)
}
