package com.bookorbit.feature.auth

import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.model.LoginResponse
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AuthRepositoryTest {
    @Test
    fun `signing in sends what was queued while signed out`() = runTest {
        val user = AuthUser(id = 1, username = "me")
        val api = mockk<ApiService>()
        coEvery { api.login(any()) } returns LoginResponse("token", user)
        val session = mockk<SessionManager>(relaxed = true)
        every { session.dataScopeChanged() } returns false
        val scheduler = mockk<SyncScheduler>(relaxed = true)

        AuthRepository(api, session, mockk(relaxed = true), scheduler).login("me", "secret")

        verify { session.signIn("token", user) }
        verify(exactly = 1) { scheduler.schedule() }
    }
}
