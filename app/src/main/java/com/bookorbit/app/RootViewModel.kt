package com.bookorbit.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.auth.SessionState
import com.bookorbit.core.network.ImageUrls
import com.bookorbit.core.network.LocalNetworkAccess
import com.bookorbit.feature.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Exposes top-level [SessionState] for navigation gating, the sign-out action, and [ImageUrls]. Also
 * gates startup on the local-network permission for an already-saved server (see [LocalNetworkGate]).
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    session: SessionManager,
    private val repo: AuthRepository,
    val imageUrls: ImageUrls,
    @ApplicationContext context: Context,
) : ViewModel() {
    val state: StateFlow<SessionState> = session.state

    /**
     * Checking until the saved server's address is known; Ask when it's on the local network and the
     * permission is missing (an upgraded install, or the user revoked it). Nothing below the root
     * renders until Ready, so no screen fires a request that would only time out.
     */
    enum class LocalNetworkGate { Checking, Ask, Ready }

    private val _localNetworkGate = MutableStateFlow(LocalNetworkGate.Checking)
    val localNetworkGate = _localNetworkGate.asStateFlow()

    init {
        viewModelScope.launch {
            val server = session.serverUrl
            _localNetworkGate.value =
                if (server != null && LocalNetworkAccess.needsPermission(context, server)) LocalNetworkGate.Ask
                else LocalNetworkGate.Ready
        }
    }

    /** Called once the prompt has been answered either way; a denial surfaces as connection errors. */
    fun onLocalNetworkAnswered() {
        _localNetworkGate.value = LocalNetworkGate.Ready
    }

    fun signOut() {
        viewModelScope.launch { repo.logout() }
    }
}
