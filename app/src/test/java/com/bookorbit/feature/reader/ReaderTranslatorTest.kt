package com.bookorbit.feature.reader

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ReaderTranslatorTest {
    private fun json(s: String) = Json.parseToJsonElement(s)

    @Test
    fun `google's sentences are joined and the source language read`() {
        val r = TranslationParsing.parseGoogle(json("""[[["Hola. ","Hello. ",null],["¿Cómo estás?","How are you?",null]],null,"en"]"""))
        assertEquals(TranslationResult("Hola. ¿Cómo estás?", "en", "Google"), r)
    }

    @Test(expected = IOException::class)
    fun `an empty google answer is an error so the fallback runs`() {
        TranslationParsing.parseGoogle(json("""[[],null,"en"]"""))
    }

    @Test
    fun `microsoft's translation and detected language are read`() {
        val r = TranslationParsing.parseAzure(json("""[{"detectedLanguage":{"language":"fr","score":1.0},"translations":[{"text":"Hello","to":"en"}]}]"""))
        assertEquals(TranslationResult("Hello", "fr", "Microsoft"), r)
    }
}
