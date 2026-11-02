package com.bookorbit.feature.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Saves a reader's position at most once per [intervalMs] while pages turn, and never drops the last
 * one. A turn inside the interval is held and saved when the interval runs out, or sooner by [flush]
 * when the reader closes or goes to the background. [save] must not depend on the reader's own scope,
 * which is already cancelled by the time `onCleared` flushes.
 */
class ProgressSaver<T : Any>(
    private val scope: CoroutineScope,
    private val intervalMs: Long = 2_000L,
    private val clock: () -> Long = System::currentTimeMillis,
    private val save: (T) -> Unit,
) {
    private var pending: T? = null
    private var lastSaved = Long.MIN_VALUE / 2
    private var trailing: Job? = null

    /** The position has moved to [position]. */
    fun offer(position: T) {
        pending = position
        val wait = lastSaved + intervalMs - clock()
        if (wait <= 0) flush()
        else if (trailing?.isActive != true) trailing = scope.launch {
            delay(wait)
            flush()
        }
    }

    /** Save the held position now, if there is one. */
    fun flush() {
        trailing?.cancel()
        trailing = null
        val position = pending ?: return
        pending = null
        lastSaved = clock()
        save(position)
    }
}
