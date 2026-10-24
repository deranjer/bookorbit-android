package com.bookorbit.feature.player

/** Server metadata survives extensionless streaming URLs and renamed offline downloads. */
internal fun audioMimeType(format: String?, filename: String?): String? {
    fun from(raw: String?): String? = when (raw?.trim()?.lowercase()?.removePrefix("audio/")?.removePrefix(".")) {
        "mp3", "mpeg" -> "audio/mpeg"
        "m4b", "m4a", "mp4" -> "audio/mp4"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "ogg", "oga", "opus" -> "audio/ogg"
        "wav", "wave", "x-wav" -> "audio/wav"
        else -> null
    }
    return from(format) ?: from(filename?.substringBefore('?')?.substringAfterLast('.', ""))
}
