package com.bookorbit.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    vm: ReaderViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val controller = remember { ReaderController() }
    var ready by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var tocVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }

    // Keep the screen on while reading.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(Unit) {
        controller.listener = { event ->
            when (event) {
                is ReaderEvent.Ready -> ready = true
                is ReaderEvent.Loaded -> vm.onLoaded(event.toc, event.title)
                is ReaderEvent.Relocate -> vm.onRelocate(event.cfi, event.fraction, event.chapterTitle)
                is ReaderEvent.Error -> vm.onError(event.message)
            }
        }
    }

    val resolved = ui.resolved
    LaunchedEffect(ready, resolved) {
        if (ready && resolved != null && !opened) {
            opened = true
            withContext(Dispatchers.IO) {
                controller.open(openParamsFor(resolved.file, resolved.format, resolved.initial, ui.settings))
            }
        }
    }

    val surface = themeBackgroundColor(ui.settings.themeName, ui.settings.isDark)
    val paginated = ui.settings.flow == "paginated"
    val showChrome = chromeVisible || !paginated
    val onSurface = if (ui.settings.isDark) Color.White else Color.Black

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surface),
    ) {
        ReaderWebView(controller = controller, modifier = Modifier.fillMaxSize())

        // Tap zones for paging (paginated mode) + chrome toggle.
        if (paginated && !tocVisible && !settingsVisible) {
            Row(modifier = Modifier.fillMaxSize()) {
                TapZone(weight = 0.3f, onTap = controller::prev)
                TapZone(weight = 0.4f, onTap = { chromeVisible = !chromeVisible })
                TapZone(weight = 0.3f, onTap = controller::next)
            }
        }

        if (showChrome) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC0A0A0A))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    ui.chapterTitle ?: ui.title ?: "",
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { tocVisible = true }) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Contents", tint = Color.White)
                }
                IconButton(onClick = { settingsVisible = true }) {
                    Icon(Icons.Filled.TextFields, contentDescription = "Settings", tint = Color.White)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC0A0A0A))
                    .navigationBarsPadding()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("${ui.percentage}%", color = Color.White.copy(alpha = 0.8f))
            }
        }

        if (!ui.loaded && ui.error == null) {
            Box(modifier = Modifier.fillMaxSize().background(surface), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        ui.error?.let { message ->
            Box(modifier = Modifier.fillMaxSize().background(surface), contentAlignment = Alignment.Center) {
                Column2(message = message, onBack = onBack, tint = onSurface)
            }
        }

        // One-time coach overlay teaching the (invisible) paginated tap zones.
        if (paginated && ui.loaded && ui.showPagingHint) {
            PagingHintOverlay(onDismiss = { vm.dismissPagingHint() })
        }
    }

    if (tocVisible) {
        ReaderTocSheet(
            toc = ui.toc,
            onSelect = { href ->
                tocVisible = false
                controller.goTo(href)
            },
            onDismiss = { tocVisible = false },
        )
    }
    if (settingsVisible) {
        ReaderSettingsSheet(
            settings = ui.settings,
            onChange = { updated ->
                vm.updateSettings(updated)
                controller.applyStyles(updated)
            },
            onDismiss = { settingsVisible = false },
        )
    }
}

@Composable
private fun PagingHintOverlay(onDismiss: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60A0A0A))
            .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
    ) {
        HintZone(weight = 0.3f, icon = Icons.Filled.ChevronLeft, label = "Previous page")
        HintZone(weight = 0.4f, icon = Icons.Filled.TouchApp, label = "Tap for menu")
        HintZone(weight = 0.3f, icon = Icons.Filled.ChevronRight, label = "Next page")
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HintZone(
    weight: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(
            label,
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TapZone(weight: Float, onTap: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clickable(interactionSource = interaction, indication = null, onClick = onTap),
    )
}

@Composable
private fun Column2(message: String, onBack: () -> Unit, tint: Color) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp),
    ) {
        Text(message, color = tint)
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = tint)
        }
    }
}
