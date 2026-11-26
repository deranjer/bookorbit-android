package com.bookorbit.feature.reader

import com.bookorbit.ui.asString
import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    var rendererFailed by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var tocVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var zonesHelpVisible by remember { mutableStateOf(false) }
    var highlightsVisible by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var noteDialog by remember { mutableStateOf<String?>(null) }
    var helpPassage by remember { mutableStateOf<String?>(null) }
    var readingTool by remember { mutableStateOf<Pair<ReadingTool, String>?>(null) }
    var readAlong by remember { mutableStateOf(ReaderEvent.ReadAlong(false, "unavailable")) }
    val nativeState by vm.nativeNarration.state.collectAsStateWithLifecycle()
    val activeNative = nativeState?.takeIf { it.owner == vm.nativeReadAlongOwner() }
    val shownReadAlong = activeNative?.event ?: readAlong
    LaunchedEffect(activeNative?.event?.position, ui.loaded) {
        if (ui.loaded) controller.nativeReadAlong(activeNative?.event?.position)
    }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.readAlong("pause")
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); controller.readAlong("stop") }
    }

    val context = LocalContext.current

    // Taps arrive from the page (so text selection keeps working): edges turn the page, the middle
    // toggles the chrome, and any tap while the highlight toolbar is open just dismisses it.
    val onTap = remember { mutableStateOf<(Double) -> Unit>({}) }
    onTap.value = { x ->
        if (ui.toolbar != null) vm.dismissToolbar()
        else when (readerTapAction(x, ui.settings.flow == "paginated", ui.rightToLeft)) {
            ReaderTapAction.PREVIOUS -> controller.prev()
            ReaderTapAction.NEXT -> controller.next()
            ReaderTapAction.MENU -> chromeVisible = !chromeVisible
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
                is ReaderEvent.Loaded -> vm.onLoaded(event.toc, event.title, event.rightToLeft)
                is ReaderEvent.Relocate -> vm.onRelocate(event.cfi, event.fraction, event.chapterTitle, event.sectionIndex)
                is ReaderEvent.Error -> vm.onError(event.message)
                is ReaderEvent.ReadAlong -> { readAlong = event; vm.onReadAlong(event) }
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
            Toast.makeText(context, msg.resolve(context), Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    val resolved = ui.resolved
    LaunchedEffect(ready, resolved, rendererFailed) {
        if (ready && resolved != null && !opened && !rendererFailed) {
            opened = true
            withContext(Dispatchers.IO) {
                controller.open(openParamsFor(resolved.ref, resolved.format, resolved.initial, ui.settings)
                    .copy(readAlongPosition = resolved.readAlongPosition, stream = resolved.stream), context)
            }
        }
    }

    val surface = themeBackgroundColor(ui.settings.themeName, ui.settings.isDark)
    val paginated = ui.settings.flow == "paginated"
    val showChrome = chromeVisible
    val onSurface = if (ui.settings.isDark) Color.White else Color.Black

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surface),
    ) {
        if (!rendererFailed) {
            ReaderWebView(
                controller = controller,
                modifier = Modifier.fillMaxSize(),
                onRendererGone = {
                    rendererFailed = true
                    vm.onRendererGone()
                },
            )
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                }
                Text(
                    ui.chapterTitle ?: ui.title ?: "",
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { searchVisible = true }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_in_book), tint = Color.White)
                }
                IconButton(onClick = {
                    vm.loadBookmarks()
                    highlightsVisible = true
                }) {
                    Icon(Icons.Filled.FormatQuote, contentDescription = stringResource(R.string.highlights), tint = Color.White)
                }
                IconButton(onClick = { tocVisible = true }) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.contents), tint = Color.White)
                }
                IconButton(onClick = { settingsVisible = true }) {
                    Icon(Icons.Filled.TextFields, contentDescription = stringResource(R.string.settings), tint = Color.White)
                }
                IconButton(onClick = { zonesHelpVisible = true }) {
                    Icon(Icons.Filled.HelpOutline, contentDescription = stringResource(R.string.reader_zones_title), tint = Color.White)
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
                Text(if (ui.resolved?.stream != null) stringResource(R.string.reader_streaming_progress, ui.percentage)
                    else "${ui.percentage}%", color = Color.White.copy(alpha = 0.8f))
            }
        }

        ui.toolbar?.let { target ->
            val annotation = target.annotationId?.let { id -> ui.annotations.firstOrNull { it.id == id } }
            val rect = target.rect
            val density = androidx.compose.ui.platform.LocalDensity.current.density
            SelectionActions(rect?.let { androidx.compose.ui.geometry.Rect(
                it.left.toFloat() * density, it.top.toFloat() * density,
                it.right.toFloat() * density, it.bottom.toFloat() * density) }, Modifier.fillMaxSize()) {
            HighlightToolbar(
                currentColor = annotation?.color,
                onColor = { vm.highlight(it) },
                onNote = { noteDialog = annotation?.note.orEmpty() },
                onDelete = annotation?.let { a -> { vm.deleteAnnotation(a.id) } },
                modifier = Modifier.fillMaxWidth(),
                onReadingHelp = (target.selection?.text ?: annotation?.text)?.takeIf { it.isNotBlank() }?.let { text ->
                    { controller.readAlong("pause"); scope.launch { vm.nativeReadAlong("pause", null) }; helpPassage = text; vm.dismissToolbar() }
                },
                onTranslate = (target.selection?.text ?: annotation?.text)?.takeIf { it.isNotBlank() }?.let { text ->
                    { readingTool = ReadingTool.TRANSLATE to text }
                },
                onDictionary = (target.selection?.text ?: annotation?.text)?.takeIf { it.isNotBlank() }?.let { text ->
                    { readingTool = ReadingTool.DICTIONARY to text }
                },
            )
            }
        }

        if (shownReadAlong.available && ui.loaded && !rendererFailed && showChrome) {
            ReadAlongBar(shownReadAlong, onCommand = { action, rate ->
                scope.launch {
                    if (activeNative != null || action in setOf("start", "resume", "restart"))
                        controller.readAlong("pause")
                    if (vm.nativeReadAlong(action, rate)) return@launch
                    if (action in setOf("start", "resume", "restart")) vm.prepareReadAlong()
                    controller.readAlong(action, rate)
                }
            }, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 44.dp),
                background = activeNative != null)
        }

        if ((!ui.loaded || !ready) && ui.error == null && !rendererFailed) {
            Box(modifier = Modifier.fillMaxSize().background(surface), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        ui.error?.let { message ->
            Box(modifier = Modifier.fillMaxSize().background(surface), contentAlignment = Alignment.Center) {
                Column2(message = message.asString(), onBack = onBack, tint = onSurface)
            }
        }

        if (rendererFailed) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize().background(surface).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(stringResource(R.string.reader_renderer_unavailable), color = onSurface)
                Button(onClick = {
                    ready = false
                    opened = false
                    rendererFailed = false
                }) { Text(stringResource(R.string.retry)) }
                Button(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
        }

        if (!rendererFailed && ui.loaded && (zonesHelpVisible || (paginated && ui.showPagingHint))) {
            ReaderTapZonesHelp(paginated = paginated, rightToLeft = ui.rightToLeft, onDismiss = {
                zonesHelpVisible = false
                vm.dismissPagingHint()
            })
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
    helpPassage?.let { passage ->
        ui.resolved?.fileId?.let { fileId ->
            com.bookorbit.feature.readinghelp.ReadingHelpSheet(fileId, passage, vm.readingHelp, onDismiss = { helpPassage = null })
        }
    }
    readingTool?.let { (tool, text) -> ReadingToolsSheet(text, tool, vm.readingTools, onDismiss = { readingTool = null }) }
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
            bookmarks = ui.bookmarks,
            onJumpBookmark = { b ->
                highlightsVisible = false
                b.cfi?.let(controller::goTo)
            },
            onAddBookmark = vm::addBookmark,
            onDeleteBookmark = { vm.deleteBookmark(it.id) },
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
private fun Column2(message: String, onBack: () -> Unit, tint: Color) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp),
    ) {
        Text(message, color = tint)
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = tint)
        }
    }
}
