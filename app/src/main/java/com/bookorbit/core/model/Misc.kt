package com.bookorbit.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class AppInfo(
    val version: String,
    val updateAvailable: Boolean? = null,
    val latestVersion: String? = null,
    val bookDockPath: String? = null,
)

/** Catalog typeahead rows (`GET /metadata/{authors,genres,tags,languages}`) return `{ name }`. */
@Serializable
data class NamedResult(val name: String? = null)

/** `GET /dashboard/widgets/reading-streak`. */
@Serializable
data class ReadingStreakWidget(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastSevenDays: List<Boolean> = emptyList(),
)

/** `GET /dashboard/widgets/reading-goal`. [goalBooks] is null until the user sets a yearly goal. */
@Serializable
data class ReadingGoalWidget(
    val goalBooks: Int? = null,
    val completedBooks: Int = 0,
    val year: Int = 0,
)

/** Scroller types accepted by `GET /dashboard/scrollers/:type`. */
object ScrollerType {
    const val RECENTLY_ADDED = "recently-added"
    const val CONTINUE_READING = "continue-reading"
    const val CONTINUE_LISTENING = "continue-listening"
    const val RANDOM = "random"
    const val SMART_SCOPE = "smart-scope"
}

/**
 * `GET /dashboard/scrollers/{type}` body: a bare `BookCard[]` before server 3.0, `{ books, total }`
 * since. Accepts both.
 */
object ScrollerBooks {
    fun decode(json: Json, body: JsonElement): List<BookCard> {
        val books = (body as? JsonObject)?.get("books") ?: body
        return json.decodeFromJsonElement(ListSerializer(BookCard.serializer()), books)
    }
}

/** `GET /user-statistics/summary`. */
@Serializable
data class UserStatsSummary(
    val trackedBooks: Int = 0,
    val startedBooks: Int = 0,
    val inProgressBooks: Int = 0,
    val completedBooks: Int = 0,
    val meanProgressPercent: Double = 0.0,
)

/** One day of `GET /user-statistics/daily-reading` (or reading-heatmap). [day] is `yyyy-mm-dd`. */
@Serializable
data class UserDailyReading(
    val day: String,
    val readingSeconds: Long = 0,
    val progressDelta: Double = 0.0,
    val eventsCount: Int = 0,
)

@Serializable
data class SourceSlice(val bucket: String, val readingSeconds: Long = 0)

/** `GET /user-statistics/reading-source-distribution`: where your reading time came from. */
@Serializable
data class SourceDistribution(
    val totalSeconds: Long = 0,
    val slices: List<SourceSlice> = emptyList(),
)

/** An audiobook bookmark (`/audiobooks/{bookId}/bookmarks`). [positionMs] is the position in the whole book. */
@Serializable
data class AudiobookBookmark(
    val id: String,
    val bookId: Int,
    val positionMs: Long,
    val chapterId: String? = null,
    val title: String = "",
    val note: String? = null,
)

@Serializable
data class CreateAudiobookBookmark(
    val clientId: String,
    val positionMs: Long,
    val title: String,
)
