package com.bookorbit.core.network

import kotlinx.serialization.json.*
import java.io.IOException
import javax.net.ssl.SSLException

enum class AttestationStage { CONNECTION, CHALLENGE, KEY, REGISTRATION, RECEIPT, STORAGE }
enum class AttestationProblem { HTTPS, TLS, NETWORK, DEVICE_KEY, SERVER, RESPONSE, LOCAL }

/** Only bounded protocol codes reach the UI, never server messages, certificates or credentials. */
class AttestationFailure(val stage: AttestationStage, val problem: AttestationProblem,
    val httpStatus: Int? = null, val serverCode: String? = null) : IOException("${stage.name}:${problem.name}")

internal fun attestationServerCode(body: String): String? = runCatching {
    Json.parseToJsonElement(body).jsonObject["errorCode"]?.jsonPrimitive?.contentOrNull
        ?.takeIf { it.matches(Regex("APP_ATTEST_[A-Z0-9_]{1,48}")) }
}.getOrNull()

internal fun attestationFailure(stage: AttestationStage, error: Exception): AttestationFailure {
    if (error is AttestationFailure) return error
    val problem = when {
        error is SSLException -> AttestationProblem.TLS
        stage == AttestationStage.KEY -> AttestationProblem.DEVICE_KEY
        error is IOException -> AttestationProblem.NETWORK
        stage == AttestationStage.STORAGE -> AttestationProblem.LOCAL
        else -> AttestationProblem.RESPONSE
    }
    return AttestationFailure(stage, problem)
}
