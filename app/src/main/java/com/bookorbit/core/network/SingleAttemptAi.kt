package com.bookorbit.core.network

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import java.io.IOException
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MobileAiApi

/** A server 503/408 or a broken connection must not silently replay a potentially billable POST. */
internal class SingleAttemptAiInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body ?: return chain.proceed(request)
        if (body.contentType()?.subtype != "json" || body.contentLength() > MAX_JSON_BYTES) throw IOException("Unsupported AI request body")
        val bytes = Buffer().also { body.writeTo(it) }.let {
            if (it.size > MAX_JSON_BYTES) throw IOException("AI request too large")
            it.readByteArray()
        }
        val once = SingleAttemptJsonBody(bytes, body.contentType())
        return chain.proceed(request.newBuilder().method(request.method, once).build())
    }

    companion object { const val MAX_JSON_BYTES = 9 * 1024 * 1024L }
}

/** Immutable bytes also allow a future attestation signer to hash the exact wire body safely. */
internal class SingleAttemptJsonBody(val bytes: ByteArray, private val type: MediaType?) : RequestBody() {
    override fun contentType() = type
    override fun contentLength() = bytes.size.toLong()
    override fun isOneShot() = true
    override fun writeTo(sink: BufferedSink) { sink.write(bytes) }
}

internal fun singleAttemptAiClient(base: OkHttpClient, attestation: AndroidAttestation? = null): OkHttpClient = base.newBuilder()
    .authenticator(okhttp3.Authenticator.NONE)
    .retryOnConnectionFailure(false)
    .followRedirects(false)
    .followSslRedirects(false)
    .addInterceptor(SingleAttemptAiInterceptor())
    // Sign at the wire boundary, after URL/auth/body preparation. A retried bodyless GET
    // gets a fresh nonce too; paid one-shot POSTs still cannot be retried.
    .apply { if (attestation != null) addNetworkInterceptor(AndroidAttestInterceptor(attestation)) }
    .build()
