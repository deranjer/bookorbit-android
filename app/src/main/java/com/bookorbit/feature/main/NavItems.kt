package com.bookorbit.feature.main

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.bookorbit.R
import com.bookorbit.core.model.AuthUser

/** Server permission required to see / use the Book Dock. */
const val BOOK_DOCK_PERMISSION = "book_dock_access"

fun AuthUser.canUseBookDrop(): Boolean = isSuperuser || BOOK_DOCK_PERMISSION in permissions

/** Everything that can sit in the bottom bar (or rail). [id] is what is saved, so never rename one. */
enum class NavItem(val id: String, val route: String) {
    HOME("home", "home"),
    LIBRARY("library", "library"),
    SEARCH("search", "search"),
    NOTES("notes", "notes"),
    STATS("stats", "stats"),
    DOWNLOADS("downloads", "downloads"),
    BOOK_DROP("bookdrop", "bookdrop"),
    SETTINGS("settings", "settings"),
    YOU("you", "you"),
}

@StringRes
fun NavItem.labelRes(): Int = when (this) {
    NavItem.HOME -> R.string.tab_home
    NavItem.LIBRARY -> R.string.tab_library
    NavItem.SEARCH -> R.string.tab_search
    NavItem.NOTES -> R.string.tab_notes
    NavItem.STATS -> R.string.nav_stats
    NavItem.DOWNLOADS -> R.string.you_downloads
    NavItem.BOOK_DROP -> R.string.you_book_drop
    NavItem.SETTINGS -> R.string.settings
    NavItem.YOU -> R.string.tab_you
}

fun NavItem.icon(): ImageVector = when (this) {
    NavItem.HOME -> Icons.Outlined.Home
    NavItem.LIBRARY -> Icons.Outlined.LocalLibrary
    NavItem.SEARCH -> Icons.Outlined.Search
    NavItem.NOTES -> Icons.Outlined.EditNote
    NavItem.STATS -> Icons.Outlined.BarChart
    NavItem.DOWNLOADS -> Icons.Filled.Download
    NavItem.BOOK_DROP -> Icons.Outlined.Inbox
    NavItem.SETTINGS -> Icons.Outlined.Settings
    NavItem.YOU -> Icons.Outlined.Person
}

/**
 * The user's choice of bottom-bar items, in order. You is always kept: it holds sign out and the
 * rest of the account, so it can be moved but not removed.
 */
object NavConfig {
    const val MAX_ITEMS = 5
    val DEFAULT = listOf(NavItem.HOME, NavItem.LIBRARY, NavItem.SEARCH, NavItem.NOTES, NavItem.YOU)

    fun parse(raw: String?): List<NavItem> {
        val items = raw.orEmpty().split(',')
            .mapNotNull { id -> NavItem.entries.firstOrNull { it.id == id.trim() } }
            .distinct()
        if (items.isEmpty()) return DEFAULT
        val capped = items.take(MAX_ITEMS)
        return when {
            NavItem.YOU in capped -> capped
            capped.size < MAX_ITEMS -> capped + NavItem.YOU
            else -> capped.dropLast(1) + NavItem.YOU
        }
    }

    fun serialize(items: List<NavItem>): String = items.joinToString(",") { it.id }

    /** What to draw: Book Drop is dropped for users without access to it. */
    fun visible(items: List<NavItem>, canUseBookDrop: Boolean): List<NavItem> =
        items.filter { canUseBookDrop || it != NavItem.BOOK_DROP }.ifEmpty { DEFAULT }

    fun moved(items: List<NavItem>, index: Int, delta: Int): List<NavItem> {
        val to = index + delta
        if (index !in items.indices || to !in items.indices) return items
        return items.toMutableList().also { it.add(to, it.removeAt(index)) }
    }

    fun removed(items: List<NavItem>, item: NavItem): List<NavItem> =
        if (item == NavItem.YOU) items else items - item

    fun added(items: List<NavItem>, item: NavItem): List<NavItem> =
        if (item in items || items.size >= MAX_ITEMS) items else items + item

    /** Items that could still be added: not already in the bar, and Book Drop only with access. */
    fun available(items: List<NavItem>, canUseBookDrop: Boolean): List<NavItem> =
        NavItem.entries.filter { it !in items && (canUseBookDrop || it != NavItem.BOOK_DROP) }
}
