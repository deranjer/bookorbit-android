package com.bookorbit.feature.bookdrop

import androidx.compose.ui.res.stringResource
import com.bookorbit.R
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
        "pending" -> Color(0xFF868E96) to stringResource(R.string.bookdrop_status_pending)
        "extracting" -> Color(0xFF1971C2) to stringResource(R.string.bookdrop_status_extracting)
        "fetching" -> Color(0xFF1098AD) to stringResource(R.string.bookdrop_status_fetching)
        "ready" -> Color(0xFF2F9E44) to stringResource(R.string.bookdrop_status_ready)
        "error" -> Color(0xFFC92A2A) to stringResource(R.string.bookdrop_status_error)
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
