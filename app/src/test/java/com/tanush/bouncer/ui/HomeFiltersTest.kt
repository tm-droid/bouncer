package com.tanush.bouncer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFiltersTest {

    @Test
    fun `no filter is inactive and has nothing to name`() {
        assertFalse(HomeFilters().active)
        assertEquals("", HomeFilters().label)
    }

    @Test
    fun `either filter alone is active and named`() {
        assertTrue(HomeFilters(competition = "League").active)
        assertEquals("league", HomeFilters(competition = "League").label)
        assertTrue(HomeFilters(gender = "Women").active)
        assertEquals("women's", HomeFilters(gender = "Women").label)
    }

    @Test
    fun `both together read as one phrase`() {
        assertEquals("women's international", HomeFilters("International", "Women").label)
    }
}
