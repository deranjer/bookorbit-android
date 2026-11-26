package com.bookorbit.feature.reader

import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** In-process service port: no credentials, media URLs or queues cross a public intent. */
@Singleton
class NativeNarrationSession @Inject constructor() {
    data class State(val owner: ReadAlongOwner, val bookId: Int, val event: ReaderEvent.ReadAlong)
    internal interface Port {
        fun start(queue: NativeNarrationQueue, owner: ReadAlongOwner, saved: ReadAlongPosition?, section: Int)
        fun command(action: String, rate: Double?)
        fun listenFrom(position: ReadAlongPosition): Boolean = false
        fun leave()
    }
    private var port: Port? = null
    @Volatile private var generation = 0L
    internal fun generation() = generation
    private val mutableState = MutableStateFlow<State?>(null)
    val state = mutableState.asStateFlow()

    @MainThread internal fun bind(port: Port) { this.port = port }
    @MainThread internal fun unbind(port: Port) { if (this.port === port) { this.port = null; mutableState.value = null; generation++ } }
    @MainThread internal fun ticket(): Long = ++generation
    @MainThread internal fun isCurrent(ticket: Long) = ticket == generation
    @MainThread internal fun start(ticket: Long, queue: NativeNarrationQueue, owner: ReadAlongOwner, saved: ReadAlongPosition?, section: Int): Boolean {
        if (!isCurrent(ticket)) { queue.plan.close(); return false }
        val target = port ?: run { queue.plan.close(); return false }
        try { target.start(queue, owner, saved, section) }
        catch (e: Exception) { target.leave(); queue.plan.close(); throw e }
        return true
    }
    @MainThread internal fun publish(state: State?) { mutableState.value = state }
    @MainThread fun command(owner: ReadAlongOwner, action: String, rate: Double? = null): Boolean {
        if (mutableState.value?.owner != owner) return false
        port?.command(action, rate) ?: return false
        return true
    }
    @MainThread internal fun listenFrom(owner: ReadAlongOwner, position: ReadAlongPosition): Boolean {
        if (mutableState.value?.owner != owner) return false
        val accepted = port?.listenFrom(position) ?: false
        if (accepted) generation++
        return accepted
    }
    @MainThread internal fun leaveForAudiobook() { generation++; port?.leave(); mutableState.value = null }
}
