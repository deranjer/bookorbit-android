package com.bookorbit.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class SeriesViewMode { LIST, GRID }

private val Context.appSettingsDataStore by preferencesDataStore("app_settings")

/**
 * App-level preferences (as opposed to the per-feature stores like ReaderSettingsStore /
 * AudioSettingsStore): appearance and download behavior configured from the Settings screen.
 * Exposed as Flows (rather than one-shot load()) since the Settings screen and theme root need to
 * react live as the user changes them.
 */
@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val wifiOnlyDownloadsKey = booleanPreferencesKey("wifi_only_downloads")
    private val seriesViewModeKey = stringPreferencesKey("series_view_mode")

    val themeMode = context.appSettingsDataStore.data.map { prefs ->
        prefs[themeModeKey]?.let { raw -> runCatching { ThemeMode.valueOf(raw) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appSettingsDataStore.edit { it[themeModeKey] = mode.name }
    }

    val wifiOnlyDownloads = context.appSettingsDataStore.data.map { it[wifiOnlyDownloadsKey] ?: false }

    suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[wifiOnlyDownloadsKey] = enabled }
    }

    val seriesViewMode = context.appSettingsDataStore.data.map { prefs ->
        prefs[seriesViewModeKey]?.let { raw -> runCatching { SeriesViewMode.valueOf(raw) }.getOrNull() } ?: SeriesViewMode.LIST
    }

    suspend fun setSeriesViewMode(mode: SeriesViewMode) {
        context.appSettingsDataStore.edit { it[seriesViewModeKey] = mode.name }
    }
}
