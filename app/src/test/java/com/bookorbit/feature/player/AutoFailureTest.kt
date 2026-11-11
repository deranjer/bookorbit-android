package com.bookorbit.feature.player

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class AutoFailureTest {
    @Test fun `authentication failures ask for sign in while missing books report unavailable`() {
        fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))
        assertEquals(AutoFailure.SIGN_IN, AutoFailure.from(http(401)))
        assertEquals(AutoFailure.SIGN_IN, AutoFailure.from(http(403)))
        assertEquals(AutoFailure.UNAVAILABLE, AutoFailure.from(http(404)))
        assertEquals(AutoFailure.NETWORK, AutoFailure.from(http(503)))
    }
    @Test fun `offline and invalid selections have distinct recovery paths`() {
        assertEquals(AutoFailure.NETWORK, AutoFailure.from(IOException("offline")))
        assertEquals(AutoFailure.INVALID_REQUEST, AutoFailure.from(IllegalArgumentException()))
        assertEquals(AutoFailure.NO_RESULTS, AutoFailure.from(AutoRequestException(AutoFailure.NO_RESULTS)))
    }
}
