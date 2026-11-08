package com.bookorbit.core.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuedWriteRejectionTest {
    @Test
    fun `client errors that will not change are permanent`() {
        listOf(400, 403, 404, 409, 422).forEach { assertTrue("HTTP $it", isPermanentRejection(it)) }
    }

    @Test
    fun `an expired session, a timeout and a rate limit are retried`() {
        listOf(401, 408, 429).forEach { assertFalse("HTTP $it", isPermanentRejection(it)) }
    }

    @Test
    fun `server errors are retried`() {
        listOf(500, 502, 503).forEach { assertFalse("HTTP $it", isPermanentRejection(it)) }
    }
}
