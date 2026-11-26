package com.bookorbit.core.network

import okhttp3.OkHttpClient
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnnotationApi

/** Retain owned authorization/refresh, but never replay a potentially accepted annotation POST. */
internal fun annotationClient(base: OkHttpClient): OkHttpClient = base.newBuilder()
    .retryOnConnectionFailure(false)
    .followRedirects(false)
    .followSslRedirects(false)
    .addInterceptor(SingleAttemptAiInterceptor()) // Same bounded one-shot JSON transport, no AI policy/signing.
    .build()
