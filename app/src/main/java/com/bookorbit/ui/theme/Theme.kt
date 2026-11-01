package com.bookorbit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext

/**
 * Every Material colour role filled from the web tokens. A role left out would fall back to
 * Material's baseline purple, so all of them are set. Surfaces stay flat: the web separates layers
 * by lightness and borders, not by Material's tinted tonal elevation.
 */
internal fun BookOrbitTokens.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    val errorContainer = destructive.copy(alpha = 0.14f).compositeOver(background)
    return base.copy(
        primary = primary,
        onPrimary = primaryForeground,
        // Containers are the web's quiet accent fills: selected chips, the active navigation item.
        primaryContainer = accentTint,
        onPrimaryContainer = foreground,
        // The other theme's primary, for an action on an inverse surface such as a snackbar.
        inversePrimary = oklch(if (isDark) 0.21f else 0.91f, 0.006f, tintH),
        secondary = mutedForeground,
        onSecondary = background,
        secondaryContainer = accentTint,
        onSecondaryContainer = foreground,
        tertiary = warning,
        onTertiary = warningForeground,
        tertiaryContainer = chipTagBackground.compositeOver(background),
        onTertiaryContainer = chipTagForeground,
        background = background,
        onBackground = foreground,
        surface = background,
        onSurface = foreground,
        surfaceVariant = muted,
        onSurfaceVariant = mutedForeground,
        surfaceTint = Color.Transparent,
        inverseSurface = foreground,
        inverseOnSurface = background,
        error = destructive,
        onError = destructiveForeground,
        errorContainer = errorContainer,
        onErrorContainer = destructive,
        outline = ring,
        outlineVariant = border,
        scrim = scrim,
        surfaceBright = surface2,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = card,
        surfaceContainer = card,
        // Never the same colour as muted (surfaceVariant): contentColorFor() matches surfaceVariant
        // first and would ink a card's text in the muted grey. The web's muted is surface3 in dark
        // and surface2 in light, so each theme steps over its own.
        surfaceContainerHigh = if (isDark) surface2 else surface3,
        surfaceContainerHighest = surface4,
    )
}

@Composable
fun BookOrbitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Use the wallpaper-derived Material You palette instead of BookOrbit's (Android 12+ only). */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val tokens = remember(darkTheme) { if (darkTheme) darkTokens() else lightTokens() }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> remember(tokens) { tokens.toColorScheme() }
    }
    CompositionLocalProvider(LocalBookOrbitTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BookOrbitTypography,
            shapes = BookOrbitShapes,
        ) {
            // Surface establishes the app background and the default LocalContentColor (onBackground),
            // so bare Text() renders in the foreground color instead of the Material default black.
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                content = content,
            )
        }
    }
}

object BookOrbitTheme {
    /** The web client's tokens for the current light or dark theme. */
    val tokens: BookOrbitTokens
        @Composable @ReadOnlyComposable get() = LocalBookOrbitTokens.current
}
