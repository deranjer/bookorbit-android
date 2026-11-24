package com.bookorbit.feature.reader

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ReaderControlsTest {
    @Test fun `books and comics share edge zones with a forty percent menu zone`() {
        assertEquals(ReaderTapAction.PREVIOUS, readerTapAction(0.0))
        assertEquals(ReaderTapAction.PREVIOUS, readerTapAction(0.299))
        assertEquals(ReaderTapAction.MENU, readerTapAction(0.3))
        assertEquals(ReaderTapAction.MENU, readerTapAction(0.7))
        assertEquals(ReaderTapAction.NEXT, readerTapAction(0.701))
        assertEquals(ReaderTapAction.NEXT, readerTapAction(1.0))
    }

    @Test fun `manga mirrors only the page actions`() {
        assertEquals(ReaderTapAction.NEXT, readerTapAction(0.1, rightToLeft = true))
        assertEquals(ReaderTapAction.PREVIOUS, readerTapAction(0.9, rightToLeft = true))
        assertEquals(ReaderTapAction.MENU, readerTapAction(0.5, rightToLeft = true))
    }

    @Test fun `continuous scrolling uses taps for menu across the whole screen`() {
        for (position in listOf(0.0, 0.1, 0.5, 0.9, 1.0)) {
            assertEquals(ReaderTapAction.MENU, readerTapAction(position, paginated = false))
            assertEquals(ReaderTapAction.MENU, readerTapAction(position, paginated = false, rightToLeft = true))
        }
    }

    @Test fun `invalid coordinates never turn pages`() {
        for (position in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1)) {
            assertEquals(ReaderTapAction.MENU, readerTapAction(position))
        }
    }

    @Test fun `older settings retain their choices and acquire the original page margin`() {
        val settings = Json.decodeFromString<ReaderSettings>("""{"fontSize":23,"flow":"scrolled","themeName":"sepia","isDark":false}""")
        assertEquals(23, settings.fontSize)
        assertEquals("scrolled", settings.flow)
        assertEquals("sepia", settings.themeName)
        assertEquals(40, settings.pageMargin)
    }

    @Test fun `all extended controls survive settings serialization`() {
        val settings = ReaderSettings(fontSize = 22, lineHeight = 1.8, fontFamily = FONT_FAMILIES[1].value,
            maxColumnCount = 3, gap = 0.12, pageMargin = 24, maxInlineSize = 600,
            justify = false, hyphenate = false, isDark = false, themeName = "sepia", flow = "scrolled")
        assertEquals(settings, Json.decodeFromString<ReaderSettings>(Json.encodeToString(ReaderSettings.serializer(), settings)))
    }
}
