package com.bookorbit.feature.dictionary

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DictionaryPackageTest {
    @Test fun `catalog packages are pinned to bounded official HTTPS artifacts`() {
        BUNDLED_DICTIONARY_CATALOG.forEach { it.validate(); assertTrue(it.url.startsWith("https://download.freedict.org/dictionaries/")) }
        val valid = BUNDLED_DICTIONARY_CATALOG.first()
        for (invalid in listOf(valid.copy(id = "../books"), valid.copy(version = "../escape"), valid.copy(bytes = Long.MAX_VALUE), valid.copy(sha512 = "abc")))
            assertTrue(runCatching { invalid.validate() }.isFailure)
    }

    @Test fun `catalog cannot redirect a release to a foreign host`() {
        val p = BUNDLED_DICTIONARY_CATALOG.first()
        fun catalog(url: String) = """[{"name":"${p.id}","headwords":"${p.headwords}","releases":[{"platform":"src","version":"${p.version}","size":"${p.bytes}","checksum":"${p.sha512}","URL":"$url"}]}]"""
        assertEquals(listOf(p), dictionaryCatalog(catalog(p.url), Json))
        assertTrue(runCatching { dictionaryCatalog(catalog("https://foreign.example/dictionary"), Json) }.isFailure)
    }

    @Test fun `word identity uses unicode normalization and locale independent case`() {
        assertEquals("hello", dictionaryWord(" «Hello!» "))
        assertEquals("ёж", dictionaryWord("ЁЖ"))
        assertEquals(dictionaryWord("é"), dictionaryWord("e\u0301"))
        assertEquals("x' or 1=1 --", dictionaryWord("x' OR 1=1 --"))
    }
}
