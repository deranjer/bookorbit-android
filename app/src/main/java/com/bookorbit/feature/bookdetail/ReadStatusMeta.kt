package com.bookorbit.feature.bookdetail

import androidx.annotation.StringRes
import com.bookorbit.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.bookorbit.ui.theme.BookOrbitTheme
import com.bookorbit.ui.theme.BookOrbitTokens

data class ReadStatusMeta(
    val value: String,
    @StringRes val label: Int,
    val icon: ImageVector,
    private val tone: (BookOrbitTokens) -> Color,
) {
    val color: Color
        @Composable @ReadOnlyComposable get() = tone(BookOrbitTheme.tokens)
}

/** Order/labels mirror the web client's STATUS_OPTIONS. */
val READ_STATUS_META: List<ReadStatusMeta> = listOf(
    ReadStatusMeta("unread", R.string.status_unread, Icons.Outlined.Book) { it.mutedForeground },
    ReadStatusMeta("want_to_read", R.string.status_want_to_read, Icons.Outlined.BookmarkBorder) { it.pills.audiobook },
    ReadStatusMeta("reading", R.string.status_reading, Icons.Filled.AutoStories) { it.info },
    ReadStatusMeta("on_hold", R.string.status_on_hold, Icons.Outlined.PauseCircleOutline) { it.warning },
    ReadStatusMeta("rereading", R.string.status_rereading, Icons.Filled.Refresh) { it.pills.series[4] },
    ReadStatusMeta("read", R.string.status_read, Icons.Filled.CheckCircle) { it.success },
    ReadStatusMeta("skimmed", R.string.status_skimmed, Icons.Outlined.Visibility) { it.pills.series[5] },
    ReadStatusMeta("abandoned", R.string.status_abandoned, Icons.Outlined.Cancel) { it.destructive },
)

private val META_BY_VALUE = READ_STATUS_META.associateBy { it.value }

fun readStatusMeta(status: String): ReadStatusMeta = META_BY_VALUE[status] ?: READ_STATUS_META.first()
