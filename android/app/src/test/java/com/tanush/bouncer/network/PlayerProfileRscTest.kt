package com.tanush.bouncer.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the App Router profile parser against real captured markup.
 *
 * The bug this guards was invisible for exactly the reason a fixture is used here: Cricbuzz moved
 * `/profiles/{id}/{slug}` off the pages router, `__NEXT_DATA__` disappeared, and the Jsoup fallback
 * silently produced a profile with a name and nothing else. Nothing threw and nothing was reported.
 */
class PlayerProfileRscTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    private val kohli by lazy {
        PlayerProfileRsc.parse(fixture("cricbuzz_profile_rsc_kohli.html"), "1413", "Fallback Name")
    }

    @Test
    fun `personal details come from the flight player object`() {
        val profile = assertNotNull(kohli).let { kohli!! }
        assertEquals("Virat Kohli", profile.name)
        assertEquals("India", profile.country)
        assertEquals("Batsman", profile.role)
        assertEquals("Right Handed Bat", profile.battingStyle)
        assertEquals("Right-arm medium", profile.bowlingStyle)
        assertEquals("1413", profile.id)
    }

    @Test
    fun `the date of birth drops the running age`() {
        // The page serves both "November 05, 1988 (37 years)" and the bare date. The age is stale
        // the moment the profile is cached, so the bare date is the one that may be stored.
        assertEquals("November 05, 1988", kohli!!.dateOfBirth)
    }

    @Test
    fun `the image url is built from faceImageId, not the raw image field`() {
        assertEquals("https://www.cricbuzz.com/a/img/v1/152x152/i1/c616517/player.jpg", kohli!!.imageUrl)
    }

    @Test
    fun `teams are split out of the comma-separated string`() {
        val teams = kohli!!.teams
        assertTrue("India" in teams)
        assertTrue("Royal Challengers Bengaluru" in teams)
        assertTrue(teams.none { it.isBlank() || it != it.trim() })
    }

    @Test
    fun `an RSC back-reference never reaches the profile as prose`() {
        // `bio` is served as "$26" — React hoisted the real text elsewhere in the stream. Storing
        // the reference would put a literal "$26" on the player screen.
        assertEquals("", kohli!!.bio)
    }

    @Test
    fun `batting stats are read by row label, per format column`() {
        val test = kohli!!.stats.getValue("Test")
        assertEquals(123, test.matches)
        assertEquals(210, test.innings)
        assertEquals(9230, test.runs)
        assertEquals("254", test.highScore)
        assertEquals(46.85, test.average, 0.001)
        assertEquals(55.58, test.strikeRate, 0.001)
        assertEquals(30, test.hundreds)
        assertEquals(31, test.fifties)
        assertEquals(1027, test.fours)
        assertEquals(30, test.sixes)
    }

    @Test
    fun `bowling stats land on the same format entry`() {
        val odi = kohli!!.stats.getValue("ODI")
        assertEquals(5, odi.wickets)
        assertEquals(136.0, odi.bowlingAverage, 0.001)
        assertEquals(6.16, odi.economy, 0.001)
        assertEquals("1/13", odi.bestBowling)
        assertEquals(0, odi.fiveWickets)
    }

    @Test
    fun `the T20 column is stored under the label the app displays`() {
        // Cricbuzz's header is "T20"; every screen and DEFAULT_FORMATS say "T20I". A mismatch here
        // is a permanently empty tab rather than an error.
        assertTrue("T20I" in kohli!!.stats.keys)
        assertTrue("T20" !in kohli!!.stats.keys)
        assertEquals(4188, kohli!!.stats.getValue("T20I").runs)
        assertEquals(9336, kohli!!.stats.getValue("IPL").runs)
    }

    @Test
    fun `recent form reads the score cell, not the leading match id`() {
        val first = kohli!!.recentForm.first()
        assertEquals(74, first.runs)
        assertEquals(60, first.balls)
        assertEquals("ENG", first.opponent)
        assertEquals("ODI", first.format)
        assertEquals("19 Jul 26", first.date)
    }

    @Test
    fun `a page with no flight player object parses to null, not an empty profile`() {
        // Null is what lets the caller fall through to another strategy. An empty PlayerProfile
        // would look like a successful scrape of a player with no career.
        assertNull(PlayerProfileRsc.parse("<html><body>nothing here</body></html>", "1413", "Fallback Name"))
    }
}
