package com.bookorbit.core.model

/** User-facing editions are not physical tracks. A server book currently has one audio edition. */
object BookEditions {
    enum class Kind { TEXT, AUDIO }

    data class Edition(val key: String, val kind: Kind, val files: List<BookFileRef>, val durationSeconds: Double? = null) {
        val fileIds: Set<Int> get() = files.map { it.id }.toSet()
        val sizeBytes: Long? get() = if (files.all { it.sizeBytes != null && it.sizeBytes >= 0 }) files.sumOf { it.sizeBytes!! } else null
        fun isOffline(ids: Set<Int>) = fileIds.all { it in ids }
    }

    fun of(book: BookDetail): List<Edition> = buildList {
        BookFiles.downloadCandidates(book).filter { BookFiles.isOpenableEbook(it.format) }.forEach {
            add(Edition("text:${it.id}", Kind.TEXT, listOf(it)))
        }
        val audio = BookFiles.audioFiles(book).distinctBy { it.id }
        if (audio.isNotEmpty()) add(Edition("audio", Kind.AUDIO, audio,
            book.audioMetadata?.durationSeconds ?: audio.takeIf { files -> files.all { it.durationSeconds != null } }?.sumOf { it.durationSeconds!! }))
    }

    /** One audio selection always expands to ALL remaining tracks, preserving precise server IDs. */
    fun additionalFiles(editions: List<Edition>, selectedKeys: Set<String>, offlineIds: Set<Int>): Set<Int> {
        require(selectedKeys.isNotEmpty() && editions.map { it.key }.containsAll(selectedKeys))
        return editions.filter { it.key in selectedKeys }.flatMap { it.files }.map { it.id }.toSet() - offlineIds
    }
}
