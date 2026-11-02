package com.tanush.bouncer.utils

import com.tanush.bouncer.data.SearchRepository
import com.tanush.bouncer.models.CricketMatch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three reported search failures, pinned.
 *
 * Each of these passed silently before the fix because the failure mode was an empty or badly
 * ordered result, never an exception.
 */
class SearchRankingTest {

    private fun match(id: String, title: String, home: String, away: String) = CricketMatch(
        id = id, seriesId = "s1", matchTitle = title,
        matchType = "T20", status = "Live",
        teams = listOf(
            CricketMatch.Team(name = home, score = "100/2", status = "batting", isBatting = true, logoUrl = null),
            CricketMatch.Team(name = away, score = "0/0", status = "bowling", isBatting = false, logoUrl = null),
        ),
        venue = "Kensington Oval", date = "2024-01-01", matchTime = "10:00",
        score = null, currentStatus = "WI 100/2", info = "",
    )

    private val westIndiesPakistan = match("m1", "West Indies vs Pakistan", "West Indies", "Pakistan")

    @Test
    fun `an exact team name does not fuzzy-match a different team`() {
        // levenshtein("india", "indies") == 2 and editTolerance("india") == 2, so the fuzzy tier
        // used to admit every West Indies fixture into a search for India at 0.39.
        runBlocking {
            val results = SearchRepository.search(
                "india", listOf(westIndiesPakistan), emptyList(), SearchRepository.KNOWN_CRICKET_TEAMS
            )
            assertTrue("India should surface as a team", results.teams.any { it.teamName == "India" })
            assertFalse(
                "a West Indies fixture is not a result for \"india\"",
                results.matches.any { it.match.id == "m1" },
            )
        }
    }

    @Test
    fun `the exact team outranks every match result when the team has no fixtures`() {
        runBlocking {
            val results = SearchRepository.search(
                "india", listOf(westIndiesPakistan), emptyList(), SearchRepository.KNOWN_CRICKET_TEAMS
            )
            val bestTeam = results.teams.maxOf { it.relevanceScore }
            val bestMatch = results.matches.maxOfOrNull { it.relevanceScore } ?: 0.0
            // Section order is now driven by these numbers, so the ordering bug is this comparison.
            assertTrue("team $bestTeam should outrank match $bestMatch", bestTeam > bestMatch)
            assertEquals(1.0, bestTeam, 0.0001)
        }
    }

    @Test
    fun `a genuine typo still fuzzy-matches`() {
        // The fix must not be "turn the fuzzy tier off". "kholi" is the same edit distance from
        // "kohli" that "india" is from "indies"; what separates them is that "india" is itself a
        // real team name and "kholi" is not.
        val kohliMatch = match("m2", "Kohli XI vs Rest", "Kohli XI", "Rest")
        runBlocking {
            val results = SearchRepository.search(
                "kholi", listOf(kohliMatch), emptyList(), SearchRepository.KNOWN_CRICKET_TEAMS
            )
            assertTrue("a typo should still find its match", results.matches.any { it.match.id == "m2" })
        }
    }

    @Test
    fun `a team that does have fixtures still surfaces them`() {
        val indiaMatch = match("m3", "India vs Australia", "India", "Australia")
        runBlocking {
            val results = SearchRepository.search(
                "india", listOf(indiaMatch, westIndiesPakistan), emptyList(), SearchRepository.KNOWN_CRICKET_TEAMS
            )
            assertTrue(results.matches.any { it.match.id == "m3" })
            assertFalse(results.matches.any { it.match.id == "m1" })
        }
    }

    @Test
    fun `competition acronyms expand to the words the corpus actually stores`() {
        // The corpus holds "Indian Premier League 2026"; nothing anywhere stores "IPL".
        assertTrue(competitionAliasesFor("IPL").contains("Indian Premier"))
        assertTrue(competitionAliasesFor("ipl").contains("Indian Premier"))
        assertTrue(competitionAliasesFor("WC").contains("World Cup"))
        assertTrue(competitionAliasesFor("CPL").contains("Caribbean Premier"))
        assertTrue(competitionAliasesFor("BBL").contains("Big Bash"))
    }

    @Test
    fun `alias expansion always keeps the original query so it can only widen a search`() {
        assertTrue(competitionAliasesFor("IPL").contains("IPL"))
        assertEquals(listOf("Ashes"), competitionAliasesFor("Ashes"))
        assertEquals(emptyList<String>(), competitionAliasesFor("   "))
    }

    @Test
    fun `an acronym expansion scores against the term that matched, not the raw query`() {
        // The corpus stores "Indian Premier League 2026". Scoring the raw "ipl" against it returns
        // 0.0 at every tier — prefix, word-prefix, substring, and Levenshtein all miss — so the one
        // result the alias table exists to surface would have sorted below everything else.
        // Scoring the matched expansion instead is what makes the Tournaments section rank at all.
        val direct = SearchRepository.relevanceOf("ipl", "indian premier league 2026")
        val viaAlias = SearchRepository.relevanceOf("indian premier", "indian premier league 2026")
        assertEquals("raw acronym cannot score against the stored name", 0.0, direct, 0.0001)
        assertTrue("the expansion must score well above zero", viaAlias > 0.5)
    }
}
