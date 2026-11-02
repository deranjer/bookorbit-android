package com.bookorbit.feature.reader

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderDictionaryTest {
    private fun json(s: String) = Json.parseToJsonElement(s)

    @Test
    fun `book languages map to dictionary sections`() {
        assertEquals("en", DictionaryParsing.normalizeLang(null))
        assertEquals("en", DictionaryParsing.normalizeLang("en-GB"))
        assertEquals("fr", DictionaryParsing.normalizeLang("fre"))
        assertEquals("de", DictionaryParsing.normalizeLang("German"))
        assertEquals("la", DictionaryParsing.normalizeLang("la"))
        assertEquals("es", DictionaryParsing.normalizeLang("es; en"))
        assertEquals("en", DictionaryParsing.normalizeLang("not a language"))
    }

    @Test
    fun `only grammatical glosses count as pointers`() {
        assertEquals("candelabrum", DictionaryParsing.pointerLemma("plural of candelabrum"))
        assertEquals("walk", DictionaryParsing.pointerLemma("simple past and past participle of walk."))
        assertEquals("be", DictionaryParsing.pointerLemma("(archaic) second-person singular of be"))
        assertEquals("endeavored", DictionaryParsing.pointerLemma("UK standard spelling of endeavored."))
        assertNull(DictionaryParsing.pointerLemma("Informal form of address used by friends"))
        assertNull(DictionaryParsing.pointerLemma("A candle holder."))
        assertNull(DictionaryParsing.pointerLemma("the of thing"))
    }

    @Test
    fun `a selection loses the punctuation around the word`() {
        assertEquals("doubt", DictionaryParsing.cleanWord(" “doubt,” "))
        assertEquals("don't", DictionaryParsing.cleanWord("don't."))
    }

    @Test
    fun `html is stripped with styles and entities handled`() {
        assertEquals(
            "A candle holder & stand.",
            DictionaryParsing.stripHtml("""<style>.x{color:red}</style><span>A <i>candle</i> holder &amp; stand.</span>"""),
        )
        assertEquals("café", DictionaryParsing.stripHtml("caf&#233;"))
    }

    @Test
    fun `free dictionary entries merge by part of speech and keep the first recorded pronunciation`() {
        val payload = DictionaryParsing.parseFreeDictionary(
            json(
                """[{"word":"run","phonetic":"/rʌn/","phonetics":[{"text":"/rʌn/","audio":""},{"text":"/ɹʌn/","audio":"https://x/run.mp3"}],
                "meanings":[{"partOfSpeech":"verb","definitions":[{"definition":"To move swiftly.","example":"Run!"}]},
                {"partOfSpeech":"noun","definitions":[{"definition":"An act of running."}]},
                {"partOfSpeech":"verb","definitions":[{"definition":"To flow."}]}]}]""",
            ),
            "run",
        )!!
        assertEquals("/ɹʌn/", payload.phonetic)
        assertEquals("https://x/run.mp3", payload.audioUrl)
        assertEquals(listOf("verb", "noun"), payload.blocks.map { it.partOfSpeech })
        assertEquals(listOf("To move swiftly.", "To flow."), payload.blocks[0].definitions.map { it.definition })
        assertEquals("Run!", payload.blocks[0].definitions[0].example)
    }

    @Test
    fun `wiktionary reads only the book's language section`() {
        val data = json(
            """{"en":[{"partOfSpeech":"Noun","definitions":[{"definition":"<b>A</b> cat.","examples":[{"text":"The <i>cat</i> sat."}]}]}],
            "la":[{"partOfSpeech":"Verb","definitions":[{"definition":"Latin sense"}]}]}""",
        )
        val en = DictionaryParsing.parseWiktionary(data, "cat", "en")!!
        assertEquals("A cat.", en.blocks.single().definitions.single().definition)
        assertEquals("The cat sat.", en.blocks.single().definitions.single().example)
        assertNull(DictionaryParsing.parseWiktionary(data, "cat", "fr"))
    }

    @Test
    fun `pointer senses are followed to the lemma, and a failing lemma is skipped`() = runTest {
        val root = DictionaryPayload(
            "candelabras",
            null,
            null,
            listOf(SenseBlock("noun", listOf(DictionaryDefinition("plural of candelabra", null)))),
        )
        val lemmas = mapOf(
            "candelabra" to listOf(SenseBlock("noun", listOf(DictionaryDefinition("plural of candelabrum", null)))),
            "candelabrum" to listOf(SenseBlock("noun", listOf(DictionaryDefinition("A branched candlestick.", null)))),
        )
        val entries = DictionaryParsing.expandPointers(root) { lemmas[it] ?: error("offline") }
        assertEquals(listOf("candelabras", "candelabra", "candelabrum"), entries.map { it.sourceWord })

        val broken = DictionaryParsing.expandPointers(root) { error("offline") }
        assertEquals(listOf("candelabras"), broken.map { it.sourceWord })
    }
}
