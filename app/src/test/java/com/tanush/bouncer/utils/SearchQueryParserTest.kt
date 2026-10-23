package com.tanush.bouncer.utils

import com.tanush.bouncer.data.SearchRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryParserTest {

    private val teams = SearchRepository.buildTeamCorpus(
        emptyList(),
        mapOf("India" to "IND", "Australia" to "AUS", "Indian Hawks" to "IHK", "Australia A" to "AUA"),
    )

    @Test
    fun `a team pair with year and stage parses into facets`() {
        val p = parseQuery("ind v aus 2011 final")
        assertEquals("ind" to "aus", p.pair)
        assertEquals(2011, p.year)
        assertEquals("FINAL", p.stage)
        assertEquals(listOf("ind", "aus"), p.words)
        assertEquals("ind v aus 2011 final", p.text)
    }

    @Test
    fun `versus spellings all split a pair`() {
        listOf("vs", "v", "versus").forEach {
            assertEquals("india" to "pakistan", parseQuery("india $it pakistan").pair)
        }
    }

    @Test
    fun `a dangling v is not a pair`() {
        assertNull(parseQuery("india v").pair)
        assertNull(parseQuery("v india").pair)
    }

    @Test
    fun `formats and plural forms`() {
        assertEquals("T20I", parseQuery("t20is india").format)
        assertEquals("T20", parseQuery("t20 world cup").format)
        assertEquals("ODI", parseQuery("odis").format)
        assertEquals("TEST", parseQuery("ashes test").format)
    }

    @Test
    fun `stage words`() {
        assertEquals("SEMI", parseQuery("semi final india").stage)
        assertEquals("SEMI", parseQuery("semis").stage)
        assertEquals("QUALIFIER", parseQuery("eliminator").stage)
        assertEquals(listOf("india"), parseQuery("semi final india").words)
    }

    @Test
    fun `a season keeps both years and a lone year has no season`() {
        val p = parseQuery("ashes 2011 12")
        assertEquals(2011, p.year)
        assertEquals("2011/12", p.season)
        assertNull(parseQuery("ashes 2011").season)
    }

    @Test
    fun `non-year numbers stay words`() {
        val p = parseQuery("sa20 12345")
        assertNull(p.year)
        assertEquals(listOf("sa20", "12345"), p.words)
    }

    @Test
    fun `a facet-only query falls back to its own text`() {
        val p = parseQuery("t20")
        assertTrue(p.words.isEmpty())
        assertEquals("t20", p.entityText)
    }

    @Test
    fun `a side resolves by code first, then by name prefix`() {
        assertEquals(listOf("Australia"), resolveSide("aus", teams))
        assertEquals(listOf("India"), resolveSide("india", teams))
        assertTrue("Indian Hawks" in resolveSide("indi", teams))
        assertTrue(resolveSide("zzz", teams).isEmpty())
    }
}
