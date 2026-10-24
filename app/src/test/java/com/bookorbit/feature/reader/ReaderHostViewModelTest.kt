package com.bookorbit.feature.reader

import androidx.lifecycle.SavedStateHandle
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.network.TokenRefresher
import com.bookorbit.core.settings.AppSettingsStore
import com.bookorbit.core.settings.ReaderInterface
import com.bookorbit.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ReaderHostViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val session = mockk<SessionManager> {
        every { currentUser } returns AuthUser(1, "reader")
    }
    private val refresher = mockk<TokenRefresher>()
    private val settings = mockk<AppSettingsStore> {
        every { readerInterface } returns flowOf(ReaderInterface.APP)
        every { readingFullscreen } returns flowOf(true)
    }
    private fun vm() = ReaderHostViewModel(
        SavedStateHandle(mapOf("id" to 12)), settings, mockk(), mockk(), session, refresher, Json,
    )

    @Test
    fun `web refresh uses the same native refresh coordinator and returns only access token`() {
        every { session.serverUrl } returns "https://books.example"
        every { session.accessToken } returns "old"
        every { refresher.refreshIfNeeded("old") } returns "fresh"
        assertEquals("{\"accessToken\":\"fresh\"}", vm().refreshResponse(ServerReaderPolicy("https://books.example", 12, 34), "old"))
        verify(exactly = 1) { refresher.refreshIfNeeded("old") }
    }

    @Test
    fun `web refresh cannot serve credentials after server changes including base path`() {
        every { session.serverUrl } returns "https://books.example/other"
        assertNull(vm().refreshResponse(ServerReaderPolicy("https://books.example", 12, 34), null))
        verify(exactly = 0) { refresher.refreshIfNeeded(any()) }
    }

    @Test
    fun `web refresh does not revive a signed out session`() {
        every { session.serverUrl } returns "https://books.example"
        every { session.accessToken } returns null
        assertNull(vm().refreshResponse(ServerReaderPolicy("https://books.example", 12, 34), null))
        verify(exactly = 0) { refresher.refreshIfNeeded(any()) }
    }

    @Test
    fun `failed native refresh gives no credential response`() {
        every { session.serverUrl } returns "https://books.example"
        every { session.accessToken } returns "old"
        every { refresher.refreshIfNeeded("old") } returns null
        assertNull(vm().refreshResponse(ServerReaderPolicy("https://books.example", 12, 34), "old"))
    }

    @Test
    fun `old web reader cannot obtain credentials after account changes`() {
        every { session.serverUrl } returns "https://books.example"
        val host = vm()
        every { session.currentUser } returns AuthUser(2, "other")
        assertNull(host.refreshResponse(ServerReaderPolicy("https://books.example", 12, 34), null))
        verify(exactly = 0) { refresher.refreshIfNeeded(any()) }
    }
}
