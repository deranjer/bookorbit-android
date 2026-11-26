package com.bookorbit.feature.ai

import com.bookorbit.core.auth.SecureStorage
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import io.mockk.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

internal class AiTestAccount {
    val values = mutableMapOf<String, String?>()
    val storage = mockk<SecureStorage>(relaxed = true).apply {
        every { getString(any()) } answers { values[firstArg()] }
        every { putString(any(), any()) } answers { values[firstArg()] = secondArg(); Unit }
        every { remove(any()) } answers { values.remove(firstArg()); Unit }
    }
    val session = SessionManager(storage, Json).apply { setServerUrl("https://books.example"); signIn("bookorbit-bearer", AuthUser(1, "reader")) }
    val store = AiConnectionStore(storage, session)
}

class AiConnectionStoreTest {
    @Test fun `default route is server and secret is never returned in connection state`() {
        val account = AiTestAccount()
        val owner = account.session.owner()!!
        assertEquals(AiRoute.SERVER, account.store.connection(owner).route)
        account.store.save(owner, AiRoute.DIRECT, AiProvider.OPENAI, "fixture-model", "guide-model", "fake-private-key")
        val connection = account.store.connection(owner)
        assertTrue(connection.hasToken)
        assertFalse(connection.toString().contains("fake-private-key"))
        val credentials = account.store.credentials(owner)
        assertFalse(credentials.toString().contains("fake-private-key"))
        account.store.save(owner, AiRoute.DIRECT, AiProvider.OPENAI, "new-model", "guide-model")
        assertEquals("fake-private-key", account.store.credentials(owner).token)
        assertFalse(account.store.current(credentials))
    }

    @Test fun `credentials are isolated by server and account but persist on same account relogin`() {
        val a = AiTestAccount()
        val first = a.session.owner()!!
        a.store.save(first, AiRoute.DIRECT, AiProvider.GOOGLE, "fixture-model", "", "fake-google-key")
        val old = a.store.credentials(first)
        a.session.signOut(); a.session.signIn("replacement", AuthUser(1, "reader"))
        assertFalse(a.store.current(old))
        assertTrue(a.store.connection(a.session.owner()!!).hasToken)
        a.session.signIn("other", AuthUser(2, "other"))
        assertFalse(a.store.connection(a.session.owner()!!).hasToken)
        a.session.setServerUrl("https://other.example"); a.session.signIn("other", AuthUser(1, "reader"))
        assertFalse(a.store.connection(a.session.owner()!!).hasToken)
        assertTrue(runCatching { a.store.save(first, AiRoute.DIRECT, AiProvider.OPENAI, "x", "", "x") }.isFailure)
    }

    @Test fun `removing a provider token never changes the active route or provider`() {
        val a = AiTestAccount(); val owner = a.session.owner()!!
        a.store.save(owner, AiRoute.DIRECT, AiProvider.OPENAI, "model", "", "key")
        a.store.save(owner, AiRoute.SERVER, AiProvider.GOOGLE, "model", "", "google-key")
        a.store.removeToken(owner, AiProvider.OPENAI)
        assertEquals(AiRoute.SERVER, a.store.connection(owner).route)
        assertEquals(AiProvider.GOOGLE, a.store.connection(owner).provider)
        assertTrue(a.store.connection(owner).hasToken)
        assertFalse(a.store.connection(owner, AiProvider.OPENAI).hasToken)
    }

    @Test fun `invalid model path and header injection are rejected before persistence`() {
        val a = AiTestAccount(); val owner = a.session.owner()!!
        assertTrue(runCatching { a.store.save(owner, AiRoute.DIRECT, AiProvider.GOOGLE, "../../secret", "", "key") }.isFailure)
        assertTrue(runCatching { a.store.save(owner, AiRoute.DIRECT, AiProvider.OPENAI, "model", "", "key\nAuthorization: other") }.isFailure)
        assertEquals(AiRoute.SERVER, a.store.connection(owner).route)
    }

    @Test fun `corrupt saved connection is unavailable rather than silently switched to server`() {
        val a = AiTestAccount(); val owner = a.session.owner()!!
        a.store.save(owner, AiRoute.DIRECT, AiProvider.OPENAI, "model", "", "key")
        val key = a.values.keys.single { it.startsWith("ai_connection_") }
        a.values[key] = "invalid-json"
        assertFalse(a.store.connection(owner).valid)
        assertTrue(runCatching { a.store.credentials(owner) }.isFailure)
    }
}
