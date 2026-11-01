package com.bookorbit.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.cos
import kotlin.math.sin

/**
 * CSS `oklch(l c h / alpha)` as an sRGB [Color]. The web client defines every colour this way
 * (`client/src/assets/theme/tokens.css`), so its values are copied here verbatim rather than
 * converted to hex by hand. OKLCH is polar Oklab; colours outside sRGB are clipped per channel.
 */
fun oklch(l: Float, c: Float, h: Float, alpha: Float = 1f): Color {
    val rad = Math.toRadians(h.toDouble())
    val a = (c * cos(rad)).toFloat()
    val b = (c * sin(rad)).toFloat()
    return Color(l, a, b, alpha, ColorSpaces.Oklab).convert(ColorSpaces.Srgb)
}
