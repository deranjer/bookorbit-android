package com.bookorbit.feature.you

import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItemDefaults
import com.bookorbit.BuildConfig
import com.bookorbit.core.model.AuthUser

/** The You tab: who's signed in, plus Downloads, Book Drop, Settings and sign out (formerly the drawer). */
@Composable
fun YouScreen(
    user: AuthUser,
    serverVersion: String?,
    updateAvailable: Boolean,
    latestVersion: String?,
    canUseBookDrop: Boolean,
    onStats: () -> Unit,
    onDownloads: () -> Unit,
    onBookDrop: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val name = user.name ?: user.username
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.firstOrNull()?.uppercase() ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                user.email?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider()
        Row2(stringResource(R.string.you_reading_stats), Icons.Outlined.BarChart, onStats)
        Row2(stringResource(R.string.you_downloads), Icons.Filled.Download, onDownloads)
        if (canUseBookDrop) Row2(stringResource(R.string.you_book_drop), Icons.Outlined.Inbox, onBookDrop)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings)) },
            leadingContent = {
                BadgedBox(badge = { if (updateAvailable) Badge() }) { Icon(Icons.Outlined.Settings, contentDescription = null) }
            },
            supportingContent = if (updateAvailable) {
                {
                    Text(
                        if (latestVersion != null) stringResource(R.string.you_update_available_version, latestVersion) else stringResource(R.string.you_update_available),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else null,
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier.clickable(onClick = onSettings),
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row2(stringResource(R.string.you_sign_out), Icons.AutoMirrored.Outlined.Logout, onSignOut)
        Spacer(Modifier.height(16.dp))
        Text(
            // VERSION_CODE is what changes build-to-build; VERSION_NAME is bumped by hand, so show both.
            if (serverVersion != null) {
                stringResource(R.string.you_app_server_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, serverVersion)
            } else {
                stringResource(R.string.you_app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Row2(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
