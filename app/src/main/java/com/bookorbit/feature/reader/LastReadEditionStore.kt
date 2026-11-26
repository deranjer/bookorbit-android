package com.bookorbit.feature.reader

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.auth.SessionOwner
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lastReadEditions by preferencesDataStore("last_read_editions")

/** A choice of edition, not a reading position. Scoped to server, account and book. */
@Singleton
class LastReadEditionStore @Inject constructor(@ApplicationContext private val context: Context, private val session: SessionManager) {
    private val recent = ConcurrentHashMap<String, Int>()
    private fun key(owner: SessionOwner, bookId: Int) = MessageDigest.getInstance("SHA-256")
        .digest("${owner.serverUrl}\n${owner.userId}\n$bookId".toByteArray()).joinToString("") { "%02x".format(it) }

    suspend fun load(owner: SessionOwner, bookId: Int): Int? {
        if (!session.owns(owner) || session.dataScopeChanged()) return null
        val key = key(owner, bookId)
        return recent[key] ?: try { context.lastReadEditions.data.first()[intPreferencesKey(key)] }
            catch (_: java.io.IOException) { null }
    }

    suspend fun record(owner: SessionOwner, bookId: Int, fileId: Int) {
        require(bookId > 0 && fileId > 0)
        if (!session.owns(owner) || session.dataScopeChanged()) return
        val key = key(owner, bookId)
        recent[key] = fileId
        try { context.lastReadEditions.edit { if (session.owns(owner) && !session.dataScopeChanged()) it[intPreferencesKey(key)] = fileId } }
        catch (_: java.io.IOException) { /* The in-memory choice still allows reading. */ }
    }
}

/** A missing/deleted edition falls back to the normal readable primary, never an arbitrary stale ID. */
fun preferredReadingFileId(book: BookDetail, remembered: Int?): Int? {
    if (remembered != null && BookFiles.readingTarget(book, remembered) != BookFiles.ReadingTarget.None) return remembered
    return when (val target = BookFiles.readingTarget(book)) {
        is BookFiles.ReadingTarget.Foliate -> target.file.id
        is BookFiles.ReadingTarget.Pdf -> target.file.id
        is BookFiles.ReadingTarget.Comic -> target.file.id
        BookFiles.ReadingTarget.None -> null
    }
}
