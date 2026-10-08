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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.bookorbit.ui.theme.Accent
import com.bookorbit.ui.theme.ErrorRed
import com.bookorbit.ui.theme.SuccessGreen
import com.bookorbit.ui.theme.TextMuted
import com.bookorbit.ui.theme.WarningOrange

data class ReadStatusMeta(
    val value: String,
    @StringRes val label: Int,
    val icon: ImageVector,
    val color: Color,
)

/** Order/labels mirror the web client's STATUS_OPTIONS. */
val READ_STATUS_META: List<ReadStatusMeta> = listOf(
    ReadStatusMeta("unread", R.string.status_unread, Icons.Outlined.Book, TextMuted),
    ReadStatusMeta("want_to_read", R.string.status_want_to_read, Icons.Outlined.BookmarkBorder, Color(0xFFA78BFA)),
    ReadStatusMeta("reading", R.string.status_reading, Icons.Filled.AutoStories, Accent),
    ReadStatusMeta("on_hold", R.string.status_on_hold, Icons.Outlined.PauseCircleOutline, WarningOrange),
    ReadStatusMeta("rereading", R.string.status_rereading, Icons.Filled.Refresh, Color(0xFFE879F9)),
    ReadStatusMeta("read", R.string.status_read, Icons.Filled.CheckCircle, SuccessGreen),
    ReadStatusMeta("skimmed", R.string.status_skimmed, Icons.Outlined.Visibility, Color(0xFF22D3EE)),
    ReadStatusMeta("abandoned", R.string.status_abandoned, Icons.Outlined.Cancel, ErrorRed),
)

private val META_BY_VALUE = READ_STATUS_META.associateBy { it.value }

fun readStatusMeta(status: String): ReadStatusMeta = META_BY_VALUE[status] ?: READ_STATUS_META.first()
