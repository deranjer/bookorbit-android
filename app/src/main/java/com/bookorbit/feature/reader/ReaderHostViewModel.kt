package com.bookorbit.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.BookFiles
import com.bookorbit.core.model.RefreshResponse
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.network.TokenRefresher
import com.bookorbit.core.settings.AppSettingsStore
import com.bookorbit.core.settings.ReaderInterface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class ReadingPreferences(val readerInterface: ReaderInterface, val fullscreen: Boolean)

@HiltViewModel
class ReaderHostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    settings: AppSettingsStore,
    private val api: ApiService,
    private val progress: ReaderProgressRepository,
    private val session: SessionManager,
    private val refresher: TokenRefresher,
    private val json: Json,
) : ViewModel() {
    private val bookId = checkNotNull(savedStateHandle.get<Int>("id"))
    private val accountId = session.currentUser?.id
    val preferences = combine(settings.readerInterface, settings.readingFullscreen) { mode, fullscreen ->
        ReadingPreferences(mode, fullscreen)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun prepare(kind: ReaderKind): ServerReaderPolicy {
        // Intentionally require a live server. Native downloads belong to App mode.
        val book = api.getBookDetail(bookId)
        val file = checkNotNull(when (kind) {
            ReaderKind.EBOOK -> BookFiles.readableFile(book)
            ReaderKind.PDF -> BookFiles.pdfFile(book)
            ReaderKind.COMIC -> BookFiles.comicFile(book)
        })
        progress.syncBeforeWeb(file.id)
        return ServerReaderPolicy(checkNotNull(session.serverUrl), bookId, file.id)
    }

    /** Called on WebView's request thread. Never copy refresh cookies into a second cookie jar. */
    fun refreshResponse(policy: ServerReaderPolicy, staleToken: String?): String? {
        val server = session.serverUrl ?: return null
        if (!policy.matchesServer(server)) return null
        if (accountId == null || session.currentUser?.id != accountId) return null
        if (session.accessToken == null) return null
        val token = refresher.refreshIfNeeded(staleToken) ?: return null
        if (session.serverUrl != server || session.currentUser?.id != accountId) return null
        return json.encodeToString(RefreshResponse.serializer(), RefreshResponse(token))
    }
}
