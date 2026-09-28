package com.bookorbit.feature.player

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val SPEED_PRESETS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
const val MIN_SPEED = 0.5f
const val MAX_SPEED = 3.0f
const val DEFAULT_SPEED = 1.0f
const val DEFAULT_SKIP_BACK = 10
const val DEFAULT_SKIP_FORWARD = 30

fun clampSpeed(v: Float): Float = v.coerceIn(MIN_SPEED, MAX_SPEED)

/** What the progress bar (in-app and on every media-session surface) measures. */
enum class ProgressBarMode {
    CHAPTER, BOOK;

    companion object {
        fun parse(raw: String?): ProgressBarMode = entries.firstOrNull { it.name == raw } ?: BOOK
    }
}

data class AudioSettings(
    val speed: Float = DEFAULT_SPEED,
    val skipBackSeconds: Int = DEFAULT_SKIP_BACK,
    val skipForwardSeconds: Int = DEFAULT_SKIP_FORWARD,
    val progressBarMode: ProgressBarMode = ProgressBarMode.BOOK,
)

private val Context.audioDataStore by preferencesDataStore("audio_settings")

@Singleton
class AudioSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val speedKey = doublePreferencesKey("speed")
    private val skipBackKey = intPreferencesKey("skip_back")
    private val skipForwardKey = intPreferencesKey("skip_forward")
    private val progressBarModeKey = stringPreferencesKey("progress_bar_mode")

    val settings: Flow<AudioSettings> = context.audioDataStore.data.map { prefs ->
        prefs.toAudioSettings()
    }

    private fun Preferences.toAudioSettings(): AudioSettings {
        return AudioSettings(
            speed = clampSpeed((this[speedKey] ?: DEFAULT_SPEED.toDouble()).toFloat()),
            skipBackSeconds = this[skipBackKey] ?: DEFAULT_SKIP_BACK,
            skipForwardSeconds = this[skipForwardKey] ?: DEFAULT_SKIP_FORWARD,
            progressBarMode = ProgressBarMode.parse(this[progressBarModeKey]),
        )
    }

    suspend fun load(): AudioSettings {
        return settings.first()
    }

    suspend fun saveSpeed(value: Float) {
        context.audioDataStore.edit { it[speedKey] = clampSpeed(value).toDouble() }
    }

    suspend fun saveSkipBack(value: Int) {
        context.audioDataStore.edit { it[skipBackKey] = value }
    }

    suspend fun saveSkipForward(value: Int) {
        context.audioDataStore.edit { it[skipForwardKey] = value }
    }

    suspend fun saveProgressBarMode(mode: ProgressBarMode) {
        context.audioDataStore.edit { it[progressBarModeKey] = mode.name }
    }
}
