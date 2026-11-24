package com.bookorbit.feature.reader

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

/** Exact /epub/{bookId}/media-overlay contract. No text/audio percentage alignment. */
@Serializable
data class NativeNarrationPlaylist(
    val bookId: Int, val fileId: Int?, val durationSeconds: Double? = null,
    val items: List<NativeNarrationClip>, val sections: List<NativeNarrationSection>,
    val resources: List<NativeNarrationResource>,
)

@Serializable
data class NativeNarrationClip(
    val index: Int, val sectionIndex: Int, val smilHref: String,
    val textHref: String, val textFragment: String? = null,
    val audioHref: String, val audioMimeType: String,
    val clipBeginSeconds: Double, val clipEndSeconds: Double? = null,
    val durationSeconds: Double? = null, val label: String? = null,
) {
    val fragment get() = textHref + (textFragment?.let { "#$it" } ?: "")
}

@Serializable
data class NativeNarrationSection(
    val index: Int, val href: String, val label: String? = null, val smilHref: String,
    val startSeconds: Double, val durationSeconds: Double? = null,
)

@Serializable
data class NativeNarrationResource(val href: String, val mediaType: String, val size: Long)

/** Validated, immutable clip order shared by the upcoming Media3 engine and checkpoint adapter. */
class NativeNarrationTimeline(playlist: NativeNarrationPlaylist, expectedBookId: Int, expectedFileId: Int) {
    val bookId = playlist.bookId
    val fileId = expectedFileId
    val clips = playlist.items.toList()
    val resources = playlist.resources.toList()
    val sections = playlist.sections.toList()
    val durationSeconds: Double? = if (clips.all { it.clipEndSeconds != null })
        clips.sumOf { checkNotNull(it.clipEndSeconds) - it.clipBeginSeconds } else null

    init {
        require(expectedBookId > 0 && expectedFileId > 0 && bookId == expectedBookId && playlist.fileId == expectedFileId)
        require(clips.size in 1..100_000 && resources.size in 1..100_000 && sections.size in 1..100_000)
        require(resources.map { it.href }.distinct().size == resources.size)
        require(sections.map { it.index }.distinct().size == sections.size)
        playlist.durationSeconds?.let { require(clock(it)) }
        val byResource = resources.associateBy { it.href }
        val bySection = sections.associateBy { it.index }
        resources.forEach { require(path(it.href) && it.size >= 0 && mime(it.mediaType)) }
        sections.forEach {
            require(it.index in 0..100_000 && path(it.href) && path(it.smilHref) && clock(it.startSeconds))
            it.durationSeconds?.let { duration -> require(clock(duration)) }
        }
        require(sections.zipWithNext().all { (a, b) -> a.index < b.index })
        clips.forEachIndexed { index, clip ->
            require(clip.index == index && clip.sectionIndex in 0..100_000)
            require(path(clip.smilHref) && path(clip.textHref) && path(clip.audioHref))
            require(clip.textFragment == null || clip.textFragment.length in 1..4096 && '\u0000' !in clip.textFragment)
            require(clip.fragment.length <= 8192)
            require(clock(clip.clipBeginSeconds) && mime(clip.audioMimeType))
            clip.clipEndSeconds?.let { require(clock(it) && it > clip.clipBeginSeconds) }
            clip.durationSeconds?.let { duration ->
                require(clock(duration))
                clip.clipEndSeconds?.let { end -> require(kotlin.math.abs(end - clip.clipBeginSeconds - duration) <= 0.01) }
            }
            val resource = byResource[clip.audioHref]
            require(resource != null && resource.mediaType == clip.audioMimeType)
            val section = bySection[clip.sectionIndex]
            require(section != null && section.href == clip.textHref && section.smilHref == clip.smilHref)
        }
        require(clips.zipWithNext().all { (a, b) -> a.sectionIndex <= b.sectionIndex })
    }

    data class Location(val index: Int, val assetSeconds: Double, val clipOffsetMs: Long)

    /** An un-narrated section advances to the next actual SMIL section, never a guessed audio time. */
    fun startAtSection(sectionIndex: Int): Location? = if (sectionIndex !in 0..100_000) null
        else clips.firstOrNull { it.sectionIndex >= sectionIndex }?.let(::start)
    fun next(index: Int): Location? = clips.getOrNull(index + 1)?.takeIf { index in clips.indices }?.let(::start)
    fun previous(index: Int): Location? = clips.getOrNull(index - 1)?.takeIf { index in clips.indices }?.let(::start)
    fun previousChapter(sectionIndex: Int): Location? = if (sectionIndex !in 0..100_000) null
        else clips.lastOrNull { it.sectionIndex < sectionIndex }?.let(::start)
    fun nextChapter(sectionIndex: Int): Location? = if (sectionIndex !in 0..100_000) null
        else clips.firstOrNull { it.sectionIndex > sectionIndex }?.let(::start)

    /** Restore only the exact section, fragment, resource and asset clock. Never use text progress. */
    fun restore(position: ReadAlongPosition): Location? {
        if (!position.valid() || position.completed) return null
        val candidates = clips.filter { it.sectionIndex == position.sectionIndex && it.fragment == position.fragment && it.audioHref == position.audioSrc }
        val containing = candidates.filter { position.audioSeconds >= it.clipBeginSeconds &&
            (it.clipEndSeconds == null || position.audioSeconds < it.clipEndSeconds) }
        if (containing.size == 1) {
            val clip = containing.single()
            return Location(clip.index, position.audioSeconds, ((position.audioSeconds - clip.clipBeginSeconds) * 1000).roundToLong())
        }
        // A stale/out-of-clip clock may restart a uniquely identified fragment, not another asset.
        return candidates.singleOrNull()?.let(::start)
    }

    /** Media3 positions are relative to the clipped item, whereas checkpoints use asset seconds. */
    fun checkpoint(index: Int, clipPositionMs: Long, rate: Double, completed: Boolean = false): ReadAlongPosition? {
        val clip = clips.getOrNull(index) ?: return null
        if (clipPositionMs < 0 || !rate.isFinite() || rate !in 0.5..2.0) return null
        var assetSeconds = clip.clipBeginSeconds + clipPositionMs / 1000.0
        clip.clipEndSeconds?.let { assetSeconds = minOf(assetSeconds, it) }
        return ReadAlongPosition(clip.sectionIndex, clip.fragment, clip.audioHref, assetSeconds, rate, completed).takeIf { it.valid() }
    }

    private fun start(clip: NativeNarrationClip) = Location(clip.index, clip.clipBeginSeconds, 0)

    companion object {
        private fun clock(value: Double) = value.isFinite() && value in 0.0..1e9
        private fun mime(value: String) = value.length in 3..256 && value.matches(Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+"))
        internal fun path(value: String) = value.length in 1..8192 && !value.startsWith('/') && ':' !in value &&
            '\\' !in value && '\u0000' !in value && '?' !in value && '#' !in value &&
            value.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }
}
