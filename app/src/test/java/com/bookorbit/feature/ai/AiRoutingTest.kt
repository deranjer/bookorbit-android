package com.bookorbit.feature.ai

import com.bookorbit.core.model.ReadingHelpEvent
import com.bookorbit.core.model.ReadingHelpRequest
import com.bookorbit.core.network.ApiService
import com.bookorbit.feature.readinghelp.ReadingHelpRepository
import com.bookorbit.feature.storyguide.StoryGuideRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AiRoutingTest {
    @Test fun `direct access and answer do not probe the BookOrbit AI endpoints`() = runBlocking {
        val a = AiTestAccount(); val owner = a.session.owner()!!
        a.store.save(owner, AiRoute.DIRECT, AiProvider.OPENAI, "fixture-model", "", "provider-key")
        val api = mockk<ApiService>()
        val direct = mockk<DirectReadingHelp>()
        every { direct.answer(any(), any()) } returns flowOf(ReadingHelpEvent("done", "answer"))
        val repository = ReadingHelpRepository(api, Json, a.store, direct, a.session)
        val access = repository.access()
        assertTrue(access.enabled)
        assertEquals("answer", repository.answer(ReadingHelpRequest("recap-note", 5, "ru", listOf("text")), access.revision).toList().single().text)
        coVerify { api wasNot Called }
        verify(exactly = 1) { direct.answer(any(), any()) }
    }

    @Test fun `a changed connection requires a fresh confirmation before any provider request`() = runBlocking {
        val a = AiTestAccount(); val owner = a.session.owner()!!
        a.store.save(owner, AiRoute.DIRECT, AiProvider.OPENAI, "fixture-model", "", "provider-key")
        val api = mockk<ApiService>(); val direct = mockk<DirectReadingHelp>()
        val repository = ReadingHelpRepository(api, Json, a.store, direct, a.session)
        val access = repository.access()
        a.store.save(owner, AiRoute.DIRECT, AiProvider.GOOGLE, "other-model", "", "other-key")
        assertTrue(runCatching { repository.answer(ReadingHelpRequest("recap-note", 5, "ru", listOf("text")), access.revision).toList() }.isFailure)
        coVerify { api wasNot Called }; verify { direct wasNot Called }
    }

    @Test fun `direct Story Guide stays explicitly unsupported without server fallback`() = runBlocking {
        val a = AiTestAccount(); val owner = a.session.owner()!!
        a.store.save(owner, AiRoute.DIRECT, AiProvider.OPENAI, "fixture-model", "guide-model", "provider-key")
        val api = mockk<ApiService>()
        val repository = StoryGuideRepository(api, Json, a.store, a.session)
        assertEquals(StoryGuideRepository.Problem.DIRECT_UNSUPPORTED, repository.load(5, 42).problem)
        assertTrue(runCatching { repository.build(5, 42, "build") }.isFailure)
        assertTrue(runCatching { repository.cancel(5, 42) }.isFailure)
        coVerify { api wasNot Called }
    }

    @Test fun `same account relogin invalidates a previous passage confirmation`() = runBlocking {
        val a = AiTestAccount(); val owner = a.session.owner()!!
        a.store.save(owner, AiRoute.DIRECT, AiProvider.OPENAI, "fixture-model", "", "provider-key")
        val api = mockk<ApiService>(); val direct = mockk<DirectReadingHelp>()
        val repository = ReadingHelpRepository(api, Json, a.store, direct, a.session)
        val access = repository.access()
        a.session.signIn("new-bearer", com.bookorbit.core.model.AuthUser(1, "reader"))
        assertTrue(runCatching { repository.answer(ReadingHelpRequest("recap-note", 5, "ru", listOf("text")), access.revision, access.owner).toList() }.isFailure)
        coVerify { api wasNot Called }; verify { direct wasNot Called }
    }
}
