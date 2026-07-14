package com.bookorbit.feature.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bookorbit.BuildConfig
import com.bookorbit.core.model.AuthUser
import com.bookorbit.feature.authors.AuthorsScreen
import com.bookorbit.feature.bookdetail.BookDetailScreen
import com.bookorbit.feature.bookdrop.BookDropScreen
import com.bookorbit.feature.collections.CollectionsScreen
import com.bookorbit.feature.dashboard.DashboardScreen
import com.bookorbit.feature.downloads.DownloadsScreen
import com.bookorbit.feature.library.LibrariesScreen
import com.bookorbit.feature.player.MiniPlayer
import com.bookorbit.feature.scopes.SmartScopesScreen
import com.bookorbit.feature.search.SearchScreen
import com.bookorbit.feature.series.SeriesScreen
import com.bookorbit.feature.settings.SettingsScreen
import kotlinx.coroutines.launch

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    DASHBOARD("dashboard", "Dashboard", Icons.Outlined.GridView),
    LIBRARIES("libraries", "Libraries", Icons.Outlined.LocalLibrary),
    SEARCH("search", "Search", Icons.Outlined.Search),
    SCOPES("scopes", "Scopes", Icons.Outlined.Adjust),
    COLLECTIONS("collections", "Collections", Icons.Outlined.FolderOpen),
}

private object DrawerRoute {
    const val AUTHORS = "authors"
    const val SERIES = "series"
    const val DOWNLOADS = "downloads"
    const val BOOK_DROP = "bookdrop"
    const val SETTINGS = "settings"
    const val BOOK_DETAIL = "book/{id}"
    fun bookDetail(id: Int) = "book/$id"
}

/** Server permission required to see / use the Book Dock. */
private const val BOOK_DOCK_PERMISSION = "book_dock_access"

/**
 * Authenticated shell: a navigation drawer (profile, Authors/Series/Downloads, sign out) plus a
 * bottom navigation bar over a nested NavHost for the five primary tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    user: AuthUser,
    onSignOut: () -> Unit,
    onOpenReader: (Int) -> Unit,
    onOpenPdf: (Int) -> Unit,
    onListen: (Int) -> Unit,
    onOpenPlayer: () -> Unit,
    vm: MainShellViewModel = hiltViewModel(),
) {
    val tabNav = rememberNavController()
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val serverVersion by vm.serverVersion.collectAsStateWithLifecycle()

    val backStackEntry by tabNav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Book detail lives inside the shell's nav host so the bottom bar + mini-player stay visible.
    val isBookDetail = currentRoute == DrawerRoute.BOOK_DETAIL
    val onBookClick: (Int) -> Unit = { id -> tabNav.navigate(DrawerRoute.bookDetail(id)) }

    fun navigateTab(route: String) {
        tabNav.navigate(route) {
            popUpTo(tabNav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun closeDrawerThen(route: String) {
        scope.launch { drawerState.close() }
        navigateTab(route)
    }

    val title = when (currentRoute) {
        Tab.DASHBOARD.route -> "Dashboard"
        Tab.LIBRARIES.route -> "Libraries"
        Tab.SEARCH.route -> "Search"
        Tab.SCOPES.route -> "Smart Scopes"
        Tab.COLLECTIONS.route -> "Collections"
        DrawerRoute.AUTHORS -> "Authors"
        DrawerRoute.SERIES -> "Series"
        DrawerRoute.DOWNLOADS -> "Downloads"
        DrawerRoute.BOOK_DROP -> "Book Drop"
        DrawerRoute.SETTINGS -> "Settings"
        else -> "BookOrbit"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                user = user,
                serverVersion = serverVersion,
                onNavigate = ::closeDrawerThen,
                onSignOut = onSignOut,
            )
        },
    ) {
        Scaffold(
            topBar = {
                // Book detail renders its own top bar (with a back arrow); hide the shell's here so
                // there aren't two stacked app bars.
                if (!isBookDetail) {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                        },
                    )
                }
            },
            bottomBar = {
                Column {
                    MiniPlayer(onOpenPlayer = onOpenPlayer)
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = tabNav,
                startDestination = Tab.DASHBOARD.route,
                modifier = Modifier.padding(padding),
            ) {
                composable(Tab.DASHBOARD.route) { DashboardScreen(onBookClick = onBookClick) }
                composable(Tab.LIBRARIES.route) { LibrariesScreen(onBookClick = onBookClick) }
                composable(Tab.SEARCH.route) { SearchScreen(onBookClick = onBookClick) }
                composable(Tab.SCOPES.route) { SmartScopesScreen(onBookClick = onBookClick) }
                composable(Tab.COLLECTIONS.route) { CollectionsScreen(onBookClick = onBookClick) }
                composable(DrawerRoute.AUTHORS) { AuthorsScreen(onBookClick = onBookClick) }
                composable(DrawerRoute.SERIES) { SeriesScreen(onBookClick = onBookClick) }
                composable(DrawerRoute.DOWNLOADS) { DownloadsScreen(onBookClick = onBookClick) }
                composable(DrawerRoute.BOOK_DROP) { BookDropScreen() }
                composable(DrawerRoute.SETTINGS) { SettingsScreen() }
                composable(
                    route = DrawerRoute.BOOK_DETAIL,
                    arguments = listOf(navArgument("id") { type = NavType.IntType }),
                ) {
                    BookDetailScreen(
                        onBack = { tabNav.popBackStack() },
                        onRead = onOpenReader,
                        onReadPdf = onOpenPdf,
                        onListen = onListen,
                        onBookClick = onBookClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    user: AuthUser,
    serverVersion: String?,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                user.name ?: user.username,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
            user.email?.let {
                Text(
                    it,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
        NavigationDrawerItem(
            label = { Text("Authors") },
            selected = false,
            icon = { Icon(Icons.Outlined.People, contentDescription = null) },
            onClick = { onNavigate(DrawerRoute.AUTHORS) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Series") },
            selected = false,
            icon = { Icon(Icons.AutoMirrored.Outlined.LibraryBooks, contentDescription = null) },
            onClick = { onNavigate(DrawerRoute.SERIES) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Downloads") },
            selected = false,
            icon = { Icon(Icons.Filled.Download, contentDescription = null) },
            onClick = { onNavigate(DrawerRoute.DOWNLOADS) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        if (user.isSuperuser || BOOK_DOCK_PERMISSION in user.permissions) {
            NavigationDrawerItem(
                label = { Text("Book Drop") },
                selected = false,
                icon = { Icon(Icons.Outlined.Inbox, contentDescription = null) },
                onClick = { onNavigate(DrawerRoute.BOOK_DROP) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = false,
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            onClick = { onNavigate(DrawerRoute.SETTINGS) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            label = { Text("Sign out") },
            selected = false,
            onClick = onSignOut,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        val versionLabel = buildString {
            // VERSION_CODE is what actually changes build-to-build (CI run number); VERSION_NAME
            // is bumped by hand, so show both -- otherwise this always reads "0.1.0".
            append("App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            serverVersion?.let { append(" · Server $it") }
        }
        Text(
            versionLabel,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}
