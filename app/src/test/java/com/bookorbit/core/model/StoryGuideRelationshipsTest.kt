package com.bookorbit.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class StoryGuideRelationshipsTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun at(s: Int) = StoryGuideAnchor(s, end = true)
    private fun entity(key: String, name: String, section: Int = 0) = StoryGuideEntity(key, "Future canonical $name", "character",
        names = listOf(StoryGuideName(name, at(section))), lines = listOf(StoryGuideLine("Known $name", at(section))))
    private val hero = entity("hero", "Traveller").copy(joins = listOf(StoryGuideJoin("masked", "Future unmasked identity", at(2),
        names = listOf(StoryGuideName("Masked rider", at(0))), lines = listOf(StoryGuideLine("A separate rider", at(0))))))
    private val friend = entity("friend", "Companion")
    private val guide = StoryGuidePayload(1, "exact-revision", (0..2).map { StoryGuideSection(it, "Chapter ${it + 1}") },
        entities = listOf(hero, friend), ties = listOf(
            StoryGuideTie("friendship", "masked", "friend_of", "friend", "friend of", at(0)),
            StoryGuideTie("revelation", "masked", "relative_of", "hero", "relative of", at(2)),
            StoryGuideTie("future", "hero", "enemy_of", "friend", "enemy of", at(2)),
        ))

    @Test fun `default and unknown bounds expose neither entries relationships nor recaps`() {
        for (bound in listOf(-1, -999, 999)) assertEquals(StoryGuideSpoilers.Projection(), StoryGuideSpoilers.project(guide, bound))
    }

    @Test fun `before a join revelation identities and relationship endpoints stay separate`() {
        val result = StoryGuideSpoilers.project(guide, 0)
        assertEquals(listOf("Traveller", "Masked rider", "Companion"), result.entries.map { it.name })
        assertEquals(listOf(StoryGuideSpoilers.Relationship("friendship", "Masked rider", "Companion", "friend_of", "friend of")), result.relationships)
        assertFalse(result.toString().contains("Future canonical"))
        assertFalse(result.toString().contains("Future unmasked"))
        assertFalse(result.toString().contains("enemy of"))
    }

    @Test fun `after explicit completion aliases join and ties point at known merged identity`() {
        val result = StoryGuideSpoilers.project(guide, 2)
        assertEquals(listOf("Traveller", "Companion"), result.entries.map { it.name })
        assertEquals("Traveller", result.relationships.first().from)
        assertEquals(listOf("friendship", "future"), result.relationships.map { it.key }) // A merged self tie is not rendered.
    }

    @Test fun `rolling the chapter boundary back removes the revelation again`() {
        StoryGuideSpoilers.project(guide, 2)
        assertEquals("Masked rider", StoryGuideSpoilers.project(guide, 0).relationships.single().from)
    }

    @Test fun `safe identity lines remain available when all profiles are still in the future`() {
        val book = guide.copy(entities = listOf(friend.copy(profiles = listOf(StoryGuideLine("Future profile", at(2))))))
        assertEquals("Known Companion", StoryGuideSpoilers.project(book, 0).entries.single().text)
    }

    @Test fun `names choose reading order and never fall back to canonical names`() {
        val item = friend.copy(names = listOf(StoryGuideName("Later alias", at(1)), StoryGuideName("Early alias", at(0))))
        assertEquals("Early alias", StoryGuideSpoilers.project(guide.copy(entities = listOf(item)), 1).entries.single().name)
        assertTrue(StoryGuideSpoilers.project(guide.copy(entities = listOf(friend.copy(names = emptyList()))), 2).entries.isEmpty())
    }

    @Test fun `unknown or invisible endpoints do not become canonical-name fallbacks`() {
        val book = guide.copy(entities = listOf(friend, entity("later", "Hidden person", 2)), ties = listOf(
            StoryGuideTie("unknown", "absent", "friend_of", "friend", "friend of", at(0)),
            StoryGuideTie("future-endpoint", "later", "friend_of", "friend", "friend of", at(0)),
        ))
        assertTrue(StoryGuideSpoilers.project(book, 0).relationships.isEmpty())
    }

    @Test fun `unknown relationship types preserve the visible server fallback label`() {
        val book = guide.copy(ties = listOf(StoryGuideTie("new-type", "hero", "new_server_type", "friend", "travels alongside", at(0))))
        assertEquals("travels alongside", StoryGuideSpoilers.project(book, 0).relationships.single().label)
    }

    @Test fun `anchors into missing sections or with negative offsets are ignored`() {
        val book = guide.copy(sections = listOf(StoryGuideSection(0), StoryGuideSection(2)),
            ties = listOf(StoryGuideTie("missing-section", "hero", "friend_of", "friend", "friend of", at(1)),
                StoryGuideTie("bad-offset", "hero", "friend_of", "friend", "friend of", StoryGuideAnchor(0, -1))))
        assertTrue(StoryGuideSpoilers.project(book, 2).relationships.isEmpty())
    }

    @Test fun `ambiguous keys fail closed instead of resolving to an unrelated identity`() {
        val book = guide.copy(entities = listOf(hero, friend, entity("masked", "Different person")))
        val result = StoryGuideSpoilers.project(book, 0)
        assertFalse(result.entries.any { it.key == "masked" })
        assertTrue(result.relationships.isEmpty())
    }

    @Test fun `format one JSON decodes joins ties and quote anchors while ignoring future fields`() {
        val payload = """{"format":1,"textKey":"revision","sections":[{"s":0,"chars":100,"h":"12345678"}],"entities":[{"key":"a","name":"future name","kind":"new-kind","names":[{"text":"Seen","at":{"s":0,"o":2,"q":"Seen"}}],"lines":[{"text":"Safe","through":{"s":0,"end":true}}],"joins":[],"fixed":false},{"key":"b","name":"Other","kind":"place","names":[{"text":"Here","at":{"s":0,"o":0,"q":"Here"}}],"profiles":[{"text":"Known place","through":{"s":0,"end":true}}]}],"ties":[{"key":"a|new|b","a":"a","b":"b","type":"new","label":"visits","at":{"s":0,"o":3,"q":"Seen visits Here"},"count":1,"quotes":[],"fixed":false}]}"""
        val decoded = json.decodeFromString<StoryGuidePayload>(payload)
        assertEquals("visits", StoryGuideSpoilers.project(decoded, 0).relationships.single().label)
        assertEquals("Seen", decoded.entities.first().names.first().at.q)
    }
}
