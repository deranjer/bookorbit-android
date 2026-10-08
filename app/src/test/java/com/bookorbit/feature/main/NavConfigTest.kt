package com.bookorbit.feature.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavConfigTest {
    @Test
    fun `nothing saved gives the default bar`() {
        assertEquals(NavConfig.DEFAULT, NavConfig.parse(null))
        assertEquals(NavConfig.DEFAULT, NavConfig.parse(""))
        assertEquals(NavConfig.DEFAULT, NavConfig.parse("nonsense,,"))
    }

    @Test
    fun `a saved bar round trips in order`() {
        val items = listOf(NavItem.LIBRARY, NavItem.DOWNLOADS, NavItem.HOME, NavItem.YOU)
        assertEquals(items, NavConfig.parse(NavConfig.serialize(items)))
    }

    @Test
    fun `unknown and duplicate ids are ignored`() {
        assertEquals(listOf(NavItem.HOME, NavItem.LIBRARY, NavItem.YOU), NavConfig.parse("home,future-thing,home,library,you"))
    }

    @Test
    fun `You is always kept, even if the saved list lost it`() {
        assertEquals(listOf(NavItem.HOME, NavItem.SEARCH, NavItem.YOU), NavConfig.parse("home,search"))
        // Over the limit and no You: You still wins the last slot.
        val parsed = NavConfig.parse("home,library,search,notes,stats,downloads")
        assertEquals(NavConfig.MAX_ITEMS, parsed.size)
        assertTrue(NavItem.YOU in parsed)
    }

    @Test
    fun `You cannot be removed`() {
        assertEquals(NavConfig.DEFAULT, NavConfig.removed(NavConfig.DEFAULT, NavItem.YOU))
        assertFalse(NavItem.NOTES in NavConfig.removed(NavConfig.DEFAULT, NavItem.NOTES))
    }

    @Test
    fun `the bar holds at most five items`() {
        assertEquals(NavConfig.DEFAULT, NavConfig.added(NavConfig.DEFAULT, NavItem.DOWNLOADS))
        val smaller = NavConfig.removed(NavConfig.DEFAULT, NavItem.NOTES)
        assertEquals(smaller + NavItem.DOWNLOADS, NavConfig.added(smaller, NavItem.DOWNLOADS))
        assertEquals(smaller, NavConfig.added(smaller, NavItem.HOME))
    }

    @Test
    fun `items move within bounds only`() {
        assertEquals(listOf(NavItem.LIBRARY, NavItem.HOME), NavConfig.moved(listOf(NavItem.HOME, NavItem.LIBRARY), 0, 1))
        assertEquals(NavConfig.DEFAULT, NavConfig.moved(NavConfig.DEFAULT, 0, -1))
        assertEquals(NavConfig.DEFAULT, NavConfig.moved(NavConfig.DEFAULT, 4, 1))
    }

    @Test
    fun `Book Drop is hidden and not offered without access`() {
        val items = listOf(NavItem.HOME, NavItem.BOOK_DROP, NavItem.YOU)
        assertEquals(listOf(NavItem.HOME, NavItem.YOU), NavConfig.visible(items, canUseBookDrop = false))
        assertEquals(items, NavConfig.visible(items, canUseBookDrop = true))
        assertFalse(NavItem.BOOK_DROP in NavConfig.available(NavConfig.DEFAULT, canUseBookDrop = false))
        assertTrue(NavItem.BOOK_DROP in NavConfig.available(NavConfig.DEFAULT, canUseBookDrop = true))
    }
}
