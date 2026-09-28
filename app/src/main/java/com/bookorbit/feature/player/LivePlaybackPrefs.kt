package com.bookorbit.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Always-current audio settings for code that must read them synchronously (the session's
 * [BookAggregatingPlayer] reads mode and skip increments on every position query).
 */
@Singleton
class LivePlaybackPrefs @Inject constructor(store: AudioSettingsStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings: StateFlow<AudioSettings> = store.settings
        .catch { emit(AudioSettings()) }
        .stateIn(scope, SharingStarted.Eagerly, AudioSettings())
}
