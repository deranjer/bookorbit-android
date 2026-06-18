package com.bookorbit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bookorbit.ui.theme.Accent
import com.bookorbit.ui.theme.TextMuted

/**
 * 1–5 star rating. When [onChange] is provided, tapping a star sets that rating; tapping the current
 * whole-star rating clears it (null).
 */
@Composable
fun StarRating(
    value: Double?,
    onChange: ((Int?) -> Unit)? = null,
    enabled: Boolean = true,
    size: Dp = 28.dp,
) {
    val current = value ?: 0.0
    Row {
        (1..5).forEach { star ->
            val icon = when {
                current >= star -> Icons.Filled.Star
                current >= star - 0.5 -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Outlined.StarBorder
            }
            val tint = if (current >= star - 0.5) Accent else TextMuted
            Icon(
                imageVector = icon,
                contentDescription = "$star stars",
                tint = tint,
                modifier = Modifier
                    .size(size)
                    .then(
                        if (onChange != null && enabled) {
                            Modifier.clickable {
                                onChange(if (value?.toInt() == star) null else star)
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}
