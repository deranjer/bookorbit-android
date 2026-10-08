package com.bookorbit.feature.bookdrop

import com.bookorbit.R
import com.bookorbit.ui.UiText
import com.bookorbit.core.model.BookDockFinalizeResult

/**
 * Turns a finalize result into something worth showing. The server answers 201 even when filing a
 * file fails, reporting the failure only inside the body, so the caller has to look.
 */
object FinalizeErrors {
    /** A user-facing reason the approval failed, or null if every file went through. */
    fun describe(result: BookDockFinalizeResult?): UiText? {
        if (result == null) return UiText.of(R.string.finalize_err_unreachable)
        if (result.failed <= 0) return null
        val raw = result.results.firstOrNull { !it.success }?.message?.trim().orEmpty()
        return when {
            raw.contains("EACCES", ignoreCase = true) || raw.contains("permission denied", ignoreCase = true) ->
                UiText.of(R.string.finalize_err_permission)
            raw.contains("ENOSPC", ignoreCase = true) -> UiText.of(R.string.finalize_err_disk_full)
            raw.isNotEmpty() -> UiText.of(R.string.finalize_err_reason, raw)
            else -> UiText.of(R.string.finalize_err_generic)
        }
    }
}
