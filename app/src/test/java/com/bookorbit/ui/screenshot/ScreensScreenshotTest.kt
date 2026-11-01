package com.bookorbit.ui.screenshot

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.model.AuthorSummary
import com.bookorbit.core.model.BookAnnotation
import com.bookorbit.core.model.BookAuthorRef
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.core.model.BookRecommendation
import com.bookorbit.core.model.CollectionRef
import com.bookorbit.core.model.HubAnnotation
import com.bookorbit.core.model.Library
import com.bookorbit.core.model.ReadingGoalWidget
import com.bookorbit.core.model.ReadingStreakWidget
import com.bookorbit.core.model.SearchResult
import com.bookorbit.core.model.SeriesSummary
import com.bookorbit.core.model.SourceDistribution
import com.bookorbit.core.model.SourceSlice
import com.bookorbit.core.model.UserBookStatus
import com.bookorbit.core.model.UserStatsSummary
import com.bookorbit.core.network.ImageUrls
import com.bookorbit.core.settings.SeriesViewMode
import com.bookorbit.feature.authors.AuthorsContent
import com.bookorbit.feature.bookdetail.BookDetailActions
import com.bookorbit.feature.bookdetail.BookDetailContent
import com.bookorbit.feature.bookdetail.BookDetailViewModel
import com.bookorbit.feature.dashboard.DashboardContent
import com.bookorbit.feature.dashboard.DashboardViewModel
import com.bookorbit.feature.downloads.DownloadStatus
import com.bookorbit.feature.downloads.DownloadsContent
import com.bookorbit.feature.library.ChipItem
import com.bookorbit.feature.library.LibrariesContent
import com.bookorbit.feature.library.LibrariesViewModel
import com.bookorbit.feature.library.PickedBooksContent
import com.bookorbit.feature.main.LibraryTabs
import com.bookorbit.feature.main.NavItem
import com.bookorbit.feature.notes.NotesContent
import com.bookorbit.feature.notes.NotesViewModel
import com.bookorbit.feature.search.SearchContent
import com.bookorbit.feature.search.SearchViewModel
import com.bookorbit.feature.series.SeriesContent
import com.bookorbit.feature.stats.StatsContent
import com.bookorbit.feature.stats.StatsViewModel
import com.bookorbit.feature.you.YouScreen
import com.bookorbit.ui.LocalImageUrls
import com.bookorbit.ui.theme.BookOrbitTheme
import com.github.takahirom.roborazzi.captureRoboImage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.AutoStories

