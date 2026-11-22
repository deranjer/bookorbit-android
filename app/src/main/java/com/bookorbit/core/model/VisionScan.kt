package com.bookorbit.core.model

import kotlinx.serialization.Serializable

@Serializable data class VisionScanCapability(val enabled: Boolean = false)
@Serializable data class VisionScanRequest(val kind: String, val image: String)
@Serializable data class VisionMatchedBook(val id: Int, val title: String? = null)
@Serializable data class VisionMatch(val book: VisionMatchedBook, val confidence: Double = 0.0)
@Serializable data class VisionScanBook(val id: String, val title: String, val author: String? = null, val matches: List<VisionMatch> = emptyList())
@Serializable data class VisionScanResponse(val kind: String, val books: List<VisionScanBook> = emptyList())
