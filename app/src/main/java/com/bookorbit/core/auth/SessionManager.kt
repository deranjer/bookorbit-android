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

/** In-process authorization lease. A logout/relogin invalidates it even for the same account. */
data class SessionOwner(val serverUrl: String, val userId: Int, val generation: Long)

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
    private var generation = 0L
    private val _owners = MutableStateFlow<SessionOwner?>(null)
    /** Unlike SignedIn equality, this also changes on same-user relogin or server replacement. */
    val owners: StateFlow<SessionOwner?> = _owners.asStateFlow()

    @Synchronized
    fun owner(): SessionOwner? {
        val server = serverUrl ?: return null
        val user = currentUser?.id ?: return null
        return SessionOwner(server, user, generation)
    }

    @Synchronized
    fun owns(owner: SessionOwner): Boolean = owner == this.owner()

    @Synchronized
    fun commitRefreshedToken(owner: SessionOwner, token: String, saveCookies: () -> Unit = {}): Boolean {
        if (!owns(owner)) return false
        updateAccessToken(token)
        saveCookies()
        return true
    }

    @Synchronized
    fun signOutIfOwned(owner: SessionOwner) {
        if (owns(owner)) signOut()
    }
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
    @Synchronized
    fun bootstrap() {
        generation++
        serverUrl = storage.getString(SecureStorage.KEY_SERVER_URL)
        accessToken = storage.getString(SecureStorage.KEY_ACCESS_TOKEN)
        val user = storage.getString(SecureStorage.KEY_USER)
            ?.let { runCatching { json.decodeFromString<AuthUser>(it) }.getOrNull() }

        // Data from before servers were kept apart belongs to whichever server this install already uses.
        if (!serverUrl.isNullOrBlank() && !accessToken.isNullOrBlank() && storage.getString(SecureStorage.KEY_DATA_OWNER) == null) {
            storage.putString(SecureStorage.KEY_DATA_OWNER, key(serverUrl!!))
        }

        _state.value = when {
            serverUrl.isNullOrBlank() -> SessionState.NeedsServer
            accessToken.isNullOrBlank() || user == null -> SessionState.SignedOut
            else -> SessionState.SignedIn(user)
        }
        _owners.value = owner()
    }

    /** Scope the open database was created for (see [dataScope]); null until it is first opened. */
    @Volatile
    var activeDataScope: String? = null

    /**
     * Names the on-device data (database file and download folders) of the configured server. Book ids
     * are per server, so each server keeps its own. The first server used owns the original data and has
     * an empty scope, which keeps existing installs and their downloads exactly where they were.
     */
    val dataScope: String
        get() = DataScope.of(storage.getString(SecureStorage.KEY_DATA_OWNER), serverUrl)

    private fun key(url: String) = DataScope.key(url)

    /** True when the configured server's data is not what the open database holds (needs an app restart). */
    fun dataScopeChanged(): Boolean = activeDataScope?.let { it != dataScope } == true

    @Synchronized
    fun setServerUrl(url: String) {
        generation++
        serverUrl = url.trimEnd('/')
        storage.putString(SecureStorage.KEY_SERVER_URL, serverUrl)
        if (_state.value is SessionState.Loading || _state.value is SessionState.NeedsServer) {
            _state.value = SessionState.SignedOut
        }
        _owners.value = owner()
    }

    /** Reverts to the no-server state (used when a setup-status probe fails during onboarding). */
    @Synchronized
    fun clearServer() {
        generation++
        serverUrl = null
        storage.remove(SecureStorage.KEY_SERVER_URL)
        _state.value = SessionState.NeedsServer
        _owners.value = null
    }

    @Synchronized
    fun signIn(token: String, user: AuthUser) {
        generation++
        // The first server anyone signs in to owns the original data; later ones get their own.
        if (storage.getString(SecureStorage.KEY_DATA_OWNER) == null) serverUrl?.let { storage.putString(SecureStorage.KEY_DATA_OWNER, key(it)) }
        accessToken = token
        storage.putString(SecureStorage.KEY_ACCESS_TOKEN, token)
        storage.putString(SecureStorage.KEY_USER, json.encodeToString(AuthUser.serializer(), user))
        _state.value = SessionState.SignedIn(user)
        _owners.value = owner()
    }

    /** Called by [com.bookorbit.core.network.TokenRefresher] after a successful silent refresh. */
    @Synchronized
    fun updateAccessToken(token: String) {
        accessToken = token
        storage.putString(SecureStorage.KEY_ACCESS_TOKEN, token)
    }

    /** Clears the token/user but keeps the server URL so the user lands back on login. */
    @Synchronized
    fun signOut() {
        generation++
        accessToken = null
        storage.remove(SecureStorage.KEY_ACCESS_TOKEN)
        storage.remove(SecureStorage.KEY_USER)
        storage.remove(SecureStorage.KEY_COOKIES)
        if (serverUrl.isNullOrBlank()) {
            _state.value = SessionState.NeedsServer
        } else {
            _state.value = SessionState.SignedOut
        }
        _owners.value = null
    }
}
