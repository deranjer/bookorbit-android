package com.bookorbit.core.diagnostics

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.net.ssl.SSLException

internal fun diagnosticRoute(path: String): DiagnosticRoute = when {
    path.endsWith("/app-attest/challenges") -> DiagnosticRoute.ATTEST_CHALLENGE
    path.endsWith("/app-attest/android-keys") -> DiagnosticRoute.ATTEST_REGISTER
    "/auth/" in path -> DiagnosticRoute.AUTH
    path.endsWith("/vision-scan") -> DiagnosticRoute.PHOTO_SCAN
    path.endsWith("/reading-help") -> DiagnosticRoute.READING_HELP
    "/story-guide" in path -> DiagnosticRoute.STORY_GUIDE
    "/audiobooks/" in path -> DiagnosticRoute.AUDIO
    "/epub/" in path -> DiagnosticRoute.EPUB
    "/dictionaries/" in path || path.endsWith("/freedict-database.json") -> DiagnosticRoute.DICTIONARY
    path.endsWith("/chat/completions") || path.endsWith("/api/chat") || path.endsWith(":generateContent") -> DiagnosticRoute.DIRECT_AI
    else -> DiagnosticRoute.OTHER
}

class DiagnosticInterceptor @Inject constructor(private val log: DiagnosticLog) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val route = diagnosticRoute(request.url.encodedPath)
        val method = DiagnosticMethod.entries.firstOrNull { it.name == request.method } ?: DiagnosticMethod.OTHER
        val start = System.nanoTime()
        try {
            val response = chain.proceed(request)
            log.record(DiagnosticEntry(DiagnosticEvent.HTTP_COMPLETED, route, method, response.code,
                (System.nanoTime() - start) / 1_000_000))
            return response
        } catch (error: IOException) {
            val failure = when {
                chain.call().isCanceled() -> DiagnosticFailure.CANCELLED
                error is SSLException -> DiagnosticFailure.TLS
                error is SocketTimeoutException -> DiagnosticFailure.TIMEOUT
                else -> DiagnosticFailure.IO
            }
            log.record(DiagnosticEntry(DiagnosticEvent.HTTP_FAILED, route, method,
                durationMs = (System.nanoTime() - start) / 1_000_000, failure = failure))
            throw error
        }
    }
}
