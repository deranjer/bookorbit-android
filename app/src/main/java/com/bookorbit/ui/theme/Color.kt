package com.bookorbit.ui.theme

import androidx.compose.ui.graphics.Color

// Palette ported from the web client to keep visual parity.
val Background = Color(0xFF0A0A0A)
val Surface = Color(0xFF1C1C1E)
val Border = Color(0xFF2C2C2E)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFAAAAAA)
val TextMuted = Color(0xFF666666)
val Accent = Color(0xFF4A9EFF)
val ErrorRed = Color(0xFFFF6B6B)
val WarningOrange = Color(0xFFFFA94D)
val SuccessGreen = Color(0xFF2F9E44)

// Light-theme neutrals only. Accent/error/warning/success/muted stay the same in both themes (they
// already read fine on either a black or white surface) so only the background/surface/text scale
// that actually needs to flip is duplicated here.
val LightBackground = Color(0xFFF7F7F8)
val LightSurface = Color(0xFFFFFFFF)
val LightBorder = Color(0xFFDDDDE1)
val LightTextPrimary = Color(0xFF1A1A1E)
val LightTextSecondary = Color(0xFF5C5C66)
