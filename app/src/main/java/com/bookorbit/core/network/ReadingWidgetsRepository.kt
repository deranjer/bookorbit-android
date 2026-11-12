package com.bookorbit.core.network

import com.bookorbit.core.model.DashboardWidgetRequest
import com.bookorbit.core.model.ReadingGoalWidget
import com.bookorbit.core.model.ReadingStreakWidget
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

data class ReadingWidgets(val streak: ReadingStreakWidget?, val goal: ReadingGoalWidget?)

/** Uses the session-authenticated dashboard API; individual widgets require official-app attestation. */
@Singleton
class ReadingWidgetsRepository @Inject constructor(private val api: ApiService, private val json: Json) {
    suspend fun load(): ReadingWidgets {
        val response = try {
            api.getDashboardWidgets(DashboardWidgetRequest(listOf("reading-streak", "reading-goal")))
        } catch (error: HttpException) {
            // Older servers predate batching. Never fall back on authentication or permission errors.
            if (error.code() !in listOf(404, 405)) throw error
            return ReadingWidgets(optional { api.getReadingStreak() }, optional { api.getReadingGoal() })
        }
        val streak = response.items.firstOrNull { it.type == "reading-streak" && !it.failed }?.data
        val goal = response.items.firstOrNull { it.type == "reading-goal" && !it.failed }?.data
        return ReadingWidgets(
            optional { streak?.let { json.decodeFromJsonElement<ReadingStreakWidget>(it) } },
            optional { goal?.let { json.decodeFromJsonElement<ReadingGoalWidget>(it) } },
        )
    }

    private suspend fun <T> optional(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}
