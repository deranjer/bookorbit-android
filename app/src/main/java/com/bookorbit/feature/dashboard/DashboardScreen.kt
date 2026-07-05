package com.bookorbit.feature.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookorbit.ui.components.HorizontalBookScroller

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onBookClick: (Int) -> Unit,
    vm: DashboardViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val isEmpty = !ui.loading && ui.continueReading.isEmpty() && ui.continueListening.isEmpty() && ui.recentlyAdded.isEmpty()

    PullToRefreshBox(
        isRefreshing = ui.loading,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        if (isEmpty) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing here yet. Add books on the server.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (ui.continueReading.isNotEmpty()) {
                    HorizontalBookScroller("Continue Reading", ui.continueReading, onBookClick)
                }
                if (ui.continueListening.isNotEmpty()) {
                    HorizontalBookScroller("Continue Listening", ui.continueListening, onBookClick)
                }
                if (ui.recentlyAdded.isNotEmpty()) {
                    HorizontalBookScroller("Recently Added", ui.recentlyAdded, onBookClick)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
            }
        }
    }
}
