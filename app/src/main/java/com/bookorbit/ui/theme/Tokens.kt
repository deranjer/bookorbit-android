package com.bookorbit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * The web client's design tokens (`client/src/assets/theme/tokens.css` at the server version the
 * app targets), so the app and the web reader look like one product. Neutrals are tinted from one
 * hue and chroma ([tintH], [tintC]): the default is a faint warm amber, and an accent picker only
 * has to change those two and [primary].
 *
 * Material components read these through the [androidx.compose.material3.ColorScheme] built in
 * [BookOrbitTheme]; app code that needs a role Material has no slot for (success, warning, format
 * colours) reads [BookOrbitTheme.tokens].
 */
@Immutable
data class BookOrbitTokens(
    val isDark: Boolean,
    val tintH: Float,
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardForeground: Color,
    val popover: Color,
    val primary: Color,
    val primaryForeground: Color,
    val secondary: Color,
    val secondaryForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val accent: Color,
    val accentForeground: Color,
    val destructive: Color,
    val destructiveForeground: Color,
    val success: Color,
    val successForeground: Color,
    val warning: Color,
    val warningForeground: Color,
    val info: Color,
    val infoForeground: Color,
    val starHighlight: Color,
    val border: Color,
    val input: Color,
    val ring: Color,
    val sidebar: Color,
    /** Elevation surfaces, lowest to highest. [surface1] is the card colour. */
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val surface4: Color,
    /** The veil behind dialogs and sheets; [scrimMedia] sits behind image viewers. */
    val scrim: Color,
    val scrimMedia: Color,
    /** Chips and buttons laid over cover art, which can be any colour. */
    val coverChip: Color,
    val coverChipForeground: Color,
    /** Primary washed onto the page at 6%, 12% and 30%: selected rows, active navigation, borders. */
    val accentWash: Color,
    val accentTint: Color,
    val accentLine: Color,
    val chipTagBackground: Color,
    val chipTagForeground: Color,
    val pills: Pills,
) {
    /** Fixed hues that keep their meaning under every accent, tuned per theme to clear AA as text. */
    @Immutable
    data class Pills(
        val formatDefault: Color,
        val format: Map<String, Color>,
        val ebook: Color,
        val audiobook: Color,
        val comic: Color,
        val pending: Color,
        /** Distinct hues for series in a chart, in order. */
        val series: List<Color>,
    )

    /** A file format's colour (`epub`, `pdf`, `m4b`, ...), as the web client colours format chips. */
    fun formatColor(format: String?): Color = pills.format[format?.lowercase()] ?: pills.formatDefault
}

/** The web client's default look: no accent class, so a near-black (light) or near-white (dark) primary. */
private const val DEFAULT_TINT_H = 80f

