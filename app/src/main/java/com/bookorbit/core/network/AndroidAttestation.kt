package com.bookorbit.core.network

import android.content.Context
import com.bookorbit.core.auth.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
internal data class AttestRegistration(val alias: String, val keyId: String)
@Serializable
private data class AttestChallenge(val challenge: String, val expiresAt: String)
@Serializable
private data class AttestEnrollment(val keyId: String, val certificateChain: List<String>, val challenge: String)
@Serializable
private data class AttestReceipt(val keyId: String, val environment: String)

/** Explicit enrollment only. No automatic enrollment, trust-policy changes or paid-request replay. */
@Singleton
class AndroidAttestation internal constructor(
    private val session: SessionManager,
    private val keys: AttestKeys,
    private val json: Json,
    private val client: OkHttpClient,
    private val read: (String) -> String?,
    private val write: (String, String?) -> Unit,
) {
    @Inject internal constructor(@ApplicationContext context: Context, session: SessionManager, keys: AndroidAttestKeys,
        json: Json, @MainClient main: OkHttpClient) : this(session, keys, json,
        main.newBuilder().apply { interceptors().removeAll { it is BaseUrlInterceptor || it is AuthInterceptor } }
            .authenticator(Authenticator.NONE).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).cache(null).build(),
        { context.getSharedPreferences("android_attestation", Context.MODE_PRIVATE).getString(it, null) },
        { key, value -> check(context.getSharedPreferences("android_attestation", Context.MODE_PRIVATE).edit().putString(key, value).commit()) })

    private val mutation = Mutex()
    private data class Owner(val server: String, val userId: Int) {
        val storageKey get() = AndroidAttestProtocol.base64(AndroidAttestProtocol.sha256("$server\n$userId".toByteArray()))
    }
    private fun owner(): Owner {
        val server = session.serverUrl?.trimEnd('/') ?: throw IOException("Not signed in")
        val url = server.toHttpUrl()
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null)
            throw IOException("Attestation requires HTTPS")
        return Owner(server, session.currentUser?.id ?: throw IOException("Not signed in"))
    }
    private fun requireOwner(expected: Owner) {
        if (owner() != expected || session.accessToken.isNullOrBlank()) throw IOException("Attestation account changed")
    }
    private fun registration(owner: Owner): AttestRegistration? = read(owner.storageKey)?.let {
        runCatching { json.decodeFromString<AttestRegistration>(it) }.getOrNull()
    }
    fun isEnrolled(): Boolean = runCatching { registration(owner()) != null }.getOrDefault(false)

    suspend fun enroll() = mutation.withLock {
        withContext(Dispatchers.IO) {
            val owner = owner()
            val challenge = json.decodeFromString<AttestChallenge>(post(owner, "challenges", "{}"))
            require(challenge.challenge.matches(Regex("[A-Za-z0-9_-]{16,256}")))
            check(Instant.parse(challenge.expiresAt).isAfter(Instant.now())) { "Attestation challenge expired" }
            val alias = "bookorbit-attest-${UUID.randomUUID()}"
            var installed = false
            try {
                requireOwner(owner)
                val key = keys.generate(alias, challenge.challenge)
                requireOwner(owner)
                check(Instant.parse(challenge.expiresAt).isAfter(Instant.now())) { "Attestation challenge expired" }
                val receipt = json.decodeFromString<AttestReceipt>(post(owner, "android-keys",
                    json.encodeToString(AttestEnrollment.serializer(), AttestEnrollment(key.keyId, key.certificateChain, challenge.challenge))))
                requireOwner(owner)
                check(receipt.keyId == key.keyId && receipt.environment == "production") { "Unexpected attestation registration" }
                currentCoroutineContext().ensureActive()
                val previous = registration(owner)
                write(owner.storageKey, json.encodeToString(AttestRegistration.serializer(), AttestRegistration(alias, key.keyId)))
                installed = true
                previous?.let { runCatching { keys.delete(it.alias) } }
            } finally { if (!installed) runCatching { keys.delete(alias) } }
        }
    }

    suspend fun disable() = mutation.withLock {
        withContext(Dispatchers.IO) {
            val owner = owner()
            val previous = registration(owner)
            write(owner.storageKey, null)
            previous?.let { keys.delete(it.alias) }
        }
    }

    internal fun sign(request: Request): Request {
        val owner = runCatching { owner() }.getOrNull() ?: return request
        val registration = registration(owner) ?: return request
        val base = "${owner.server}/api/v1/".toHttpUrl()
        if (request.url.scheme != base.scheme || request.url.host != base.host || request.url.port != base.port ||
            !request.url.encodedPath.startsWith(base.encodedPath)) throw IOException("Attestation destination changed")
        requireOwner(owner)
        if (request.header("Authorization") != "Bearer ${session.accessToken}") throw IOException("Attestation authorization changed")
        val body = request.body
        val bytes = when (body) {
            null -> ByteArray(0)
            is SingleAttemptJsonBody -> body.bytes
            else -> throw IOException("Attestation requires immutable exact request bytes")
        }
        val timestamp = System.currentTimeMillis().toString()
        val nonce = AndroidAttestProtocol.nonce()
        val assertion = try { keys.sign(registration.alias, AndroidAttestProtocol.clientData(request.method, request.url, timestamp, bytes, nonce)) }
            catch (e: Exception) { throw IOException("Attestation signing failed; register the device again", e) }
        requireOwner(owner)
        return request.newBuilder().header("X-BookOrbit-Attest-Key", registration.keyId)
            .header("X-BookOrbit-Attest-Assertion", assertion).header("X-BookOrbit-Attest-Time", timestamp)
            .header("X-BookOrbit-Attest-Nonce", nonce).build()
    }

    private suspend fun post(owner: Owner, path: String, body: String): String {
        requireOwner(owner)
        val bytes = body.toByteArray(Charsets.UTF_8)
        val request = Request.Builder().url("${owner.server}/api/v1/app-attest/$path")
            .header("Authorization", "Bearer ${session.accessToken}")
            .post(SingleAttemptJsonBody(bytes, "application/json".toMediaType())).build()
        val call = client.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            requireOwner(owner)
                            if (!it.isSuccessful) throw IOException("Attestation registration refused (HTTP ${it.code})")
                            val source = it.body?.source() ?: throw IOException("Missing attestation response")
                            source.request(65_537)
                            if (source.buffer.size > 65_536) throw IOException("Attestation response too large")
                            source.readUtf8()
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
    }
}

internal class AndroidAttestInterceptor(private val attestation: AndroidAttestation) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(attestation.sign(chain.request()))
}
