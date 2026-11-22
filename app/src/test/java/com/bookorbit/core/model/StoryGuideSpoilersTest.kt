package com.bookorbit.core.model

import org.junit.Assert.*
import org.junit.Test

class StoryGuideSpoilersTest {
    private val guide = StoryGuidePayload(1, "edition-key", listOf(StoryGuideSection(0, "One"), StoryGuideSection(1, "Two")),
        entities = listOf(StoryGuideEntity("hero", "Secret future identity", "person",
            names = listOf(StoryGuideName("Known name", StoryGuideAnchor(0)), StoryGuideName("Secret future identity", StoryGuideAnchor(1))),
            profiles = listOf(StoryGuideLine("Safe identity", StoryGuideAnchor(0, end = true)), StoryGuideLine("Future revelation", StoryGuideAnchor(1, end = true))))),
        summaries = listOf(StoryGuideLine("First chapter", StoryGuideAnchor(0, end = true)), StoryGuideLine("Second chapter", StoryGuideAnchor(1, end = true))))

    @Test fun `no completed chapter means no spoilers by default`() {
        assertTrue(StoryGuideSpoilers.entries(guide, -1).isEmpty())
        assertTrue(StoryGuideSpoilers.recap(guide, -1).isEmpty())
    }
    @Test fun `canonical future aliases and future profiles do not leak into earlier chapters`() {
        assertEquals(listOf(StoryGuideSpoilers.Entry("Known name", "Safe identity")), StoryGuideSpoilers.entries(guide, 0))
        assertEquals(listOf("First chapter"), StoryGuideSpoilers.recap(guide, 0))
    }
    @Test fun `unknown section does not silently reveal the entire book`() {
        assertTrue(StoryGuideSpoilers.entries(guide, 999).isEmpty())
    }
}
