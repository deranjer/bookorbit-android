package com.bookorbit.core.diagnostics

import com.bookorbit.core.network.AttestationProblem
import com.bookorbit.core.network.AttestationStage
import java.io.File
import java.time.Instant

enum class DiagnosticEvent { APP_STARTED, LOGGING_ENABLED, HTTP_COMPLETED, HTTP_FAILED, ATTESTATION_COMPLETED, ATTESTATION_FAILED, PLAYBACK_FAILED }
enum class DiagnosticRoute { AUTH, ATTEST_CHALLENGE, ATTEST_REGISTER, PHOTO_SCAN, READING_HELP, STORY_GUIDE, AUDIO, EPUB, DICTIONARY, DIRECT_AI, OTHER }
enum class DiagnosticMethod { GET, POST, PUT, PATCH, DELETE, HEAD, OTHER }
enum class DiagnosticFailure { TLS, TIMEOUT, CANCELLED, IO, OTHER }

data class DiagnosticEntry(val event: DiagnosticEvent, val route: DiagnosticRoute? = null,
    val method: DiagnosticMethod? = null, val status: Int? = null, val durationMs: Long? = null,
    val failure: DiagnosticFailure? = null, val stage: AttestationStage? = null,
    val problem: AttestationProblem? = null, val serverCode: String? = null, val playerCode: Int? = null)

/** No arbitrary messages, URLs, identifiers or exception strings can be written by this formatter. */
internal fun diagnosticLine(entry: DiagnosticEntry, now: Long): String = buildString {
    append(Instant.ofEpochMilli(now)).append(' ').append(entry.event.name)
    entry.route?.let { append(" route=").append(it.name) }
    entry.method?.let { append(" method=").append(it.name) }
    entry.status?.takeIf { it in 100..599 }?.let { append(" status=").append(it) }
    entry.durationMs?.let { append(" duration_ms=").append(it.coerceIn(0, 86_400_000)) }
    entry.failure?.let { append(" failure=").append(it.name) }
    entry.stage?.let { append(" stage=").append(it.name) }
    entry.problem?.let { append(" problem=").append(it.name) }
    entry.serverCode?.takeIf { it.matches(Regex("APP_ATTEST_[A-Z0-9_]{1,48}")) }?.let { append(" code=").append(it) }
    entry.playerCode?.takeIf { it in 0..100_000 }?.let { append(" player_code=").append(it) }
    append('\n')
}

internal class DiagnosticFiles(private val root: File, initialEnabled: Boolean = false,
    private val persist: (Boolean) -> Unit = {}, private val limit: Int = 256 * 1024) {
    private var enabled = initialEnabled
    private var generation = 0L
    private val current get() = File(root, "events.log")
    private val previous get() = File(root, "events.previous.log")

    @Synchronized fun isEnabled(): Boolean = enabled
    @Synchronized fun ticket(): Long? = generation.takeIf { enabled }
    @Synchronized fun setEnabled(value: Boolean) {
        persist(value)
        enabled = value; generation++
    }
    @Synchronized fun append(ticket: Long, entry: DiagnosticEntry, now: Long = System.currentTimeMillis()) {
        if (!enabled || ticket != generation) return
        val bytes = diagnosticLine(entry, now).toByteArray(Charsets.UTF_8)
        require(bytes.size <= limit)
        require(root.isDirectory || root.mkdirs())
        if (current.length() + bytes.size > limit) {
            if (previous.exists()) check(previous.delete())
            if (current.exists()) check(current.renameTo(previous))
        }
        current.appendBytes(bytes)
    }
    @Synchronized fun snapshot(): ByteArray = buildList {
        for (file in listOf(previous, current)) {
            if (file.isFile) {
                require(file.length() <= limit)
                add(file.readBytes())
            }
        }
    }.fold(ByteArray(0)) { total, bytes -> total + bytes }

    @Synchronized fun clear() {
        generation++
        for (file in listOf(previous, current)) if (file.exists()) check(file.delete())
    }
}
