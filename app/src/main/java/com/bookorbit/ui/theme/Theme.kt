package com.bookorbit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// BookOrbit is dark-first (matching the web client), so the dark palette is the "native" one.
// Every role the components read is set explicitly: anything left out falls back to Material's
// baseline purple, which showed up as lavender chips and navigation pills.
private val DarkColors = darkColorScheme(
    primary = Accent,
    // Near-black on the bright blue: white on #4A9EFF was only ~2.7:1, below the 4.5:1 AA minimum.
    onPrimary = Color(0xFF04223F),
    primaryContainer = Color(0xFF204A43),
    onPrimaryContainer = Color(0xFFD3F6E9),
    secondary = Color(0xFFA9B6C8),
    onSecondary = Color(0xFF16202E),
    secondaryContainer = Color(0xFF2B3646),
    onSecondaryContainer = Color(0xFFDCE6F3),
    tertiary = Color(0xFFFFB070),
    onTertiary = Color(0xFF3A1A00),
    tertiaryContainer = Color(0xFF5A2E0B),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF26262A),
    onSurfaceVariant = Color(0xFFB0B0B8),
    surfaceContainerLowest = Background,
    surfaceContainerLow = Color(0xFF132124),
    surfaceContainer = Color(0xFF192A2D),
    surfaceContainerHigh = Color(0xFF223538),
    surfaceContainerHighest = Color(0xFF2D4144),
    outline = Color(0xFF6E727C),
    outlineVariant = Border,
    error = ErrorRed,
)

private val LightColors = lightColorScheme(
    // A deeper blue than the dark theme's: white on it is ~5.9:1.
    primary = Color(0xFF16685E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7ECE3),
    onPrimaryContainer = Color(0xFF113D35),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Color(0xFFB4531A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBE3D3),
    onTertiaryContainer = Color(0xFF4A1D05),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = Color(0xFFECEEF2),
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0EEE6),
    surfaceContainer = Color(0xFFEAEAE0),
    surfaceContainerHigh = Color(0xFFE3E6DB),
    surfaceContainerHighest = Color(0xFFDCE1D5),
    outline = Color(0xFF8A8F99),
    outlineVariant = LightBorder,
    error = ErrorRed,
)

@Composable
fun BookOrbitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Use the wallpaper-derived Material You palette instead of BookOrbit's (Android 12+ only). */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = BookOrbitTypography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp), extraLarge = RoundedCornerShape(32.dp)),
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
