package com.bookorbit.feature.ai

import com.bookorbit.core.auth.SecureStorage
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.auth.SessionOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

enum class AiRoute { SERVER, DIRECT }
enum class AiProvider(val displayName: String, val requiresToken: Boolean = true, val customEndpoint: Boolean = false) {
    OPENAI("OpenAI"), GOOGLE("Google Gemini"), OPENROUTER("OpenRouter"),
    OLLAMA("Ollama", requiresToken = false, customEndpoint = true),
    OPENAI_COMPATIBLE("OpenAI-compatible", requiresToken = false, customEndpoint = true),
}

data class AiConnection(
    val route: AiRoute = AiRoute.SERVER,
    val provider: AiProvider = AiProvider.OPENAI,
    val helpModel: String = "",
    val guideModel: String = "",
    val hasToken: Boolean = false,
    val valid: Boolean = true,
    val revision: Long = 0,
    val baseUrl: String = "",
    val allowInsecure: Boolean = false,
)

/** Never a data class: accidental logs cannot stringify the secret. */
internal class DirectAiCredentials(val owner: SessionOwner, val revision: Long, val provider: AiProvider,
    val model: String, val token: String, val baseUrl: String = defaultAiBaseUrl(provider), val allowInsecure: Boolean = false) {
    override fun toString() = "DirectAiCredentials([redacted])"
}

@Serializable
private data class ProtectedAiConnection(
    val route: AiRoute = AiRoute.SERVER,
    val provider: AiProvider = AiProvider.OPENAI,
    val helpModels: Map<AiProvider, String> = emptyMap(),
    val guideModels: Map<AiProvider, String> = emptyMap(),
    val tokens: Map<AiProvider, String> = emptyMap(),
    val endpoints: Map<AiProvider, String> = emptyMap(),
    val insecure: Map<AiProvider, Boolean> = emptyMap(),
)

@Singleton
class AiConnectionStore @Inject constructor(private val storage: SecureStorage, private val session: SessionManager) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableRevision = MutableStateFlow(0L)
    private val version = AtomicLong()
    private val lock = Any()
    val revision: Long get() = version.get()
    val changes = mutableRevision.asStateFlow()

    fun connection(owner: SessionOwner, provider: AiProvider? = null): AiConnection {
        if (!session.owns(owner)) return AiConnection(valid = false)
        val (value, readRevision) = synchronized(lock) { runCatching { read(owner) }.getOrNull() to version.get() }
        if (value == null) return AiConnection(valid = false)
        if (!session.owns(owner)) return AiConnection(valid = false)
        val selected = provider ?: value.provider
        val endpoint = value.endpoints[selected] ?: defaultAiBaseUrl(selected)
        val insecure = value.insecure[selected] == true
        return AiConnection(value.route, selected, value.helpModels[selected].orEmpty(), value.guideModels[selected].orEmpty(), !value.tokens[selected].isNullOrBlank(), revision = readRevision,
            baseUrl = endpoint, allowInsecure = insecure,
            valid = value.route == AiRoute.SERVER || runCatching { validatedAiBaseUrl(selected, endpoint, insecure) }.isSuccess)
    }

    /** Blank replacement keeps an existing token; removing it is a separate explicit operation. */
    fun save(owner: SessionOwner, route: AiRoute, provider: AiProvider, helpModel: String, guideModel: String,
        replacementToken: String = "", baseUrl: String = defaultAiBaseUrl(provider), allowInsecure: Boolean = false) {
        require(session.owns(owner))
        require(validAiModel(helpModel) && validAiModel(guideModel))
        if (provider == AiProvider.GOOGLE) require('/' !in helpModel && '/' !in guideModel)
        require(replacementToken.length <= 8192 && replacementToken.all { it.code in 33..126 })
        val endpoint = validatedAiBaseUrl(provider, baseUrl, allowInsecure).toString()
        val committed = synchronized(lock) {
            val old = runCatching { read(owner) }.getOrDefault(ProtectedAiConnection())
            val tokens = old.tokens.toMutableMap()
            // A key saved for one endpoint must never follow a changed address.
            if ((old.endpoints[provider] ?: defaultAiBaseUrl(provider)) != endpoint) tokens.remove(provider)
            if (replacementToken.isNotEmpty()) tokens[provider] = replacementToken
            val value = old.copy(route = route, provider = provider, helpModels = old.helpModels + (provider to helpModel),
                guideModels = old.guideModels + (provider to guideModel), tokens = tokens,
                endpoints = old.endpoints + (provider to endpoint), insecure = old.insecure + (provider to allowInsecure))
            storage.putString(key(owner), json.encodeToString(ProtectedAiConnection.serializer(), value))
            version.incrementAndGet()
        }
        // Never emit or acquire SessionManager's monitor while holding the configuration lock.
        mutableRevision.update { maxOf(it, committed) }
    }

    internal fun credentials(owner: SessionOwner): DirectAiCredentials {
        require(session.owns(owner))
        val (value, revision) = synchronized(lock) { read(owner) to version.get() }
        require(session.owns(owner))
        require(value.route == AiRoute.DIRECT)
        val model = value.helpModels[value.provider].orEmpty()
        val token = value.tokens[value.provider].orEmpty()
        val endpoint = validatedAiBaseUrl(value.provider, value.endpoints[value.provider] ?: defaultAiBaseUrl(value.provider), value.insecure[value.provider] == true)
        require(model.isNotBlank() && validAiModel(model) && (!value.provider.requiresToken || token.isNotBlank()) && token.length <= 8192 && token.all { it.code in 33..126 })
        if (value.provider == AiProvider.GOOGLE) require('/' !in model)
        return DirectAiCredentials(owner, revision, value.provider, model, token, endpoint.toString(), value.insecure[value.provider] == true)
    }

    fun removeToken(owner: SessionOwner, provider: AiProvider) {
        require(session.owns(owner))
        val committed = synchronized(lock) {
            val value = read(owner)
            storage.putString(key(owner), json.encodeToString(ProtectedAiConnection.serializer(), value.copy(tokens = value.tokens - provider)))
            version.incrementAndGet()
        }
        mutableRevision.update { maxOf(it, committed) }
    }

    fun useServer(owner: SessionOwner) {
        require(session.owns(owner))
        val committed = synchronized(lock) {
            val value = runCatching { read(owner) }.getOrDefault(ProtectedAiConnection())
            storage.putString(key(owner), json.encodeToString(ProtectedAiConnection.serializer(), value.copy(route = AiRoute.SERVER)))
            version.incrementAndGet()
        }
        mutableRevision.update { maxOf(it, committed) }
    }

    internal fun current(credentials: DirectAiCredentials): Boolean = session.owns(credentials.owner) && revision == credentials.revision

    private fun read(owner: SessionOwner): ProtectedAiConnection = storage.getString(key(owner))
        ?.let { json.decodeFromString<ProtectedAiConnection>(it) } ?: ProtectedAiConnection()

    private fun key(owner: SessionOwner): String {
        val identity = "${owner.serverUrl}\n${owner.userId}"
        return "ai_connection_" + MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

internal fun validAiModel(value: String): Boolean = value.isEmpty() || value.length <= 240 &&
    value.split('/').all { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]*")) && it != "." && it != ".." }
