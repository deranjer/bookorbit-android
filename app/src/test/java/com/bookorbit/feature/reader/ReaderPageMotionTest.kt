package com.bookorbit.feature.reader

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ReaderPageMotionTest {
    private val contract = Json.parseToJsonElement(checkNotNull(javaClass.getResourceAsStream("/reader-paging-contract.json"))
        .bufferedReader().use { it.readText() }).jsonObject

    @Test fun `native and EPUB use one shared snap contract in logical dp coordinates`() {
        for (sample in contract.getValue("snap").jsonArray) {
            val row = sample.jsonObject
            assertEquals(row.toString(), row.getValue("delta").jsonPrimitive.int,
                readerSnapDelta(row.getValue("drag").jsonPrimitive.double, row.getValue("velocity").jsonPrimitive.double))
        }
    }

    @Test fun `native and EPUB share duration and ease out positions`() {
        assertEquals(contract.getValue("durationMs").jsonPrimitive.int, READER_PAGE_TURN_MS)
        for (sample in contract.getValue("ease").jsonArray) {
            val row = sample.jsonObject
            assertEquals(row.getValue("position").jsonPrimitive.float,
                readerPageEase(row.getValue("progress").jsonPrimitive.float), 0.0001f)
        }
    }

    @Test fun `invalid input and extreme velocity cannot skip multiple pages`() {
        assertEquals(0, readerSnapDelta(Double.NaN, Double.POSITIVE_INFINITY))
        assertEquals(1, readerSnapDelta(0.0, Double.MAX_VALUE))
        assertEquals(-1, readerSnapDelta(0.0, -Double.MAX_VALUE))
    }
}
