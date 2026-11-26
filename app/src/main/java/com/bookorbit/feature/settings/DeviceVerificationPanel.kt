package com.bookorbit.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bookorbit.R
import com.bookorbit.ui.UiText
import com.bookorbit.ui.asString

@Composable
internal fun DeviceVerificationPanel(enrolled: Boolean, busy: Boolean, error: UiText?, onRegister: () -> Unit, onDisable: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(if (enrolled) R.string.attest_status_registered else R.string.attest_status_off), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.attest_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = onRegister, enabled = !busy) { Text(stringResource(if (busy) R.string.attest_busy else R.string.attest_register)) }
        if (enrolled) TextButton(onClick = onDisable, enabled = !busy) { Text(stringResource(R.string.attest_disable)) }
    }
}
