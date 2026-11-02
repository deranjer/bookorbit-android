package com.bookorbit.ui.components

import android.os.SystemClock
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bookorbit.ui.theme.BookOrbitTheme
import kotlinx.coroutines.delay

/** How long the spinner stays after a pull, however quickly the reload comes back. */
private const val MIN_REFRESH_MS = 800L

/**
 * Pull to refresh, as the app's lists use it. A refresh the user pulls for keeps its spinner for at
 * least [MIN_REFRESH_MS]: a nearby server answers within a frame or two, and a spinner gone the moment
 * it's let go reads as the pull having been cancelled. The indicator takes the design's colours;
 * Material's default is a dark disc that all but disappears on the dark theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = BookOrbitTheme.tokens
    val state = rememberPullToRefreshState()
    var heldUntil by remember { mutableLongStateOf(0L) }
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(held) {
        if (!held) return@LaunchedEffect
        delay((heldUntil - SystemClock.uptimeMillis()).coerceAtLeast(0))
        held = false
    }
    val refreshing = isRefreshing || held
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            heldUntil = SystemClock.uptimeMillis() + MIN_REFRESH_MS
            held = true
            onRefresh()
        },
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = tokens.surface4,
                color = tokens.primary,
            )
        },
        content = content,
    )
}
