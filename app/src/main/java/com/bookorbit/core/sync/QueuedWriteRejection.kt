package com.bookorbit.core.sync

/**
 * True when the server rejected a queued offline write for good, so it can be dropped instead of
 * retried (e.g. 404: the book, file or highlight is gone).
 *
 * 401 is not one of these: it means the session expired or the user signed out, and the write must
 * wait for the next sign-in. Timeouts (408) and rate limits (429) are retried too.
 */
fun isPermanentRejection(httpCode: Int): Boolean =
    httpCode in 400..499 && httpCode !in RETRYABLE_CLIENT_ERRORS

private val RETRYABLE_CLIENT_ERRORS = setOf(401, 408, 429)
