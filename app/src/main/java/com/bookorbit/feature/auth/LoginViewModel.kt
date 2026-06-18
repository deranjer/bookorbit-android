package com.bookorbit.feature.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.OidcProviderPublic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repo: AuthRepository,
    private val oidc: OidcManager,
    session: SessionManager,
) : ViewModel() {

    data class UiState(
        val submitting: Boolean = false,
        val oidcLoadingSlug: String? = null,
        val error: String? = null,
        val providers: List<OidcProviderPublic> = emptyList(),
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    val serverUrl: String? = session.serverUrl

    init {
        viewModelScope.launch {
            _ui.update { it.copy(providers = repo.oidcProviders()) }
        }
    }

    /** A successful login flips SessionManager state to SignedIn, which swaps in the main shell. */
    fun login(username: String, password: String) {
        if (username.isBlank() || password.isEmpty()) return
        _ui.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                repo.login(username, password)
            } catch (_: Exception) {
                _ui.update { it.copy(error = "Invalid username or password.") }
            } finally {
                _ui.update { it.copy(submitting = false) }
            }
        }
    }

    /** Builds the OIDC auth Intent and hands it to [launch] (an ActivityResultLauncher). */
    fun beginOidc(provider: OidcProviderPublic, launch: (Intent) -> Unit) {
        _ui.update { it.copy(oidcLoadingSlug = provider.slug, error = null) }
        viewModelScope.launch {
            try {
                launch(oidc.buildAuthIntent(provider))
            } catch (e: Exception) {
                _ui.update { it.copy(oidcLoadingSlug = null, error = e.message ?: "OIDC login failed") }
            }
        }
    }

    /** Handles the AppAuth result; commits a successful login or surfaces a non-cancel error. */
    fun completeOidc(data: Intent?) {
        viewModelScope.launch {
            try {
                repo.commitOidc(oidc.completeLogin(data))
            } catch (_: OidcCancelledException) {
                // User dismissed the browser — stay silent.
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message ?: "OIDC login failed") }
            } finally {
                _ui.update { it.copy(oidcLoadingSlug = null) }
            }
        }
    }
}
