package com.bookorbit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// BookOrbit is dark-first (matching the web client). A light scheme can be added
// later; for now both system modes resolve to the dark palette to preserve the existing look.
private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = TextPrimary,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Surface,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    error = ErrorRed,
)

@Composable
fun BookOrbitTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = BookOrbitTypography,
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
