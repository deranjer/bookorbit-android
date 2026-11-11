package com.bookorbit.feature.player

import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.BookFiles
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

/** Content-based navigation IDs survive reordering and never expose server page offsets. */
object AutoLibraryTree {
    const val TITLES = "audiobooks/title"
    const val AUTHORS = "audiobooks/author"
    const val SERIES = "audiobooks/series"
    private const val GROUP = "alphabet/"
    private const val TARGET_SIZE = 40

    data class AlphabetGroup(val parent: String, val first: String, val last: String)

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun decode(value: String): String? = try { URLDecoder.decode(value, "UTF-8") } catch (_: IllegalArgumentException) { null }
    private fun authorId(name: String) = "$AUTHORS/${encode(name)}"
    private fun seriesId(name: String) = "$SERIES/${encode(name)}"
    private fun groupId(parent: String, first: String, last: String) = "$GROUP${encode(parent)}/${encode(first)}/${encode(last)}"

    fun parseGroup(id: String): AlphabetGroup? {
        if (!id.startsWith(GROUP) || id.length > 16_384) return null
        val parts = id.removePrefix(GROUP).split('/')
        if (parts.size != 3) return null
        return AlphabetGroup(decode(parts[0]) ?: return null, decode(parts[1]) ?: return null, decode(parts[2]) ?: return null)
    }

    fun modes(labels: AutoBrowseTree.Labels) = listOf(
        folder(TITLES, labels.byTitle), folder(AUTHORS, labels.byAuthor), folder(SERIES, labels.bySeries),
    )

    fun children(id: String, books: List<BookCard>, labels: AutoBrowseTree.Labels): List<AutoBrowseTree.BrowseEntry> {
        if (id == AutoBrowseTree.LIBRARY_ID) return modes(labels)
        val entries = entries(id, books, labels, depth = 0)
        val seriesOrder = baseParent(id)?.startsWith("$SERIES/") == true
        return grouped(id, entries, labels, alphabetical = !seriesOrder)
    }

    fun item(id: String, books: List<BookCard>, labels: AutoBrowseTree.Labels): AutoBrowseTree.BrowseEntry? {
        modes(labels).firstOrNull { it.mediaId == id }?.let { return it }
        val group = parseGroup(id)
        if (group != null) return children(group.parent, books, labels).firstOrNull { it.mediaId == id }
        val parent = when {
            id.startsWith("$AUTHORS/") -> AUTHORS
            id.startsWith("$SERIES/") -> SERIES
            else -> return null
        }
        return entries(parent, books, labels, 0).firstOrNull { it.mediaId == id }
    }

    private fun baseParent(id: String, depth: Int = 0): String? {
        if (depth > 8) return null
        val group = parseGroup(id) ?: return id
        return baseParent(group.parent, depth + 1)
    }

    private fun entries(id: String, books: List<BookCard>, labels: AutoBrowseTree.Labels, depth: Int): List<AutoBrowseTree.BrowseEntry> {
        if (depth > 8) throw AutoRequestException(AutoFailure.INVALID_REQUEST)
        parseGroup(id)?.let { group ->
            val parent = entries(group.parent, books, labels, depth + 1)
            val start = parent.indexOfFirst { it.mediaId == group.first }
            val end = parent.indexOfFirst { it.mediaId == group.last }
            if (start < 0 || end < start) throw AutoRequestException(AutoFailure.INVALID_REQUEST)
            return parent.subList(start, end + 1)
        }
        val audio = books.filter { book -> book.files.any { it.format?.lowercase() in BookFiles.AUDIO_FORMATS } }.distinctBy { it.id }
        val selected = when {
            id == TITLES -> audio
            id == AUTHORS -> return audio.flatMap { book ->
                book.authors.filter { it.isNotBlank() }.ifEmpty { listOf("") }.distinct().map { it to book.id }
            }.groupBy({ it.first }, { it.second }).map { (author, ids) ->
                folder(authorId(author), author.ifBlank { labels.unknownAuthor }, labels.bookCount(ids.distinct().size))
            }.sortedWith(entryOrder)
            id == SERIES -> return audio.filter { !it.seriesName.isNullOrBlank() }.groupBy { it.seriesName!! }.map { (series, members) ->
                folder(seriesId(series), series, labels.bookCount(members.size))
            }.sortedWith(entryOrder)
            id.startsWith("$AUTHORS/") -> {
                val name = decode(id.removePrefix("$AUTHORS/")) ?: throw AutoRequestException(AutoFailure.INVALID_REQUEST)
                audio.filter { book -> if (name.isEmpty()) book.authors.none { it.isNotBlank() } else name in book.authors }
            }
            id.startsWith("$SERIES/") -> {
                val name = decode(id.removePrefix("$SERIES/")) ?: throw AutoRequestException(AutoFailure.INVALID_REQUEST)
                return audio.filter { it.seriesName == name }
                    .sortedWith(compareBy<BookCard> { it.seriesIndex ?: Double.MAX_VALUE }.thenBy { key(it.title.orEmpty()) }.thenBy { it.id })
                    .map { bookEntry(it, labels) }
            }
            else -> throw AutoRequestException(AutoFailure.INVALID_REQUEST)
        }
        return selected.map { bookEntry(it, labels) }.sortedWith(entryOrder)
    }

    // Keep each initial contiguous, including punctuation at both ends of Unicode ordering.
    private val entryOrder = compareBy<AutoBrowseTree.BrowseEntry> { initial(it.title) }
        .thenBy { key(it.title) }.thenBy { it.mediaId }
    private fun key(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").trim().lowercase(Locale.ROOT)

    private fun initial(title: String): String {
        val code = key(title).codePoints().findFirst().orElse('#'.code)
        return when {
            Character.isDigit(code) -> "0–9"
            Character.isLetter(code) -> String(Character.toChars(code)).uppercase(Locale.ROOT)
            else -> "#"
        }
    }

    private fun grouped(parent: String, entries: List<AutoBrowseTree.BrowseEntry>, labels: AutoBrowseTree.Labels, alphabetical: Boolean): List<AutoBrowseTree.BrowseEntry> {
        if (entries.size <= TARGET_SIZE) return entries
        if (alphabetical) {
            val letters = entries.groupBy { initial(it.title) }
            if (letters.size in 2..TARGET_SIZE) return letters.map { (letter, members) ->
                folder(groupId(parent, members.first().mediaId, members.last().mediaId), letter)
            }
        }
        // Dense letters, long series and non-Latin alphabets use meaningful endpoint names.
        val size = maxOf(TARGET_SIZE, ((entries.size.toLong() + TARGET_SIZE - 1) / TARGET_SIZE).toInt())
        return entries.chunked(size).map { members ->
            val first = members.first()
            val last = members.last()
            folder(groupId(parent, first.mediaId, last.mediaId), labels.titleRange(first.title, last.title))
        }
    }

    private fun bookEntry(book: BookCard, labels: AutoBrowseTree.Labels) = AutoBrowseTree.BrowseEntry(
        AutoBrowseTree.bookMediaId(book.id), book.title?.takeIf { it.isNotBlank() } ?: labels.audiobook,
        book.narrators.ifEmpty { book.authors }.joinToString(", ").ifBlank { null }, null, true,
    )
    private fun folder(id: String, title: String, subtitle: String? = null) =
        AutoBrowseTree.BrowseEntry(id, title, subtitle, null, false)
}
