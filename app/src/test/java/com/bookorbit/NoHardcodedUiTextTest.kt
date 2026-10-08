package com.bookorbit

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the move to string resources: no Compose text call may take an English literal. Text should come
 * from `stringResource(R.string...)` (or a `UiText`), so the app can be translated.
 *
 * The check is deliberately narrow (the call sites that render text), so it stays low-noise. It does not
 * look at literals used as identifiers, routes, JSON keys, log messages, or server values.
 */
class NoHardcodedUiTextTest {
    private val root = File("src/main/java/com/bookorbit")

    private val textCall = Regex(
        """\b(?:Text\(\s*(?:text\s*=\s*)?|contentDescription\s*=\s*|placeholder\s*=\s*|""" +
            """(?:Section|SectionHeader|SectionTitle|Field|Tile|Label|SectionLabel|Chip|CenteredHint)\(\s*)"([^"\\$]*[A-Za-z]{3,}[^"\\$]*)"""",
    )

    /** Literals that are fine: non-language symbols and units. */
    private val allowed = setOf("EPUB", "PDF", "MP3", "CBR", "CBZ", "BookOrbit", "ISBN")

    @Test
    fun `no English literals are passed to text-rendering calls`() {
        val offenders = root.walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@mapIndexedNotNull null
                    textCall.find(line)?.let { m ->
                        val literal = m.groupValues[1]
                        if (literal in allowed) null else "${file.relativeTo(root).path}:${i + 1}: \"$literal\""
                    }
                }
            }
            .toList()
        assertTrue(
            "Hard-coded UI text found. Move it to res/values/strings.xml and use stringResource():\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
