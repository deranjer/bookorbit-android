package com.bookorbit.feature.visionscan

import com.bookorbit.core.model.VisionScanRequest
import com.bookorbit.core.model.VisionScanResponse
import com.bookorbit.core.network.ApiService
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import okhttp3.ResponseBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisionScanRepository @Inject constructor(@com.bookorbit.core.network.MobileAiApi private val api: ApiService, private val json: Json) {
    enum class Problem { RESTRICTED, FORBIDDEN, DISABLED, UNSUPPORTED, AUTH, BUDGET, RATE_LIMIT, TIMEOUT, REFUSED, IMAGE, NETWORK, PROVIDER }
    data class Access(val enabled: Boolean = false, val problem: Problem? = null)
    class Failure(val problem: Problem) : Exception(problem.name)

    suspend fun access(): Access = try {
        val info = api.getAppInfo()
        if ("vision-scan" !in info.features || info.visionScan == null) Access(problem = Problem.UNSUPPORTED)
        else {
            val response = api.probeVisionScan(buildJsonObject {})
            try {
                when (response.code()) {
                    400 -> if (info.visionScan.enabled) Access(true) else Access(problem = Problem.DISABLED)
                    401 -> Access(problem = Problem.AUTH)
                    403 -> Access(problem = problem(response.code(), response.errorBody(), gated = true))
                    404 -> Access(problem = Problem.UNSUPPORTED)
                    else -> Access(problem = Problem.NETWORK)
                }
            } finally { response.body()?.close(); response.errorBody()?.close() }
        }
    } catch (e: CancellationException) { throw e }
    catch (e: retrofit2.HttpException) { Access(problem = when (e.code()) {
        401 -> Problem.AUTH; 403 -> Problem.RESTRICTED; 404 -> Problem.UNSUPPORTED; else -> Problem.NETWORK
    }) }
    catch (_: Exception) { Access(problem = Problem.NETWORK) }

    /** Only called after photo preview + provider disclosure; Retrofit cancellation closes the call. */
    suspend fun scan(kind: String, image: String): VisionScanResponse {
        require(kind in setOf("cover", "shelf"))
        require(image.isNotBlank() && image.length % 4 == 0 && image.length <= 8 * 1024 * 1024 && image.matches(Regex("[A-Za-z0-9+/]+={0,2}")))
        val access = access()
        if (!access.enabled) throw Failure(access.problem ?: Problem.PROVIDER)
        val response = api.visionScan(VisionScanRequest(kind, image))
        try {
            if (!response.isSuccessful) throw Failure(problem(response.code(), response.errorBody()))
            val result = response.body() ?: throw Failure(Problem.PROVIDER)
            if (result.kind != kind || result.books.size > 40 || result.books.any { book ->
                book.id.isBlank() || book.title.length > 4096 || book.matches.any { it.book.id <= 0 || !it.confidence.isFinite() || it.confidence !in 0.0..1.0 }
            }) throw Failure(Problem.PROVIDER)
            return result
        } finally { response.errorBody()?.close() }
    }

    private fun problem(status: Int, body: ResponseBody?, gated: Boolean = false): Problem {
        val code = runCatching {
            body?.source()?.let { source ->
                source.request(65_537)
                if (source.buffer.size > 65_536) null else json.parseToJsonElement(source.readUtf8()).jsonObject["errorCode"]?.jsonPrimitive?.contentOrNull
            }
        }.getOrNull()
        if (code?.startsWith("APP_ATTEST_") == true) return Problem.RESTRICTED
        return when (code) {
            "VISION_SCAN_OFF" -> Problem.DISABLED
            "VISION_SCAN_BUDGET" -> Problem.BUDGET
            "VISION_SCAN_TIMEOUT" -> Problem.TIMEOUT
            "VISION_SCAN_REFUSED" -> Problem.REFUSED
            "VISION_SCAN_IMAGE_INVALID" -> Problem.IMAGE
            else -> if (code?.startsWith("VISION_SCAN_") == true) Problem.PROVIDER else when (status) {
                401 -> Problem.AUTH; 403 -> if (gated) Problem.RESTRICTED else Problem.FORBIDDEN
                404 -> Problem.UNSUPPORTED; 413 -> Problem.IMAGE; 429 -> Problem.RATE_LIMIT; 504 -> Problem.TIMEOUT; else -> Problem.PROVIDER
            }
        }
    }
}
