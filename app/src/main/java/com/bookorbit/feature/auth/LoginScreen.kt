package com.bookorbit.feature.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookorbit.R
import com.bookorbit.core.model.OidcProviderPublic
import com.bookorbit.ui.asString
import com.bookorbit.ui.components.BrandLogo
import com.bookorbit.ui.components.ErrorState
import com.bookorbit.ui.components.GhostButton
import com.bookorbit.ui.components.PrimaryButton
import com.bookorbit.ui.components.SecondaryButton
import com.bookorbit.ui.components.readableWidth
import com.bookorbit.ui.theme.BookOrbitTheme

@Composable
fun LoginScreen(
    onChangeServer: () -> Unit,
    modifier: Modifier = Modifier,
    vm: LoginViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val oidcLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> vm.completeOidc(result.data) }

    LoginContent(
        ui = ui,
        serverUrl = vm.serverUrl,
        onLogin = vm::login,
        onOidc = { provider -> vm.beginOidc(provider) { intent -> oidcLauncher.launch(intent) } },
        onRetry = vm::loadOptions,
        onChangeServer = onChangeServer,
        modifier = modifier,
    )
}

/**
 * Sign-in as the server allows it, as the web's LoginPage does: a button per OIDC provider first,
 * then the username and password form only if the server has password login on (it's off under
 * DISABLE_LOCAL_AUTH). Nothing but a spinner shows until the server has answered.
 */
@Composable
fun LoginContent(
    ui: LoginViewModel.UiState,
    serverUrl: String?,
    onLogin: (username: String, password: String) -> Unit,
    onOidc: (OidcProviderPublic) -> Unit,
    onRetry: () -> Unit,
    onChangeServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = BookOrbitTheme.tokens
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandLogo()
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.sign_in_to_your_library), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            serverUrl?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it.substringAfter("://"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.mutedForeground,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(28.dp))

            val options = ui.options
            when {
                options == null && ui.optionsFailed ->
                    ErrorState(stringResource(R.string.login_err_options), onRetry = onRetry)
                options == null ->
                    CircularProgressIndicator(Modifier.padding(24.dp).size(28.dp), strokeWidth = 2.5.dp)
                else -> {
                    val providers = options.oidcProviders.filter { it.enabled }
                    providers.forEach { provider ->
                        val loading = ui.oidcLoadingSlug == provider.slug
                        PrimaryButton(
                            text = if (loading) stringResource(R.string.login_opening, provider.displayName)
                            else stringResource(R.string.login_continue_with, provider.displayName),
                            onClick = { onOidc(provider) },
                            icon = Icons.AutoMirrored.Outlined.Login,
                            enabled = ui.oidcLoadingSlug == null && !ui.submitting,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        )
                    }
                    if (options.passwordLoginEnabled) {
                        if (providers.isNotEmpty()) OrDivider()
                        PasswordForm(
                            submitting = ui.submitting,
                            primary = providers.isEmpty(),
                            enabled = ui.oidcLoadingSlug == null,
                            onLogin = onLogin,
                        )
                    } else if (providers.isEmpty()) {
                        Text(
                            stringResource(R.string.login_no_methods),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.mutedForeground,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
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

            Spacer(Modifier.height(24.dp))
            GhostButton(stringResource(R.string.change_server), onClick = onChangeServer)
        }
    }
}

@Composable
private fun OrDivider() {
    val tokens = BookOrbitTheme.tokens
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = tokens.border)
        Text(
            stringResource(R.string.login_or_password),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.mutedForeground,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = tokens.border)
    }
}

/** Username and password; [primary] when it's the only way in, a secondary button under OIDC otherwise. */
@Composable
private fun PasswordForm(
    submitting: Boolean,
    primary: Boolean,
    enabled: Boolean,
    onLogin: (String, String) -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    fun submit() = onLogin(username, password)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            label = { Text(stringResource(R.string.username)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            label = { Text(stringResource(R.string.password)) },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(if (showPassword) R.string.login_hide_password else R.string.login_show_password),
                    )
                }
            },
        )
        val label = stringResource(if (submitting) R.string.login_signing_in else R.string.sign_in)
        val canSubmit = enabled && !submitting && username.isNotBlank() && password.isNotEmpty()
        if (primary) {
            PrimaryButton(label, onClick = { submit() }, enabled = canSubmit, modifier = Modifier.fillMaxWidth())
        } else {
            SecondaryButton(label, onClick = { submit() }, enabled = canSubmit, modifier = Modifier.fillMaxWidth())
        }
    }
}
