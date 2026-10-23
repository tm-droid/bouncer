package com.tanush.bouncer.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pair file is found from `idx/teams.json` keys, whatever order or gender the app names the sides in. */
class H2HRepositoryKeyTest {

    @Test
    fun `a pair's path sorts the slugs, so either order finds the same file`() {
        assertEquals("s/h2h/australia__india.json", h2hPath("india", "australia"))
        assertEquals("s/h2h/australia__india.json", h2hPath("australia", "india"))
        assertEquals("s/h2h/sri-lanka__west-indies.json", h2hPath("west-indies", "sri-lanka"))
    }

    @Test
    fun `a women's pair has its own file, marked once at the end`() {
        assertEquals("s/h2h/australia__india__w.json", h2hPath("india__w", "australia__w"))
    }

    @Test
    fun `a women's side is the plain name with the gender apart`() {
        assertTrue(isWomensPair("Australia Women", "Sri Lanka Women"))
        assertTrue(isWomensPair("India", "England Women's"))
        assertFalse(isWomensPair("Australia", "Sri Lanka"))
        assertEquals("Australia", archiveSideName(" Australia Women "))
        assertEquals("West Indies", archiveSideName("West Indies Women's"))
    }

    @Test
    fun `a match type reads as the format whose counters exist, never another`() {
        val all = setOf("Test", "ODI", "T20I")
        assertEquals("Test", h2hFormat("TEST", all))
        assertEquals("ODI", h2hFormat("ODI", all))
        assertEquals("T20I", h2hFormat("T20", all))
        assertEquals("T20", h2hFormat("T20", setOf("T20", "ODI")))
        assertNull(h2hFormat("ODI", setOf("Test", "T20I")))
        assertNull(h2hFormat("FC", all))
    }
}
