package com.bookorbit.feature.bookdrop

import com.bookorbit.R
import com.bookorbit.core.model.BookDockFinalizeFileResult
import com.bookorbit.core.model.BookDockFinalizeResult
import com.bookorbit.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Messages are [UiText], so these assert on which resource is chosen (and its arguments), not on English wording. */
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
        assertEquals(UiText.of(R.string.finalize_err_unreachable), FinalizeErrors.describe(null))
    }

    @Test
    fun `EACCES is explained as a folder permission problem`() {
        assertEquals(UiText.of(R.string.finalize_err_permission), FinalizeErrors.describe(result(1, "EACCES")))
    }

    @Test
    fun `permission denied wording is recognised too`() {
        assertEquals(UiText.of(R.string.finalize_err_permission), FinalizeErrors.describe(result(1, "Permission denied")))
    }

    @Test
    fun `out of disk space is named`() {
        assertEquals(UiText.of(R.string.finalize_err_disk_full), FinalizeErrors.describe(result(1, "ENOSPC: no space left")))
    }

    @Test
    fun `an unknown server message is passed through as an argument`() {
        assertEquals(UiText.of(R.string.finalize_err_reason, "Unsupported format"), FinalizeErrors.describe(result(1, "Unsupported format")))
    }

    @Test
    fun `a failure without a message still says something useful`() {
        assertEquals(UiText.of(R.string.finalize_err_generic), FinalizeErrors.describe(result(1, null)))
    }
}
