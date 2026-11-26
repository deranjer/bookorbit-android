package com.bookorbit.feature.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.LocalFocusManager
import com.bookorbit.R
import com.bookorbit.core.auth.SessionOwner

@Composable
fun AiConnectionDialog(store: AiConnectionStore, owner: SessionOwner?, onDeviceVerification: (() -> Unit)? = null, onDismiss: () -> Unit) {
    val focus = LocalFocusManager.current
    var initial by remember(owner) { mutableStateOf(owner?.let { store.connection(it) } ?: AiConnection(valid = false)) }
    var route by remember(owner) { mutableStateOf(initial.route) }
    var provider by remember(owner) { mutableStateOf(initial.provider) }
    var helpModel by remember(owner) { mutableStateOf(initial.helpModel) }
    var guideModel by remember(owner) { mutableStateOf(initial.guideModel) }
    var baseUrl by remember(owner) { mutableStateOf(initial.baseUrl) }
    var insecure by remember(owner) { mutableStateOf(initial.allowInsecure) }
    // Never save credentials in a Bundle, SavedStateHandle or ordinary preferences.
    var replacement by remember(owner, provider) { mutableStateOf("") }
    var failed by remember(owner) { mutableStateOf(false) }
    var choosingProvider by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    val tokenValid = replacement.length <= 8192 && replacement.all { it.code in 33..126 }
    val endpointValid = runCatching { validatedAiBaseUrl(provider, baseUrl, insecure) }.isSuccess
    val sameEndpoint = runCatching { validatedAiBaseUrl(provider, baseUrl, insecure).toString() == initial.baseUrl }.getOrDefault(false)
    val hasToken = initial.hasToken && sameEndpoint
    val valid = owner != null &&
        (route == AiRoute.SERVER || tokenValid && validAiModel(helpModel) && validAiModel(guideModel) && endpointValid && helpModel.isNotBlank() && (provider != AiProvider.GOOGLE || '/' !in helpModel) &&
            (!provider.requiresToken || hasToken || replacement.isNotBlank()))
    Dialog(onDismissRequest = { replacement = ""; onDismiss() },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn, usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
          Scaffold(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
            topBar = { Text(stringResource(R.string.ai_connection_title), Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall) },
            bottomBar = {
                Surface(shadowElevation = 4.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { replacement = ""; onDismiss() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                        Button(enabled = valid, modifier = Modifier.weight(1f), onClick = {
                            failed = runCatching {
                                if (route == AiRoute.SERVER) store.useServer(requireNotNull(owner))
                                else store.save(requireNotNull(owner), route, provider, helpModel, guideModel, replacement, baseUrl, insecure)
                                replacement = ""; onDismiss()
                            }.isFailure
                        }) { Text(stringResource(R.string.ai_save)) }
                    }
                }
            }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.ai_route_heading), style = MaterialTheme.typography.titleMedium)
                AiRouteCard(route == AiRoute.SERVER, { route = AiRoute.SERVER; focus.clearFocus() }, R.string.ai_route_server, R.string.ai_server_summary)
                AiRouteCard(route == AiRoute.DIRECT, { route = AiRoute.DIRECT; focus.clearFocus() }, R.string.ai_route_direct, R.string.ai_direct_summary)
                HorizontalDivider()
                if (route == AiRoute.SERVER) {
                    Text(stringResource(R.string.ai_server_setup), style = MaterialTheme.typography.bodyMedium)
                    if (onDeviceVerification != null) OutlinedButton(onClick = { replacement = ""; onDeviceVerification() }) {
                        Text(stringResource(R.string.attest_title))
                    }
                }
                if (route == AiRoute.DIRECT) {
                    Text(stringResource(R.string.ai_provider), style = MaterialTheme.typography.titleMedium)
                    Box {
                      OutlinedButton(onClick = { choosingProvider = true }, modifier = Modifier.fillMaxWidth()) { Text(provider.displayName) }
                      DropdownMenu(expanded = choosingProvider, onDismissRequest = { choosingProvider = false }) {
                        AiProvider.entries.forEach { candidate -> DropdownMenuItem(text = { Text(candidate.displayName) }, onClick = {
                            choosingProvider = false
                            focus.clearFocus()
                            provider = candidate
                            initial = owner?.let { store.connection(it, candidate) } ?: AiConnection(valid = false)
                            helpModel = initial.helpModel; guideModel = initial.guideModel; failed = false
                            baseUrl = initial.baseUrl; insecure = initial.allowInsecure
                        }) }
                      }
                    }
                    if (provider.customEndpoint) {
                        OutlinedTextField(baseUrl, { baseUrl = it }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(), isError = baseUrl.isNotBlank() && !endpointValid,
                            label = { Text(stringResource(R.string.ai_base_url)) })
                        Row {
                            Checkbox(insecure, { insecure = it })
                            Text(stringResource(R.string.ai_allow_http), Modifier.weight(1f))
                        }
                        if (!sameEndpoint && initial.hasToken) Text(stringResource(R.string.ai_endpoint_token_reset), color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(helpModel, { helpModel = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.ai_help_model)) })
                    Text(stringResource(R.string.ai_token_stored, stringResource(if (hasToken) R.string.ai_yes else R.string.ai_no)))
                    if (!provider.requiresToken) Text(stringResource(R.string.ai_optional_token), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(replacement, { replacement = it }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), label = { Text(stringResource(R.string.ai_token_replacement)) })
                    if (initial.hasToken) TextButton(onClick = {
                        failed = runCatching { store.removeToken(requireNotNull(owner), provider)
                            initial = store.connection(owner, provider); replacement = "" }.isFailure
                    }) { Text(stringResource(R.string.ai_token_remove)) }
                    Text(stringResource(R.string.ai_direct_scope), style = MaterialTheme.typography.bodySmall)
                    if (!endpointValid) Text(stringResource(R.string.ai_enter_endpoint), style = MaterialTheme.typography.bodySmall)
                    if (helpModel.isBlank()) Text(stringResource(R.string.ai_enter_model), style = MaterialTheme.typography.bodySmall)
                    if (provider.requiresToken && !hasToken && replacement.isBlank()) Text(stringResource(R.string.ai_enter_token), style = MaterialTheme.typography.bodySmall)
                    if (!tokenValid) Text(stringResource(R.string.ai_invalid_token), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { details = !details }) { Text(stringResource(R.string.ai_connection_details)) }
                if (details) {
                    Text(stringResource(R.string.ai_connection_notice), style = MaterialTheme.typography.bodySmall)
                    if (route == AiRoute.DIRECT) {
                        if (provider.customEndpoint) Text(stringResource(if (provider == AiProvider.OLLAMA) R.string.ai_ollama_notice else R.string.ai_compatible_notice), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.ai_model_notice), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.ai_guide_direct_unsupported), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!initial.valid && !endpointValid || failed) Text(stringResource(R.string.ai_configuration_required), color = MaterialTheme.colorScheme.error)
            }
          }
        }
    }
}

@Composable
private fun AiRouteCard(selected: Boolean, onClick: () -> Unit, title: Int, description: Int) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
        colors = CardDefaults.outlinedCardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall)
        }
    }
}
