package com.bookorbit.feature.reader

import android.util.Log
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.ReaderStorageModeRequest
import com.bookorbit.core.network.ApiService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** The server's format group for every format the foliate reader opens. */
private const val EPUB_GROUP = "epub"

private const val TAG = "ReaderPrefs"

/**
 * Reader settings, layered as the web reader layers them: built-in defaults, then the user's
 * defaults for EPUB-like books, then this book's own changes. When the user keeps reader settings
 * on the server (`syncReaderPreferences`, the web's "Sync across devices"), the two upper layers are
 * the server's `reader/defaults` and `reader/preferences` rows, so a change here shows in the web
 * reader and the reverse. Otherwise they stay on the device. Writes send only the fields that changed
 * (PATCH), as the web does, so two clients editing different fields don't undo each other.
 */
@Singleton
class ReaderPreferencesRepository @Inject constructor(
    private val api: ApiService,
    private val store: ReaderSettingsStore,
    private val session: SessionManager,
) {
    // Every field written out, nulls included: a null letterSpacing means "the book's own" and must
    // replace a stored number, not be skipped.
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** What the reader opens with, and whether changes go to the server. */
    data class Loaded(val settings: ReaderSettings, val bookCustomized: Boolean, val sync: Boolean)

    /** The user's sync choice, fresh from the server when it can be reached. */
    suspend fun syncEnabled(): Boolean =
        runCatching { api.getMe().settings?.syncReaderPreferences == true }
            .getOrElse { session.currentUser?.settings?.syncReaderPreferences == true }

    suspend fun load(fileId: Int): Loaded {
        val sync = syncEnabled()
        var defaults = store.load()
        var delta = store.loadBookDelta(fileId)
        if (sync) {
            // Offline, the last copy of the server's rows stands in.
            runCatching { api.getReaderDefaults()[EPUB_GROUP] }
                .onSuccess { row -> if (row != null) defaults = overlay(defaults, row).also { store.save(it) } }
                .onFailure { Log.w(TAG, "defaults unavailable", it) }
            runCatching { api.getReaderPreference(fileId).settings }
                .onSuccess { row -> delta = row; store.saveBookDelta(fileId, row) }
                .onFailure { Log.w(TAG, "book preference unavailable", it) }
        }
        val current = delta
        return Loaded(
            settings = if (current != null) overlay(defaults, current) else defaults,
            bookCustomized = current != null && current.isNotEmpty(),
            sync = sync,
        )
    }

    /** Records [next] as this book's settings: the fields that differ from [previous] join its own changes. */
    suspend fun updateBook(fileId: Int, previous: ReaderSettings, next: ReaderSettings, sync: Boolean) {
        val changed = diff(previous, next)
        if (changed.isEmpty()) return
        val delta = JsonObject((store.loadBookDelta(fileId) ?: JsonObject(emptyMap())) + changed)
        store.saveBookDelta(fileId, delta)
        if (sync) send { api.patchReaderPreference(fileId, buildJsonObject { put("set", changed) }) }
    }

    /** Drops this book's own changes; it reads with the defaults again. */
    suspend fun resetBook(fileId: Int, sync: Boolean): ReaderSettings {
        store.saveBookDelta(fileId, null)
        if (sync) send { api.deleteReaderPreference(fileId) }
        return store.load()
    }

    /** Makes [settings] the defaults for every book, and this one's own changes moot. */
    suspend fun saveAsDefaults(fileId: Int, settings: ReaderSettings, sync: Boolean) {
        val previous = store.load()
        store.save(settings)
        store.saveBookDelta(fileId, null)
        if (sync) {
            val changed = diff(previous, settings)
            if (changed.isNotEmpty()) send { api.patchReaderDefaults(EPUB_GROUP, buildJsonObject { put("set", changed) }) }
            send { api.deleteReaderPreference(fileId) }
        }
    }

    /** Turns server sync on or off for the account, as the web's reader settings page does. */
    suspend fun setSync(enabled: Boolean): Boolean =
        runCatching { api.setReaderStorageMode(ReaderStorageModeRequest(enabled)) }.isSuccess

    private suspend fun send(call: suspend () -> Unit) {
        runCatching { call() }.onFailure { Log.w(TAG, "reader settings not saved to the server", it) }
    }

    private fun encode(settings: ReaderSettings): JsonObject =
        json.encodeToJsonElement(ReaderSettings.serializer(), settings).jsonObject

    /** The fields of [next] that differ from [previous]. */
    internal fun diff(previous: ReaderSettings, next: ReaderSettings): JsonObject {
        val before = encode(previous)
        return JsonObject(encode(next).filter { (k, v) -> before[k] != v })
    }

    /**
     * [base] with every field of [row] this app knows laid over it. A field that doesn't fit (a type
     * another client wrote differently) is skipped rather than failing the whole row.
     */
    internal fun overlay(base: ReaderSettings, row: JsonObject): ReaderSettings {
        var result = base
        for ((k, v) in row) {
            if (v is JsonArray) continue
            val merged = encode(result).toMutableMap().apply { if (containsKey(k)) put(k, v) }
            result = runCatching { json.decodeFromJsonElement(ReaderSettings.serializer(), JsonObject(merged)) }
                .getOrDefault(result)
        }
        return result.sanitized()
    }
}

