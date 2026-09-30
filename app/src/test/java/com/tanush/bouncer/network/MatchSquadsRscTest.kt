package com.tanush.bouncer.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `/cricket-match-squads/{id}` against captured markup, both shapes: after the toss (`playing XI`
 * + `bench`) and before it (`Squad` only). Captured 2026-09-30 — IND v WI 2nd ODI (151543, in
 * progress) and PAK v BAN (171051, preview).
 */
class MatchSquadsRscTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    @Test
    fun `after the toss each side has a playing eleven and a bench`() {
        val squads = checkNotNull(MatchSquadsRsc.parse(fixture("cricbuzz_match_squads_xi.html")))
        assertTrue(squads.hasPlayingXi)

        with(squads.team1) {
            assertEquals("2", teamId)
            assertEquals("IND", shortName)
            assertEquals(11, playingXi.size)
            assertEquals(4, bench.size)
            assertTrue(squad.isEmpty())
            assertEquals(SquadPlayer("576", "Rohit Sharma", "Batter", captain = false, keeper = false), playingXi.first())
            assertEquals("Shubman Gill", playingXi.single { it.captain }.name)
            assertEquals("KL Rahul", playingXi.single { it.keeper }.name)
        }
        with(squads.team2) {
            assertEquals("10", teamId)
            assertEquals(11, playingXi.size)
            assertEquals(3, bench.size)
            val hope = playingXi.single { it.captain }
            assertEquals("Shai Hope", hope.name)
            assertTrue("captain and keeper can be the same player", hope.keeper)
        }
    }

    @Test
    fun `before the toss there is a squad and no eleven`() {
        val squads = checkNotNull(MatchSquadsRsc.parse(fixture("cricbuzz_match_squads_preview.html")))
        assertFalse(squads.hasPlayingXi)
        assertEquals("PAK", squads.team1.shortName)
        assertEquals(15, squads.team1.squad.size)
        assertTrue(squads.team1.playingXi.isEmpty())
        assertEquals("BAN", squads.team2.shortName)
        assertEquals(13, squads.team2.squad.size)
        assertEquals("Parvez Hossain Emon", squads.team2.squad.single { it.keeper }.name)
    }

    @Test
    fun `a page without team sheets is null, not an empty squad`() {
        assertNull(MatchSquadsRsc.parse(fixture("cricbuzz_points_table.html")))
    }
}
