package com.bookorbit.feature.bookdetail

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookorbit.R
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.model.BookAnnotation
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFiles
import com.bookorbit.feature.reader.highlightColor
import com.bookorbit.ui.LocalImageUrls
import com.bookorbit.ui.components.BookCover
import com.bookorbit.ui.components.CoverBackdrop
import com.bookorbit.ui.components.ErrorState
import com.bookorbit.ui.components.GhostButton
import com.bookorbit.ui.components.Panel
import com.bookorbit.ui.components.Pill
import com.bookorbit.ui.components.PrimaryButton
import com.bookorbit.ui.components.RecommendationShelf
import com.bookorbit.ui.components.SecondaryButton
import com.bookorbit.ui.components.SectionLabel
import com.bookorbit.ui.components.StarRating
import com.bookorbit.ui.components.htmlToAnnotatedString
import com.bookorbit.ui.theme.BookOrbitTheme
import kotlin.math.roundToInt

/** Everything the book page can do, so its content can be drawn without a ViewModel. */
class BookDetailActions(
    val onBack: () -> Unit,
    val onRead: () -> Unit,
    val onListen: () -> Unit,
    val onDownload: () -> Unit,
    val onRemoveDownload: () -> Unit,
    val onOpenStatus: () -> Unit,
    val onOpenCollections: () -> Unit,
    val onRate: ((Int?) -> Unit)?,
    val onBookClick: (Int) -> Unit,
    val onAuthorClick: (id: Int, name: String) -> Unit,
    val onSeriesClick: (id: Int, name: String) -> Unit,
    val onRetry: () -> Unit,
    val onOpenUri: (String) -> Unit,
)

@Composable
fun BookDetailScreen(
    onBack: () -> Unit,
    onRead: (Int) -> Unit,
    onReadPdf: (Int) -> Unit,
    onReadComic: (Int) -> Unit,
    onListen: (Int) -> Unit,
    onBookClick: (Int) -> Unit,
    onAuthorClick: (id: Int, name: String) -> Unit,
    onSeriesClick: (id: Int, name: String) -> Unit,
    vm: BookDetailViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Collected only while the page is on screen, so coming back from the reader reloads its progress once.
    val progressSaves by vm.progressSaves.collectAsStateWithLifecycle()
    LaunchedEffect(progressSaves) { vm.onProgressSaved(progressSaves) }
    val collections by vm.collections.collectAsStateWithLifecycle()
    val download by vm.downloadState.collectAsStateWithLifecycle()
    var statusSheet by remember { mutableStateOf(false) }
    var collectionSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.startDownload() }

    val actions = BookDetailActions(
        onBack = onBack,
        onRead = {
            ui.book?.let { book ->
                when (BookFiles.readingTarget(book)) {
                    is BookFiles.ReadingTarget.Pdf -> onReadPdf(book.id)
                    is BookFiles.ReadingTarget.Comic -> onReadComic(book.id)
                    else -> onRead(book.id)
                }
            }
        },
        onListen = { ui.book?.let { onListen(it.id) } },
        onDownload = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                vm.startDownload()
            }
        },
        onRemoveDownload = vm::removeDownload,
        onOpenStatus = { statusSheet = true },
        onOpenCollections = { collectionSheet = true },
        onRate = if (vm.canRate) vm::setRating else null,
        onBookClick = onBookClick,
        onAuthorClick = onAuthorClick,
        onSeriesClick = onSeriesClick,
        onRetry = vm::load,
        onOpenUri = { uriHandler.openUri(it) },
    )
    BookDetailContent(ui, download, actions)

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

/** Wider than this, the cover and actions get their own column beside the details, as on the web. */
private val TwoColumnWidth = 720.dp

