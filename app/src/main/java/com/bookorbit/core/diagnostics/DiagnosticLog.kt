package com.bookorbit.core.diagnostics

import android.content.Context
import android.os.Build
import com.bookorbit.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticLog @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("diagnostic_logging", Context.MODE_PRIVATE)
    private val files = DiagnosticFiles(File(context.noBackupFilesDir, "diagnostics"), prefs.getBoolean("enabled", false),
        { check(prefs.edit().putBoolean("enabled", it).commit()) })
    private val mutableEnabled = MutableStateFlow(files.isEnabled())
    val enabled = mutableEnabled.asStateFlow()
    private val mutableFailed = MutableStateFlow(false)
    val failed = mutableFailed.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private sealed interface Work {
        data class Event(val ticket: Long, val entry: DiagnosticEntry, val time: Long) : Work
        data class Snapshot(val result: CompletableDeferred<ByteArray>) : Work
    }
    private val queue = Channel<Work>(256)

    init {
        scope.launch {
            for (work in queue) when (work) {
                is Work.Event -> runCatching { files.append(work.ticket, work.entry, work.time) }.onFailure { mutableFailed.value = true }
                is Work.Snapshot -> runCatching { files.snapshot() }.fold(work.result::complete, work.result::completeExceptionally)
            }
        }
    }

    fun record(entry: DiagnosticEntry) {
        val ticket = files.ticket() ?: return
        if (queue.trySend(Work.Event(ticket, entry, System.currentTimeMillis())).isFailure) mutableFailed.value = true
    }

    suspend fun setEnabled(value: Boolean) = withContext(Dispatchers.IO) {
        files.setEnabled(value); mutableEnabled.value = value; mutableFailed.value = false
        if (value) record(DiagnosticEntry(DiagnosticEvent.LOGGING_ENABLED))
    }

    suspend fun clear() = withContext(Dispatchers.IO) { files.clear(); mutableFailed.value = false }

    suspend fun export(): ByteArray {
        val result = CompletableDeferred<ByteArray>()
        queue.send(Work.Snapshot(result))
        val header = "BookOrbit Client diagnostics\nformat=1 app=${BuildConfig.VERSION_NAME} code=${BuildConfig.VERSION_CODE} android_api=${Build.VERSION.SDK_INT}\n" +
            "Structured app events only. No logcat, request bodies, URLs, account/book IDs or exception messages.\n\n"
        return header.toByteArray(Charsets.UTF_8) + result.await()
    }
    internal fun close() { queue.close(); scope.cancel() }
}
