package com.bookorbit.feature.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.bookorbit.core.model.OidcCallbackRequest
import com.bookorbit.core.model.OidcCallbackResponse
import com.bookorbit.core.model.OidcProviderPublic
import com.bookorbit.core.network.ApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Raised when the user dismisses the OIDC browser session (kept silent in the UI). */
class OidcCancelledException : Exception("OIDC login was cancelled")

/**
 * Orchestrates the server-driven OIDC + PKCE login.
 *
 * BookOrbit's flow is server-driven: the server mints the CSRF `state` and resolves the provider's
 * authorize endpoint ([ApiService.generateOidcState]); the client generates PKCE + nonce, opens the
 * authorize URL in a Custom Tab via AppAuth, and on redirect sends the code back to the server
 * ([ApiService.oidcCallback]) — the server (not the client) exchanges the code for tokens.
 *
 * AppAuth is used only to launch the browser, capture the `bookorbit://oauth2-callback` redirect,
 * and validate the returned `state`. We supply our own PKCE verifier/challenge so the server can
 * complete the exchange.
 */
@Singleton
class OidcManager @Inject constructor(
    private val api: ApiService,
    @ApplicationContext private val context: Context,
) {
    private data class Pending(
        val codeVerifier: String,
        val nonce: String,
        val redirectUri: String,
        val state: String,
    )

    private var pending: Pending? = null

    /**
     * Builds the AppAuth authorization Intent for [provider]. The caller launches it with an
     * ActivityResultLauncher and passes the result back to [completeLogin].
     */
    suspend fun buildAuthIntent(provider: OidcProviderPublic): Intent {
        require(provider.enabled) { "OIDC provider is not enabled" }

        val codeVerifier = randomBase64Url(32)
        val codeChallenge = sha256Base64Url(codeVerifier)
        val nonce = randomBase64Url(16)

        val stateResponse = api.generateOidcState(provider.slug)

        val authEndpoint = Uri.parse(stateResponse.authorizationEndpoint)
        // Token endpoint is unused (the server exchanges the code), but AppAuth requires a non-null
        // value — reuse the authorize endpoint as a harmless placeholder.
        val config = AuthorizationServiceConfiguration(authEndpoint, authEndpoint)

        val request = AuthorizationRequest.Builder(
            config,
            provider.clientId,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI),
        )
            .setScope(provider.scopes)
            .setState(stateResponse.state)
            .setNonce(nonce)
            .setCodeVerifier(codeVerifier, codeChallenge, "S256")
            .build()

        pending = Pending(codeVerifier, nonce, REDIRECT_URI, stateResponse.state)

        return AuthorizationService(context).getAuthorizationRequestIntent(request)
    }

    /**
     * Completes login from the AppAuth result Intent. Throws [OidcCancelledException] on user
     * cancellation, or a generic exception on provider/network error.
     */
    suspend fun completeLogin(data: Intent?): OidcCallbackResponse {
        if (data == null) throw OidcCancelledException()

        val response = AuthorizationResponse.fromIntent(data)
        val exception = AuthorizationException.fromIntent(data)

        if (response == null) {
            if (exception?.code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code) {
                throw OidcCancelledException()
            }
            throw exception ?: Exception("OIDC login failed")
        }

        val p = pending ?: throw IllegalStateException("No pending OIDC request")
        val code = response.authorizationCode
            ?: throw Exception("Missing authorization code in OIDC callback")

        return api.oidcCallback(
            OidcCallbackRequest(
                code = code,
                codeVerifier = p.codeVerifier,
                redirectUri = p.redirectUri,
                nonce = p.nonce,
                state = p.state,
            ),
        ).also { pending = null }
    }

    private fun randomBase64Url(numBytes: Int): String {
        val bytes = ByteArray(numBytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, BASE64_FLAGS)
    }

    private fun sha256Base64Url(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, BASE64_FLAGS)
    }

    companion object {
        const val REDIRECT_URI = "bookorbit://oauth2-callback"
        private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    }
}
