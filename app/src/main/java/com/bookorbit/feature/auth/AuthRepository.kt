package com.bookorbit.feature.auth

import android.content.Context
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.data.ProcessRestarter
import dagger.hilt.android.qualifiers.ApplicationContext
import com.bookorbit.core.model.LoginRequest
import com.bookorbit.core.model.OidcCallbackResponse
import com.bookorbit.core.model.OidcProviderPublic
import com.bookorbit.core.model.SetupStatus
import com.bookorbit.core.network.ApiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auth-flow operations. Wraps [ApiService] and commits successful logins into [SessionManager].
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionManager,
    @ApplicationContext private val context: Context,
) {
    /**
     * Each server keeps its own database (see [SessionManager.dataScope]). The open one belongs to the
     * server we were using, so after signing in to another server the app restarts onto the right one.
     */
    private fun restartIfServerChanged() {
        if (session.dataScopeChanged()) ProcessRestarter.restart(context)
    }

    /** Probes the configured server. Throws if unreachable / not a BookOrbit server. */
    suspend fun setupStatus(): SetupStatus = api.getSetupStatus()

    suspend fun login(username: String, password: String) {
        val result = api.login(LoginRequest(username.trim(), password))
        session.signIn(result.accessToken, result.user)
        restartIfServerChanged()
    }

    /** Returns enabled OIDC providers, or an empty list if the endpoint is unavailable. */
    suspend fun oidcProviders(): List<OidcProviderPublic> =
        runCatching { api.getPublicOidcProviders() }.getOrDefault(emptyList())

    /** Commits an OIDC login result into the session. */
    fun commitOidc(result: OidcCallbackResponse) {
        session.signIn(result.accessToken, result.user)
        restartIfServerChanged()
    }

    suspend fun logout() {
        runCatching { api.logout() }
        session.signOut()
    }
}