fun lightTokens(tintH: Float = DEFAULT_TINT_H, tintC: Float = 0.006f): BookOrbitTokens {
    fun t(l: Float, alpha: Float = 1f) = oklch(l, tintC, tintH, alpha)
    val background = t(0.99f)
    val primary = t(0.21f)
    return BookOrbitTokens(
        isDark = false,
        tintH = tintH,
        background = background,
        foreground = t(0.145f),
        card = t(0.975f),
        cardForeground = t(0.145f),
        popover = t(0.975f),
        primary = primary,
        primaryForeground = oklch(0.985f, 0.004f, tintH),
        secondary = t(0.955f),
        secondaryForeground = t(0.21f),
        muted = t(0.955f),
        mutedForeground = oklch(0.52f, 0.001f, tintH),
        accent = t(0.955f),
        accentForeground = t(0.21f),
        destructive = oklch(0.577f, 0.245f, 27.325f),
        destructiveForeground = oklch(0.985f, 0f, 0f),
        success = oklch(0.52f, 0.14f, 148f),
        successForeground = oklch(0.985f, 0f, 0f),
        warning = oklch(0.52f, 0.13f, 68f),
        warningForeground = oklch(0.985f, 0f, 0f),
        info = oklch(0.55f, 0.12f, 255f),
        infoForeground = oklch(0.985f, 0f, 0f),
        starHighlight = oklch(0.76f, 0.18f, 90f),
        border = t(0.878f),
        input = t(0.878f),
        ring = t(0.65f),
        sidebar = t(0.965f),
        surface1 = t(0.975f),
        surface2 = t(0.955f),
        surface3 = t(0.93f),
        surface4 = t(0.91f),
        scrim = oklch(0.2f, 0.02f, tintH, 0.35f),
        scrimMedia = oklch(0f, 0f, 0f, 0.82f),
        coverChip = oklch(0.16f, 0.01f, tintH, 0.72f),
        coverChipForeground = oklch(0.985f, 0.002f, tintH),
        accentWash = primary.copy(alpha = 0.06f).compositeOver(background),
        accentTint = primary.copy(alpha = 0.12f).compositeOver(background),
        accentLine = primary.copy(alpha = 0.30f).compositeOver(background),
        chipTagBackground = oklch(0.769f, 0.188f, 70.08f, 0.1f),
        chipTagForeground = oklch(0.52f, 0.13f, 68f),
        pills = BookOrbitTokens.Pills(
            formatDefault = oklch(0.55f, 0.02f, 260f),
            format = mapOf(
                "epub" to oklch(0.55f, 0.14f, 150f),
                "kepub" to oklch(0.55f, 0.12f, 178f),
                "mobi" to oklch(0.55f, 0.18f, 275f),
                "azw3" to oklch(0.53f, 0.11f, 185f),
                "azw" to oklch(0.55f, 0.14f, 235f),
                "fb2" to oklch(0.55f, 0.17f, 355f),
                "djvu" to oklch(0.55f, 0.04f, 255f),
                "txt" to oklch(0.55f, 0.03f, 60f),
                "pdf" to oklch(0.55f, 0.17f, 25f),
                "cbz" to oklch(0.55f, 0.16f, 260f),
                "cbr" to oklch(0.58f, 0.15f, 55f),
                "cb7" to oklch(0.55f, 0.19f, 300f),
                "cbx" to oklch(0.55f, 0.17f, 325f),
                "m4b" to oklch(0.58f, 0.13f, 75f),
                "m4a" to oklch(0.58f, 0.13f, 95f),
                "mp3" to oklch(0.56f, 0.15f, 145f),
                "opus" to oklch(0.55f, 0.11f, 205f),
                "ogg" to oklch(0.56f, 0.15f, 130f),
                "flac" to oklch(0.55f, 0.13f, 165f),
            ),
            ebook = oklch(0.55f, 0.16f, 245f),
            audiobook = oklch(0.55f, 0.2f, 300f),
            comic = oklch(0.55f, 0.18f, 25f),
            pending = oklch(0.55f, 0.09f, 255f),
            series = listOf(
                oklch(0.55f, 0.16f, 245f),
                oklch(0.55f, 0.14f, 160f),
                oklch(0.58f, 0.15f, 60f),
                oklch(0.55f, 0.17f, 355f),
                oklch(0.55f, 0.19f, 330f),
                oklch(0.53f, 0.11f, 180f),
            ),
        ),
    )
}

