package com.bookorbit.feature.bookdrop

import com.bookorbit.core.model.BookDockFinalizeFileResult
import com.bookorbit.core.model.BookDockFinalizeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalizeErrorsTest {
    private fun result(failed: Int, message: String?) = BookDockFinalizeResult(
        total = 1,
        succeeded = 1 - failed,
        failed = failed,
        results = listOf(BookDockFinalizeFileResult(fileId = 1, fileName = "a.cbr", success = failed == 0, message = message)),
    )

    @Test
    fun `no error when every file went through`() {
        assertNull(FinalizeErrors.describe(result(0, null)))
    }

    @Test
    fun `a missing result means the server was unreachable`() {
        assertTrue(FinalizeErrors.describe(null)!!.contains("reach the server"))
    }

    @Test
    fun `EACCES is explained as a folder permission problem`() {
        val text = FinalizeErrors.describe(result(1, "EACCES"))!!
        assertTrue(text.contains("permission denied"))
        assertTrue(text.contains("permissions on the server"))
    }

    @Test
    fun `out of disk space is named`() {
        assertEquals("The server is out of disk space.", FinalizeErrors.describe(result(1, "ENOSPC: no space left")))
    }

    @Test
    fun `an unknown server message is passed through`() {
        assertEquals("Couldn't add this book to the library: Unsupported format", FinalizeErrors.describe(result(1, "Unsupported format")))
    }

    @Test
    fun `a failure without a message still says something useful`() {
        assertTrue(FinalizeErrors.describe(result(1, null))!!.contains("server logs"))
    }
}
