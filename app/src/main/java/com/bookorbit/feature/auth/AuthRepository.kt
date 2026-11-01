package com.bookorbit.feature.auth

import android.content.Context
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.data.ProcessRestarter
import dagger.hilt.android.qualifiers.ApplicationContext
import com.bookorbit.core.model.LoginOptions
import com.bookorbit.core.model.LoginRequest
import com.bookorbit.core.model.OidcCallbackResponse
import com.bookorbit.core.model.OidcProviderPublic
import com.bookorbit.core.model.SetupStatus
import com.bookorbit.core.network.ApiService
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
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

    /**
     * Confirms the configured server is BookOrbit: `server-identity` names the product. A server
     * older than that endpoint is accepted if it answers setup-status. Throws
     * [NotBookOrbitServerException] when something else answers, and the network error when nothing does.
     */
    suspend fun checkServer() {
        val product = try {
            api.getServerIdentity().product
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            runCatching { api.getSetupStatus() }.getOrNull()?.let { BOOKORBIT_PRODUCT }
        } catch (_: SerializationException) {
            null
        }
        if (product != BOOKORBIT_PRODUCT) throw NotBookOrbitServerException()
    }

    /**
     * How this server allows sign-in. A server older than `login-options` gets password sign-in plus
     * its public providers, which is all it offered. Network errors are thrown, so the screen can
     * offer a retry rather than guess.
     */
    suspend fun loginOptions(): LoginOptions = try {
        api.getLoginOptions()
    } catch (e: HttpException) {
        if (e.code() != 404) throw e
        LoginOptions(oidcProviders = oidcProviders())
    }

    suspend fun login(username: String, password: String) {
        val result = api.login(LoginRequest(username.trim(), password))
        session.signIn(result.accessToken, result.user)
        restartIfServerChanged()
    }

    /** Returns enabled OIDC providers, or an empty list if the endpoint is unavailable. */
    private suspend fun oidcProviders(): List<OidcProviderPublic> =
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

private const val BOOKORBIT_PRODUCT = "bookorbit"

/** The address answered, but not as a BookOrbit server (a web page, another app, a proxy's error). */
class NotBookOrbitServerException : Exception("Not a BookOrbit server")
