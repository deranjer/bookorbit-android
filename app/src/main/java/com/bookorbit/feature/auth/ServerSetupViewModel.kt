package com.bookorbit.feature.auth

import android.content.Context
import com.bookorbit.R
import com.bookorbit.ui.UiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.network.LocalNetworkAccess
import com.bookorbit.core.network.UserCaSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.net.ssl.SSLException
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
    private val userCaSettings: UserCaSettings,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** [askLocalNetwork]: the screen should show the system prompt for [LocalNetworkAccess.PERMISSION]. */
    data class UiState(val loading: Boolean = false, val error: UiText? = null, val askLocalNetwork: Boolean = false)

    /** The connect that is waiting on the local-network prompt; survives the activity being recreated. */
    private var pendingConnect: (suspend () -> Unit)? = null

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    val currentServerUrl: String? get() = session.serverUrl

    fun trustsUserCas(url: String): Boolean = userCaSettings.isEnabledForUrl(url)

    /**
     * Sets the server URL and checks a BookOrbit server answers there. On failure the URL is reverted
     * so the user stays on this screen.
     */
    fun connect(rawUrl: String, trustUserCas: Boolean, onConnected: () -> Unit) {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // Ask before setServerUrl: it switches the screen to Login, and on Android 17 every
            // request to a local server would otherwise just time out.
            if (LocalNetworkAccess.needsPermission(context, trimmed)) {
                pendingConnect = { probe(trimmed, trustUserCas, onConnected) }
                _ui.update { it.copy(askLocalNetwork = true) }
            } else {
                probe(trimmed, trustUserCas, onConnected)
            }
        }
    }

    /** The screen has launched the prompt; clearing the flag keeps a recreated screen from asking twice. */
    fun onLocalNetworkPromptShown() = _ui.update { it.copy(askLocalNetwork = false) }

    fun onLocalNetworkResult(granted: Boolean) {
        val next = pendingConnect ?: return
        pendingConnect = null
        if (granted) {
            viewModelScope.launch { next() }
        } else {
            _ui.update { it.copy(loading = false, error = UiText.of(R.string.server_err_local_network)) }
        }
    }

    private suspend fun probe(trimmed: String, trustUserCas: Boolean, onConnected: () -> Unit) {
        try {
            withContext(Dispatchers.IO) { userCaSettings.setEnabledForUrl(trimmed, trustUserCas) }
            session.setServerUrl(trimmed)
            repo.checkServer()
            onConnected()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            session.clearServer()
            val message = when {
                e is NotBookOrbitServerException -> R.string.server_err_not_bookorbit
                generateSequence<Throwable>(e) { it.cause }.any { it is SSLException } -> R.string.server_err_certificate
                else -> R.string.server_err_connect
            }
            _ui.update { it.copy(error = UiText.of(message)) }
        } finally {
            _ui.update { it.copy(loading = false) }
        }
    }
}
