package com.bookorbit.feature.reader.comic

import com.bookorbit.ui.asString
import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatTextdirectionLToR
import androidx.compose.material.icons.automirrored.filled.FormatTextdirectionRToL
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.bookorbit.ui.LocalImageUrls
import com.bookorbit.feature.reader.ReaderTapAction
import com.bookorbit.feature.reader.ReaderTapZonesHelp
import com.bookorbit.feature.reader.readerTapAction
import com.bookorbit.feature.reader.*
import androidx.compose.runtime.produceState
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch

private const val MAX_ZOOM = 4f

/**
 * Comic reader for CBR/CB7: a pager over server-rendered page images with pinch-to-zoom, double-tap
 * zoom, tap zones to turn pages, a page slider, and a right-to-left (manga) switch.
 */
@Composable
fun ComicReaderScreen(
    onBack: () -> Unit,
    vm: ComicReaderViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val rtl by vm.rtl.collectAsStateWithLifecycle()
    var chromeVisible by remember { mutableStateOf(true) }
    var zonesHelpVisible by remember { mutableStateOf(false) }
    var ocrEnabled by remember { mutableStateOf(false) }
    var readingTool by remember { mutableStateOf<Pair<ReadingTool, String>?>(null) }

    // Keep the screen on while reading.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            ui.error != null -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ui.error?.asString().orEmpty(), color = Color.White)
                Row(Modifier.padding(top = 16.dp)) {
                    Button(onClick = vm::load) { Text(stringResource(R.string.retry)) }
                    Button(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) { Text(stringResource(R.string.back)) }
                }
            }
            else -> ComicPages(ui, rtl, vm, ocrEnabled, onTool = { tool, text -> readingTool = tool to text }, onToggleChrome = { chromeVisible = !chromeVisible })
        }

        if (chromeVisible) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC0A0A0A))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White) }
                Text(ui.title.orEmpty(), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.setRtl(!rtl) }) {
                    Icon(
                        if (rtl) Icons.AutoMirrored.Filled.FormatTextdirectionRToL else Icons.AutoMirrored.Filled.FormatTextdirectionLToR,
                        contentDescription = stringResource(if (rtl) R.string.comic_dir_rtl else R.string.comic_dir_ltr),
                        tint = Color.White,
                    )
                }
                IconButton(onClick = { zonesHelpVisible = true }) {
                    Icon(Icons.Filled.HelpOutline, contentDescription = stringResource(R.string.reader_zones_title), tint = Color.White)
                }
                IconButton(onClick = { ocrEnabled = !ocrEnabled }) {
                    Icon(Icons.Filled.TextFields, contentDescription = stringResource(R.string.reader_comic_ocr),
                        tint = if (ocrEnabled) MaterialTheme.colorScheme.tertiary else Color.White)
                }
            }
        }
        if (zonesHelpVisible) ReaderTapZonesHelp(rightToLeft = rtl, comic = true, onDismiss = { zonesHelpVisible = false })
    }
    readingTool?.let { (tool, text) -> ReadingToolsSheet(text, tool, vm.readingTools, onDismiss = { readingTool = null }) }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ComicPages(
    ui: ComicReaderViewModel.UiState,
    rtl: Boolean,
    vm: ComicReaderViewModel,
    ocrEnabled: Boolean,
    onTool: (ReadingTool, String) -> Unit,
    onToggleChrome: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = ui.initialPage, pageCount = { ui.pageCount })
    val readerSettings by vm.readerSettings.collectAsStateWithLifecycle()
    val pageAnimation = readerPageAnimation(readerSettings.animatePageTurns)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var zoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { vm.onPageChanged(it) }
    }
    // A new page always starts un-zoomed.
    LaunchedEffect(pagerState.currentPage) { zoomed = false }

    ReaderPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        rightToLeft = rtl,
        userScrollEnabled = !zoomed,
        animated = readerSettings.animatePageTurns,
    ) { page ->
        ZoomablePage(
            fileId = ui.fileId,
            page = page,
            active = page == pagerState.currentPage,
            vm = vm,
            ocrEnabled = ocrEnabled,
            onTool = onTool,
            onZoomChanged = { if (page == pagerState.currentPage) zoomed = it },
            onTap = { fraction ->
                // Edge taps turn the page (mirrored when reading right to left); the middle shows the bars.
                when (readerTapAction(fraction.toDouble(), rightToLeft = rtl)) {
                    ReaderTapAction.NEXT -> scope.launch { if (pagerState.currentPage < ui.pageCount - 1) pagerState.animateScrollToPage(pagerState.currentPage + 1, animationSpec = pageAnimation) }
                    ReaderTapAction.PREVIOUS -> scope.launch { if (pagerState.currentPage > 0) pagerState.animateScrollToPage(pagerState.currentPage - 1, animationSpec = pageAnimation) }
                    ReaderTapAction.MENU -> onToggleChrome()
                }
            },
        )
    }

    // Bottom bar: page slider, mirrored for right-to-left.
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Color(0xCC0A0A0A))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (ui.pageCount > 1) {
            var dragging by remember { mutableFloatStateOf(-1f) }
            val shown = if (dragging >= 0) dragging else pagerState.currentPage.toFloat()
            Slider(
                value = shown,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    val target = dragging.toInt()
                    dragging = -1f
                    scope.launch { pagerState.scrollToPage(target) }
                },
                valueRange = 0f..(ui.pageCount - 1).toFloat(),
                modifier = Modifier.graphicsLayer { scaleX = if (rtl) -1f else 1f },
            )
        }
        Text("${pagerState.currentPage + 1} / ${ui.pageCount}", color = Color.White.copy(alpha = 0.85f))
    }
}

