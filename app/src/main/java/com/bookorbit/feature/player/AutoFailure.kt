package com.bookorbit.feature.player

import kotlinx.coroutines.TimeoutCancellationException
import retrofit2.HttpException
import java.io.IOException

enum class AutoFailure { SIGN_IN, NETWORK, UNAVAILABLE, NO_RESULTS, NO_HISTORY, INVALID_REQUEST;
    companion object {
        fun from(error: Throwable): AutoFailure = when (error) {
            is AutoRequestException -> error.failure
            is HttpException -> when (error.code()) {
                401, 403 -> SIGN_IN
                404 -> UNAVAILABLE
                else -> NETWORK
            }
            is IOException, is TimeoutCancellationException -> NETWORK
            is IllegalArgumentException -> INVALID_REQUEST
            else -> UNAVAILABLE
        }
    }
}

class AutoRequestException(val failure: AutoFailure) : Exception(failure.name)
