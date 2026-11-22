package com.bookorbit.feature.readinghelp

import com.bookorbit.core.model.ReadingHelpEvent
import com.bookorbit.core.model.ReadingHelpRequest
import com.bookorbit.core.network.ApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingHelpRepository @Inject constructor(@com.bookorbit.core.network.MobileAiApi private val api: ApiService, private val json: Json) {
    enum class Problem { RESTRICTED, FORBIDDEN, DISABLED, UNSUPPORTED, AUTH, BUDGET, READER_CAP, CAREFUL_OFF, TIMEOUT, REFUSED, TOO_LONG, NETWORK, PROVIDER }
    data class Access(val enabled: Boolean = false, val careful: Boolean = false, val problem: Problem? = null)
    class Failure(val problem: Problem) : IOException(problem.name)

    /** No valid passage is sent: the DTO must fail validation before a provider can be called. */
    suspend fun access(): Access = try {
        val info = api.getAppInfo()
        if ("reading-help" !in info.features || info.readingHelp == null) Access(problem = Problem.UNSUPPORTED)
        else {
            val response = api.probeReadingHelp(buildJsonObject {})
            try {
                when (response.code()) {
                    400 -> if (info.readingHelp.enabled) Access(true, info.readingHelp.careful)
                        else Access(problem = Problem.DISABLED)
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
    catch (e: Exception) { Access(problem = Problem.NETWORK) }

    /** Each collection is one explicitly confirmed request. Cancel collection to cancel the HTTP call. */
    fun answer(request: ReadingHelpRequest): Flow<ReadingHelpEvent> = callbackFlow {
        request.validate()
        val access = access()
        if (!access.enabled) throw Failure(access.problem ?: Problem.PROVIDER)
        if (request.careful && !access.careful) throw Failure(Problem.CAREFUL_OFF)
        val call = api.readingHelp(request)
        call.enqueue(object : Callback<ResponseBody> {
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) { close(Failure(Problem.NETWORK)) }
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                try {
                    if (!response.isSuccessful) throw Failure(problem(response.code(), response.errorBody()))
                    val body = response.body() ?: throw Failure(Problem.PROVIDER)
                    if (body.contentType()?.let { it.type == "text" && it.subtype == "event-stream" } != true) throw Failure(Problem.PROVIDER)
                    body.use {
                        ReadingHelpStream.read(it.source(), json) { event ->
                            if (event.type == "error") throw Failure(codeProblem(event.errorCode))
                            if (trySend(event).isFailure) throw IOException("Answer cancelled")
                        }
                    }
                    close()
                } catch (e: Exception) { close(if (e is Failure) e else Failure(Problem.PROVIDER)) }
                finally { response.body()?.close(); response.errorBody()?.close() }
            }
        })
        awaitClose { call.cancel() }
        // Decoder bounds both total bytes and event count, including when a collector is slower.
    }.buffer(Channel.UNLIMITED)

    private fun problem(status: Int, body: ResponseBody?, gated: Boolean = false): Problem {
        val code = runCatching {
            body?.source()?.let { source ->
                source.request(64 * 1024L + 1)
                if (source.buffer.size > 64 * 1024) null
                else json.parseToJsonElement(source.readUtf8()).jsonObject["errorCode"]?.jsonPrimitive?.contentOrNull
            }
        }.getOrNull()
        return if (code?.startsWith("APP_ATTEST_") == true) Problem.RESTRICTED
        else if (code?.startsWith("READING_HELP_") == true) codeProblem(code) else when (status) {
            401 -> Problem.AUTH; 403 -> if (gated) Problem.RESTRICTED else Problem.FORBIDDEN; 404 -> Problem.UNSUPPORTED
            429 -> Problem.READER_CAP; 504 -> Problem.TIMEOUT; else -> Problem.PROVIDER
        }
    }

    private fun codeProblem(code: String?) = when (code) {
        "READING_HELP_OFF" -> Problem.DISABLED
        "READING_HELP_CAREFUL_OFF" -> Problem.CAREFUL_OFF
        "READING_HELP_BUDGET" -> Problem.BUDGET
        "READING_HELP_READER_CAP" -> Problem.READER_CAP
        "READING_HELP_TIMEOUT" -> Problem.TIMEOUT
        "READING_HELP_REFUSED" -> Problem.REFUSED
        "READING_HELP_TOO_LONG" -> Problem.TOO_LONG
        else -> Problem.PROVIDER
    }
}
