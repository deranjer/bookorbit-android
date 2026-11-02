package com.bookorbit.feature.main

import android.net.Uri
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.bookorbit.R
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
import com.bookorbit.ui.components.BrandLogo
import com.bookorbit.ui.components.EmptyState
import com.bookorbit.ui.components.PageHeader
import com.bookorbit.ui.components.readableWidth
import com.bookorbit.ui.theme.BookOrbitTheme
import com.bookorbit.ui.theme.Motion

/** Screens reached from the You tab, plus book detail. They keep the navigation and mini-player. */
internal object SubRoute {
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
}

/** The screens whose lists open a book: on an expanded window these show the book beside the list. */
internal val LIST_ROUTES = setOf(
    NavItem.LIBRARY.route,
    NavItem.SEARCH.route,
    NavItem.NOTES.route,
    SubRoute.AUTHOR,
    SubRoute.SERIES,
    SubRoute.DOWNLOADS,
)

/** Screens that draw their own title (a [PageHeader] or a hero), so the shell adds none. */
private val SELF_TITLED_ROUTES = setOf(
    NavItem.HOME.route,
    NavItem.LIBRARY.route,
    NavItem.SEARCH.route,
    NavItem.NOTES.route,
    NavItem.YOU.route,
    SubRoute.BOOK_DETAIL,
)

/** Start destination of the book pane: nothing selected yet. */
private const val PANE_EMPTY = "pane-empty"

/**
 * The navigation for a window: a short bar on phones (and on any window too short for a rail), a
 * collapsed rail from medium widths, and the expanded rail, which reads like the web client's
 * sidebar, from expanded widths.
 */
internal fun shellNavigationType(info: WindowAdaptiveInfo): NavigationSuiteType {
    val size = info.windowSizeClass
    return when {
        !size.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) -> NavigationSuiteType.ShortNavigationBarCompact
        info.windowPosture.isTabletop || !size.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND) ->
            NavigationSuiteType.ShortNavigationBarMedium
        size.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) -> NavigationSuiteType.WideNavigationRailExpanded
        else -> NavigationSuiteType.WideNavigationRailCollapsed
    }
}