/** One page: fit-to-screen by default, pinch or double-tap to zoom, drag to pan while zoomed. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ZoomablePage(
    fileId: Int,
    page: Int,
    active: Boolean,
    vm: ComicReaderViewModel,
    ocrEnabled: Boolean,
    onTool: (ReadingTool, String) -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    onTap: (Float) -> Unit,
) {
    val imageUrls = LocalImageUrls.current
    var pageFailed by remember { mutableStateOf(false) }
    val localPage by produceState<Any?>(null, fileId, page) {
        try { value = vm.localPage(page) ?: imageUrls.comicPage(fileId, page) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { value = null; pageFailed = true }
    }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var recognized by remember { mutableStateOf<OcrPage?>(null) }
    var recognizing by remember { mutableStateOf(false) }
    var ocrFailed by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Int>?>(null) }
    LaunchedEffect(ocrEnabled, active, bitmap) {
        if (!active || !ocrEnabled) { selected = null; return@LaunchedEffect }
        val image = bitmap ?: return@LaunchedEffect
        recognizing = true; ocrFailed = false
        try { recognized = vm.recognizePage(page, image) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { ocrFailed = true }
        finally { recognizing = false }
    }
    // The gesture block below is keyed on Unit and never restarts, so read these through State to
    // always see the latest lambdas (e.g. after the reading direction flips).
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = (size.width * (s - 1f)) / 2f
        val maxY = (size.height * (s - 1f)) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun zoomAt(pos: Offset) {
        if (scale > 1.02f) { scale = 1f; offset = Offset.Zero }
        else {
            scale = 2.5f
            offset = clamp(Offset((size.width / 2f - pos.x) * 1.5f, (size.height / 2f - pos.y) * 1.5f), scale)
        }
        currentOnZoomChanged(scale > 1.02f)
    }

    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
        offset = clamp(offset + panChange, scale)
        currentOnZoomChanged(scale > 1.02f)
    }
    // Leaving the page resets it, so coming back doesn't show a stale zoom.
    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { pos -> currentOnTap(pos.x / size.width.coerceAtLeast(1)) },
                    onDoubleTap = ::zoomAt,
                )
            }
            // Only claim drags while zoomed, so an un-zoomed swipe still reaches the pager.
            .transformable(state = transform, canPan = { scale > 1.02f }),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().onSizeChanged { size = it }.graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
        }) {
        AsyncImage(
            model = localPage,
            contentDescription = stringResource(R.string.page, page + 1),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { pageFailed = false; bitmap = it.result.drawable.toBitmap() },
            onError = { pageFailed = true },
        )
        if (ocrEnabled && active) recognized?.let { result ->
            ComicOcrOverlay(result, Modifier.fillMaxSize(), null, selectedWords = selected,
                onExtend = { start, end -> selected = result.selectionBetween(start, end) },
                onDoubleTap = { pos -> zoomAt(Offset(
                    (pos.x - size.width / 2f) * scale + size.width / 2f + offset.x,
                    (pos.y - size.height / 2f) * scale + size.height / 2f + offset.y)) }, onSelect = { index, sentence ->
                selected = when {
                    sentence -> result.sentenceWordsAt(index)
                    else -> setOf(index)
                }
            })
        }
        }
        if (pageFailed) Text(stringResource(R.string.comic_err_read), color = Color.White, modifier = Modifier.align(Alignment.Center))
        if (ocrEnabled && active) Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp, start = 12.dp, end = 12.dp)) {
            if (recognizing) CircularProgressIndicator()
            if (ocrFailed) Text(stringResource(R.string.reader_ocr_failed), color = Color.White)
            if (!recognizing && recognized?.words?.isEmpty() == true) Text(stringResource(R.string.reader_ocr_empty), color = Color.White)
            if (!recognizing && recognized?.words?.isNotEmpty() == true && selected == null) {
                Surface(shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.reader_ocr_hint),
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(8.dp)) }
            }
        }
            selected?.let { indices -> recognized?.let { result ->
                val text = indices.sorted().joinToString(" ") { result.words[it].text }
                val factor = minOf(size.width.toFloat() / result.width, size.height.toFloat() / result.height)
                val chosen = indices.map { result.words[it] }
                val dx = (size.width - result.width * factor) / 2
                val dy = (size.height - result.height * factor) / 2
                fun screenX(x: Float) = ((dx + x * factor) - size.width / 2f) * scale + size.width / 2f + offset.x
                fun screenY(y: Float) = ((dy + y * factor) - size.height / 2f) * scale + size.height / 2f + offset.y
                val bounds = androidx.compose.ui.geometry.Rect(screenX(chosen.minOf { it.left }), screenY(chosen.minOf { it.top }),
                    screenX(chosen.maxOf { it.right }), screenY(chosen.maxOf { it.bottom }))
                SelectionActions(bounds, Modifier.fillMaxSize()) {
                Surface(shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(4.dp).verticalScroll(rememberScrollState())) {
                        androidx.compose.foundation.layout.FlowRow {
                            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                            TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)) }) { Text(stringResource(android.R.string.copy)) }
                            TextButton(onClick = { onTool(ReadingTool.TRANSLATE, text) }) { Text(stringResource(R.string.reader_translate)) }
                            TextButton(onClick = { onTool(ReadingTool.DICTIONARY, text) }) { Text(stringResource(R.string.reader_dictionary)) }
                            IconButton(onClick = { selected = null }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.reader_ocr_clear))
                            }
                        }
                    }
                }
                }
            } }
    }
}