@Composable
internal fun BookDetailContent(
    ui: BookDetailViewModel.UiState,
    download: DownloadEntity?,
    actions: BookDetailActions,
) {
    val imageUrls = LocalImageUrls.current
    val book = ui.book
    val coverModel = book?.let { imageUrls.cover(it.id) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= TwoColumnWidth) CoverBackdrop(coverModel, Modifier.fillMaxWidth().height(420.dp))
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            ui.error || book == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                ErrorState(stringResource(R.string.failed_to_load_book_details), onRetry = actions.onRetry)
            }
            maxWidth >= TwoColumnWidth -> Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(300.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 8.dp, end = 8.dp, bottom = 24.dp),
                ) {
                    BackButton(actions.onBack)
                    BookCover(
                        model = coverModel,
                        title = book.title,
                        elevated = true,
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(390.dp),
                    )
                    PrimaryActions(book, ui, download, actions, Modifier.padding(16.dp), stacked = true)
                }
                Column(
                    Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 56.dp, end = 16.dp, bottom = 24.dp),
                ) {
                    TitleBlock(book, ui, actions, large = true, modifier = Modifier.padding(horizontal = 16.dp))
                    DetailTabs(book, ui, actions)
                }
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Box {
                    CoverBackdrop(coverModel, Modifier.matchParentSize())
                    Column {
                        BackButton(actions.onBack)
                        Row(Modifier.padding(horizontal = 20.dp)) {
                            BookCover(
                                model = coverModel,
                                title = book.title,
                                elevated = true,
                                titleSize = MaterialTheme.typography.titleMedium.fontSize,
                                modifier = Modifier.width(124.dp).height(186.dp),
                            )
                            TitleBlock(book, ui, actions, large = false, modifier = Modifier.weight(1f).padding(start = 18.dp))
                        }
                        PrimaryActions(book, ui, download, actions, Modifier.padding(horizontal = 20.dp, vertical = 18.dp))
                    }
                }
                DetailTabs(book, ui, actions)
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
    }
}

/** Title, subtitle, "by" line, narrators, series, then rating and reading status. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitleBlock(
    book: BookDetail,
    ui: BookDetailViewModel.UiState,
    actions: BookDetailActions,
    large: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = BookOrbitTheme.tokens
    Column(modifier) {
        Text(
            book.title ?: stringResource(R.string.card_unknown_title),
            style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        book.subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = tokens.mutedForeground, modifier = Modifier.padding(top = 4.dp))
        }
        if (book.authors.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.detail_by), style = MaterialTheme.typography.bodyMedium, color = tokens.mutedForeground)
                book.authors.forEachIndexed { index, author ->
                    Text(
                        author.name + if (index < book.authors.lastIndex) "," else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.books_by, author.name)) { actions.onAuthorClick(author.id, author.name) },
                    )
                }
            }
        }
        val narrators = book.audioMetadata?.narrators.orEmpty()
        if (narrators.isNotEmpty()) {
            Text(
                stringResource(R.string.detail_narrated_by, narrators.joinToString(", ") { it.name }),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.mutedForeground,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        book.seriesName?.let { series ->
            val seriesId = book.seriesId
            MutedChip(
                series + (book.seriesIndex?.let { " #${it.toInt()}" } ?: ""),
                onClick = if (seriesId != null) ({ actions.onSeriesClick(seriesId, series) }) else null,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusButton(book, ui.statusUpdating, actions.onOpenStatus)
        }
        if (actions.onRate != null || book.rating != null) {
            StarRating(
                value = book.rating,
                onChange = actions.onRate,
                enabled = !ui.ratingUpdating,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** The reading status as a small outlined button that opens the status sheet. */
@Composable
private fun StatusButton(book: BookDetail, updating: Boolean, onClick: () -> Unit) {
    val tokens = BookOrbitTheme.tokens
    val meta = book.readStatus?.let { readStatusMeta(it.status) }
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, tokens.input, MaterialTheme.shapes.small)
            .background(tokens.background.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(meta?.icon ?: Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = meta?.color ?: tokens.mutedForeground, modifier = Modifier.size(16.dp))
        Text(
            stringResource(meta?.label ?: R.string.status_set),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (updating) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = tokens.mutedForeground, modifier = Modifier.size(16.dp))
        }
    }
}