/**
 * Authenticated shell: the navigation suite (bar, rail or sidebar by window size) around a nested
 * NavHost, with the mini-player under the content. There is no drawer; everything it held lives under
 * You, and Series/Authors/Collections/Scopes live inside Library. On expanded windows, list screens
 * show the open book beside them in a [ListDetailPaneScaffold], with a handle to resize the panes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
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
    val configured by vm.navItems.collectAsStateWithLifecycle()
    // Wait for the saved bar so the app opens on the user's first item rather than flashing Home.
    val navItems = configured ?: run { Box(Modifier.fillMaxSize()); return }
    val canUseBookDrop = user.canUseBookDrop()
    val bar = NavConfig.visible(navItems, canUseBookDrop)
    val barRoutes = bar.map { it.route }.toSet()
    val unpinned = NavConfig.available(bar, canUseBookDrop).filter { it != NavItem.YOU }
    val startRoute = remember { bar.first().route }
    val tabNav = rememberNavController()
    // The book pane beside a list. It has its own back stack, so each book keeps its own saved state
    // and BookDetailViewModel finds its id exactly as it does in the full-screen route.
    val detailNav = rememberNavController()
    val appInfo by vm.appInfo.collectAsStateWithLifecycle()
    val tokens = BookOrbitTheme.tokens

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val navigationType = shellNavigationType(adaptiveInfo)
    val directive = remember(adaptiveInfo) {
        calculatePaneScaffoldDirective(adaptiveInfo).copy(horizontalPartitionSpacerSize = 0.dp, defaultPanePreferredWidth = 420.dp)
    }

    val backStackEntry by tabNav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // The tab the current screen belongs to: the nearest tab root under it in the back stack, so a
    // book opened from Home still counts as Home.
    val backStack by tabNav.currentBackStack.collectAsStateWithLifecycle()
    val currentTab = backStack.lastOrNull { it.destination.route in barRoutes }?.destination?.route
    val twoPane = directive.maxHorizontalPartitions > 1 && currentRoute in LIST_ROUTES
    val onBookClick: (Int) -> Unit = { id -> tabNav.navigate(SubRoute.bookDetail(id)) }
    // In two-pane mode a tap on a list row opens the book in the detail pane instead of a new screen.
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
        if (route == currentTab) {
            // Tapping the tab you're in goes back to its first screen. Navigating to it again would
            // restore its saved stack, book and all, so nothing would change.
            tabNav.popBackStack(route, inclusive = false)
            detailNav.popBackStack(PANE_EMPTY, inclusive = false)
            return
        }
        tabNav.navigate(route) {
            popUpTo(tabNav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // The tab to highlight: the You sub-screens count as You, and a book, author or series page
    // counts as the tab it was opened from. A screen that isn't in the bar is opened from You: it
    // counts as You and gets a back button.
    val openedFromYou = currentRoute != null && currentRoute !in barRoutes && unpinned.any { it.route == currentRoute }
    val selectedRoute = when {
        currentRoute in barRoutes -> currentRoute
        openedFromYou -> NavItem.YOU.route
        else -> currentTab
    }

    val title = when (currentRoute) {
        SubRoute.STATS -> stringResource(R.string.title_reading_stats)
        SubRoute.DOWNLOADS -> stringResource(R.string.title_downloads)
        SubRoute.BOOK_DROP -> stringResource(R.string.title_book_drop)
        SubRoute.SETTINGS -> stringResource(R.string.title_settings)
        else -> ""
    }

    // Screens opened from somewhere get a back bar; author and series pages draw their own title
    // under it. Pinned screens that don't title themselves get a page header instead.
    val appBar: @Composable () -> Unit = {
        val back = openedFromYou || currentRoute == SubRoute.AUTHOR || currentRoute == SubRoute.SERIES
        when {
            back -> TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { tabNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = tokens.background),
            )
            currentRoute != null && currentRoute !in SELF_TITLED_ROUTES -> PageHeader(title)
        }
    }

    val rail = navigationType == NavigationSuiteType.WideNavigationRailCollapsed || navigationType == NavigationSuiteType.WideNavigationRailExpanded
    NavigationSuiteScaffold(
        navigationSuiteType = navigationType,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            shortNavigationBarContainerColor = tokens.background,
            shortNavigationBarContentColor = tokens.mutedForeground,
            wideNavigationRailColors = WideNavigationRailDefaults.colors(containerColor = tokens.sidebar, contentColor = tokens.foreground),
        ),
        primaryActionContent = { if (rail) RailBrand(expanded = navigationType == NavigationSuiteType.WideNavigationRailExpanded) },
        navigationItems = {
            bar.forEach { tab ->
                NavigationSuiteItem(
                    selected = selectedRoute == tab.route,
                    onClick = { navigateTab(tab.route) },
                    icon = { Icon(tab.icon(), contentDescription = null) },
                    label = { Text(stringResource(tab.labelRes()), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationSuiteType = navigationType,
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
        ) {
            val listPane: @Composable () -> Unit = {
                Column(Modifier.fillMaxSize()) {
                    appBar()
                    NavHost(
                        navController = tabNav,
                        startDestination = startRoute,
                        enterTransition = { Motion.ScreenEnter },
                        exitTransition = { Motion.ScreenExit },
                        popEnterTransition = { Motion.ScreenEnter },
                        popExitTransition = { Motion.ScreenExit },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        composable(NavItem.HOME.route) {
                            // Home keeps a readable width itself, inside a scroll that spans the window.
                            DashboardScreen(
                                userName = user.name ?: user.username,
                                onOpenProfile = { navigateTab(NavItem.YOU.route) },
                                onBookClick = onBookClick,
                            )
                        }
                        composable(NavItem.LIBRARY.route) {
                            LibraryHubScreen(onBookClick = onListBookClick, onAuthorClick = onAuthorClick, onSeriesClick = onSeriesClick)
                        }
                        composable(
                            route = SubRoute.AUTHOR,
                            arguments = listOf(
                                navArgument("id") { type = NavType.IntType },
                                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                            ),
                        ) { entry ->
                            AuthorBooksScreen(
                                authorId = entry.arguments?.getInt("id") ?: 0,
                                name = entry.arguments?.getString("name").orEmpty(),
                                onBookClick = onListBookClick,
                            )
                        }
                        composable(
                            route = SubRoute.SERIES,
                            arguments = listOf(
                                navArgument("id") { type = NavType.IntType },
                                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                            ),
                        ) { entry ->
                            SeriesBooksScreen(
                                seriesId = entry.arguments?.getInt("id") ?: 0,
                                name = entry.arguments?.getString("name").orEmpty(),
                                onBookClick = onListBookClick,
                            )
                        }
                        composable(NavItem.SEARCH.route) { Readable { SearchScreen(onBookClick = onListBookClick) } }
                        composable(NavItem.NOTES.route) { Readable { NotesScreen(onBookClick = onListBookClick) } }
                        composable(NavItem.YOU.route) {
                            Readable {
                                YouScreen(
                                    user = user,
                                    serverVersion = appInfo?.version,
                                    updateAvailable = appInfo?.updateAvailable == true,
                                    latestVersion = appInfo?.latestVersion,
                                    unpinned = unpinned,
                                    onOpen = { item -> tabNav.navigate(item.route) },
                                    onSignOut = onSignOut,
                                )
                            }
                        }
                        composable(SubRoute.STATS) { Readable { StatsScreen() } }
                        composable(SubRoute.DOWNLOADS) { Readable { DownloadsScreen(onBookClick = onListBookClick) } }
                        composable(SubRoute.BOOK_DROP) { BookDropScreen() }
                        composable(SubRoute.SETTINGS) { Readable { SettingsScreen() } }
                        composable(
                            route = SubRoute.BOOK_DETAIL,
                            arguments = listOf(navArgument("id") { type = NavType.IntType }),
                        ) {
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
            }
            val detailPane: @Composable () -> Unit = {
                NavHost(
                    navController = detailNav,
                    startDestination = PANE_EMPTY,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { Motion.ScreenEnter },
                    exitTransition = { Motion.ScreenExit },
                    popEnterTransition = { Motion.ScreenEnter },
                    popExitTransition = { Motion.ScreenExit },
                ) {
                    composable(PANE_EMPTY) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            EmptyState(Icons.Outlined.AutoStories, stringResource(R.string.two_pane_select_book))
                        }
                    }
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

            ListDetailPaneScaffold(
                directive = directive,
                value = ThreePaneScaffoldValue(
                    primary = if (twoPane) PaneAdaptedValue.Expanded else PaneAdaptedValue.Hidden,
                    secondary = PaneAdaptedValue.Expanded,
                    tertiary = PaneAdaptedValue.Hidden,
                ),
                listPane = { AnimatedPane { listPane() } },
                detailPane = { AnimatedPane { detailPane() } },
                paneExpansionDragHandle = { state ->
                    val interactionSource = remember { MutableInteractionSource() }
                    VerticalDragHandle(
                        modifier = Modifier.paneExpansionDraggable(state, LocalMinimumInteractiveComponentSize.current, interactionSource),
                        interactionSource = interactionSource,
                    )
                },
                modifier = Modifier.weight(1f),
            )
            MiniPlayer(onOpenPlayer = onOpenPlayer)
        }
    }
}

/** Single-column screens keep a readable width, centred, on wide windows. */
@Composable
private fun Readable(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.readableWidth().fillMaxSize()) { content() }
    }
}

/** The brand at the head of the rail: the logo, and the name beside it when the rail is expanded. */
@Composable
private fun RailBrand(expanded: Boolean) {
    Row(Modifier.padding(start = if (expanded) 20.dp else 0.dp, top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        BrandLogo(size = 34)
        if (expanded) {
            Text(stringResource(R.string.bookorbit), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
        }
    }
}
