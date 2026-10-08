package com.bookorbit.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
    var highlightsVisible by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var noteDialog by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    // Taps arrive from the page (so text selection keeps working): edges turn the page, the middle
    // toggles the chrome, and any tap while the highlight toolbar is open just dismisses it.
    val onTap = remember { mutableStateOf<(Double) -> Unit>({}) }
    onTap.value = { x ->
        when {
            ui.toolbar != null -> vm.dismissToolbar()
            ui.settings.flow == "paginated" && x < 0.3 -> controller.prev()
            ui.settings.flow == "paginated" && x > 0.7 -> controller.next()
            else -> chromeVisible = !chromeVisible
        }
    }

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
                is ReaderEvent.Tap -> onTap.value(event.x)
                is ReaderEvent.SearchResults -> vm.onSearchResults(event.label, event.hits)
                is ReaderEvent.SearchProgress -> vm.onSearchProgress(event.progress)
                is ReaderEvent.SearchDone -> vm.onSearchDone(event.total, event.capped)
                is ReaderEvent.Selection -> vm.onSelection(event.text, event.cfi, event.rect)
                ReaderEvent.SelectionCleared -> vm.onSelectionCleared()
                is ReaderEvent.AnnotationTap -> vm.onAnnotationTap(event.cfi, event.rect)
            }
        }
    }

    // Draw the book's highlights in the page whenever they (or the loaded page) change.
    LaunchedEffect(ui.annotations, ui.loaded) {
        if (ui.loaded) controller.setAnnotations(ui.annotations)
    }

    // Drop the page's text selection once the toolbar goes away (saved, dismissed or deleted).
    LaunchedEffect(ui.toolbar == null) {
        if (ui.toolbar == null && ui.loaded) controller.clearSelection()
    }

    ui.message?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    val resolved = ui.resolved
    LaunchedEffect(ready, resolved) {
        if (ready && resolved != null && !opened) {
            opened = true
            withContext(Dispatchers.IO) {
                controller.open(openParamsFor(resolved.ref, resolved.format, resolved.initial, ui.settings), context)
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
                IconButton(onClick = { searchVisible = true }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search in book", tint = Color.White)
                }
                IconButton(onClick = { highlightsVisible = true }) {
                    Icon(Icons.Filled.FormatQuote, contentDescription = "Highlights", tint = Color.White)
                }
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

        ui.toolbar?.let { target ->
            val annotation = target.annotationId?.let { id -> ui.annotations.firstOrNull { it.id == id } }
            val screenWidth = LocalConfiguration.current.screenWidthDp
            val toolbarWidth = if (annotation != null) 340 else 290
            val rect = target.rect
            val centerX = rect?.let { (it.left + it.right) / 2 } ?: (screenWidth / 2.0)
            val x = (centerX - toolbarWidth / 2).coerceIn(8.0, (screenWidth - toolbarWidth - 8).coerceAtLeast(8).toDouble())
            // Above the selection when there's room, otherwise below it (clear of the selection handles).
            val y = when {
                rect == null -> 120.0
                rect.top > 150 -> rect.top - 60
                else -> rect.bottom + 36
            }
            HighlightToolbar(
                currentColor = annotation?.color,
                onColor = { vm.highlight(it) },
                onNote = { noteDialog = annotation?.note.orEmpty() },
                onDelete = annotation?.let { a -> { vm.deleteAnnotation(a.id) } },
                modifier = Modifier.offset(x = x.dp, y = y.dp),
            )
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
    noteDialog?.let { initial ->
        NoteDialog(
            initial = initial,
            onSave = { note ->
                noteDialog = null
                vm.saveNote(note)
            },
            onDismiss = { noteDialog = null },
        )
    }
    if (searchVisible) {
        ReaderSearchSheet(
            state = ui.search,
            onSearch = { q ->
                vm.startSearch(q)
                controller.search(q)
            },
            onClear = {
                vm.clearSearch()
                controller.clearSearch()
            },
            onJump = { cfi ->
                searchVisible = false
                controller.goTo(cfi)
            },
            onDismiss = { searchVisible = false },
        )
    }
    if (highlightsVisible) {
        HighlightsSheet(
            annotations = ui.annotations,
            onJump = { a ->
                highlightsVisible = false
                a.cfi?.let(controller::goTo)
            },
            onDelete = { vm.deleteAnnotation(it.id) },
            onDismiss = { highlightsVisible = false },
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
