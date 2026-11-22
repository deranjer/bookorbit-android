package com.bookorbit.feature.storyguide

import com.bookorbit.core.model.*
import com.bookorbit.core.network.ApiService
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoryGuideRepository @Inject constructor(@com.bookorbit.core.network.MobileAiApi private val api: ApiService, private val json: Json) {
    enum class Problem { UNSUPPORTED, RESTRICTED, AUTH, NETWORK, FORMAT }
    data class Snapshot(val status: StoryGuideStatus? = null, val content: StoryGuidePayload? = null, val canRead: Boolean = false, val problem: Problem? = null)

    /** Only reads metadata/content. Opening this panel never requests generation or sends a book to AI. */
    suspend fun load(bookId: Int, fileId: Int): Snapshot {
        var status: StoryGuideStatus? = null
        return try {
            status = api.getStoryGuideStatus(bookId, fileId)
            if (status.fileId != fileId) return Snapshot(status, problem = Problem.UNSUPPORTED)
            val response = api.getStoryGuideContent(fileId)
            try {
                when {
                    response.isSuccessful -> {
                        val content = response.body()?.use { json.decodeFromString<StoryGuidePayload>(it.string()) }
                            ?: return Snapshot(status, problem = Problem.FORMAT)
                        if (content.format != 1 || status.textKey != null && status.textKey != content.textKey) Snapshot(status, problem = Problem.FORMAT)
                        else Snapshot(status, content, canRead = true)
                    }
                    response.code() == 403 -> Snapshot(status, problem = Problem.RESTRICTED)
                    response.code() == 401 -> Snapshot(status, problem = Problem.AUTH)
                    response.code() == 404 -> {
                        val missing = response.errorBody()?.string()?.let { json.parseToJsonElement(it) }
                        val none = (missing as? kotlinx.serialization.json.JsonObject)?.get("reason")?.toString() == "\"none\""
                        if (none) Snapshot(status, canRead = true) else Snapshot(status, problem = Problem.FORMAT)
                    }
                    else -> Snapshot(status, problem = Problem.NETWORK)
                }
            } finally {
                response.body()?.close()
                response.errorBody()?.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            Snapshot(status, problem = when (e.code()) { 404 -> Problem.UNSUPPORTED; 401 -> Problem.AUTH; 403 -> Problem.RESTRICTED; else -> Problem.NETWORK })
        } catch (e: Exception) {
            Snapshot(status, problem = Problem.NETWORK)
        }
    }

    /** Caller must present the provider/cost confirmation before requesting this mutation. */
    suspend fun build(bookId: Int, fileId: Int, kind: String): StoryGuideStatus {
        require(kind in setOf("build", "update"))
        // Recheck access immediately before a potentially billable request.
        val available = load(bookId, fileId)
        check(available.canRead && available.status?.canManage == true && available.status.build.allowed) { "Story Guide unavailable" }
        val response = api.buildStoryGuide(bookId, StoryGuideBuildRequest(fileId, kind))
        return if (response.isSuccessful) checkNotNull(response.body())
        else if (response.code() == 409) response.errorBody()?.use { json.decodeFromString<StoryGuideStatus>(it.string()) }
            ?: throw HttpException(response)
        else throw HttpException(response)
    }

    suspend fun retry(bookId: Int, fileId: Int): StoryGuideStatus {
        val available = load(bookId, fileId)
        check(available.canRead && available.status?.canManage == true) { "Story Guide unavailable" }
        return api.retryStoryGuide(bookId, StoryGuideFileRequest(fileId))
    }
    suspend fun cancel(bookId: Int, fileId: Int) = api.cancelStoryGuide(bookId, StoryGuideFileRequest(fileId))
}