/**
 * Every rebuilt screen, drawn from fixed data through its stateless content composable, at each
 * [ScreenshotDevice] in light and dark. Covers are generated gradients, so no server is needed.
 * Book Drop and Settings are left out: their state is still read inside the screen.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevice.SDK], application = Application::class)
class ScreensScreenshotTest(private val device: ScreenshotDevice, private val dark: Boolean) {

    @Before
    fun fakeCovers() {
        SingletonImageLoader.setUnsafe { context ->
            ImageLoader.Builder(context)
                .coroutineContext(Dispatchers.Unconfined)
                .components { add(CoverInterceptor) }
                .build()
        }
    }

    @Test
    fun home() = capture("screen-home") {
        DashboardContent(
            DashboardViewModel.UiState(
                continueReading = Fake.books.take(4),
                recentlyAdded = Fake.books.drop(3),
                loading = false,
                streak = ReadingStreakWidget(currentStreak = 4, longestStreak = 9, lastSevenDays = listOf(false, true, true, false, true, true, true)),
                goal = ReadingGoalWidget(goalBooks = 24, completedBooks = 17, year = 2026),
            ),
            userName = "Pranava",
            greeting = "Good evening",
            onRefresh = {}, onOpenProfile = {}, onBookClick = {},
        )
    }

    @Test
    fun libraryBooks() = capture("screen-library-books") {
        LibrariesContent(
            libraries = listOf(Library(1, "Classics", bookCount = 10, createdAt = "", updatedAt = ""), Library(2, "Comics", createdAt = "", updatedAt = "")),
            status = LibrariesViewModel.Status.READY,
            selectedId = 1,
            activeFilterCount = 2,
            books = pagingOf(Fake.books),
            tabs = { LibraryTabs(0) {} },
            onSelectLibrary = {}, onOpenFilters = {}, onRetry = {}, onBookClick = {},
        )
    }

    @Test
    fun librarySeries() = capture("screen-library-series") {
        SeriesContent(
            series = pagingOf(Fake.series),
            viewMode = SeriesViewMode.GRID,
            onViewMode = {},
            tabs = { LibraryTabs(1) {} },
            onSelect = {},
        )
    }

    @Test
    fun libraryAuthors() = capture("screen-library-authors") {
        AuthorsContent(
            authors = pagingOf(Fake.authors),
            tabs = { LibraryTabs(2) {} },
            onSelect = {},
        )
    }

    @Test
    fun libraryCollections() = capture("screen-library-collections") {
        PickedBooksContent(
            title = "Collections", eyebrow = "Library", tabs = { LibraryTabs(3) {} },
            chips = listOf(ChipItem(1, "Favourites", 6), ChipItem(2, "Book club", 3), ChipItem(3, "Summer", 4)),
            selectedId = 1, onSelect = {},
            books = pagingOf(Fake.books.take(6)),
            onBookClick = {},
            emptyIcon = Icons.Outlined.CollectionsBookmark, emptyTitle = "", emptyBody = "", emptyBooksText = "",
        )
    }

    @Test
    fun book() = capture("screen-book") {
        BookDetailContent(
            BookDetailViewModel.UiState(
                loading = false,
                book = Fake.detail,
                authorBooks = Fake.recommendations.take(3),
                recommendations = Fake.recommendations,
                fileProgress = mapOf(11 to 42.0),
            ),
            download = null,
            actions = Fake.bookActions,
        )
    }

    @Test
    fun search() = capture("screen-search") {
        SearchContent(
            query = "the",
            state = SearchViewModel.UiState(results = Fake.searchResults, showHint = false),
            onQueryChange = {}, onBookClick = {},
        )
    }

    @Test
    fun notes() = capture("screen-notes") {
        NotesContent(
            NotesViewModel.UiState(items = Fake.highlights, total = Fake.highlights.size, loading = false),
            onQuery = {}, onOnlyWithNotes = {}, onLoadMore = {}, onRetry = {}, onBookClick = {},
        )
    }

    @Test
    fun stats() = capture("screen-stats") {
        val today = LocalDate.of(2026, 10, 31)
        StatsContent(
            StatsViewModel.UiState(
                loading = false,
                summary = UserStatsSummary(trackedBooks = 30, startedBooks = 24, inProgressBooks = 3, completedBooks = 17, meanProgressPercent = 68.0),
                streak = ReadingStreakWidget(currentStreak = 4, lastSevenDays = listOf(false, true, true, false, true, true, true)),
                goal = ReadingGoalWidget(goalBooks = 24, completedBooks = 17, year = 2026),
                daily = (83 downTo 0).associate { today.minusDays(it.toLong()) to ((it * 37 + 11) % 5) * 900L },
                sources = SourceDistribution(36_000, listOf(SourceSlice("android", 20_000), SourceSlice("bookorbit", 12_000), SourceSlice("koreader", 4_000))),
            ),
            onRetry = {},
        )
    }

    @Test
    fun you() = capture("screen-you") {
        YouScreen(
            user = AuthUser(id = 1, username = "pranava", name = "Pranava", email = "reader@example.test"),
            serverVersion = "v3.6.0",
            updateAvailable = true,
            latestVersion = "3.7.0",
            unpinned = listOf(NavItem.STATS, NavItem.DOWNLOADS, NavItem.SETTINGS),
            onOpen = {}, onSignOut = {},
        )
    }

    @Test
    fun downloads() = capture("screen-downloads") {
        DownloadsContent(Fake.downloads, onBookClick = {}, onRetry = {}, onDelete = {}, coverModel = { "test://cover/${it.bookId}" })
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers(device.qualifiers(dark))
        captureRoboImage(screenshotPath(name, device, dark)) {
            BookOrbitTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalImageUrls provides Fake.imageUrls) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        // Phone only: the paged screens take most of a minute each to render here.
        fun params() = listOf(arrayOf<Any>(ScreenshotDevice.Phone, false), arrayOf<Any>(ScreenshotDevice.Phone, true))
    }
}

/**
 * A fixed list as paged items. Collected unconfined: on the default context each paged screen
 * waited minutes for Compose to go idle.
 */
