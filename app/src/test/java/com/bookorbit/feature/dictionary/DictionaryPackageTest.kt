package com.bookorbit.feature.dictionary

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DictionaryPackageTest {
    @Test fun `bundled catalog metadata includes many languages without bundling dictionary data`() {
        val packs = dictionaryCatalog(java.io.File("src/main/assets/dictionaries/catalog.json").readText(), Json)
        assertEquals(305, packs.size)
        for (id in listOf("fra-eng", "eng-deu", "deu-eng", "jpn-eng", "spa-eng", "eng-rus", "epo-eng")) assertTrue(packs.any { it.id == id })
        packs.forEach { it.validate() }
        assertEquals(packs.size, packs.map { it.id }.distinct().size)
    }

    @Test fun `release versions containing plus are encoded without changing their identity`() {
        val pack = BUNDLED_DICTIONARY_CATALOG.first().copy(id = "epo-eng", version = "2024.10.06+fd1")
        pack.validate()
        assertEquals("https://download.freedict.org/dictionaries/epo-eng/2024.10.06%2Bfd1/freedict-epo-eng-2024.10.06%2Bfd1.src.tar.xz", pack.url)
    }

    @Test fun `dictionary filenames accept ISO language pairs but never arbitrary paths`() {
        for (id in listOf("fra-eng", "zho-rus", "jpn-deu")) assertTrue(validDictionaryId(id))
        for (id in listOf("../eng-rus", "en-ru", "eng/rus", "eng-rus.sqlite", "ENG-RUS")) assertFalse(validDictionaryId(id))
        assertTrue(dictionaryLanguage("fra", java.util.Locale.ENGLISH).contains("French"))
    }
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
