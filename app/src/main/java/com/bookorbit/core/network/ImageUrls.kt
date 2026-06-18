package com.bookorbit.core.network

import com.bookorbit.core.auth.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds absolute cover/author-thumbnail image URLs for Coil. Auth is supplied by the [ImageClient]
 * OkHttp client's interceptor, so no per-request headers are needed here.
 */
@Singleton
class ImageUrls @Inject constructor(
    private val session: SessionManager,
) {
    private fun base(): String = "${session.serverUrl.orEmpty()}/api/v1"

    fun cover(bookId: Int): String = "${base()}/books/$bookId/cover"

    fun thumbnail(bookId: Int): String = "${base()}/books/$bookId/thumbnail"

    fun authorThumbnail(authorId: Int): String = "${base()}/authors/$authorId/thumbnail"
}