/** Read and Listen, then download and collections, then the formats this book comes in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrimaryActions(
    book: BookDetail,
    ui: BookDetailViewModel.UiState,
    download: DownloadEntity?,
    actions: BookDetailActions,
    modifier: Modifier = Modifier,
    /** One button per row, for the narrow cover column on wide windows. */
    stacked: Boolean = false,
) {
    val canListen = BookFiles.isAudiobook(book)
    val canRead = BookFiles.readingTarget(book) != BookFiles.ReadingTarget.None
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (canRead) PrimaryButton(stringResource(R.string.read), actions.onRead, Modifier.weight(1f), icon = Icons.Outlined.AutoStories)
            if (canListen) {
                if (canRead) {
                    SecondaryButton(stringResource(R.string.listen), actions.onListen, Modifier.weight(1f), icon = Icons.Outlined.Headphones)
                } else {
                    PrimaryButton(stringResource(R.string.listen), actions.onListen, Modifier.weight(1f), icon = Icons.Outlined.Headphones)
                }
            }
        }
        val collectionLabel = if (book.collections.isNotEmpty()) {
            pluralStringResource(R.plurals.detail_in_collections, book.collections.size, book.collections.size)
        } else {
            stringResource(R.string.detail_add_to_collection)
        }
        if (stacked) {
            DownloadControl(download, actions, Modifier.fillMaxWidth())
            SecondaryButton(collectionLabel, actions.onOpenCollections, Modifier.fillMaxWidth(), icon = Icons.Outlined.CollectionsBookmark)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DownloadControl(download, actions, Modifier.weight(1f))
                SecondaryButton(collectionLabel, actions.onOpenCollections, Modifier.weight(1f), icon = Icons.Outlined.CollectionsBookmark)
            }
        }
        val formats = book.files.filter { it.format != null }.distinctBy { it.format!!.lowercase() }
        if (formats.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val tokens = BookOrbitTheme.tokens
                formats.forEach { file ->
                    val pct = ui.fileProgress[file.id]
                    Pill(file.format!!.uppercase() + (pct?.let { " · ${it.roundToInt()}%" } ?: ""), tokens.formatColor(file.format))
                }
            }
        }
    }
}

@Composable
private fun DownloadControl(download: DownloadEntity?, actions: BookDetailActions, modifier: Modifier) {
    val tokens = BookOrbitTheme.tokens
    when (download?.status) {
        "COMPLETE" -> SecondaryButton(stringResource(R.string.downloaded), actions.onRemoveDownload, modifier, icon = Icons.Outlined.CheckCircle)
        "DOWNLOADING" -> Row(
            modifier.height(40.dp).clip(MaterialTheme.shapes.small).border(1.dp, tokens.input, MaterialTheme.shapes.small).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(progress = { download.progress }, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.downloading, (download.progress * 100).toInt()), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        }
        "FAILED" -> SecondaryButton(stringResource(R.string.retry_download), actions.onDownload, modifier, icon = Icons.Outlined.Download)
        else -> SecondaryButton(stringResource(R.string.download), actions.onDownload, modifier, icon = Icons.Outlined.Download)
    }
}

