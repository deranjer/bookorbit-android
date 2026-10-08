package com.bookorbit.feature.settings

import com.bookorbit.ui.asString
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import com.bookorbit.feature.main.NavConfig
import com.bookorbit.feature.main.NavItem
import com.bookorbit.feature.main.icon
import com.bookorbit.feature.main.labelRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookorbit.BuildConfig
import com.bookorbit.core.settings.ThemeMode
import com.bookorbit.feature.player.ProgressBarMode
import com.bookorbit.feature.player.SPEED_PRESETS
import kotlin.math.abs
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val navItems by vm.navItems.collectAsStateWithLifecycle()
    val wifiOnly by vm.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()
    val downloadsSummary by vm.downloadsSummary.collectAsStateWithLifecycle()
    val defaultSpeed by vm.defaultSpeed.collectAsStateWithLifecycle()
    val progressBarMode by vm.progressBarMode.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val downloadTreeUri by vm.downloadTreeUri.collectAsStateWithLifecycle()
    val downloadLocationLabel by vm.downloadLocationLabel.collectAsStateWithLifecycle()
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val downloadLocationAccessible by vm.downloadLocationAccessible.collectAsStateWithLifecycle()
    val appInfo by vm.appInfo.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClearDownloads by remember { mutableStateOf(false) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setDownloadLocation(uri)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it.resolve(appContext))
            vm.consumeMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(padding)) {
            item { SectionHeader(stringResource(R.string.appearance)) }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { vm.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                                label = { Text(mode.label()) },
                            )
                        }
                    }
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                item {
                    SettingsRow(
                        title = stringResource(R.string.settings_material_you),
                        subtitle = stringResource(R.string.settings_material_you_desc),
                        trailing = { Switch(checked = dynamicColor, onCheckedChange = vm::setDynamicColor) },
                    )
                }
            }
            item { HorizontalDivider() }

            item { SectionHeader(stringResource(R.string.settings_nav_bar)) }
            item {
                Text(
                    stringResource(R.string.settings_nav_bar_desc, NavConfig.MAX_ITEMS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            itemsIndexed(navItems) { index, navItem ->
                val label = stringResource(navItem.labelRes())
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = { Icon(navItem.icon(), contentDescription = null) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { vm.moveNavItem(index, -1) }, enabled = index > 0) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.settings_nav_move_up, label))
                            }
                            IconButton(onClick = { vm.moveNavItem(index, 1) }, enabled = index < navItems.lastIndex) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.settings_nav_move_down, label))
                            }
                            IconButton(onClick = { vm.removeNavItem(navItem) }, enabled = navItem != NavItem.YOU) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.settings_nav_remove, label))
                            }
                        }
                    },
                )
            }
            val addable = NavConfig.available(navItems, vm.canUseBookDrop)
            if (addable.isNotEmpty()) {
                item {
                    Text(
                        if (navItems.size >= NavConfig.MAX_ITEMS) stringResource(R.string.settings_nav_full) else stringResource(R.string.settings_nav_available),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
                }
                items(addable) { navItem ->
                    val label = stringResource(navItem.labelRes())
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { Icon(navItem.icon(), contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { vm.addNavItem(navItem) }, enabled = navItems.size < NavConfig.MAX_ITEMS) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_nav_add, label))
                            }
                        },
                    )
                }
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedButton(onClick = vm::resetNavItems, enabled = navItems != NavConfig.DEFAULT) {
                        Text(stringResource(R.string.settings_nav_reset))
                    }
                }
            }
            item { HorizontalDivider() }

            item { SectionHeader(stringResource(R.string.downloads)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_wifi_only),
                    subtitle = stringResource(R.string.settings_wifi_only_desc),
                    trailing = { Switch(checked = wifiOnly, onCheckedChange = vm::setWifiOnlyDownloads) },
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_download_location),
                    subtitle = downloadLocationLabel.asString(),
                    trailing = { TextButton(onClick = { folderLauncher.launch(null) }) { Text(stringResource(R.string.change)) } },
                )
            }
            if (downloadTreeUri != null) {
                item {
                    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                        TextButton(onClick = vm::resetDownloadLocation) { Text(stringResource(R.string.use_app_storage_instead)) }
                    }
                }
            }
            if (downloadTreeUri != null && !downloadLocationAccessible) {
                item {
                    Text(
                        stringResource(R.string.settings_folder_inaccessible),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        pluralStringResource(R.plurals.settings_downloads_summary, downloadsSummary.count, downloadsSummary.count, formatBytes(downloadsSummary.totalBytes)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { confirmClearDownloads = true },
                        enabled = downloadsSummary.count > 0,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.clear_all_downloads))
                    }
                }
            }
            item { HorizontalDivider() }

            item { SectionHeader(stringResource(R.string.storage)) }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedButton(onClick = vm::clearImageCache) {
                        Text(stringResource(R.string.clear_image_cache))
                    }
                }
            }
            item { HorizontalDivider() }

            item { SectionHeader(stringResource(R.string.playback)) }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.default_speed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        SPEED_PRESETS.forEach { preset ->
                            FilterChip(
                                selected = abs(preset - defaultSpeed) < 0.001f,
                                onClick = { vm.setDefaultSpeed(preset) },
                                label = { Text(stringResource(R.string.x, preset)) },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.progress_bar),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        listOf(ProgressBarMode.BOOK to stringResource(R.string.settings_progress_book), ProgressBarMode.CHAPTER to stringResource(R.string.settings_progress_chapter)).forEach { (mode, label) ->
                            FilterChip(
                                selected = progressBarMode == mode,
                                onClick = { vm.setProgressBarMode(mode) },
                                label = { Text(label) },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }

            item { HorizontalDivider() }
            item { SectionHeader(stringResource(R.string.about)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_app_version),
                    subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    trailing = {},
                )
            }
            if (appInfo != null) {
                item {
                    val updateAvailable = appInfo?.updateAvailable == true
                    SettingsRow(
                        title = stringResource(if (updateAvailable) R.string.settings_update_available else R.string.settings_up_to_date),
                        subtitle = if (updateAvailable) {
                            appInfo?.latestVersion?.let { stringResource(R.string.settings_server_newer, it) }
                                ?: stringResource(R.string.settings_server_newer_unknown)
                        } else {
                            stringResource(R.string.settings_server_version, appInfo?.version.orEmpty())
                        },
                        trailing = { if (updateAvailable) Badge() },
                    )
                }
            }
        }
    }

    if (confirmClearDownloads) {
        AlertDialog(
            onDismissRequest = { confirmClearDownloads = false },
            title = { Text(stringResource(R.string.clear_all_downloads_2)) },
            text = { Text(stringResource(R.string.this_removes_every_downloaded_file)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAllDownloads()
                    confirmClearDownloads = false
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDownloads = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingsRow(title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun ThemeMode.label() = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val units = listOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / 1024.0.pow(digitGroups)
    return "%.1f %s".format(value, units[digitGroups])
}
