package com.bookorbit.feature.reader

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReadAlongPositionStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val owner = ReadAlongOwner("https://books.test", 3, 17, "sha256-of-exact-bytes")
    private val position = ReadAlongPosition(1, "chapter2.xhtml#sentence7", "audio/ch02.mp3", 37.125, 1.25)

    @Test fun `audio position is scoped to exact server account edition and actual file fingerprint`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { temp.newFolder().resolve("positions.preferences_pb") })
            val store = ReadAlongPositionStore(data, Json, scope)
            store.record(owner, position)
            assertEquals(position, store.load(owner))
            assertNull(store.load(owner.copy(server = "https://another.test")))
            assertNull(store.load(owner.copy(userId = 4)))
            assertNull(store.load(owner.copy(fileId = 18)))
            assertNull(store.load(owner.copy(fingerprint = "changed-file-bytes")))
            withTimeout(5_000) { data.data.first { it[stringPreferencesKey(owner.key())] != null } }
            // Simulate a fresh repository with no in-memory state.
            assertEquals(position, ReadAlongPositionStore(data, Json, scope).load(owner))
        } finally { scope.cancel() }
    }

    @Test fun `serial writer preserves newest checkpoint and does not overwrite text progress`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val file = temp.newFolder().resolve("positions.preferences_pb")
            val data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val store = ReadAlongPositionStore(data, Json, scope)
            repeat(20) { store.record(owner, position.copy(audioSeconds = it + .125)) }
            withTimeout(5_000) { data.data.first { it[stringPreferencesKey(owner.key())]?.contains("19.125") == true } }
            assertEquals(19.125, ReadAlongPositionStore(data, Json, scope).load(owner)!!.audioSeconds, 0.0)
            assertEquals(setOf(owner.key()), data.data.first().asMap().keys.map { it.name }.toSet())
        } finally { scope.cancel() }
    }

    @Test fun `invalid checkpoints are ignored and bridge parsing cannot inject invalid clock values`() {
        assertTrue(position.valid())
        for (bad in listOf(position.copy(audioSeconds = Double.NaN), position.copy(audioSeconds = -1.0),
            position.copy(sectionIndex = -1), position.copy(rate = 99.0), position.copy(audioSrc = ""))) assertFalse(bad.valid())
        val event = ReaderBridge.parseEvent("""{"type":"readAlong","available":true,"state":"paused","position":{"sectionIndex":0,"fragment":"c#1","audioSrc":"a.mp3","audioSeconds":-1}}""") as ReaderEvent.ReadAlong
        assertNull(event.position)
    }

    @Test fun `0_9_5 checkpoint remains readable and other content revisions cannot overwrite it`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { temp.newFolder().resolve("positions.preferences_pb") })
            val raw = """{"fingerprint":"${owner.fingerprint}","position":${Json.encodeToString(ReadAlongPosition.serializer(), position)}}"""
            data.edit { it[stringPreferencesKey(owner.legacyKey())] = raw }
            val store = ReadAlongPositionStore(data, Json, scope)
            assertEquals(position, store.load(owner))
            val revision = owner.copy(fingerprint = "stream-v1:other-revision")
            store.record(revision, position.copy(audioSeconds = 5.5))
            assertEquals(position, store.load(owner))
            assertEquals(5.5, store.load(revision)!!.audioSeconds, 0.0)
        } finally { scope.cancel() }
    }
}
