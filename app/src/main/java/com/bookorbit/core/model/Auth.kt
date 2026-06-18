package com.bookorbit.core.model

import kotlinx.serialization.Serializable

/** The authenticated user as returned by the server. */
@Serializable
data class AuthUser(
    val id: Int,
    val username: String,
    val name: String? = null,
    val email: String? = null,
    val active: Boolean = true,
    val isSuperuser: Boolean = false,
    val isDefaultPassword: Boolean = false,
    val avatarUrl: String? = null,
    val provisioningMethod: String = "local",
    val permissions: List<String> = emptyList(),
)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val accessToken: String, val user: AuthUser)

/** `POST /auth/refresh` response. The refresh token itself rides as an HTTP-only cookie. */
@Serializable
data class RefreshResponse(val accessToken: String)

@Serializable
data class SetupStatus(val needsSetup: Boolean)

@Serializable
data class OidcProviderPublic(
    val slug: String,
    val displayName: String,
    val enabled: Boolean,
    val iconUrl: String? = null,
    val clientId: String,
    val scopes: String,
)

@Serializable
data class OidcStateResponse(
    val state: String,
    val authorizationEndpoint: String,
)

@Serializable
data class OidcCallbackRequest(
    val code: String,
    val codeVerifier: String,
    val redirectUri: String,
    val nonce: String,
    val state: String,
)

@Serializable
data class OidcCallbackResponse(
    val mode: String = "login",
    val accessToken: String,
    val user: AuthUser,
)
