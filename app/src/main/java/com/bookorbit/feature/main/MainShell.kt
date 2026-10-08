package com.bookorbit.feature.main

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bookorbit.core.model.AuthUser
import com.bookorbit.feature.authors.AuthorBooksScreen
import com.bookorbit.feature.bookdetail.BookDetailScreen
import com.bookorbit.feature.bookdrop.BookDropScreen
import com.bookorbit.feature.dashboard.DashboardScreen
import com.bookorbit.feature.downloads.DownloadsScreen
import com.bookorbit.feature.notes.NotesScreen
import com.bookorbit.feature.player.MiniPlayer
import com.bookorbit.feature.search.SearchScreen
import com.bookorbit.feature.series.SeriesBooksScreen
import com.bookorbit.feature.settings.SettingsScreen
import com.bookorbit.feature.stats.StatsScreen
import com.bookorbit.feature.you.YouScreen
import com.bookorbit.ui.components.CenteredContent

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    HOME("home", R.string.tab_home, Icons.Outlined.Home),
    LIBRARY("library", R.string.tab_library, Icons.Outlined.LocalLibrary),
    SEARCH("search", R.string.tab_search, Icons.Outlined.Search),
    NOTES("notes", R.string.tab_notes, Icons.Outlined.EditNote),
    YOU("you", R.string.tab_you, Icons.Outlined.Person),
}

/** Screens reached from the You tab, plus book detail. They keep the bottom bar and mini-player. */
private object SubRoute {
    const val STATS = "stats"
    const val DOWNLOADS = "downloads"
    const val BOOK_DROP = "bookdrop"
    const val SETTINGS = "settings"
    const val BOOK_DETAIL = "book/{id}"
    const val AUTHOR = "author/{id}?name={name}"
    const val SERIES = "series/{id}?name={name}"
    fun bookDetail(id: Int) = "book/$id"
    fun author(id: Int, name: String) = "author/$id?name=${Uri.encode(name)}"
    fun series(id: Int, name: String) = "series/$id?name=${Uri.encode(name)}"

    val fromYou = setOf(STATS, DOWNLOADS, BOOK_DROP, SETTINGS)
}

/** At and above this width the shell uses a navigation rail and caps single-column content. */
const val WIDE_BREAKPOINT_DP = 600

/** Start destination of the book pane: nothing selected yet. */
private const val PANE_EMPTY = "pane-empty"

/** Server permission required to see / use the Book Dock. */
private const val BOOK_DOCK_PERMISSION = "book_dock_access"

