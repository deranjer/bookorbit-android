package com.bookorbit.feature.player

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

val SPEED_PRESETS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
const val MIN_SPEED = 0.5f
const val MAX_SPEED = 3.0f
const val DEFAULT_SPEED = 1.0f
const val DEFAULT_SKIP_BACK = 10
const val DEFAULT_SKIP_FORWARD = 30

fun clampSpeed(v: Float): Float = v.coerceIn(MIN_SPEED, MAX_SPEED)

data class AudioSettings(
    val speed: Float = DEFAULT_SPEED,
    val skipBackSeconds: Int = DEFAULT_SKIP_BACK,
    val skipForwardSeconds: Int = DEFAULT_SKIP_FORWARD,
)

private val Context.audioDataStore by preferencesDataStore("audio_settings")

@Singleton
class AudioSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val speedKey = doublePreferencesKey("speed")
    private val skipBackKey = intPreferencesKey("skip_back")
    private val skipForwardKey = intPreferencesKey("skip_forward")

    suspend fun load(): AudioSettings {
        val prefs = context.audioDataStore.data.first()
        return AudioSettings(
            speed = clampSpeed((prefs[speedKey] ?: DEFAULT_SPEED.toDouble()).toFloat()),
            skipBackSeconds = prefs[skipBackKey] ?: DEFAULT_SKIP_BACK,
            skipForwardSeconds = prefs[skipForwardKey] ?: DEFAULT_SKIP_FORWARD,
        )
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
}
