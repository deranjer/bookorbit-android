package com.bookorbit.feature.reader

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Only publish complete files. A failed request must never become a reusable reader cache. */
object ReaderFileCache {
    private val mutex = Mutex()

    suspend fun getOrFetch(dest: File, expectedSize: Long?, fetch: suspend (File) -> Unit): File = mutex.withLock {
        fun valid(file: File) = file.isFile && file.length() > 0 &&
            (expectedSize == null || expectedSize <= 0 || file.length() == expectedSize)
        if (valid(dest)) return@withLock dest
        check(dest.parentFile!!.isDirectory || dest.parentFile!!.mkdirs())
        val partial = File.createTempFile("reader-", ".part", dest.parentFile)
        try {
            fetch(partial)
            check(valid(partial)) { "Incomplete reading file. Please retry." }
            Files.move(partial.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            dest
        } finally {
            partial.delete()
        }
    }
}