/**
 * Authenticated shell: a bottom navigation bar (Home, Library, Search, Notes, You) over a nested
 * NavHost, with the mini-player above the bar. There is no drawer; everything it held lives under
 * You, and Series/Authors/Collections/Scopes live inside Library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    user: AuthUser,
    onSignOut: () -> Unit,
    onOpenReader: (Int) -> Unit,
    onOpenPdf: (Int) -> Unit,
    onOpenComic: (Int) -> Unit,
    onListen: (Int) -> Unit,
    onOpenPlayer: () -> Unit,
    vm: MainShellViewModel = hiltViewModel(),
) {
    val tabNav = rememberNavController()
    // Tablets, unfolded foldables and landscape phones: a navigation rail beats a bottom bar.
    val widthDp = LocalConfiguration.current.screenWidthDp
    val wide = widthDp >= WIDE_BREAKPOINT_DP
    // The book pane beside a list (see TwoPane). It has its own back stack, so each book keeps its own
    // saved state and BookDetailViewModel finds its id exactly as it does in the full-screen route.
    val detailNav = rememberNavController()
    val appInfo by vm.appInfo.collectAsStateWithLifecycle()

    val backStackEntry by tabNav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isBookDetail = currentRoute == SubRoute.BOOK_DETAIL
    val twoPane = TwoPane.isActive(widthDp, currentRoute)
    val onBookClick: (Int) -> Unit = { id -> tabNav.navigate(SubRoute.bookDetail(id)) }
    // In two-pane mode a tap on a list row opens the book in the right-hand pane instead of a new screen.
    val onListBookClick: (Int) -> Unit = { id ->
        if (twoPane) {
            detailNav.navigate(SubRoute.bookDetail(id)) {
                // Re-tapping or switching books replaces the one shown, so Back in the pane just closes it.
                popUpTo(PANE_EMPTY) { inclusive = false }
                launchSingleTop = true
            }
        } else {
            onBookClick(id)
        }
    }
    val onAuthorClick: (Int, String) -> Unit = { id, name -> tabNav.navigate(SubRoute.author(id, name)) }
    val onSeriesClick: (Int, String) -> Unit = { id, name -> tabNav.navigate(SubRoute.series(id, name)) }

    fun navigateTab(route: String) {
        tabNav.navigate(route) {
            popUpTo(tabNav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // The tab to highlight: the You sub-screens count as You; book detail highlights nothing.
    val selectedRoute = when (currentRoute) {
        in SubRoute.fromYou -> Tab.YOU.route
        else -> currentRoute
    }

    val title = when (currentRoute) {
        Tab.LIBRARY.route -> stringResource(R.string.title_library)
        Tab.SEARCH.route -> stringResource(R.string.title_search)
        Tab.NOTES.route -> stringResource(R.string.title_notes)
        Tab.YOU.route -> stringResource(R.string.title_you)
        SubRoute.STATS -> stringResource(R.string.title_reading_stats)
        SubRoute.DOWNLOADS -> stringResource(R.string.title_downloads)
        SubRoute.BOOK_DROP -> stringResource(R.string.title_book_drop)
        SubRoute.SETTINGS -> stringResource(R.string.title_settings)
        SubRoute.AUTHOR, SubRoute.SERIES -> backStackEntry?.arguments?.getString("name").orEmpty()
        else -> stringResource(R.string.app_name)
    }

    val canUseBookDrop = user.isSuperuser || BOOK_DOCK_PERMISSION in user.permissions

    Scaffold(
        topBar = {
            // Home draws its own greeting header and book detail its own bar; the rest share this one.
            if (!isBookDetail && currentRoute != Tab.HOME.route) {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        if (currentRoute in SubRoute.fromYou || currentRoute == SubRoute.AUTHOR || currentRoute == SubRoute.SERIES) {
                            IconButton(onClick = { tabNav.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            Column {
                MiniPlayer(onOpenPlayer = onOpenPlayer)
                // On wide windows the tabs move to a rail on the left instead.
                if (!wide) {
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedRoute == tab.route,
                                onClick = { navigateTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = stringResource(tab.label)) },
                                label = { Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
      val detailPane: @Composable () -> Unit = {
          NavHost(navController = detailNav, startDestination = PANE_EMPTY, modifier = Modifier.fillMaxSize()) {
              composable(PANE_EMPTY) { EmptyDetailPane() }
              composable(
                  route = SubRoute.BOOK_DETAIL,
                  arguments = listOf(navArgument("id") { type = NavType.IntType }),
              ) {
                  BookDetailScreen(
                      onBack = { detailNav.popBackStack() },
                      onRead = onOpenReader,
                      onReadPdf = onOpenPdf,
                      onReadComic = onOpenComic,
                      onListen = onListen,
                      // "More by this author" / "Similar books" stay in the pane.
                      onBookClick = onListBookClick,
                      onAuthorClick = onAuthorClick,
                      onSeriesClick = onSeriesClick,
                  )
              }
          }
      }
      Row(Modifier.padding(padding).consumeWindowInsets(padding)) {
        if (wide) {
            NavigationRail {
                Spacer(Modifier.weight(1f))
                Tab.entries.forEach { tab ->
                    NavigationRailItem(
                        selected = selectedRoute == tab.route,
                        onClick = { navigateTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = stringResource(tab.label)) },
                        label = { Text(stringResource(tab.label), maxLines = 1) },
                        alwaysShowLabel = true,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }
        NavHost(
            navController = tabNav,
            startDestination = Tab.HOME.route,
            // Consume the insets Scaffold already applied, so a nested screen's own TopAppBar
            // (book detail) doesn't add the status-bar inset a second time.
            // Two-pane: a fixed-width list column, with the book pane hosted once below (not per destination).
            modifier = if (twoPane) Modifier.width(TWO_PANE_LIST_WIDTH).fillMaxHeight() else Modifier.weight(1f).fillMaxHeight(),
        ) {
            composable(Tab.HOME.route) {
                CenteredContent {
                    DashboardScreen(
                        userName = user.name ?: user.username,
                        onOpenProfile = { navigateTab(Tab.YOU.route) },
                        onBookClick = onBookClick,
                    )
                }
            }
            composable(Tab.LIBRARY.route) {
                LibraryHubScreen(onBookClick = onListBookClick, onAuthorClick = onAuthorClick, onSeriesClick = onSeriesClick)
            }
            composable(
                route = SubRoute.AUTHOR,
                arguments = listOf(
                    navArgument("id") { type = NavType.IntType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                AuthorBooksScreen(authorId = entry.arguments?.getInt("id") ?: 0, onBookClick = onBookClick)
            }
            composable(
                route = SubRoute.SERIES,
                arguments = listOf(
                    navArgument("id") { type = NavType.IntType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                SeriesBooksScreen(seriesId = entry.arguments?.getInt("id") ?: 0, onBookClick = onBookClick)
            }
            composable(Tab.SEARCH.route) { SearchScreen(onBookClick = onListBookClick) }
            composable(Tab.NOTES.route) {
                // Two-pane: the list column is already narrow, so don't centre it as well.
                if (twoPane) NotesScreen(onBookClick = onListBookClick) else CenteredContent { NotesScreen(onBookClick = onBookClick) }
            }
            composable(Tab.YOU.route) {
                CenteredContent {
                YouScreen(
                    user = user,
                    serverVersion = appInfo?.version,
                    updateAvailable = appInfo?.updateAvailable == true,
                    latestVersion = appInfo?.latestVersion,
                    canUseBookDrop = canUseBookDrop,
                    onStats = { tabNav.navigate(SubRoute.STATS) },
                    onDownloads = { tabNav.navigate(SubRoute.DOWNLOADS) },
                    onBookDrop = { tabNav.navigate(SubRoute.BOOK_DROP) },
                    onSettings = { tabNav.navigate(SubRoute.SETTINGS) },
                    onSignOut = onSignOut,
                )
                }
            }
            composable(SubRoute.STATS) { CenteredContent { StatsScreen() } }
            composable(SubRoute.DOWNLOADS) { DownloadsScreen(onBookClick = onBookClick) }
            composable(SubRoute.BOOK_DROP) { BookDropScreen() }
            composable(SubRoute.SETTINGS) { CenteredContent { SettingsScreen() } }
            composable(
                route = SubRoute.BOOK_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
            ) {
                CenteredContent {
                BookDetailScreen(
                    onBack = { tabNav.popBackStack() },
                    onRead = onOpenReader,
                    onReadPdf = onOpenPdf,
                    onReadComic = onOpenComic,
                    onListen = onListen,
                    onBookClick = onBookClick,
                    onAuthorClick = onAuthorClick,
                    onSeriesClick = onSeriesClick,
                )
                }
            }
        }
        if (twoPane) {
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) { detailPane() }
        }
      }
    }
}
