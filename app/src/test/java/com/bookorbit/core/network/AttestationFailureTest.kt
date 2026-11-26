package com.bookorbit.core.network

import org.junit.Assert.*
import org.junit.Test
import javax.net.ssl.SSLHandshakeException

class AttestationFailureTest {
    @Test fun `only short app attest codes are exposed never arbitrary messages`() {
        assertEquals("APP_ATTEST_INVALID", attestationServerCode("""{"errorCode":"APP_ATTEST_INVALID","message":"private"}"""))
        for (value in listOf("private-token", "APP_ATTEST_invalid", "APP_ATTEST_" + "A".repeat(49), "APP_ATTEST_KEY\nSECRET"))
            assertNull(attestationServerCode(kotlinx.serialization.json.buildJsonObject { put("errorCode", kotlinx.serialization.json.JsonPrimitive(value)) }.toString()))
        assertNull(attestationServerCode("<html>private</html>"))
    }

    @Test fun `TLS and device failures are distinct and exclude original exception details`() {
        val tls = attestationFailure(AttestationStage.CHALLENGE, SSLHandshakeException("private-url"))
        assertEquals(AttestationProblem.TLS, tls.problem)
        val device = attestationFailure(AttestationStage.KEY, IllegalStateException("private-cert"))
        assertEquals(AttestationProblem.DEVICE_KEY, device.problem)
        assertNull(device.cause); assertFalse(device.toString().contains("private"))
    }
}
