package com.bookorbit.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookorbit.R
import com.bookorbit.core.settings.ThemeMode
import com.bookorbit.ui.components.ChoiceChipRow
import com.bookorbit.ui.components.GhostButton
import com.bookorbit.ui.components.PrimaryButton
import com.bookorbit.ui.components.readableWidth
import com.bookorbit.ui.theme.BookOrbitTheme
import kotlinx.coroutines.launch

private const val TOUR_PAGES = 3

/** Shown once, after the first sign-in: the theme, how reading works, and offline books. */
@Composable
fun TourScreen(onDone: () -> Unit, vm: TourViewModel = hiltViewModel()) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    TourContent(themeMode = themeMode, onThemeMode = vm::setThemeMode, onDone = onDone)
}

@Composable
fun TourContent(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberPagerState { TOUR_PAGES },
) {
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == TOUR_PAGES - 1
    Column(
        modifier
            .fillMaxSize()
            .background(BookOrbitTheme.tokens.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            if (!last) GhostButton(stringResource(R.string.tour_skip), onClick = onDone)
        }
        HorizontalPager(pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> TourPage(Icons.Outlined.Palette, stringResource(R.string.tour_look_title), stringResource(R.string.tour_look_body)) {
                    val modes = ThemeMode.entries
                    ChoiceChipRow(
                        labels = listOf(
                            stringResource(R.string.tour_look_system),
                            stringResource(R.string.tour_look_light),
                            stringResource(R.string.tour_look_dark),
                        ),
                        selectedIndex = modes.indexOf(themeMode),
                        onSelect = { onThemeMode(modes[it]) },
                        contentPadding = PaddingValues(0.dp),
                    )
                }
                1 -> TourPage(Icons.Outlined.TouchApp, stringResource(R.string.tour_reading_title), stringResource(R.string.tour_reading_body))
                else -> TourPage(Icons.Outlined.DownloadForOffline, stringResource(R.string.tour_downloads_title), stringResource(R.string.tour_downloads_body))
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageDots(pagerState.currentPage, TOUR_PAGES)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = stringResource(if (last) R.string.tour_done else R.string.tour_next),
                onClick = { if (last) onDone() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                modifier = Modifier.readableWidth(420.dp),
            )
        }
    }
}

@Composable
private fun TourPage(
    icon: ImageVector,
    title: String,
    body: String,
    extra: (@Composable () -> Unit)? = null,
) {
    val tokens = BookOrbitTheme.tokens
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.readableWidth(420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(tokens.muted)
                    .border(1.dp, tokens.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tokens.foreground, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = tokens.mutedForeground, textAlign = TextAlign.Center)
            if (extra != null) {
                Spacer(Modifier.height(24.dp))
                extra()
            }
        }
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    val tokens = BookOrbitTheme.tokens
    val description = stringResource(R.string.tour_page, current + 1, count)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = description }) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(if (index == current) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (index == current) tokens.foreground else tokens.border),
            )
        }
    }
}
