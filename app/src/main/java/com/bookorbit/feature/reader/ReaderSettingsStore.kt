package com.bookorbit.feature.reader

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.readerDataStore by preferencesDataStore("reader_settings")

/**
 * Reader settings on the device: the defaults for every book, and each book's own changes on top
 * (keyed by book file, as the server keys them). With sync on this is the offline copy of the
 * server's rows; with it off it is the only copy.
 */
@Singleton
class ReaderSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val key = stringPreferencesKey("settings")
    private val pagingHintSeenKey = booleanPreferencesKey("paging_hint_seen")
    private fun bookKey(fileId: Int) = stringPreferencesKey("book_$fileId")

    /** The defaults every book starts from. */
    suspend fun load(): ReaderSettings {
        val raw = context.readerDataStore.data.first()[key] ?: return ReaderSettings()
        return runCatching { json.decodeFromString<ReaderSettings>(raw) }.getOrDefault(ReaderSettings())
    }

    suspend fun save(settings: ReaderSettings) {
        context.readerDataStore.edit { it[key] = json.encodeToString(ReaderSettings.serializer(), settings) }
    }

    /** Only the fields changed for this book, as the server stores them; null when it has none. */
    suspend fun loadBookDelta(fileId: Int): JsonObject? {
        val raw = context.readerDataStore.data.first()[bookKey(fileId)] ?: return null
        return runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
    }

    suspend fun saveBookDelta(fileId: Int, delta: JsonObject?) {
        context.readerDataStore.edit {
            if (delta == null || delta.isEmpty()) it.remove(bookKey(fileId)) else it[bookKey(fileId)] = delta.toString()
        }
    }

    /** Whether the one-time paginated tap-zones coach overlay has already been shown. */
    suspend fun hasSeenPagingHint(): Boolean =
        context.readerDataStore.data.first()[pagingHintSeenKey] ?: false

    suspend fun markPagingHintSeen() {
        context.readerDataStore.edit { it[pagingHintSeenKey] = true }
    }
}
