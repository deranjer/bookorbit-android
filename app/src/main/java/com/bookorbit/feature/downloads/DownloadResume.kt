package com.bookorbit.feature.downloads

/**
 * Decisions for resuming an interrupted file download with an HTTP `Range` request instead of
 * starting it over - a multi-GB audiobook file that restarts from zero after every interruption may
 * never finish.
 */
object DownloadResume {

    sealed interface FileStart {
        /** Already fully downloaded - skip it. */
        data object Complete : FileStart
        /** Nothing usable on disk - download from the start. */
        data object Fresh : FileStart
        /** [offset] bytes already on disk - request the rest. */
        data class Resume(val offset: Long) : FileStart
    }

    enum class WriteMode { APPEND, OVERWRITE }

    /**
     * Where to start a file given what's already on disk. With no known expected size an existing
     * file counts as complete (the previous behavior - there's nothing to verify it against).
     */
    fun plan(existingBytes: Long?, expectedBytes: Long?): FileStart {
        if (existingBytes == null || existingBytes <= 0) return FileStart.Fresh
        if (expectedBytes == null || expectedBytes <= 0) return FileStart.Complete
        return when {
            existingBytes == expectedBytes -> FileStart.Complete
            existingBytes < expectedBytes -> FileStart.Resume(existingBytes)
            else -> FileStart.Fresh
        }
    }

    fun rangeHeader(offset: Long): String = "bytes=$offset-"

    /**
     * Whether a response continues the partial file. Only a 206 whose Content-Range starts at the
     * requested offset is appended; anything else (a server that ignored the range and sent the whole
     * file, or a mismatched range) overwrites from the start rather than corrupting the file.
     */
    fun writeMode(requestedOffset: Long, responseCode: Int, contentRange: String?): WriteMode =
        if (requestedOffset > 0 && responseCode == 206 && contentRange?.startsWith("bytes $requestedOffset-") == true) {
            WriteMode.APPEND
        } else {
            WriteMode.OVERWRITE
        }
}
