package com.bookorbit.core.model

import kotlinx.serialization.Serializable

/**
 * Body of `POST /books/files/{fileId}/sessions`. Re-sending the same [sessionId] supersedes the
 * earlier partial row. No defaults on purpose: the client's JSON config doesn't encode defaults.
 */
@Serializable
data class SaveReadingSession(
    val sessionId: String,
    val startedAt: String,
    val endedAt: String,
    val durationSeconds: Int,
    val progressDelta: Double?,
    val endProgress: Double?,
    val sessionType: String,
    val source: String,
)
