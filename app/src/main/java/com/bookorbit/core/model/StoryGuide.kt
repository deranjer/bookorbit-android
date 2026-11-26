package com.bookorbit.core.model

import kotlinx.serialization.Serializable

@Serializable data class StoryGuideBuild(val allowed: Boolean, val reason: String? = null, val limit: Int? = null)
@Serializable data class StoryGuideInfo(val builtAt: String, val coverage: Double, val format: Int, val outdated: Boolean = false, val modelsChanged: Boolean = false)
@Serializable data class StoryGuideJob(val kind: String, val state: String, val reason: String? = null, val phase: String? = null, val done: Int? = null, val total: Int? = null)
@Serializable data class StoryGuideEstimate(val words: Int? = null, val minutes: Double? = null, val cost: Double? = null)
@Serializable data class StoryGuideStatus(
    val fileId: Int?, val canManage: Boolean, val build: StoryGuideBuild, val textKey: String? = null,
    val guide: StoryGuideInfo? = null, val job: StoryGuideJob? = null, val estimate: StoryGuideEstimate? = null,
)
@Serializable data class StoryGuideBuildRequest(val fileId: Int, val kind: String = "build")
@Serializable data class StoryGuideFileRequest(val fileId: Int)
@Serializable data class StoryGuideAnchor(val s: Int, val o: Int? = null, val end: Boolean = false, val q: String? = null)
@Serializable data class StoryGuideName(val text: String, val at: StoryGuideAnchor)
@Serializable data class StoryGuideLine(val text: String, val through: StoryGuideAnchor)
@Serializable data class StoryGuideSection(val s: Int, val title: String? = null)
@Serializable data class StoryGuideJoin(
    val key: String, val name: String, val at: StoryGuideAnchor,
    val names: List<StoryGuideName> = emptyList(), val lines: List<StoryGuideLine> = emptyList(),
    val profiles: List<StoryGuideLine> = emptyList(),
)
@Serializable data class StoryGuideTie(
    val key: String, val a: String, val type: String, val b: String, val label: String,
    val at: StoryGuideAnchor,
)
@Serializable data class StoryGuideEntity(
    val key: String, val name: String, val kind: String, val names: List<StoryGuideName> = emptyList(),
    val lines: List<StoryGuideLine> = emptyList(), val profiles: List<StoryGuideLine> = emptyList(),
    val joins: List<StoryGuideJoin> = emptyList(),
)
@Serializable data class StoryGuidePayload(
    val format: Int, val textKey: String, val sections: List<StoryGuideSection>,
    val entities: List<StoryGuideEntity> = emptyList(), val summaries: List<StoryGuideLine> = emptyList(),
    val ties: List<StoryGuideTie> = emptyList(),
)

/** Explicit completed-section bounds only. Never infer spoiler safety from a percentage. */
object StoryGuideSpoilers {
    data class Entry(val name: String, val text: String)
    data class ProjectedEntry(val key: String, val kind: String, val name: String, val text: String)
    data class Relationship(val key: String, val from: String, val to: String, val type: String, val label: String)
    data class Projection(val entries: List<ProjectedEntry> = emptyList(), val relationships: List<Relationship> = emptyList(), val recaps: List<String> = emptyList())

    fun project(payload: StoryGuidePayload, completedSection: Int): Projection {
        if (completedSection < 0 || payload.sections.none { it.s == completedSection }) return Projection()
        val visibleSections = payload.sections.map { it.s }.filter { it in 0..completedSection }.toSet()
        fun visible(anchor: StoryGuideAnchor) = anchor.s in visibleSections && (anchor.o == null || anchor.o >= 0)
        val anchorOrder = compareBy<StoryGuideAnchor> { it.s }.thenBy { if (it.end) Int.MAX_VALUE else it.o ?: 0 }
        fun safeName(names: List<StoryGuideName>) = names.filter { visible(it.at) && it.text.isNotBlank() }
            .minWithOrNull(compareBy(anchorOrder) { it.at })?.text
        fun safeLine(profiles: List<StoryGuideLine>, lines: List<StoryGuideLine>): String? {
            fun latest(values: List<StoryGuideLine>) = values.filter { visible(it.through) && it.text.isNotBlank() }
                .maxWithOrNull(compareBy(anchorOrder) { it.through })?.text
            return latest(profiles) ?: latest(lines)
        }
        val entries = mutableListOf<ProjectedEntry>()
        val aliases = mutableMapOf<String, String>()
        val ambiguous = mutableSetOf<String>()
        fun mapAlias(alias: String, owner: String) {
            if (alias.isBlank()) return
            val previous = aliases.putIfAbsent(alias, owner)
            if (previous != null && previous != owner) ambiguous.add(alias)
        }
        for (entity in payload.entities) {
            val merged = entity.joins.filter { visible(it.at) }
            val name = safeName(entity.names) ?: safeName(merged.flatMap { it.names })
            val text = safeLine(entity.profiles, entity.lines)
                ?: safeLine(merged.flatMap { it.profiles }, merged.flatMap { it.lines })
            if (entity.key.isNotBlank() && name != null && text != null) {
                entries.add(ProjectedEntry(entity.key, entity.kind, name, text))
                mapAlias(entity.key, entity.key)
                merged.forEach { mapAlias(it.key, entity.key) }
            }
            // Before the revelation a joined identity is an independent entry, not an alias.
            for (join in entity.joins.filterNot { visible(it.at) }) {
                val joinName = safeName(join.names) ?: continue
                val joinText = safeLine(join.profiles, join.lines) ?: continue
                if (join.key.isBlank()) continue
                entries.add(ProjectedEntry(join.key, entity.kind, joinName, joinText))
                mapAlias(join.key, join.key)
            }
        }
        val duplicateKeys = entries.groupingBy { it.key }.eachCount().filterValues { it > 1 }.keys
        val safeEntries = entries.filterNot { it.key in duplicateKeys || it.key in ambiguous }
        val byKey = safeEntries.associateBy { it.key }
        val relationships = payload.ties.filter { visible(it.at) }.mapNotNull { tie ->
            if (tie.a in ambiguous || tie.b in ambiguous) return@mapNotNull null
            val from = aliases[tie.a]?.let(byKey::get) ?: return@mapNotNull null
            val to = aliases[tie.b]?.let(byKey::get) ?: return@mapNotNull null
            if (from.key == to.key || tie.label.isBlank()) return@mapNotNull null
            Relationship(tie.key, from.name, to.name, tie.type, tie.label)
        }.distinctBy { it.key }
        val recaps = payload.summaries.filter { visible(it.through) }.map { it.text }
        return Projection(safeEntries, relationships, recaps)
    }
    fun entries(payload: StoryGuidePayload, completedSection: Int): List<Entry> =
        project(payload, completedSection).entries.map { Entry(it.name, it.text) }
    fun recap(payload: StoryGuidePayload, completedSection: Int): List<String> =
        project(payload, completedSection).recaps
}
