package com.bookorbit.feature.reader.pdf

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val PDF_BACKDROP = Color(0xFF20242A)

@Composable
fun PdfReaderScreen(
    onBack: () -> Unit,
    vm: PdfReaderViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chromeVisible by remember { mutableStateOf(true) }
    var settingsVisible by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var jumpVisible by remember { mutableStateOf(false) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val core = ui.core
    Box(modifier = Modifier.fillMaxSize().background(PDF_BACKDROP)) {
        if (core != null && ui.pageCount > 0) {
            PdfContent(
                core = core,
                ui = ui,
                vm = vm,
                onToggleChrome = { chromeVisible = !chromeVisible },
                onLink = { link -> handleLink(context, vm, link) },
                onCopyText = { page ->
                    if (ui.hasTextLayer) {
                        scope.launch {
                            val text = core.pageText(page)
                            if (text.isBlank()) {
                                Toast.makeText(context, "No selectable text on this page", Toast.LENGTH_SHORT).show()
                            } else {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Page text", text))
                                Toast.makeText(context, "Page text copied", Toast.LENGTH_SHORT).show()
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
                    Text(message, color = Color.White)
                    TextButton(onClick = onBack) { Text("Back") }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.White)
                    }
                }
                IconButton(onClick = { settingsVisible = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
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
        )
    }
    if (jumpVisible) {
        JumpToPageDialog(
            pageCount = ui.pageCount,
            current = ui.currentPage,
            onGo = { page -> vm.onPageChanged(page); jumpVisible = false },
            onDismiss = { jumpVisible = false },
        )
    }
}

@Composable
private fun PdfContent(
    core: PdfRenderCore,
    ui: PdfReaderViewModel.UiState,
    vm: PdfReaderViewModel,
    onToggleChrome: () -> Unit,
    onLink: (PdfLink) -> Unit,
    onCopyText: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val onToggleZoom = remember(vm) {
        {
            val s = vm.ui.value.settings
            if (s.zoomMode == PdfReaderSettings.ZoomMode.CUSTOM) {
                vm.updateSettings(s.copy(zoomMode = PdfReaderSettings.ZoomMode.FIT_WIDTH))
            } else {
                vm.updateSettings(s.withCustomScale(2.0))
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }

        if (ui.settings.scrollMode == PdfReaderSettings.ScrollMode.CONTINUOUS) {
            PdfContinuous(core, ui, vm, widthPx, heightPx, onToggleChrome, onLink, onCopyText, onToggleZoom)
        } else {
            PdfPaginated(core, ui, vm, widthPx, heightPx, onLink, onCopyText, onToggleZoom)
        }
    }
}

@Composable
private fun PdfContinuous(
    core: PdfRenderCore,
    ui: PdfReaderViewModel.UiState,
    vm: PdfReaderViewModel,
    widthPx: Int,
    heightPx: Int,
    onToggleChrome: () -> Unit,
    onLink: (PdfLink) -> Unit,
    onCopyText: (Int) -> Unit,
    onToggleZoom: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ui.initialPage)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { vm.onPageChanged(it) }
    }
    val active = ui.search?.active
    LaunchedEffect(active) { active?.let { listState.animateScrollToItem(it.page) } }

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
                )
            }
        }
    }
}

@Composable
private fun PdfPaginated(
    core: PdfRenderCore,
    ui: PdfReaderViewModel.UiState,
    vm: PdfReaderViewModel,
    widthPx: Int,
    heightPx: Int,
    onLink: (PdfLink) -> Unit,
    onCopyText: (Int) -> Unit,
    onToggleZoom: () -> Unit,
) {
    val items = remember(ui.pageCount, ui.settings.spread) {
        PdfLayout.spreadItems(ui.pageCount, ui.settings.spread)
    }
    val pagerState = rememberPagerState(
        initialPage = PdfLayout.itemForPage(items, ui.initialPage),
    ) { items.size }

    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { vm.onPageChanged(PdfLayout.firstPageOfItem(items, it)) }
    }
    val active = ui.search?.active
    LaunchedEffect(active, items) { active?.let { pagerState.animateScrollToPage(PdfLayout.itemForPage(items, it.page)) } }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { item ->
        val pages = items.getOrElse(item) { listOf(0) }
        Box(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
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
        title = { Text("Go to page") },
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
            }) { Text("Go") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun handleLink(context: Context, vm: PdfReaderViewModel, link: PdfLink) {
    when (link) {
        is PdfLink.Internal -> vm.onPageChanged(link.targetPage)
        is PdfLink.External -> {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.uri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }.onFailure {
                if (it is ActivityNotFoundException) {
                    Toast.makeText(context, "No app to open link", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
