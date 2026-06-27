package com.bookorbit.feature.reader.pdf

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pdfReaderDataStore by preferencesDataStore("pdf_reader_settings")

/** Persists native PDF reader render settings in DataStore (mirrors ReaderSettingsStore). */
@Singleton
class PdfReaderSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val key = stringPreferencesKey("settings")

    suspend fun load(): PdfReaderSettings {
        val raw = context.pdfReaderDataStore.data.first()[key] ?: return PdfReaderSettings()
        return runCatching { json.decodeFromString<PdfReaderSettings>(raw) }.getOrDefault(PdfReaderSettings())
    }

    suspend fun save(settings: PdfReaderSettings) {
        context.pdfReaderDataStore.edit { it[key] = json.encodeToString(PdfReaderSettings.serializer(), settings) }
    }
}
