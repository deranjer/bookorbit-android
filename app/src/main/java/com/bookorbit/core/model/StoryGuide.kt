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
@Serializable data class StoryGuideAnchor(val s: Int, val o: Int? = null, val end: Boolean = false)
@Serializable data class StoryGuideName(val text: String, val at: StoryGuideAnchor)
@Serializable data class StoryGuideLine(val text: String, val through: StoryGuideAnchor)
@Serializable data class StoryGuideSection(val s: Int, val title: String? = null)
@Serializable data class StoryGuideEntity(
    val key: String, val name: String, val kind: String, val names: List<StoryGuideName> = emptyList(),
    val lines: List<StoryGuideLine> = emptyList(), val profiles: List<StoryGuideLine> = emptyList(),
)
@Serializable data class StoryGuidePayload(
    val format: Int, val textKey: String, val sections: List<StoryGuideSection>,
    val entities: List<StoryGuideEntity> = emptyList(), val summaries: List<StoryGuideLine> = emptyList(),
)

/** Explicit completed-section bounds only. Never infer spoiler safety from a percentage. */
object StoryGuideSpoilers {
    data class Entry(val name: String, val text: String)
    fun entries(payload: StoryGuidePayload, completedSection: Int): List<Entry> {
        if (completedSection < 0 || payload.sections.none { it.s == completedSection }) return emptyList()
        return payload.entities.mapNotNull { entity ->
            // The canonical name may be a future revelation; use only an already-seen name.
            val name = entity.names.firstOrNull { it.at.s in 0..completedSection }?.text ?: return@mapNotNull null
            val candidates = (entity.profiles.ifEmpty { entity.lines }).filter { it.through.s in 0..completedSection }
            val line = candidates.maxWithOrNull(compareBy<StoryGuideLine> { it.through.s }.thenBy { if (it.through.end) Int.MAX_VALUE else it.through.o ?: 0 })
                ?: return@mapNotNull null
            Entry(name, line.text)
        }
    }
    fun recap(payload: StoryGuidePayload, completedSection: Int): List<String> =
        if (completedSection < 0 || payload.sections.none { it.s == completedSection }) emptyList()
        else payload.summaries.filter { it.through.s in 0..completedSection }.map { it.text }
}
