package com.bookorbit.feature.bookdrop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Colored pill for a Book Dock file's processing status. */
@Composable
fun BookDockStatusBadge(status: String, modifier: Modifier = Modifier) {
    val (color, label) = when (status) {
        "pending" -> Color(0xFF868E96) to "Pending"
        "extracting" -> Color(0xFF1971C2) to "Extracting"
        "fetching" -> Color(0xFF1098AD) to "Fetching"
        "ready" -> Color(0xFF2F9E44) to "Ready"
        "error" -> Color(0xFFC92A2A) to "Error"
        else -> Color(0xFF868E96) to status.replaceFirstChar { it.uppercase() }
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