fun darkTokens(tintH: Float = DEFAULT_TINT_H, tintC: Float = 0.0175f): BookOrbitTokens {
    fun t(l: Float, alpha: Float = 1f) = oklch(l, tintC, tintH, alpha)
    val background = t(0.145f)
    val primary = t(0.91f)
    return BookOrbitTokens(
        isDark = true,
        tintH = tintH,
        background = background,
        foreground = oklch(0.985f, 0.001f, tintH),
        card = t(0.18f),
        cardForeground = oklch(0.985f, 0.001f, tintH),
        popover = t(0.18f),
        primary = primary,
        primaryForeground = t(0.18f),
        secondary = t(0.245f),
        secondaryForeground = oklch(0.985f, 0.001f, tintH),
        muted = t(0.245f),
        mutedForeground = oklch(0.725f, 0.001f, tintH),
        accent = t(0.245f),
        accentForeground = oklch(0.985f, 0.001f, tintH),
        destructive = oklch(0.704f, 0.191f, 22.216f),
        destructiveForeground = oklch(0.985f, 0f, 0f),
        success = oklch(0.72f, 0.15f, 148f),
        successForeground = oklch(0.145f, 0f, 0f),
        warning = oklch(0.8f, 0.13f, 75f),
        warningForeground = oklch(0.145f, 0f, 0f),
        info = oklch(0.72f, 0.12f, 255f),
        infoForeground = oklch(0.145f, 0f, 0f),
        starHighlight = oklch(0.84f, 0.17f, 95f),
        // The web uses translucent white for both; flattened onto the page so Material's outlines
        // and dividers draw the same colour on any surface.
        border = Color.White.copy(alpha = 0.10f).compositeOver(background),
        input = Color.White.copy(alpha = 0.15f).compositeOver(background),
        ring = oklch(0.52f, 0.008f, tintH),
        sidebar = t(0.18f),
        surface1 = t(0.18f),
        surface2 = t(0.235f),
        surface3 = t(0.245f),
        surface4 = t(0.295f),
        scrim = oklch(0.06f, 0.01f, tintH, 0.62f),
        scrimMedia = oklch(0f, 0f, 0f, 0.82f),
        coverChip = oklch(0.12f, 0.01f, tintH, 0.78f),
        coverChipForeground = oklch(0.97f, 0.002f, tintH),
        accentWash = primary.copy(alpha = 0.06f).compositeOver(background),
        accentTint = primary.copy(alpha = 0.12f).compositeOver(background),
        accentLine = primary.copy(alpha = 0.30f).compositeOver(background),
        chipTagBackground = oklch(0.769f, 0.188f, 70.08f, 0.1f),
        chipTagForeground = oklch(0.828f, 0.189f, 84.429f),
        pills = BookOrbitTokens.Pills(
            formatDefault = oklch(0.75f, 0.02f, 260f),
            format = mapOf(
                "epub" to oklch(0.75f, 0.13f, 150f),
                "kepub" to oklch(0.75f, 0.11f, 178f),
                "mobi" to oklch(0.72f, 0.16f, 275f),
                "azw3" to oklch(0.76f, 0.11f, 185f),
                "azw" to oklch(0.72f, 0.13f, 235f),
                "fb2" to oklch(0.73f, 0.15f, 355f),
                "djvu" to oklch(0.75f, 0.04f, 255f),
                "txt" to oklch(0.76f, 0.03f, 60f),
                "pdf" to oklch(0.72f, 0.15f, 25f),
                "cbz" to oklch(0.72f, 0.14f, 260f),
                "cbr" to oklch(0.76f, 0.13f, 55f),
                "cb7" to oklch(0.72f, 0.17f, 300f),
                "cbx" to oklch(0.73f, 0.15f, 325f),
                "m4b" to oklch(0.79f, 0.13f, 75f),
                "m4a" to oklch(0.8f, 0.13f, 95f),
                "mp3" to oklch(0.78f, 0.15f, 145f),
                "opus" to oklch(0.76f, 0.11f, 205f),
                "ogg" to oklch(0.78f, 0.15f, 130f),
                "flac" to oklch(0.75f, 0.13f, 165f),
            ),
            ebook = oklch(0.72f, 0.14f, 245f),
            audiobook = oklch(0.72f, 0.17f, 300f),
            comic = oklch(0.72f, 0.16f, 25f),
            pending = oklch(0.72f, 0.09f, 255f),
            series = listOf(
                oklch(0.72f, 0.14f, 245f),
                oklch(0.75f, 0.13f, 160f),
                oklch(0.76f, 0.13f, 60f),
                oklch(0.73f, 0.15f, 355f),
                oklch(0.72f, 0.17f, 330f),
                oklch(0.76f, 0.11f, 180f),
            ),
        ),
    )
}

private val DefaultDarkTokens by lazy { darkTokens() }

/** A format's colour as tuned for a dark ground, for marks that always sit on one (chips over covers). */
fun darkFormatColor(format: String?): Color = DefaultDarkTokens.formatColor(format)

val LocalBookOrbitTokens = staticCompositionLocalOf { DefaultDarkTokens }
