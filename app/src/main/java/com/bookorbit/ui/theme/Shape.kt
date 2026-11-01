package com.bookorbit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** The web's `--radius` (0.625rem). Its Tailwind steps are this minus 4/2px and plus 4px. */
val Radius = 10.dp

/**
 * Material's shape roles on the web's radius steps: chips and badges `rounded-sm`, inputs and
 * buttons `rounded-md`, dialogs `rounded-lg`, cards `rounded-xl`, sheets one step further.
 */
val BookOrbitShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius - 4.dp),
    small = RoundedCornerShape(Radius - 2.dp),
    medium = RoundedCornerShape(Radius),
    large = RoundedCornerShape(Radius + 4.dp),
    extraLarge = RoundedCornerShape(Radius + 10.dp),
)
