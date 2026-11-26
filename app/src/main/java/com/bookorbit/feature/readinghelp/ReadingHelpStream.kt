package com.bookorbit.feature.readinghelp

import com.bookorbit.core.model.ReadingHelpEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.BufferedSource

/** Bounded SSE decoder: comments/CRLF/multiline data work; EOF is never mistaken for success. */
internal object ReadingHelpStream {
    const val MAX_ANSWER = 128 * 1024
    private const val MAX_WIRE = 2 * 1024 * 1024

    fun read(source: BufferedSource, json: Json, receive: (ReadingHelpEvent) -> Unit) {
        var bytes = 0L
        var events = 0
        var answer = 0
        var data = StringBuilder()
        while (!source.exhausted()) {
            val line = source.readUtf8LineStrict(64 * 1024)
            bytes += line.toByteArray(Charsets.UTF_8).size + 1
            check(bytes <= MAX_WIRE) { "AI stream too large" }
            if (line.isEmpty()) {
                if (data.isEmpty()) continue
                check(++events <= 4096) { "Too many AI events" }
                val payload = json.parseToJsonElement(data.toString().removeSuffix("\n")).jsonObject
                val event = json.decodeFromJsonElement(ReadingHelpEvent.serializer(), payload)
                if (event.type in setOf("delta", "done")) check(payload["text"]?.jsonPrimitive?.isString == true)
                data = StringBuilder()
                when (event.type) {
                    "delta" -> { answer += event.text.length; check(answer <= MAX_ANSWER); receive(event) }
                    "done" -> { check(event.text.length <= MAX_ANSWER); receive(event); return }
                    "error" -> { receive(event); return }
                    // Forward-compatible unknown event types do not terminate the response.
                }
            } else if (line.startsWith("data:")) {
                data.append(line.substring(5).removePrefix(" ")).append('\n')
                check(data.length <= 64 * 1024)
            }
        }
        error("AI stream ended without a terminal event")
    }
}
