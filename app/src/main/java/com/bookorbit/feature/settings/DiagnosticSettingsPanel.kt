package com.bookorbit.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bookorbit.R

@Composable
internal fun DiagnosticSettingsPanel(enabled: Boolean, busy: Boolean, failed: Boolean,
    onToggle: (Boolean) -> Unit, onExport: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.diagnostics_switch), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.diagnostics_description), style = MaterialTheme.typography.bodySmall)
            }
            Switch(enabled, onToggle, enabled = !busy)
        }
        if (failed) Text(stringResource(R.string.diagnostics_dropped), color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.diagnostics_retention), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExport, enabled = !busy) { Text(stringResource(R.string.diagnostics_export)) }
            TextButton(onClick = onClear, enabled = !busy) { Text(stringResource(R.string.diagnostics_clear)) }
        }
    }
}
