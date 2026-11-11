package com.bookorbit.feature.player

import com.bookorbit.feature.downloads.DownloadsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** One search implementation for car result lists and voice-to-play requests. */
@Singleton
class AutoSearch @Inject constructor(
    private val catalog: AutoCatalog,
    private val downloads: DownloadsRepository,
    private val progress: AudioProgressRepository,
) {
    suspend fun find(query: String, labels: AutoBrowseTree.Labels, currentBookId: Int? = null): List<AutoBrowseTree.BrowseEntry> {
        val term = normalize(query)
        if (term.isBlank()) return catalog.continueListening(labels)
        val records = downloads.downloads.first()
        val metadata = records.associateBy { AutoBrowseTree.bookMediaId(it.bookId) }
        val local = AutoBrowseTree.downloadedAudiobooks(records, labels).filter { entry ->
            val record = metadata[entry.mediaId]
            matches(term, "${entry.title} ${record?.authors.orEmpty()} ${record?.narrators.orEmpty()}")
        }
        var failure: Exception? = null
        val online = try { catalog.page(0, labels, query.trim()).entries }
        catch (e: CancellationException) {
            if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            failure = e
            emptyList()
        } catch (e: Exception) { failure = e; emptyList() }
        if (local.isEmpty() && online.isEmpty()) failure?.let { throw it }
        val recent = progress.recent().mapIndexed { index, p -> AutoBrowseTree.bookMediaId(p.bookId) to index }.toMap()
        val current = currentBookId?.let(AutoBrowseTree::bookMediaId)
        return (local + online).distinctBy { it.mediaId }.sortedWith(
            compareBy<AutoBrowseTree.BrowseEntry> {
                val title = normalize(it.title)
                when {
                    title == term -> 0
                    title.startsWith(term) -> 1
                    title.contains(term) -> 2
                    else -> 3
                }
            }.thenBy { if (it.mediaId == current) 0 else 1 }
                .thenBy { recent[it.mediaId] ?: Int.MAX_VALUE }
                .thenBy { normalize(it.title) }.thenBy { it.mediaId },
        ).take(40)
    }

    companion object {
        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
        private fun matches(term: String, text: String): Boolean {
            val normalized = normalize(text)
            return term.split(' ').all { it in normalized }
        }
    }
}
