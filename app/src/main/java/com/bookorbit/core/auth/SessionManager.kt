package com.bookorbit.core.auth

import com.bookorbit.core.model.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Top-level auth state used to gate navigation (server-setup -> login -> main). */
sealed interface SessionState {
    /** Initial state before persisted credentials have been loaded. */
    data object Loading : SessionState

    /** No server configured yet — show the server-setup screen. */
    data object NeedsServer : SessionState

    /** Server known but not authenticated — show login. */
    data object SignedOut : SessionState

    data class SignedIn(val user: AuthUser) : SessionState
}

/**
 * Single source of truth for auth: server URL, access token, and the signed-in user.
 *
 * The access token and server URL are cached in memory (volatile) for synchronous access from the
 * OkHttp interceptors, and mirrored into [SecureStorage] for persistence across launches. The
 * refresh token lives only in the persisted cookie jar.
 */
@Singleton
class SessionManager @Inject constructor(
    private val storage: SecureStorage,
    private val json: Json,
) {
    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    var serverUrl: String? = null
        private set

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** The signed-in user, or null when not authenticated. */
    val currentUser: AuthUser? get() = (_state.value as? SessionState.SignedIn)?.user

    /** Load persisted credentials once at startup and derive the initial [SessionState]. */
    fun bootstrap() {
        serverUrl = storage.getString(SecureStorage.KEY_SERVER_URL)
        accessToken = storage.getString(SecureStorage.KEY_ACCESS_TOKEN)
        val user = storage.getString(SecureStorage.KEY_USER)
            ?.let { runCatching { json.decodeFromString<AuthUser>(it) }.getOrNull() }

        _state.value = when {
            serverUrl.isNullOrBlank() -> SessionState.NeedsServer
            accessToken.isNullOrBlank() || user == null -> SessionState.SignedOut
            else -> SessionState.SignedIn(user)
        }
    }

    fun setServerUrl(url: String) {
        serverUrl = url.trimEnd('/')
        storage.putString(SecureStorage.KEY_SERVER_URL, serverUrl)
        if (_state.value is SessionState.Loading || _state.value is SessionState.NeedsServer) {
            _state.value = SessionState.SignedOut
        }
    }

    /** Reverts to the no-server state (used when a setup-status probe fails during onboarding). */
    fun clearServer() {
        serverUrl = null
        storage.remove(SecureStorage.KEY_SERVER_URL)
        _state.value = SessionState.NeedsServer
    }

    fun signIn(token: String, user: AuthUser) {
        accessToken = token
        storage.putString(SecureStorage.KEY_ACCESS_TOKEN, token)
        storage.putString(SecureStorage.KEY_USER, json.encodeToString(AuthUser.serializer(), user))
        _state.value = SessionState.SignedIn(user)
    }

    /** Called by [com.bookorbit.core.network.TokenRefresher] after a successful silent refresh. */
    fun updateAccessToken(token: String) {
        accessToken = token
        storage.putString(SecureStorage.KEY_ACCESS_TOKEN, token)
    }

    /** Clears the token/user but keeps the server URL so the user lands back on login. */
    fun signOut() {
        accessToken = null
        storage.remove(SecureStorage.KEY_ACCESS_TOKEN)
        storage.remove(SecureStorage.KEY_USER)
        storage.remove(SecureStorage.KEY_COOKIES)
        if (serverUrl.isNullOrBlank()) {
            _state.value = SessionState.NeedsServer
        } else {
            _state.value = SessionState.SignedOut
        }
    }
}
