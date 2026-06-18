package com.bookorbit.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerSetupViewModel @Inject constructor(
    private val session: SessionManager,
    private val repo: AuthRepository,
) : ViewModel() {

    data class UiState(val loading: Boolean = false, val error: String? = null)

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    val currentServerUrl: String? get() = session.serverUrl

    /**
     * Sets the server URL and probes setup-status to confirm it's reachable. On failure the URL is
     * reverted so the user stays on this screen.
     */
    fun connect(rawUrl: String, onConnected: () -> Unit) {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            session.setServerUrl(trimmed)
            try {
                repo.setupStatus()
                onConnected()
            } catch (_: Exception) {
                session.clearServer()
                _ui.update {
                    it.copy(error = "Could not connect. Check the URL (including the port, e.g. :3000) and try again.")
                }
            } finally {
                _ui.update { it.copy(loading = false) }
            }
        }
    }
}
