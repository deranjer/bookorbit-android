package com.bookorbit.feature.bookdetail

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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    onListen: (Int) -> Unit,
    onBookClick: (Int) -> Unit,
    vm: BookDetailViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    var statusSheet by remember { mutableStateOf(false) }
    var collectionSheet by remember { mutableStateOf(false) }

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
                    Text("Failed to load book details", color = MaterialTheme.colorScheme.error)
                }
                else -> BookDetailContent(
                    book = ui.book!!,
                    vm = vm,
                    ui = ui,
                    onRead = onRead,
                    onListen = onListen,
                    onBookClick = onBookClick,
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
    onListen: (Int) -> Unit,
    onBookClick: (Int) -> Unit,
    onOpenStatusSheet: () -> Unit,
    onOpenCollectionSheet: () -> Unit,
) {
    val imageUrls = LocalImageUrls.current
    val uriHandler = LocalUriHandler.current
    val canListen = BookFiles.isAudiobook(book)
    val canRead = BookFiles.isReadable(book)
    val goodreadsId = book.providerIds["goodreads"]

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        // Hero
        Row(modifier = Modifier.padding(20.dp)) {
            AsyncImage(
                model = imageUrls.cover(book.id),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 110.dp, height = 165.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(book.title ?: "Unknown Title", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                book.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (book.authors.isNotEmpty()) {
                    Text(
                        book.authors.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                book.seriesName?.let { series ->
                    Text(
                        series + (book.seriesIndex?.let { " #${it.toInt()}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        // Actions
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (canListen) {
                Button(onClick = { onListen(book.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Headset, contentDescription = null)
                    Text("Listen", modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (canRead) {
                Button(onClick = { onRead(book.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Book, contentDescription = null)
                    Text("Read", modifier = Modifier.padding(start = 8.dp))
                }
            }

            val download by vm.downloadState.collectAsStateWithLifecycle()
            when (download?.status) {
                "COMPLETE" -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = com.bookorbit.ui.theme.SuccessGreen)
                    Text("Downloaded", color = com.bookorbit.ui.theme.SuccessGreen, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = vm::removeDownload) { Text("Remove") }
                }
                "DOWNLOADING" -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Downloading ${((download?.progress ?: 0f) * 100).toInt()}%")
                }
                else -> androidx.compose.material3.OutlinedButton(
                    onClick = vm::startDownload,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Text("Download", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        // Shelf: status, rating, collections
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenStatusSheet),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenCollectionSheet),
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

        book.description?.let { description ->
            Section("Synopsis") {
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }

        DetailsSection(book)

        if (goodreadsId != null) {
            Section("Links") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { uriHandler.openUri("https://www.goodreads.com/book/show/$goodreadsId") }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("View on Goodreads", modifier = Modifier.padding(start = 10.dp))
                }
            }
        }

        if (book.genres.isNotEmpty()) {
            Section("Genres") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { book.genres.forEach { Chip(it) } } }
        }
        if (book.tags.isNotEmpty()) {
            Section("Tags") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { book.tags.forEach { Chip(it) } } }
        }

        if (ui.authorBooks.isNotEmpty()) {
            RecommendationScroller("More by this Author", ui.authorBooks, onBookClick = onBookClick)
        }
        if (ui.recommendations.isNotEmpty()) {
            RecommendationScroller("Similar Books", ui.recommendations, onBookClick = onBookClick)
        }

        Box(Modifier.height(24.dp))
    }
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
private fun Chip(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
