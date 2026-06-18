package com.bookorbit.core.network

import javax.inject.Qualifier

/** The primary OkHttp client: base-URL rewrite, bearer auth, cookies, and 401 refresh. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainClient

/**
 * A stripped-down client used only by [TokenRefresher]: it shares the cookie jar (so the refresh
 * cookie is sent) but has NO authenticator, breaking the refresh-on-401 recursion cycle.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshClient

/**
 * Client for Coil image loads (covers/thumbnails). Adds the bearer token + cookies but NO base-URL
 * rewrite, because image URLs ([com.bookorbit.core.network.ImageUrls]) are already absolute.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ImageClient
