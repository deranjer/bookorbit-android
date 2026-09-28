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

    enum class WriteMode { APPEND, OVERWRITE, RESTART }

    /**
     * Where to start a file given what's already on disk. Without an expected size we cannot tell
     * a completed file from an interrupted one, so download it again rather than keeping a partial.
     */
    fun plan(existingBytes: Long?, expectedBytes: Long?): FileStart {
        if (existingBytes == null || existingBytes <= 0) return FileStart.Fresh
        if (expectedBytes == null || expectedBytes <= 0) return FileStart.Fresh
        return when {
            existingBytes == expectedBytes -> FileStart.Complete
            existingBytes < expectedBytes -> FileStart.Resume(existingBytes)
            else -> FileStart.Fresh
        }
    }

    fun rangeHeader(offset: Long): String = "bytes=$offset-"

    /**
     * Only a matching 206 may be appended. A 200 contains the whole file and may overwrite it.
     * A mismatched 206 contains only part of a file, so it must trigger a fresh request; writing it
     * from byte zero would leave a truncated file that the worker could mark complete.
     */
    fun writeMode(requestedOffset: Long, responseCode: Int, contentRange: String?): WriteMode = when {
        responseCode == 200 -> WriteMode.OVERWRITE
        requestedOffset > 0 && responseCode == 206 && isCompleteRemainder(requestedOffset, contentRange) -> WriteMode.APPEND
        responseCode == 206 || responseCode == 416 -> WriteMode.RESTART
        else -> throw IllegalArgumentException("Unexpected HTTP status $responseCode")
    }

    private fun isCompleteRemainder(offset: Long, contentRange: String?): Boolean {
        val match = contentRange?.let { CONTENT_RANGE.matchEntire(it) } ?: return false
        val start = match.groupValues[1].toLongOrNull() ?: return false
        val end = match.groupValues[2].toLongOrNull() ?: return false
        val total = match.groupValues[3].toLongOrNull() ?: return false
        return start == offset && total > offset && end == total - 1
    }

    private val CONTENT_RANGE = Regex("""bytes (\d+)-(\d+)/(\d+)""")
}
