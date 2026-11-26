package com.bookorbit.core.diagnostics

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DiagnosticFilesTest {
    @Test fun `logging is off by default and creates no files`() {
        val root = Files.createTempDirectory("diagnostics-off").toFile()
        try { val store = DiagnosticFiles(root); assertNull(store.ticket()); assertTrue(root.listFiles()!!.isEmpty()); assertTrue(store.snapshot().isEmpty()) }
        finally { root.deleteRecursively() }
    }
    @Test fun `disabling and clearing invalidate queued tickets`() {
        val root = Files.createTempDirectory("diagnostics-tickets").toFile()
        try {
            var persisted = false
            val store = DiagnosticFiles(root, persist = { persisted = it })
            store.setEnabled(true); assertTrue(persisted)
            val ticket = store.ticket()!!
            store.append(ticket, DiagnosticEntry(DiagnosticEvent.APP_STARTED))
            val before = store.snapshot()
            store.setEnabled(false); assertFalse(persisted)
            store.append(ticket, DiagnosticEntry(DiagnosticEvent.HTTP_FAILED))
            assertArrayEquals(before, store.snapshot())
            store.setEnabled(true); val second = store.ticket()!!
            store.clear(); store.append(second, DiagnosticEntry(DiagnosticEvent.HTTP_FAILED))
            assertTrue(store.snapshot().isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun `rotation stays bounded and survives reopening`() {
        val root = Files.createTempDirectory("diagnostics-rotation").toFile()
        try {
            val store = DiagnosticFiles(root, true, limit = 256)
            repeat(100) { store.append(store.ticket()!!, DiagnosticEntry(DiagnosticEvent.HTTP_COMPLETED, status = 200, durationMs = it.toLong()), it.toLong()) }
            assertTrue(root.listFiles()!!.size <= 2)
            assertTrue(root.listFiles()!!.all { it.length() <= 256 })
            assertTrue(store.snapshot().size <= 512)
            assertArrayEquals(store.snapshot(), DiagnosticFiles(root, false, limit = 256).snapshot())
            assertTrue(store.snapshot().decodeToString().contains("duration_ms=99"))
        } finally { root.deleteRecursively() }
    }
    @Test fun `formatter only exposes bounded technical values`() {
        val secret = "https://private.example/?token=secret\nAuthorization: Bearer private"
        val line = diagnosticLine(DiagnosticEntry(DiagnosticEvent.ATTESTATION_FAILED, status = 403, serverCode = secret, durationMs = Long.MAX_VALUE), 0)
        assertFalse(line.contains("secret")); assertFalse(line.contains("private")); assertEquals(1, line.count { it == '\n' })
        assertTrue(line.contains("status=403")); assertTrue(line.contains("duration_ms=86400000"))
        assertTrue(diagnosticLine(DiagnosticEntry(DiagnosticEvent.ATTESTATION_FAILED, serverCode = "APP_ATTEST_INVALID"), 0).contains("code=APP_ATTEST_INVALID"))
    }
    @Test fun `route classification never returns dynamic path identifiers`() {
        assertEquals(DiagnosticRoute.ATTEST_REGISTER, diagnosticRoute("/prefix/api/v1/app-attest/android-keys"))
        assertEquals(DiagnosticRoute.STORY_GUIDE, diagnosticRoute("/api/v1/mobile/books/files/secret-book/story-guide"))
        assertEquals(DiagnosticRoute.DIRECT_AI, diagnosticRoute("/private-prefix/v1/chat/completions"))
        assertEquals(DiagnosticRoute.OTHER, diagnosticRoute("/private-account/private-book"))
    }
}
