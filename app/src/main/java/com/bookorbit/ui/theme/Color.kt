package com.bookorbit.ui.theme

import androidx.compose.ui.graphics.Color

// Palette ported from the web client to keep visual parity.
val Background = Color(0xFF0D171A)
val Surface = Color(0xFF172427)
val Border = Color(0xFF2C4144)
val TextPrimary = Color(0xFFF4F2E9)
val TextSecondary = Color(0xFFB3C4C3)
val TextMuted = Color(0xFF8BA5A6)
val Accent = Color(0xFF99DBCB)
val ErrorRed = Color(0xFFFF6B6B)
val WarningOrange = Color(0xFFFFA94D)
val SuccessGreen = Color(0xFF2F9E44)

// Light-theme neutrals only. Accent/error/warning/success/muted stay the same in both themes (they
// already read fine on either a black or white surface) so only the background/surface/text scale
// that actually needs to flip is duplicated here.
val LightBackground = Color(0xFFF6F3EC)
val LightSurface = Color(0xFFFFFDFA)
val LightBorder = Color(0xFFD8DED7)
val LightTextPrimary = Color(0xFF172D31)
val LightTextSecondary = Color(0xFF536A6C)
