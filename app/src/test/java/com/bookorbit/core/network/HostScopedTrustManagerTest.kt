package com.bookorbit.core.network

import io.mockk.mockk
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import org.junit.Assert.*
import org.junit.Test

class HostScopedTrustManagerTest {
    private val chain = arrayOf(mockk<X509Certificate>())
    private var hosts = emptySet<String>()
    private var systemTrusted = false
    private var userTrusted = true
    private var userChecks = 0
    private val manager = HostScopedTrustManager(
        system = mockk<X509TrustManager>(relaxed = true),
        systemCheck = { certificates, _, _ ->
            if (!systemTrusted) throw CertificateException("Not a system issuer")
            certificates.toList()
        },
        userCheck = { certificates, _, _ ->
            userChecks++
            if (!userTrusted) throw CertificateException("Expired or invalid user chain")
            certificates.toList()
        },
        enabled = { it in hosts },
    )

    private fun rejected(host: String) {
        try {
            manager.checkServerTrusted(chain, "RSA", host)
            fail("Expected certificate rejection for $host")
        } catch (_: CertificateException) { }
    }

    @Test fun `system certificates need no opt in`() {
        systemTrusted = true
        assertEquals(chain.toList(), manager.checkServerTrusted(chain, "RSA", "public.example"))
        assertEquals(0, userChecks)
    }

    @Test fun `user root is rejected by default and allowed only for chosen host`() {
        rejected("private.example")
        assertEquals(0, userChecks)
        hosts = setOf("private.example")
        assertEquals(chain.toList(), manager.checkServerTrusted(chain, "RSA", "private.example"))
        rejected("redirect.example")
        assertEquals(1, userChecks)
    }

    @Test fun `revoking consent affects the same manager immediately`() {
        hosts = setOf("private.example")
        manager.checkServerTrusted(chain, "RSA", "private.example")
        hosts = emptySet()
        rejected("private.example")
        assertEquals(1, userChecks)
    }

    @Test fun `opting in never bypasses invalid certificate chains`() {
        hosts = setOf("private.example")
        userTrusted = false
        rejected("private.example")
        assertEquals(1, userChecks)
    }
}
