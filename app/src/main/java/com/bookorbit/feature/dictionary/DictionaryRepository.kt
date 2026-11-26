package com.bookorbit.feature.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException

data class InstalledDictionary(val pack: DictionaryPackage, val title: String, val notice: String, val diskBytes: Long)

/** Public dictionary transport is intentionally independent of BookOrbit credentials and cookies. */
@Singleton
class DictionaryRepository internal constructor(private val root: File, private val client: OkHttpClient,
    private val bootstrap: () -> List<DictionaryPackage> = { BUNDLED_DICTIONARY_CATALOG }) {
    @Inject constructor(@ApplicationContext context: Context, diagnostics: com.bookorbit.core.diagnostics.DiagnosticLog? = null) : this(File(context.filesDir, "dictionaries"),
        OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .apply { if (diagnostics != null) addInterceptor(com.bookorbit.core.diagnostics.DiagnosticInterceptor(diagnostics)) }
            .retryOnConnectionFailure(false).callTimeout(120, TimeUnit.SECONDS).build(),
        { context.assets.open("dictionaries/catalog.json").bufferedReader().use { dictionaryCatalog(it.readText(), Json) } })
    init { require(root.isDirectory || root.mkdirs()) }
    private val json = Json { ignoreUnknownKeys = true }
    private val installMutex = Mutex()
    private val databaseMutex = Mutex()
    private val catalogMutex = Mutex()

    /** Metadata only. Opening settings never downloads a dictionary or refreshes over the network. */
    suspend fun availableCatalog(): List<DictionaryPackage> = withContext(Dispatchers.IO) {
        catalogMutex.withLock {
            val cached = File(root, "catalog.json")
            runCatching {
                require(cached.length() in 1..2_097_152)
                json.decodeFromString<List<DictionaryPackage>>(cached.readText()).also { packs ->
                    require(packs.isNotEmpty() && packs.size <= 2000 && packs.map { it.id }.distinct().size == packs.size)
                    packs.forEach { it.validate() }
                }
            }.getOrElse { bootstrap() }
        }
    }

    suspend fun catalog(): List<DictionaryPackage> = withContext(Dispatchers.IO) {
        withResponse("https://freedict.org/freedict-database.json") { result ->
            require(result.isSuccessful)
            val source = requireNotNull(result.body).source()
            source.request(2_097_153)
            require(source.buffer.size <= 2_097_152)
            val packs = dictionaryCatalog(source.readUtf8(), json)
            catalogMutex.withLock {
                val pending = File(root, "catalog-${UUID.randomUUID()}.pending")
                try {
                    pending.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(DictionaryPackage.serializer()), packs))
                    currentCoroutineContext().ensureActive()
                    Files.move(pending.toPath(), File(root, "catalog.json").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } finally { pending.delete() }
            }
            packs
        }
    }

    suspend fun installed(): List<InstalledDictionary> = withContext(Dispatchers.IO) {
        databaseMutex.withLock { root.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".sqlite") && validDictionaryId(it.name.removeSuffix(".sqlite")) }
            .sortedBy { it.name }.mapNotNull { file ->
            val id = file.name.removeSuffix(".sqlite")
            if (!file.isFile) null else runCatching {
                open(file).use { db ->
                    fun value(key: String) = db.rawQuery("SELECT value FROM metadata WHERE name = ?", arrayOf(key)).use {
                        require(it.moveToFirst()); it.getString(0)
                    }
                    InstalledDictionary(json.decodeFromString<DictionaryPackage>(value("package")).also { it.validate(); require(it.id == id) },
                        value("title"), value("notice"), file.length())
                }
            }.getOrNull()
        } }
    }

    suspend fun install(pack: DictionaryPackage, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        pack.validate()
        installMutex.withLock {
            // Only our UUID staging files can remain after process death. Never touch installed data.
            root.listFiles().orEmpty().filter { it.name.matches(Regex("[a-f0-9-]{36}\\.(download|pending)(-journal)?")) }.forEach { it.delete() }
            val archive = File(root, "${UUID.randomUUID()}.download")
            try {
                withResponse(pack.url) { result ->
                    require(result.isSuccessful)
                    val body = requireNotNull(result.body)
                    require(body.contentLength() == -1L || body.contentLength() == pack.bytes)
                    body.byteStream().use { input -> archive.outputStream().use { output ->
                        val buffer = ByteArray(32768)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= pack.bytes)
                            output.write(buffer, 0, read)
                            progress(total, pack.bytes)
                        }
                        require(total == pack.bytes)
                    } }
                }
                installArchive(pack, archive)
            } finally { archive.delete() }
        }
    }

    /** Internal test seam; the same checksum and publication gates apply to downloaded archives. */
    internal suspend fun installArchive(pack: DictionaryPackage, archive: File) = withContext(Dispatchers.IO) {
        pack.validate()
        require(archive.length() == pack.bytes)
        val digest = MessageDigest.getInstance("SHA-512")
        archive.inputStream().use { input ->
            val buffer = ByteArray(32768)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == pack.sha512)
        val pending = File(root, "${UUID.randomUUID()}.pending")
        try {
            SQLiteDatabase.openOrCreateDatabase(pending, null).use { db ->
                db.execSQL("PRAGMA page_size = 4096")
                db.rawQuery("PRAGMA max_page_count = 131072", null).use { require(it.moveToFirst() && it.getLong(0) == 131072L) }
                db.rawQuery("PRAGMA journal_mode = DELETE", null).use { require(it.moveToFirst() && it.getString(0).equals("delete", true)) }
                db.execSQL("CREATE TABLE metadata(name TEXT PRIMARY KEY, value TEXT NOT NULL)")
                db.execSQL("CREATE TABLE entries(word TEXT NOT NULL, definition TEXT NOT NULL)")
                db.beginTransaction()
                try {
                    val job = currentCoroutineContext()
                    var indexed = 0L
                    var rows = 0
                    val license = db.compileStatement("INSERT INTO entries(word, definition) VALUES(?, ?)").use { insert ->
                        archive.inputStream().use { input -> importDictionaryArchive(input, { job.ensureActive() }) { word, definition ->
                            indexed += word.toByteArray().size + definition.toByteArray().size
                            require(indexed <= 256L * 1024 * 1024 && ++rows <= 2_000_000)
                            insert.bindString(1, word); insert.bindString(2, definition); insert.executeInsert()
                        } }
                    }
                    db.execSQL("CREATE INDEX entries_word ON entries(word)")
                    for ((key, value) in mapOf("package" to json.encodeToString(DictionaryPackage.serializer(), pack),
                        "title" to license.title, "notice" to license.notice)) db.execSQL("INSERT INTO metadata(name,value) VALUES(?,?)", arrayOf(key, value))
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            currentCoroutineContext().ensureActive()
            databaseMutex.withLock {
                Files.move(pending.toPath(), database(pack.id).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            pending.delete()
            File(pending.path + "-journal").delete()
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        require(validDictionaryId(id))
        installMutex.withLock { databaseMutex.withLock { require(!database(id).exists() || database(id).delete()) } }
    }

    suspend fun lookup(id: String, word: String): String? = withContext(Dispatchers.IO) {
        require(validDictionaryId(id))
        val key = dictionaryWord(word)
        databaseMutex.withLock {
            require(database(id).isFile)
            open(database(id)).use { db ->
                db.rawQuery("SELECT definition FROM entries WHERE word = ? LIMIT 10", arrayOf(key)).use { rows ->
                    val definitions = mutableListOf<String>()
                    while (rows.moveToNext()) { currentCoroutineContext().ensureActive(); definitions.add(rows.getString(0)) }
                    definitions.takeIf { it.isNotEmpty() }?.joinToString("\n\n")?.let {
                        if (it.length > 131072) it.take(131072) + "\n[…]" else it
                    }
                }
            }
        }
    }

    private fun database(id: String) = File(root, "$id.sqlite")
    private fun open(file: File) = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)

    private suspend fun <T> withResponse(url: String, block: suspend (Response) -> T): T = coroutineScope {
        val download = response(url)
        val value = download.response
        val closer = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { download.call.cancel() }
        }
        try { block(value) }
        catch (error: Exception) { currentCoroutineContext().ensureActive(); throw error }
        finally { closer.cancel(); value.close() }
    }

    private data class DownloadResponse(val call: Call, val response: Response)

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun response(url: String): DownloadResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "BookOrbitClient (offline dictionaries)").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("Dictionary download failed"))
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(DownloadResponse(call, response)) { _, value, _ -> value.call.cancel(); value.response.close() }
            }
        })
    }
}