@Composable
private fun <T : Any> pagingOf(items: List<T>) = flowOf(PagingData.from(items)).collectAsLazyPagingItems(Dispatchers.Unconfined)

/** Answers every image request with a two-colour gradient picked from the URL, standing in for covers. */
private object CoverInterceptor : Interceptor {
    private val palettes = listOf(
        0xFF6B2D2D to 0xFFD9A86C, 0xFF1F3A5F to 0xFF8FB3D9, 0xFF2F4F3A to 0xFFB8D4A0, 0xFF4A3260 to 0xFFD6B8E8,
        0xFF5C4A1E to 0xFFE8D28F, 0xFF1E4A4A to 0xFF9FD6D0, 0xFF3D3D3D to 0xFFCFCFCF, 0xFF7A3B12 to 0xFFF2B880,
    )

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val key = chain.request.data.toString().hashCode().let { if (it < 0) -it else it }
        val (top, bottom) = palettes[key % palettes.size]
        val bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(
            0f, 0f, 200f, 300f,
            Paint().apply { shader = LinearGradient(0f, 0f, 60f, 300f, top.toInt(), bottom.toInt(), Shader.TileMode.CLAMP) },
        )
        return SuccessResult(image = bitmap.asImage(), request = chain.request, dataSource = DataSource.MEMORY)
    }
}

/** Fixed data for the screens: ten public-domain books and what hangs off them. */
private object Fake {
    val imageUrls: ImageUrls = mockk {
        every { cover(any()) } answers { "test://cover/${firstArg<Int>()}" }
        every { thumbnail(any()) } answers { "test://cover/${firstArg<Int>()}" }
        every { authorThumbnail(any()) } answers { "test://author/${firstArg<Int>()}" }
        every { bookDockCover(any()) } answers { "test://dock/${firstArg<Int>()}" }
    }

    private val titles = listOf(
        "Pride and Prejudice" to "Jane Austen",
        "Moby Dick; Or, The Whale" to "Herman Melville",
        "Alice's Adventures in Wonderland" to "Lewis Carroll",
        "Frankenstein" to "Mary Wollstonecraft Shelley",
        "A Tale of Two Cities" to "Charles Dickens",
        "Dracula" to "Bram Stoker",
        "The Picture of Dorian Gray" to "Oscar Wilde",
        "Crime and Punishment" to "Fyodor Dostoyevsky",
        "The Adventures of Sherlock Holmes" to "Arthur Conan Doyle",
        "The Yellow Wallpaper" to "Charlotte Perkins Gilman",
    )
    private val formats = listOf("epub", "epub", "pdf", "epub", "m4b", "epub", "cbz", "epub", "epub", "mobi")

    val books = titles.mapIndexed { i, (title, author) ->
        BookCard(
            id = i + 1,
            title = title,
            authors = listOf(author),
            files = listOf(BookFileRef(id = i + 11, format = formats[i], role = "primary")),
            readingProgress = listOf(42.0, 8.0, 76.0, null, 15.0, null, null, 100.0, null, null)[i],
            addedAt = "2026-10-01",
            // One book without art shows the serif title on a blank cover.
            hasCover = i != 6,
        )
    }

    val recommendations = books.drop(1).map { BookRecommendation(it.id, it.title, hasCover = it.hasCover, authors = it.authors) }

