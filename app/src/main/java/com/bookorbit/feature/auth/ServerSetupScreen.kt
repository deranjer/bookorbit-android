package com.bookorbit.feature.auth

import com.bookorbit.ui.asString
import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookorbit.ui.theme.BookOrbitTheme
import com.bookorbit.ui.components.BrandLogo
import com.bookorbit.ui.components.PrimaryButton
import com.bookorbit.ui.components.readableWidth
import com.bookorbit.core.network.LocalNetworkAccess
import com.bookorbit.core.network.UserCaSettings

/** The first screen: where the server is. Connecting checks a BookOrbit server answers there. */
@Composable
fun ServerSetupScreen(
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ServerSetupViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val tokens = BookOrbitTheme.tokens
    var url by rememberSaveable { mutableStateOf(vm.currentServerUrl.orEmpty()) }
    val isInsecure = url.trim().startsWith("http://", ignoreCase = true)
    val certificateHost = UserCaSettings.httpsHost(url)
    var trustUserCas by remember(certificateHost) { mutableStateOf(vm.trustsUserCas(url)) }

    fun submit() = vm.connect(url, trustUserCas, onConnected)

    val localNetworkPrompt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.onLocalNetworkResult(it)
    }
    LaunchedEffect(ui.askLocalNetwork) {
        if (ui.askLocalNetwork) {
            vm.onLocalNetworkPromptShown()
            localNetworkPrompt.launch(LocalNetworkAccess.PERMISSION)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .readableWidth(420.dp)
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            BrandLogo(modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.server_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.enter_your_server_url),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.mutedForeground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                placeholder = { Text(stringResource(R.string.https_your_bookorbit_server_com)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
            )

            if (isInsecure) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = tokens.warning, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.server_http_warning), color = tokens.warning, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (certificateHost != null) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_trust_user_cas), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = trustUserCas, onCheckedChange = { trustUserCas = it }, enabled = !ui.loading)
                }
                Text(
                    stringResource(R.string.settings_trust_user_cas_desc, certificateHost),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.mutedForeground,
                )
            }

            ui.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = stringResource(if (ui.loading) R.string.server_connecting else R.string.connect),
                onClick = { submit() },
                enabled = !ui.loading && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
