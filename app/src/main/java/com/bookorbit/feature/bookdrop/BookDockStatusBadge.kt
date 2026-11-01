package com.bookorbit.feature.bookdrop

import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bookorbit.ui.components.Pill
import com.bookorbit.ui.theme.BookOrbitTheme

/** Colored pill for a Book Dock file's processing status. */
@Composable
fun BookDockStatusBadge(status: String, modifier: Modifier = Modifier) {
    val tokens = BookOrbitTheme.tokens
    val (color, label) = when (status) {
        "pending" -> tokens.mutedForeground to stringResource(R.string.bookdrop_status_pending)
        "extracting" -> tokens.info to stringResource(R.string.bookdrop_status_extracting)
        "fetching" -> tokens.pills.pending to stringResource(R.string.bookdrop_status_fetching)
        "ready" -> tokens.success to stringResource(R.string.bookdrop_status_ready)
        "error" -> tokens.destructive to stringResource(R.string.bookdrop_status_error)
        else -> tokens.mutedForeground to status.replaceFirstChar { it.uppercase() }
    }
    Pill(label, color, modifier)
}