/** Details, Files and Highlights as underlined text tabs, the web's book-page tab strip. */
@Composable
private fun DetailTabs(book: BookDetail, ui: BookDetailViewModel.UiState, actions: BookDetailActions) {
    val tokens = BookOrbitTheme.tokens
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf(
        stringResource(R.string.detail_tab_details),
        stringResource(R.string.detail_tab_files),
        if (ui.highlights.isNotEmpty()) stringResource(R.string.detail_tab_highlights_n, ui.highlights.size) else stringResource(R.string.detail_tab_highlights),
    )
    Column(Modifier.padding(top = 8.dp)) {
        Row(Modifier.padding(horizontal = 12.dp)) {
            labels.forEachIndexed { index, label ->
                val selected = index == tab
                Column(
                    Modifier.width(IntrinsicSize.Max).clip(MaterialTheme.shapes.small).clickable { tab = index }.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) tokens.foreground else tokens.mutedForeground,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    Box(Modifier.height(2.dp).fillMaxWidth().background(if (selected) tokens.foreground else androidx.compose.ui.graphics.Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = tokens.border)
        when (tab) {
            0 -> DetailsTab(book, ui, actions)
            1 -> FilesTab(book)
            else -> HighlightsTab(ui.highlights)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsTab(book: BookDetail, ui: BookDetailViewModel.UiState, actions: BookDetailActions) {
    Column {
        book.description?.takeIf { it.isNotBlank() }?.let { description ->
            Section(stringResource(R.string.synopsis)) {
                val annotated = remember(description) { htmlToAnnotatedString(description) }
                Text(annotated, style = MaterialTheme.typography.bodyLarge)
            }
        }

        DetailsSection(book)

        val goodreadsId = book.providerIds["goodreads"]
        if (goodreadsId != null) {
            Section(stringResource(R.string.links)) {
                GhostButton(
                    stringResource(R.string.view_on_goodreads),
                    onClick = { actions.onOpenUri("https://www.goodreads.com/book/show/$goodreadsId") },
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                )
            }
        }

        if (book.genres.isNotEmpty()) Section(stringResource(R.string.genres)) { ChipFlow(book.genres) }
        if (book.tags.isNotEmpty()) Section(stringResource(R.string.tags)) { ChipFlow(book.tags) }
        if (book.collections.isNotEmpty()) Section(stringResource(R.string.lib_chip_collections)) { ChipFlow(book.collections.map { it.name }) }

        if (ui.authorBooks.isNotEmpty()) {
            RecommendationShelf(
                stringResource(R.string.more_by_this_author),
                Icons.Outlined.Person,
                ui.authorBooks,
                onBookClick = actions.onBookClick,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
            )
        }
        if (ui.recommendations.isNotEmpty()) {
            RecommendationShelf(
                stringResource(R.string.similar_books),
                Icons.Outlined.AutoStories,
                ui.recommendations,
                onBookClick = actions.onBookClick,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(items: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { MutedChip(it) }
    }
}

@Composable
private fun FilesTab(book: BookDetail) {
    val tokens = BookOrbitTheme.tokens
    Column(Modifier.padding(16.dp)) {
        if (book.files.isEmpty()) {
            Text(stringResource(R.string.no_files), style = MaterialTheme.typography.bodyMedium, color = tokens.mutedForeground)
            return@Column
        }
        Panel(contentPadding = PaddingValues(0.dp)) {
            book.files.forEachIndexed { index, f ->
                if (index > 0) HorizontalDivider(color = tokens.border)
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill((f.format ?: "file").uppercase(), tokens.formatColor(f.format))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            f.filename ?: (f.format ?: "").uppercase(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val detail = listOfNotNull(
                            if (f.role == "primary") stringResource(R.string.primary) else null,
                            f.sizeBytes?.let { formatBytes(it) },
                            f.durationSeconds?.let { formatDuration(it) },
                        ).joinToString(" · ")
                        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = tokens.mutedForeground)
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightsTab(highlights: List<BookAnnotation>) {
    val tokens = BookOrbitTheme.tokens
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (highlights.isEmpty()) {
            Text(stringResource(R.string.no_highlights_yet_press_and), style = MaterialTheme.typography.bodyMedium, color = tokens.mutedForeground)
        }
        highlights.forEach { a -> HighlightCard(a.text, a.note, a.color, a.chapterTitle) }
    }
}

/** A highlight as a card: the passage in the serif beside a bar of its colour, the note under it. */
@Composable
fun HighlightCard(text: String, note: String?, color: String, source: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val tokens = BookOrbitTheme.tokens
    Panel(modifier = modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row {
            Box(Modifier.width(3.dp).height(48.dp).clip(RoundedCornerShape(2.dp)).background(highlightColor(color)))
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    text,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight, fontWeight = FontWeight.Normal),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
                note?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("✎ ") }
                            append(it)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                source?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = tokens.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
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
    val tokens = BookOrbitTheme.tokens
    val rows = buildList {
        book.pageCount?.let { add(stringResource(R.string.detail_row_pages) to it.toString()) }
        book.language?.let { add(stringResource(R.string.detail_row_language) to it.uppercase()) }
        book.publisher?.let { add(stringResource(R.string.detail_row_publisher) to it) }
        book.publishedYear?.let { add(stringResource(R.string.detail_row_published) to it.toString()) }
        book.isbn13?.let { add(stringResource(R.string.detail_row_isbn13) to it) }
        book.isbn10?.let { add(stringResource(R.string.detail_row_isbn10) to it) }
        add(stringResource(R.string.detail_row_library) to book.libraryName)
    }
    Section(stringResource(R.string.details)) {
        Panel(contentPadding = PaddingValues(0.dp)) {
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider(color = tokens.border)
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = tokens.mutedForeground)
                    Spacer(Modifier.width(16.dp))
                    Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        SectionLabel(title)
        Box(Modifier.padding(horizontal = 16.dp)) { content() }
    }
}

/** A round muted chip for genres, tags and the series, optionally tappable. */
@Composable
private fun MutedChip(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val tokens = BookOrbitTheme.tokens
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = tokens.foreground,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(tokens.muted)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
