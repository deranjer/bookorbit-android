package com.bookorbit.feature.reader.pdf

import com.bookorbit.ui.asString
import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import com.bookorbit.feature.reader.ReaderPager
import com.bookorbit.feature.reader.ReaderTapAction
import com.bookorbit.feature.reader.ReaderTapZonesHelp
import com.bookorbit.feature.reader.readerTapAction
import com.bookorbit.feature.reader.readerPageAnimation

private val PDF_BACKDROP = Color(0xFF20242A)

@Composable
fun PdfReaderScreen(
    onBack: () -> Unit,
    vm: PdfReaderViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val motion by vm.readerSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val noTextMessage = stringResource(R.string.pdf_no_text)
    val clipLabel = stringResource(R.string.pdf_clip_label)
    val copiedMessage = stringResource(R.string.pdf_text_copied)
    val scope = rememberCoroutineScope()
    var chromeVisible by remember { mutableStateOf(true) }
    var settingsVisible by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var jumpVisible by remember { mutableStateOf(false) }
    var zonesVisible by remember { mutableStateOf(false) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val core = ui.core
    Box(modifier = Modifier.fillMaxSize().background(PDF_BACKDROP)) {
        if (core != null && ui.pageCount > 0) {
            PdfReaderContent(
                core = core,
                ui = ui,
                onPageChanged = vm::onPageChanged,
                animated = motion.animatePageTurns,
                onNavigationApplied = vm::onNavigationApplied,
                onToggleZoom = {
                    val settings = vm.ui.value.settings
                    vm.updateSettings(if (settings.zoomMode == PdfReaderSettings.ZoomMode.CUSTOM)
                        settings.copy(zoomMode = PdfReaderSettings.ZoomMode.FIT_WIDTH) else settings.withCustomScale(2.0))
                },
                onToggleChrome = { chromeVisible = !chromeVisible },
                onLink = { link -> handleLink(context, vm, link) },
                onCopyText = { page ->
                    if (ui.hasTextLayer) {
                        scope.launch {
                            val text = core.pageText(page)
                            if (text.isBlank()) {
                                Toast.makeText(context, noTextMessage, Toast.LENGTH_SHORT).show()
                            } else {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
                                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
            )
        }

        if (ui.loading && ui.error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        ui.error?.let { message ->
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(message.asString(), color = Color.White)
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                }
            }
        }

        if (chromeVisible) {
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
                    ui.title ?: "",
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (ui.hasTextLayer) {
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search), tint = Color.White)
                    }
                }
                IconButton(onClick = { settingsVisible = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings), tint = Color.White)
                }
                IconButton(onClick = { zonesVisible = true }) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(R.string.reader_zones_title), tint = Color.White)
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
                TextButton(onClick = { jumpVisible = true }) {
                    Text("${ui.currentPage + 1} / ${ui.pageCount}", color = Color.White.copy(alpha = 0.85f))
                }
            }
        }

        if (searchVisible && ui.hasTextLayer) {
            PdfSearchBar(
                search = ui.search,
                onQuery = vm::search,
                onPrev = vm::prevMatch,
                onNext = vm::nextMatch,
                onClose = { searchVisible = false; vm.clearSearch() },
                modifier = Modifier.statusBarsPadding(),
            )
        }
    }

    if (settingsVisible) {
        PdfReaderSettingsSheet(
            settings = ui.settings,
            onChange = vm::updateSettings,
            onDismiss = { settingsVisible = false },
            animated = motion.animatePageTurns,
            onAnimationChanged = vm::updatePageAnimation,
        )
    }
    if (jumpVisible) {
        JumpToPageDialog(
            pageCount = ui.pageCount,
            current = ui.currentPage,
            onGo = { page -> vm.goToPage(page); jumpVisible = false },
            onDismiss = { jumpVisible = false },
        )
    }
    if (zonesVisible) ReaderTapZonesHelp(pdf = true,
        paginated = ui.settings.scrollMode == PdfReaderSettings.ScrollMode.PAGINATED,
        onDismiss = { zonesVisible = false })
}

@Composable
internal fun PdfReaderContent(
    core: PdfRenderCore,
    ui: PdfReaderViewModel.UiState,
    onPageChanged: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    onLink: (PdfLink) -> Unit,
    onCopyText: (Int) -> Unit,
    onToggleZoom: () -> Unit,
    animated: Boolean = true,
    onNavigationApplied: (PdfPageRequest) -> Unit = {},
) {
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }

        if (ui.settings.scrollMode == PdfReaderSettings.ScrollMode.CONTINUOUS) {
            PdfContinuous(core, ui, onPageChanged, widthPx, heightPx, onToggleChrome, onLink, onCopyText, onToggleZoom, onNavigationApplied)
        } else {
            key(ui.settings.spread) {
                PdfPaginated(core, ui, onPageChanged, widthPx, heightPx, onLink, onCopyText, onToggleZoom, onToggleChrome, animated, onNavigationApplied)
            }
        }
    }
}

