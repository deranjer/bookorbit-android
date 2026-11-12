package com.bookorbit.feature.browse

import com.bookorbit.core.model.BookQuery
import com.bookorbit.core.model.BooksPage
import com.bookorbit.core.model.Pagination
import com.bookorbit.core.network.ApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class BrowseRepositoryTest {
    private val api = mockk<ApiService>()
    private val repo = BrowseRepository(api, Json)
    private val result = mockk<BooksPage>()

    @Test
    fun `smart scope uses session query route preserving scope and pagination`() = runTest {
        coEvery { api.querySmartScopeBooks(7, BookQuery(pagination = Pagination(2, 50))) } returns result
        assertSame(result, repo.smartScopeBooks(7, 2, 50))
        coVerify(exactly = 0) { api.getSmartScopeBooks(any(), any(), any(), any()) }
    }

    @Test
    fun `older servers use legacy scope route only when query is unavailable`() = runTest {
        for (code in listOf(404, 405)) {
            coEvery { api.querySmartScopeBooks(any(), any()) } throws httpError(code)
            coEvery { api.getSmartScopeBooks(7, 2, 50) } returns result
            assertSame(result, repo.smartScopeBooks(7, 2, 50))
        }
    }

    @Test
    fun `scope permission errors are preserved without retrying guarded route`() = runTest {
        coEvery { api.querySmartScopeBooks(any(), any()) } throws httpError(403)
        try {
            repo.smartScopeBooks(7, 2, 50)
            fail("Expected 403")
        } catch (error: HttpException) {
            assertEquals(403, error.code())
        }
        coVerify(exactly = 0) { api.getSmartScopeBooks(any(), any(), any(), any()) }
    }

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "{}".toResponseBody()))
}
