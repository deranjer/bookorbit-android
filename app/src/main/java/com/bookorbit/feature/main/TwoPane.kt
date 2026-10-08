package com.bookorbit.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bookorbit.R

/** Windows at least this wide show a list beside the open book. Narrower wide windows keep one pane. */
const val TWO_PANE_BREAKPOINT_DP = 840

/** Width of the list pane; the detail pane takes the rest. */
val TWO_PANE_LIST_WIDTH = 460.dp

object TwoPane {
    /** The tabs whose lists open a book: these show the book beside the list on a wide window. */
    val LIST_ROUTES = setOf("library", "search", "notes")

    /** True when [route] should be drawn as list + detail at this window width. */
    fun isActive(widthDp: Int, route: String?): Boolean =
        widthDp >= TWO_PANE_BREAKPOINT_DP && route in LIST_ROUTES
}

/** What the detail pane shows before a book is chosen. */
@Composable
fun EmptyDetailPane() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.two_pane_select_book),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(280.dp),
        )
    }
}