@Composable
private fun PdfContinuous(
    core: PdfRenderCore,
    ui: PdfReaderViewModel.UiState,
    onPageChanged: (Int) -> Unit,
    widthPx: Int,
    heightPx: Int,
    onToggleChrome: () -> Unit,
    onLink: (PdfLink) -> Unit,
    onCopyText: (Int) -> Unit,
    onToggleZoom: () -> Unit,
    onNavigationApplied: (PdfPageRequest) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ui.currentPage)

    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            PdfLayout.mostVisiblePage(
                pages = layout.visibleItemsInfo.map { PdfLayout.VisiblePage(it.index, it.offset, it.size) },
                viewportStart = layout.viewportStartOffset,
                viewportEnd = layout.viewportEndOffset,
            )
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect(onPageChanged)
    }
    val active = ui.search?.active
    LaunchedEffect(ui.navigation) { ui.navigation?.let { listState.scrollToItem(it.page); onNavigationApplied(it) } }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onToggleChrome() }) },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ui.pageCount) { index ->
            Box(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                PdfPageView(
                    core = core,
                    index = index,
                    baseWidthPx = widthPx,
                    containerHeightPx = heightPx,
                    settings = ui.settings,
                    highlights = ui.search?.hits?.filter { it.page == index }?.map { it.rect }.orEmpty(),
                    activeHighlight = active?.takeIf { it.page == index }?.rect,
                    onLink = onLink,
                    onLongPress = onCopyText,
                    onToggleZoom = onToggleZoom,
                    onBackgroundTap = { onToggleChrome() },
                )
            }
        }
    }
}

@Composable
private fun PdfPaginated(
    core: PdfRenderCore,
    ui: PdfReaderViewModel.UiState,
    onPageChanged: (Int) -> Unit,
    widthPx: Int,
    heightPx: Int,
    onLink: (PdfLink) -> Unit,
    onCopyText: (Int) -> Unit,
    onToggleZoom: () -> Unit,
    onToggleChrome: () -> Unit,
    animated: Boolean,
    onNavigationApplied: (PdfPageRequest) -> Unit,
) {
    val items = remember(ui.pageCount, ui.settings.spread) {
        PdfLayout.spreadItems(ui.pageCount, ui.settings.spread)
    }
    val pagerState = rememberPagerState(
        initialPage = PdfLayout.itemForPage(items, ui.currentPage),
    ) { items.size }

    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { onPageChanged(PdfLayout.firstPageOfItem(items, it)) }
    }
    val active = ui.search?.active
    LaunchedEffect(ui.navigation, items) { ui.navigation?.let { pagerState.scrollToPage(PdfLayout.itemForPage(items, it.page)); onNavigationApplied(it) } }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val pageAnimation = readerPageAnimation(animated)
    val tap: (Offset) -> Unit = { point ->
        when (readerTapAction((point.x - origin.x) / widthPx.toDouble())) {
            ReaderTapAction.PREVIOUS -> scope.launch {
                if (pagerState.currentPage > 0) pagerState.animateScrollToPage(pagerState.currentPage - 1, animationSpec = pageAnimation)
            }
            ReaderTapAction.NEXT -> scope.launch {
                if (pagerState.currentPage < items.lastIndex) pagerState.animateScrollToPage(pagerState.currentPage + 1, animationSpec = pageAnimation)
            }
            ReaderTapAction.MENU -> onToggleChrome()
        }
    }

    ReaderPager(state = pagerState, animated = animated,
        userScrollEnabled = ui.settings.zoomMode != PdfReaderSettings.ZoomMode.CUSTOM || ui.settings.customScale <= 1.0,
        modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(tap) { detectTapGestures(onTap = { tap(origin + it) }) }) { item ->
        val pages = items.getOrElse(item) { listOf(0) }
        Box(
            modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                pages.forEach { index ->
                    val baseW = if (pages.size == 2) widthPx / 2 else widthPx
                    PdfPageView(
                        core = core,
                        index = index,
                        baseWidthPx = baseW,
                        containerHeightPx = heightPx,
                        settings = ui.settings,
                        highlights = ui.search?.hits?.filter { it.page == index }?.map { it.rect }.orEmpty(),
                        activeHighlight = active?.takeIf { it.page == index }?.rect,
                        onLink = onLink,
                        onLongPress = onCopyText,
                        onToggleZoom = onToggleZoom,
                        onBackgroundTap = tap,
                    )
                }
            }
        }
    }
}

@Composable
private fun JumpToPageDialog(
    pageCount: Int,
    current: Int,
    onGo: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("${current + 1}") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.go_to_page)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(7) },
                singleLine = true,
                label = { Text("1 – $pageCount") },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val page = (text.toIntOrNull() ?: (current + 1)).coerceIn(1, pageCount) - 1
                onGo(page)
            }) { Text(stringResource(R.string.go)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun handleLink(context: Context, vm: PdfReaderViewModel, link: PdfLink) {
    when (link) {
        is PdfLink.Internal -> vm.goToPage(link.targetPage)
        is PdfLink.External -> {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.uri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }.onFailure {
                if (it is ActivityNotFoundException) {
                    Toast.makeText(context, context.getString(R.string.pdf_no_link_app), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
