package com.bookorbit.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
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
import com.bookorbit.core.settings.ThemeMode
import com.bookorbit.feature.player.SPEED_PRESETS
import kotlin.math.abs
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val wifiOnly by vm.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val downloadsSummary by vm.downloadsSummary.collectAsStateWithLifecycle()
    val defaultSpeed by vm.defaultSpeed.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val downloadTreeUri by vm.downloadTreeUri.collectAsStateWithLifecycle()
    val downloadLocationLabel by vm.downloadLocationLabel.collectAsStateWithLifecycle()
    val downloadLocationAccessible by vm.downloadLocationAccessible.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClearDownloads by remember { mutableStateOf(false) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setDownloadLocation(uri)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(padding)) {
            item { SectionHeader("Appearance") }
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
            item { HorizontalDivider() }

            item { SectionHeader("Downloads") }
            item {
                SettingsRow(
                    title = "Wi-Fi only",
                    subtitle = "Only download books over an unmetered connection",
                    trailing = { Switch(checked = wifiOnly, onCheckedChange = vm::setWifiOnlyDownloads) },
                )
            }
            item {
                SettingsRow(
                    title = "Download location",
                    subtitle = downloadLocationLabel,
                    trailing = { TextButton(onClick = { folderLauncher.launch(null) }) { Text("Change") } },
                )
            }
            if (downloadTreeUri != null) {
                item {
                    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                        TextButton(onClick = vm::resetDownloadLocation) { Text("Use app storage instead") }
                    }
                }
            }
            if (downloadTreeUri != null && !downloadLocationAccessible) {
                item {
                    Text(
                        "This folder isn't accessible anymore (permission revoked or the storage was " +
                            "removed). New downloads will use app storage until you pick a new folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "${downloadsSummary.count} book(s), ${formatBytes(downloadsSummary.totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { confirmClearDownloads = true },
                        enabled = downloadsSummary.count > 0,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Clear all downloads")
                    }
                }
            }
            item { HorizontalDivider() }

            item { SectionHeader("Storage") }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedButton(onClick = vm::clearImageCache) {
                        Text("Clear image cache")
                    }
                }
            }
            item { HorizontalDivider() }

            item { SectionHeader("Playback") }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Default speed",
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
                                label = { Text("${preset}x") },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClearDownloads) {
        AlertDialog(
            onDismissRequest = { confirmClearDownloads = false },
            title = { Text("Clear all downloads?") },
            text = { Text("This removes every downloaded file from this device. You can download books again later.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAllDownloads()
                    confirmClearDownloads = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDownloads = false }) { Text("Cancel") }
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

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val units = listOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / 1024.0.pow(digitGroups)
    return "%.1f %s".format(value, units[digitGroups])
}
