package com.bookorbit.feature.bookdrop

import com.bookorbit.core.model.BookDockFinalizeResult

/**
 * Turns a finalize result into something worth showing. The server answers 201 even when filing a
 * file fails, reporting the failure only inside the body, so the caller has to look.
 */
object FinalizeErrors {
    /** A user-facing reason the approval failed, or null if every file went through. */
    fun describe(result: BookDockFinalizeResult?): String? {
        if (result == null) return "Couldn't reach the server. Check your connection and try again."
        if (result.failed <= 0) return null
        val raw = result.results.firstOrNull { !it.success }?.message?.trim().orEmpty()
        return when {
            raw.contains("EACCES", ignoreCase = true) || raw.contains("permission denied", ignoreCase = true) ->
                "The server can't write to that library folder (permission denied). " +
                    "Check the folder's permissions on the server, then try again."
            raw.contains("ENOSPC", ignoreCase = true) -> "The server is out of disk space."
            raw.isNotEmpty() -> "Couldn't add this book to the library: $raw"
            else -> "Couldn't add this book to the library. Check the server logs for details."
        }
    }
}
