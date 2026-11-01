package com.bookorbit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bookorbit.R

// The web client's two faces: Inter for the interface, Fraunces for page titles and the brand.
// Each ships as one variable file (res/font, OFL licences in assets/licenses); every weight below is
// an instance of that file. Reader typography is separate, inside the WebView.

private fun inter(weight: FontWeight) = Font(
    R.font.inter,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

// Browsers pick Fraunces' optical size from the rendered size; Compose can't per style, so every
// heading uses the cut drawn for display sizes.
private fun fraunces(weight: FontWeight) = Font(
    R.font.fraunces,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("opsz", 48f),
    ),
)

val Inter = FontFamily(
    inter(FontWeight.Normal),
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold),
)

val Fraunces = FontFamily(
    fraunces(FontWeight.Normal),
    fraunces(FontWeight.Medium),
    fraunces(FontWeight.SemiBold),
)

/** `tracking-tight`, which the web puts on every serif title. */
private val TightTracking = (-0.025).em

private fun serif(size: Int, lineHeight: Int) = TextStyle(
    fontFamily = Fraunces,
    fontWeight = FontWeight.SemiBold,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = TightTracking,
)

private fun sans(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
)

/**
 * Material's roles on the web client's scale. Display, headline and [Typography.titleLarge] (the
 * top app bar title) are Fraunces, like the web's page titles; everything a user reads or taps is
 * Inter, at the web's sizes (`text-sm` body, `text-xs` captions).
 */
val BookOrbitTypography = Typography(
    displayLarge = serif(48, 54),
    displayMedium = serif(40, 46),
    displaySmall = serif(34, 40),
    headlineLarge = serif(30, 36),
    headlineMedium = serif(26, 32),
    headlineSmall = serif(24, 30),
    titleLarge = serif(22, 28),
    titleMedium = sans(16, 24, FontWeight.SemiBold),
    titleSmall = sans(14, 20, FontWeight.SemiBold),
    bodyLarge = sans(16, 24),
    bodyMedium = sans(14, 20),
    bodySmall = sans(12, 16),
    labelLarge = sans(14, 20, FontWeight.Medium),
    labelMedium = sans(12, 16, FontWeight.Medium),
    labelSmall = sans(11, 16, FontWeight.Medium, tracking = 0.02f),
)
