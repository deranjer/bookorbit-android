package com.bookorbit.feature.reader

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bookorbit.core.auth.DataScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ReadAlongPosition(
    val sectionIndex: Int,
    val fragment: String,
    val audioSrc: String,
    val audioSeconds: Double,
    val rate: Double = 1.0,
    val completed: Boolean = false,
) {
    fun valid() = sectionIndex in 0..100_000 && fragment.isNotBlank() && fragment.length <= 8192 &&
        audioSrc.isNotBlank() && audioSrc.length <= 8192 && audioSeconds.isFinite() && audioSeconds in 0.0..1e9 &&
        rate.isFinite() && rate in 0.5..2.0
}

data class ReadAlongOwner(val server: String, val userId: Int, val fileId: Int, val fingerprint: String) {
    fun key(): String {
        require(server.isNotBlank() && userId > 0 && fileId > 0 && fingerprint.isNotBlank())
        val identity = "${DataScope.key(server)}\n$userId\n$fileId"
        return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

private val Context.readAlongDataStore by preferencesDataStore("read_along_positions")

/** Local audio coordinate only: no extra reading sessions, percentages, server writes or inferred alignment. */
@Singleton
class ReadAlongPositionStore internal constructor(
    private val data: DataStore<Preferences>,
    private val json: Json,
    scope: CoroutineScope,
) {
    @Inject constructor(@ApplicationContext context: Context, json: Json) : this(
        context.readAlongDataStore, json, CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )
    @Serializable private data class Saved(val fingerprint: String, val position: ReadAlongPosition)
    private val latest = ConcurrentHashMap<String, Saved>()
    private val writes = Channel<Pair<String, Saved>>(Channel.UNLIMITED)

    init {
        // App-owned serial writer survives the reader ViewModel being cleared. One update per second.
        scope.launch {
            for ((key, saved) in writes) {
                try {
                    data.edit { it[stringPreferencesKey(key)] = json.encodeToString(Saved.serializer(), saved) }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: java.io.IOException) { /* Keep the in-memory position; a later update retries storage. */ }
            }
        }
    }

    suspend fun load(owner: ReadAlongOwner): ReadAlongPosition? {
        val key = owner.key()
        val saved = latest[key] ?: run {
            val raw = try { data.data.first()[stringPreferencesKey(key)] } catch (_: java.io.IOException) { null }
            latest[key] ?: raw?.let { runCatching { json.decodeFromString<Saved>(it) }.getOrNull() }
        }
        return saved?.takeIf { it.fingerprint == owner.fingerprint }?.position?.takeIf { it.valid() }
    }

    fun record(owner: ReadAlongOwner, position: ReadAlongPosition) {
        if (!position.valid()) return
        val key = owner.key()
        val saved = Saved(owner.fingerprint, position)
        latest[key] = saved
        writes.trySend(key to saved)
    }
}
