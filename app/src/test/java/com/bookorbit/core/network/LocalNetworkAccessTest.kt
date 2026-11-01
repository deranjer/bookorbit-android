package com.bookorbit.core.network

import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class LocalNetworkAccessTest {
    private fun local(literal: String) = LocalNetworkAccess.isLocalAddress(InetAddress.getByName(literal))

    @Test
    fun `private, CGNAT and link-local IPv4 ranges are local`() {
        listOf("10.0.2.2", "172.16.0.1", "172.31.255.254", "192.168.68.212", "169.254.1.1", "100.64.0.1", "100.127.255.254")
            .forEach { assertTrue(it, local(it)) }
    }

    @Test
    fun `public and edge-of-range IPv4 addresses are not local`() {
        listOf("8.8.8.8", "172.15.255.255", "172.32.0.1", "192.169.0.1", "100.63.255.255", "100.128.0.1", "127.0.0.1")
            .forEach { assertFalse(it, local(it)) }
    }

    @Test
    fun `IPv6 unique-local and link-local are local, global is not`() {
        assertTrue(local("fd12:3456:789a::1"))
        assertTrue(local("fc00::1"))
        assertTrue(local("fe80::1"))
        assertFalse(local("2606:4700::1111"))
        assertFalse(local("::1"))
    }

    // runBlocking, not runTest: the lookup runs on a real IO thread, and runTest's virtual clock
    // would fire the resolve timeout immediately.
    @Test
    fun `server URLs with literal addresses and mDNS names resolve without DNS`() = runBlocking {
        assertTrue(LocalNetworkAccess.isLocalServer("http://10.0.2.2:8765"))
        assertTrue(LocalNetworkAccess.isLocalServer("http://bookorbit.local:3000"))
        assertTrue(LocalNetworkAccess.isLocalServer("192.168.1.20:3000"))
        assertFalse(LocalNetworkAccess.isLocalServer("https://1.1.1.1"))
        assertFalse(LocalNetworkAccess.isLocalServer("not a url"))
    }
}
