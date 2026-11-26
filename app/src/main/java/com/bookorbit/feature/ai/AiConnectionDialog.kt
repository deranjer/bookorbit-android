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
import com.bookorbit.R
import com.bookorbit.core.auth.SessionOwner

@Composable
fun AiConnectionDialog(store: AiConnectionStore, owner: SessionOwner?, onDismiss: () -> Unit) {
    var initial by remember(owner) { mutableStateOf(owner?.let { store.connection(it) } ?: AiConnection(valid = false)) }
    var route by remember(owner) { mutableStateOf(initial.route) }
    var provider by remember(owner) { mutableStateOf(initial.provider) }
    var helpModel by remember(owner) { mutableStateOf(initial.helpModel) }
    var guideModel by remember(owner) { mutableStateOf(initial.guideModel) }
    // Never save credentials in a Bundle, SavedStateHandle or ordinary preferences.
    var replacement by remember(owner, provider) { mutableStateOf("") }
    var failed by remember(owner) { mutableStateOf(false) }
    val valid = owner != null && validAiModel(helpModel) && validAiModel(guideModel) &&
        (route == AiRoute.SERVER || helpModel.isNotBlank() && (initial.hasToken || replacement.isNotBlank()))
    AlertDialog(onDismissRequest = { replacement = ""; onDismiss() },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.ai_connection_title)) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.ai_connection_notice))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(route == AiRoute.SERVER, { route = AiRoute.SERVER }, label = { Text(stringResource(R.string.ai_route_server)) })
                    FilterChip(route == AiRoute.DIRECT, { route = AiRoute.DIRECT }, label = { Text(stringResource(R.string.ai_route_direct)) })
                }
                if (route == AiRoute.DIRECT) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiProvider.entries.forEach { candidate -> FilterChip(provider == candidate, onClick = {
                            provider = candidate
                            initial = owner?.let { store.connection(it, candidate) } ?: AiConnection(valid = false)
                            helpModel = initial.helpModel; guideModel = initial.guideModel; failed = false
                        }, label = { Text(candidate.displayName) }) }
                    }
                    Text(stringResource(R.string.ai_token_stored, stringResource(if (initial.hasToken) R.string.ai_yes else R.string.ai_no)))
                    OutlinedTextField(replacement, { replacement = it }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        visualTransformation = PasswordVisualTransformation(), label = { Text(stringResource(R.string.ai_token_replacement)) })
                    if (initial.hasToken) TextButton(onClick = {
                        failed = runCatching { store.removeToken(requireNotNull(owner), provider)
                            initial = store.connection(owner, provider); replacement = "" }.isFailure
                    }) { Text(stringResource(R.string.ai_token_remove)) }
                    OutlinedTextField(helpModel, { helpModel = it }, singleLine = true, label = { Text(stringResource(R.string.ai_help_model)) })
                    OutlinedTextField(guideModel, { guideModel = it }, singleLine = true, label = { Text(stringResource(R.string.ai_guide_model)) })
                    Text(stringResource(R.string.ai_model_notice), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.ai_guide_direct_unsupported), style = MaterialTheme.typography.bodySmall)
                }
                if (!initial.valid || failed) Text(stringResource(R.string.ai_configuration_required), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = {
            failed = runCatching { store.save(requireNotNull(owner), route, provider, helpModel, guideModel, replacement)
                replacement = ""; onDismiss() }.isFailure
        }) { Text(stringResource(R.string.ai_save)) } },
        dismissButton = { TextButton(onClick = { replacement = ""; onDismiss() }) { Text(stringResource(R.string.cancel)) } })
}
