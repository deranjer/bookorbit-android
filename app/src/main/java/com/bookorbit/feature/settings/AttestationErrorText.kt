package com.bookorbit.feature.settings

import com.bookorbit.R
import com.bookorbit.core.network.*
import com.bookorbit.ui.UiText

internal fun attestationErrorText(error: Exception): UiText {
    val failure = error as? AttestationFailure ?: return UiText.of(R.string.attest_failed)
    val stage = when (failure.stage) {
        AttestationStage.CONNECTION -> R.string.attest_stage_connection
        AttestationStage.CHALLENGE -> R.string.attest_stage_challenge
        AttestationStage.KEY -> R.string.attest_stage_key
        AttestationStage.REGISTRATION -> R.string.attest_stage_registration
        AttestationStage.RECEIPT -> R.string.attest_stage_receipt
        AttestationStage.STORAGE -> R.string.attest_stage_storage
    }
    val reason = when (failure.problem) {
        AttestationProblem.HTTPS -> R.string.attest_reason_https
        AttestationProblem.TLS -> R.string.attest_reason_tls
        AttestationProblem.NETWORK -> R.string.attest_reason_network
        AttestationProblem.DEVICE_KEY -> R.string.attest_reason_key
        AttestationProblem.SERVER -> when (failure.httpStatus) {
            401 -> R.string.attest_reason_auth
            404 -> R.string.attest_reason_missing
            429 -> R.string.attest_reason_rate
            else -> R.string.attest_reason_server
        }
        AttestationProblem.RESPONSE -> R.string.attest_reason_response
        AttestationProblem.LOCAL -> R.string.attest_reason_local
    }
    return UiText.of(R.string.attest_error_detail, UiText.of(stage), UiText.of(reason),
        failure.httpStatus?.let { "HTTP $it" }.orEmpty(), failure.serverCode.orEmpty())
}
