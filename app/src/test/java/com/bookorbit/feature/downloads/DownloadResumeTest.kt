package com.bookorbit.feature.downloads

import com.bookorbit.feature.downloads.DownloadResume.FileStart
import com.bookorbit.feature.downloads.DownloadResume.WriteMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** Resuming an interrupted download instead of starting the file over. */
class DownloadResumeTest {

    @Test
    fun `no partial file starts fresh`() {
        assertEquals(FileStart.Fresh, DownloadResume.plan(existingBytes = null, expectedBytes = 1_000))
        assertEquals(FileStart.Fresh, DownloadResume.plan(existingBytes = 0, expectedBytes = 1_000))
    }

    @Test
    fun `a partial file resumes where it stopped`() {
        assertEquals(FileStart.Resume(400), DownloadResume.plan(existingBytes = 400, expectedBytes = 1_000))
    }

    @Test
    fun `a full-size file is complete`() {
        assertEquals(FileStart.Complete, DownloadResume.plan(existingBytes = 1_000, expectedBytes = 1_000))
    }

    @Test
    fun `a file larger than expected is corrupt and starts fresh`() {
        assertEquals(FileStart.Fresh, DownloadResume.plan(existingBytes = 1_500, expectedBytes = 1_000))
    }

    @Test
    fun `with no known size an existing file counts as complete, as before`() {
        assertEquals(FileStart.Complete, DownloadResume.plan(existingBytes = 400, expectedBytes = null))
        assertEquals(FileStart.Complete, DownloadResume.plan(existingBytes = 400, expectedBytes = 0))
    }

    @Test
    fun `range header asks for the rest of the file`() {
        assertEquals("bytes=400-", DownloadResume.rangeHeader(400))
    }

    @Test
    fun `a matching partial response is appended`() {
        assertEquals(WriteMode.APPEND, DownloadResume.writeMode(requestedOffset = 400, responseCode = 206, contentRange = "bytes 400-999/1000"))
    }

    @Test
    fun `a server that ignores the range sends the whole file, which overwrites`() {
        assertEquals(WriteMode.OVERWRITE, DownloadResume.writeMode(requestedOffset = 400, responseCode = 200, contentRange = null))
    }

    @Test
    fun `a partial response for the wrong offset overwrites rather than corrupting the file`() {
        assertEquals(WriteMode.OVERWRITE, DownloadResume.writeMode(requestedOffset = 400, responseCode = 206, contentRange = "bytes 0-999/1000"))
        assertEquals(WriteMode.OVERWRITE, DownloadResume.writeMode(requestedOffset = 400, responseCode = 206, contentRange = null))
    }

    @Test
    fun `a fresh download always overwrites`() {
        assertEquals(WriteMode.OVERWRITE, DownloadResume.writeMode(requestedOffset = 0, responseCode = 200, contentRange = null))
    }
}
