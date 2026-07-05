package com.bookorbit.feature.player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Thin ViewModel exposing the shared [PlayerManager] to the player screen, mini-player, and detail. */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val manager: PlayerManager,
) : ViewModel() {
    val state = manager.state

    fun loadAndPlay(bookId: Int) = manager.loadAndPlay(bookId)
    fun refreshIfStale() = manager.refreshIfStale()
    fun togglePlay() = manager.togglePlay()
    fun skipBack() = manager.skipBack()
    fun skipForward() = manager.skipForward()
    fun seekToAbsolute(absoluteSec: Double) = manager.seekToAbsolute(absoluteSec)
    fun setSpeed(value: Float) = manager.setSpeed(value)
    fun stop() = manager.stop()
}
