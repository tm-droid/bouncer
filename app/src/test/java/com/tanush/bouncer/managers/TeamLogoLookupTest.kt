package com.tanush.bouncer.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the normalized-name fallback in the logo lookup.
 *
 * The lookup was exact-key only for its whole life, and the bundled asset ships keys a scraped name
 * cannot match verbatim — `"Namibia  U19"` carries a double space, and `"Cape cobras"` and
 * `"Cape Cobras"` are both present as separate keys. A miss rendered no crest at all and reported
 * nothing.
 */
class TeamLogoLookupTest {

    private val logos = mapOf(
        "India" to "https://static.cricbuzz.com/i1/c776162/i.jpg",
        "Namibia  U19" to "https://static.cricbuzz.com/i1/c1/i.jpg",
        "St. Kitts & Nevis Patriots" to "https://static.cricbuzz.com/i1/c2/i.jpg",
    )
    private val normalized = buildNormalizedLogoIndex(logos)

    private fun lookup(name: String) = lookupLogo(logos, normalized, name)

    @Test
    fun `an exact key still wins`() {
        assertEquals(logos["India"], lookup("India"))
    }

    @Test
    fun `case drift resolves`() {
        assertEquals(logos["India"], lookup("india"))
    }

    @Test
    fun `the double space Cricbuzz ships resolves from a single-spaced name`() {
        assertEquals(logos["Namibia  U19"], lookup("Namibia U19"))
    }

    @Test
    fun `punctuation drift resolves`() {
        assertEquals(logos["St. Kitts & Nevis Patriots"], lookup("St Kitts & Nevis Patriots"))
    }

    @Test
    fun `an ampersand spelled out is still a miss`() {
        // normalizeTeamName strips punctuation to whitespace; it does not translate "&" into "and",
        // so this pair does not normalize alike. Pinned so the limit is a known one rather than a
        // surprise: closing it means dropping noise words, which would collide distinct sides.
        assertNull(lookup("St Kitts and Nevis Patriots"))
    }

    @Test
    fun `an unknown team is still null`() {
        assertNull(lookup("Not A Team"))
    }

    @Test
    fun `a variant takes its parent side's crest`() {
        assertEquals(logos["India"], lookup("India A Women"))
        assertEquals(logos["India"], lookup("India U-19"))
    }

    @Test
    fun `a variant's own crest beats its parent's`() {
        assertEquals(logos["Namibia  U19"], lookup("Namibia U19"))
    }

    @Test
    fun `a variant of an unknown side is still null`() {
        assertNull(lookup("Not A Team Women"))
    }

    @Test
    fun `the first key wins a normalized collision`() {
        val collided = mapOf("Cape cobras" to "first", "Cape Cobras" to "second")
        assertEquals("first", lookupLogo(collided, buildNormalizedLogoIndex(collided), "cape COBRAS"))
    }
}
