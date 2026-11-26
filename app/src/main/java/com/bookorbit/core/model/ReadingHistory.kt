package com.bookorbit.core.model

import kotlinx.serialization.Serializable

/** Stock session timeline has no READ/LISTEN discriminator. */
@Serializable
data class ReadingHistoryItem(
    val sessionId: Int,
    val bookId: Int,
    val bookTitle: String,
    val bookFormat: String? = null,
    val bookSource: String? = null,
    val startedAt: String,
    val endedAt: String,
    val durationSeconds: Long,
)

@Serializable
data class ReadingHistory(
    val year: Int,
    val week: Int,
    val weekStart: String,
    val weekEnd: String,
    val items: List<ReadingHistoryItem> = emptyList(),
)
