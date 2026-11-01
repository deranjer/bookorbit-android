package com.bookorbit.feature.you

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookorbit.BuildConfig
import com.bookorbit.R
import com.bookorbit.core.model.AuthUser
import com.bookorbit.feature.main.NavItem
import com.bookorbit.feature.main.icon
import com.bookorbit.feature.main.labelRes
import com.bookorbit.ui.components.GroupedRows
import com.bookorbit.ui.components.IconTile
import com.bookorbit.ui.components.PageHeader
import com.bookorbit.ui.components.Panel
import com.bookorbit.ui.components.SectionLabel
import com.bookorbit.ui.theme.BookOrbitTheme

/** The You tab: who's signed in, every screen that isn't in the bottom bar, and sign out. */
@Composable
fun YouScreen(
    user: AuthUser,
    serverVersion: String?,
    updateAvailable: Boolean,
    latestVersion: String?,
    /** The screens that aren't in the bottom bar, so they can still be opened from here. */
    unpinned: List<NavItem>,
    onOpen: (NavItem) -> Unit,
    onSignOut: () -> Unit,
) {
    val tokens = BookOrbitTheme.tokens
    val name = user.name ?: user.username
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
    ) {
        PageHeader(title = stringResource(R.string.title_you))
        Panel(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), accent = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(60.dp).clip(CircleShape).background(tokens.accentTint).border(1.dp, tokens.border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name.firstOrNull()?.uppercase() ?: "", style = MaterialTheme.typography.headlineMedium)
                }
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        user.email ?: "@${user.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.mutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (unpinned.isNotEmpty()) {
            SectionLabel(stringResource(R.string.you_more))
            // Everything that isn't in the bottom bar is reached from here.
            GroupedRows(
                Modifier.padding(horizontal = 16.dp),
                rows = unpinned.map { item ->
                    @Composable {
                        val label = if (item == NavItem.STATS) R.string.you_reading_stats else item.labelRes()
                        val update = item == NavItem.SETTINGS && updateAvailable
                        YouRow(
                            label = stringResource(label),
                            icon = item.icon(),
                            supporting = if (update) {
                                if (latestVersion != null) stringResource(R.string.you_update_available_version, latestVersion) else stringResource(R.string.you_update_available)
                            } else null,
                            dot = update,
                            onClick = { onOpen(item) },
                        )
                    }
                },
            )
        }

        SectionLabel(stringResource(R.string.you_account))
        GroupedRows(
            Modifier.padding(horizontal = 16.dp),
            rows = listOf(
                @Composable {
                    YouRow(stringResource(R.string.you_sign_out), Icons.AutoMirrored.Outlined.Logout, onClick = onSignOut, tint = tokens.destructive, chevron = false)
                },
            ),
        )

        Text(
            // VERSION_CODE is what changes build-to-build; VERSION_NAME is bumped by hand, so show both.
            if (serverVersion != null) {
                stringResource(R.string.you_app_server_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, serverVersion)
            } else {
                stringResource(R.string.you_app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            },
            style = MaterialTheme.typography.labelSmall,
            color = tokens.mutedForeground,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun YouRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    supporting: String? = null,
    dot: Boolean = false,
    tint: Color = BookOrbitTheme.tokens.foreground,
    chevron: Boolean = true,
) {
    val tokens = BookOrbitTheme.tokens
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, tint = tint)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = tokens.info)
        }
        if (dot) Box(Modifier.size(8.dp).clip(CircleShape).background(tokens.info))
        if (chevron) Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = tokens.mutedForeground, modifier = Modifier.size(18.dp))
    }
}
