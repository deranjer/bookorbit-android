package com.bookorbit.feature.reader

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.readerDataStore by preferencesDataStore("reader_settings")

/** Persists reader render settings in DataStore. */
@Singleton
class ReaderSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val key = stringPreferencesKey("settings")
    private val pagingHintSeenKey = booleanPreferencesKey("paging_hint_seen")

    suspend fun load(): ReaderSettings {
        val raw = context.readerDataStore.data.first()[key] ?: return ReaderSettings()
        return runCatching { json.decodeFromString<ReaderSettings>(raw) }.getOrDefault(ReaderSettings())
    }

    suspend fun save(settings: ReaderSettings) {
        context.readerDataStore.edit { it[key] = json.encodeToString(ReaderSettings.serializer(), settings) }
    }

    /** Whether the one-time paginated tap-zones coach overlay has already been shown. */
    suspend fun hasSeenPagingHint(): Boolean =
        context.readerDataStore.data.first()[pagingHintSeenKey] ?: false

    suspend fun markPagingHintSeen() {
        context.readerDataStore.edit { it[pagingHintSeenKey] = true }
    }
}
