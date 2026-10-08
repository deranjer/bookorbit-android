package com.bookorbit.feature.bookdetail

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.bookorbit.ui.components.htmlToAnnotatedString
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Button
import com.bookorbit.feature.reader.highlightColor
import com.bookorbit.core.model.BookAnnotation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFiles
import com.bookorbit.ui.LocalImageUrls
import com.bookorbit.ui.components.RecommendationScroller
import com.bookorbit.ui.components.StarRating

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookDetailScreen(
    onBack: () -> Unit,
    onRead: (Int) -> Unit,
    onReadPdf: (Int) -> Unit,
    onListen: (Int) -> Unit,
    onBookClick: (Int) -> Unit,
    vm: BookDetailViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    var statusSheet by remember { mutableStateOf(false) }
    var collectionSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.startDownload() }
    val startDownload = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.startDownload()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.book?.title ?: "Book Details", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                ui.error || ui.book == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Failed to load book details", color = MaterialTheme.colorScheme.error)
                        Button(onClick = vm::load, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
                    }
                }
                else -> BookDetailContent(
                    book = ui.book!!,
                    vm = vm,
                    ui = ui,
                    onRead = onRead,
                    onReadPdf = onReadPdf,
                    onListen = onListen,
                    onBookClick = onBookClick,
                    onStartDownload = startDownload,
                    onOpenStatusSheet = { statusSheet = true },
                    onOpenCollectionSheet = { collectionSheet = true },
                )
            }
        }
    }

    val book = ui.book
    if (book != null && statusSheet) {
        ReadStatusSheet(
            current = book.readStatus?.status,
            onSelect = {
                vm.setStatus(it)
                statusSheet = false
            },
            onDismiss = { statusSheet = false },
        )
    }
    if (book != null && collectionSheet) {
        CollectionPickerSheet(
            state = collections,
            onLoad = vm::loadCollections,
            onToggle = vm::toggleCollection,
            onDismiss = { collectionSheet = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookDetailContent(
    book: BookDetail,
    vm: BookDetailViewModel,
    ui: BookDetailViewModel.UiState,
    onRead: (Int) -> Unit,
    onReadPdf: (Int) -> Unit,
    onListen: (Int) -> Unit,
    onBookClick: (Int) -> Unit,
    onStartDownload: () -> Unit,
    onOpenStatusSheet: () -> Unit,
    onOpenCollectionSheet: () -> Unit,
) {
    val imageUrls = LocalImageUrls.current
    val uriHandler = LocalUriHandler.current
    val canListen = BookFiles.isAudiobook(book)
    val readingTarget = BookFiles.readingTarget(book)
    val canRead = readingTarget != BookFiles.ReadingTarget.None
    val goodreadsId = book.providerIds["goodreads"]

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val download by vm.downloadState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DetailHero(book)

        // Primary actions side by side; download state sits underneath.
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (canRead) {
                    Button(
                        onClick = {
                            when (readingTarget) {
                                is BookFiles.ReadingTarget.Pdf -> onReadPdf(book.id)
                                else -> onRead(book.id)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Book, contentDescription = null)
                        Text("Read", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (canListen) {
                    if (canRead) {
                        androidx.compose.material3.OutlinedButton(onClick = { onListen(book.id) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Headset, contentDescription = null)
                            Text("Listen", modifier = Modifier.padding(start = 8.dp))
                        }
                    } else {
                        Button(onClick = { onListen(book.id) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Headset, contentDescription = null)
                            Text("Listen", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            DownloadControl(download, onStartDownload = onStartDownload, onRemove = vm::removeDownload)
        }

        FormatChips(book, ui.fileProgress)

        val tabs = listOf("Details", "Files", if (ui.highlights.isNotEmpty()) "Highlights ${ui.highlights.size}" else "Highlights")
        TabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 8.dp)) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, maxLines = 1) })
            }
        }

        when (tab) {
            0 -> DetailsTab(
                book = book,
                vm = vm,
                ui = ui,
                goodreadsId = goodreadsId,
                onBookClick = onBookClick,
                onOpenStatusSheet = onOpenStatusSheet,
                onOpenCollectionSheet = onOpenCollectionSheet,
                onOpenUri = { uriHandler.openUri(it) },
            )
            1 -> FilesTab(book)
            else -> HighlightsTab(ui.highlights)
        }

        Box(Modifier.height(24.dp))
    }
}

/** Cover on a soft tinted backdrop, with title, authors, narrators and series centred beneath. */
@Composable
private fun DetailHero(book: BookDetail) {
    val imageUrls = LocalImageUrls.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = imageUrls.cover(book.id),
            contentDescription = book.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 150.dp, height = 225.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Text(
            book.title ?: "Unknown title",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        book.subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (book.authors.isNotEmpty()) {
            Text(
                book.authors.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        val narrators = book.audioMetadata?.narrators.orEmpty()
        if (narrators.isNotEmpty()) {
            Text(
                "Narrated by " + narrators.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        book.seriesName?.let { series ->
            Chip(series + (book.seriesIndex?.let { " #${it.toInt()}" } ?: ""), modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun DownloadControl(
    download: com.bookorbit.core.db.DownloadEntity?,
    onStartDownload: () -> Unit,
    onRemove: () -> Unit,
) {
    when (download?.status) {
        "COMPLETE" -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = com.bookorbit.ui.theme.SuccessGreen)
            Text("Downloaded", color = com.bookorbit.ui.theme.SuccessGreen, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onRemove) { Text("Remove") }
        }
        "DOWNLOADING" -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("Downloading ${((download.progress) * 100).toInt()}%")
        }
        "FAILED" -> Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                download.lastError ?: "Download failed",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            androidx.compose.material3.OutlinedButton(
                onClick = onStartDownload,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Text("Retry download", modifier = Modifier.padding(start = 8.dp))
            }
        }
        else -> androidx.compose.material3.OutlinedButton(onClick = onStartDownload, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Text("Download", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** One chip per format the book comes in, with your reading position in it when there is one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatChips(book: BookDetail, fileProgress: Map<Int, Double>) {
    val formats = book.files.filter { it.format != null }
    if (formats.isEmpty()) return
    FlowRow(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        formats.distinctBy { it.format!!.lowercase() }.forEach { file ->
            val pct = fileProgress[file.id]
            Chip(file.format!!.uppercase() + (pct?.let { " ${it.toInt()}%" } ?: ""), emphasized = pct != null)
        }
    }
}

@Composable
private fun DetailsTab(
    book: BookDetail,
    vm: BookDetailViewModel,
    ui: BookDetailViewModel.UiState,
    goodreadsId: String?,
    onBookClick: (Int) -> Unit,
    onOpenStatusSheet: () -> Unit,
    onOpenCollectionSheet: () -> Unit,
    onOpenUri: (String) -> Unit,
) {
    Column {
        ShelfCards(book, vm, ui, onOpenStatusSheet, onOpenCollectionSheet)

        book.description?.let { description ->
            Section("Synopsis") {
                val annotated = remember(description) { htmlToAnnotatedString(description) }
                Text(annotated, style = MaterialTheme.typography.bodyMedium)
            }
        }

        DetailsSection(book)

        if (goodreadsId != null) {
            Section("Links") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenUri("https://www.goodreads.com/book/show/$goodreadsId") }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("View on Goodreads", modifier = Modifier.padding(start = 10.dp))
                }
            }
        }

        if (book.genres.isNotEmpty()) {
            Section("Genres") { FlowRowChips(book.genres) }
        }
        if (book.tags.isNotEmpty()) {
            Section("Tags") { FlowRowChips(book.tags) }
        }

        if (ui.authorBooks.isNotEmpty()) {
            RecommendationScroller("More by this Author", ui.authorBooks, onBookClick = onBookClick)
        }
        if (ui.recommendations.isNotEmpty()) {
            RecommendationScroller("Similar Books", ui.recommendations, onBookClick = onBookClick)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(items: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { Chip(it) }
    }
}

/** Reading status, rating and collections. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShelfCards(
    book: BookDetail,
    vm: BookDetailViewModel,
    ui: BookDetailViewModel.UiState,
    onOpenStatusSheet: () -> Unit,
    onOpenCollectionSheet: () -> Unit,
) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenStatusSheet),
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                val meta = book.readStatus?.let { readStatusMeta(it.status) }
                Icon(
                    meta?.icon ?: Icons.Filled.Book,
                    contentDescription = null,
                    tint = meta?.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    meta?.label ?: "Set reading status",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                if (ui.statusUpdating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (vm.canRate || book.rating != null) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Rating", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StarRating(
                        value = book.rating,
                        onChange = if (vm.canRate) vm::setRating else null,
                        enabled = !ui.ratingUpdating,
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenCollectionSheet),
        ) {
            Text(
                if (book.collections.isNotEmpty()) {
                    "In ${book.collections.size} collection${if (book.collections.size == 1) "" else "s"}"
                } else {
                    "Add to collection"
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(14.dp),
            )
        }
        if (book.collections.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                book.collections.forEach { Chip(it.name) }
            }
        }
    }
}

@Composable
private fun FilesTab(book: BookDetail) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        if (book.files.isEmpty()) {
            Text("No files.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        book.files.forEach { f ->
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (f.format ?: "file").uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (f.role == "primary") {
                        Text("  ·  Primary", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.weight(1f))
                    val detail = listOfNotNull(
                        f.sizeBytes?.let { formatBytes(it) },
                        f.durationSeconds?.let { formatDuration(it) },
                    ).joinToString(" · ")
                    if (detail.isNotEmpty()) {
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                f.filename?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun HighlightsTab(highlights: List<BookAnnotation>) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        if (highlights.isEmpty()) {
            Text(
                "No highlights yet. Press and hold text while reading to highlight it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        highlights.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .width(4.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(highlightColor(a.color)),
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text(a.text, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    a.note?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    a.chapterTitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    else -> "%d KB".format(bytes / 1_000)
}

private fun formatDuration(seconds: Double): String {
    val total = seconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
private fun DetailsSection(book: BookDetail) {
    val rows = buildList {
        book.pageCount?.let { add("Pages" to it.toString()) }
        book.language?.let { add("Language" to it.uppercase()) }
        book.publisher?.let { add("Publisher" to it) }
        book.publishedYear?.let { add("Published" to it.toString()) }
        book.isbn13?.let { add("ISBN-13" to it) }
        book.isbn10?.let { add("ISBN-10" to it) }
        add("Library" to book.libraryName)
    }
    if (rows.isEmpty()) return
    Section("Details") {
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 24.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun Chip(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Surface(
        color = if (emphasized) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