    val detail = BookDetail(
        id = 1, libraryId = 1, libraryName = "Classics", status = "present", addedAt = "2026-10-01",
        title = "Pride and Prejudice",
        description = "<p>Since its immediate success in 1813, <i>Pride and Prejudice</i> has remained one of the most popular novels in the English language. Jane Austen called this brilliant work \"her own darling child\".</p>",
        publisher = "T. Egerton", publishedYear = 1813, language = "en", pageCount = 432,
        seriesId = 4, seriesName = "Austen Novels", seriesIndex = 2.0, rating = 4.0,
        authors = listOf(BookAuthorRef(1, "Jane Austen")),
        genres = listOf("Romance", "Classics", "Satire"),
        tags = listOf("Regency", "Courtship"),
        files = listOf(BookFileRef(11, "epub", "primary", 812_000, filename = "Pride and Prejudice.epub"), BookFileRef(12, "pdf", "alternate", 2_400_000, filename = "pride.pdf")),
        readStatus = UserBookStatus("reading", "web", updatedAt = "2026-10-30"),
        collections = listOf(CollectionRef(1, "Favourites")),
    )

    val bookActions = BookDetailActions(
        onBack = {}, onRead = {}, onListen = {}, onDownload = {}, onRemoveDownload = {}, onOpenStatus = {},
        onOpenCollections = {}, onRate = {}, onBookClick = {}, onAuthorClick = { _, _ -> }, onSeriesClick = { _, _ -> },
        onRetry = {}, onOpenUri = {},
    )

    val searchResults = books.take(6).map {
        SearchResult(it.id, it.title, seriesName = if (it.id == 1) "Austen Novels" else null, authors = it.authors, libraryId = 1, libraryName = "Classics", formats = it.files.mapNotNull { f -> f.format })
    }

    val highlights = listOf(
        HubAnnotation(1, 1, text = "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.", color = "yellow", bookTitle = "Pride and Prejudice", author = "Jane Austen"),
        HubAnnotation(2, 2, text = "Call me Ishmael.", color = "blue", note = "The most famous opening line.", bookTitle = "Moby Dick", author = "Herman Melville"),
        HubAnnotation(3, 3, text = "Why, sometimes I've believed as many as six impossible things before breakfast.", color = "pink", bookTitle = "Alice's Adventures in Wonderland", author = "Lewis Carroll"),
        HubAnnotation(4, 8, text = "Pain and suffering are always inevitable for a large intelligence and a deep heart.", color = "green", note = "Raskolnikov, to Dunya.", bookTitle = "Crime and Punishment", author = "Fyodor Dostoyevsky"),
    )

    val series = listOf(
        SeriesSummary(1, "Sherlock Holmes", bookCount = 9, readCount = 4, authors = listOf("Arthur Conan Doyle"), coverBookIds = listOf(9, 2, 5)),
        SeriesSummary(2, "Austen Novels", bookCount = 6, readCount = 6, authors = listOf("Jane Austen"), coverBookIds = listOf(1, 3)),
        SeriesSummary(3, "Gothic Classics", bookCount = 4, readCount = 1, authors = listOf("Various"), coverBookIds = listOf(6, 4, 7)),
        SeriesSummary(4, "Russian Novels", bookCount = 5, readCount = 0, authors = listOf("Fyodor Dostoyevsky")),
    )

    val authors = titles.mapIndexed { i, (_, author) -> AuthorSummary(i + 1, author, bookCount = (i % 3) + 1) }

    val downloads = listOf(
        DownloadEntity(1, "Pride and Prejudice", "Jane Austen", "", false, "epub", 812_000, 0, "x", "", "", DownloadStatus.COMPLETE.name, 1f),
        DownloadEntity(2, "Moby Dick; Or, The Whale", "Herman Melville", "", false, "epub", 1_200_000, 0, "x", "", "", DownloadStatus.DOWNLOADING.name, 0.45f),
        DownloadEntity(5, "A Tale of Two Cities", "Charles Dickens", "", true, "m4b", 0, 0, "x", "", "", DownloadStatus.FAILED.name, 0f, lastError = "The server went away mid-download."),
    )
}
