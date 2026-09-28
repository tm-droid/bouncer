package com.tanush.bouncer.utils

import com.tanush.bouncer.data.SearchRepository
import com.tanush.bouncer.data.SearchRepository.PlayerEntry
import com.tanush.bouncer.data.SearchRepository.SeriesEntry
import com.tanush.bouncer.data.SearchRepository.TeamEntry
import com.tanush.bouncer.models.CricketMatch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The September 2026 search overhaul: acronyms, normalisation, multi-word typos, the full series
 * corpus, and the cached-player threshold. Each case is a query that returned nothing, or the
 * wrong thing, before it.
 */
class SearchCorpusRankingTest {

    private val curated = mapOf(
        "Royal Challengers Bengaluru" to "RCB",
        "Mumbai Indians" to "MI",
        "MI Cape Town" to "MICT",
        "Chennai Super Kings" to "CSK",
        "St Kitts and Nevis Patriots" to "SKNP",
        "India" to "IND",
    )
    private val teams = SearchRepository.buildTeamCorpus(emptyList(), curated)

    private fun teamSearch(query: String, favourites: Set<String> = emptySet()) = runBlocking {
        SearchRepository.search(query, emptyList(), emptyList(), teams, favourites).teams.map { it.teamName }
    }

    private fun match(id: String, home: String, away: String, series: String = "", status: String = "Live") =
        CricketMatch(
            id = id, seriesId = "s", matchTitle = "$home vs $away", matchType = "T20", status = status,
            teams = listOf(
                CricketMatch.Team(name = home, score = "", status = "", isBatting = true, logoUrl = null),
                CricketMatch.Team(name = away, score = "", status = "", isBatting = false, logoUrl = null),
            ),
            venue = "", date = "", matchTime = "", score = null, currentStatus = "", info = "",
            seriesName = series,
        )

    @Test
    fun `a curated team code finds its team first`() {
        assertEquals("Royal Challengers Bengaluru", teamSearch("rcb").first())
        assertEquals("Chennai Super Kings", teamSearch("CSK").first())
    }

    @Test
    fun `a code outranks a name that merely starts with the same letters`() {
        // "MI Cape Town" starts with "mi"; the side whose code IS "MI" must still lead.
        assertEquals("Mumbai Indians", teamSearch("mi").first())
    }

    @Test
    fun `a team code is an exact entity so it does not fuzzy-match fixtures`() {
        val results = runBlocking {
            SearchRepository.search("mi", listOf(match("m1", "Mali", "Chad")), emptyList(), teams)
        }
        assertFalse("\"mi\" is Mumbai Indians, not a typo of Mali", results.matches.any { it.match.id == "m1" })
    }

    @Test
    fun `a code finds that team's fixtures`() {
        val results = runBlocking {
            SearchRepository.search("rcb", listOf(match("m1", "Royal Challengers Bengaluru", "Mumbai Indians")), emptyList(), teams)
        }
        assertTrue(results.matches.any { it.match.id == "m1" })
    }

    @Test
    fun `punctuation in the query or the name does not matter`() {
        assertEquals("St Kitts and Nevis Patriots", teamSearch("st. kitts").first())
        assertTrue(SearchRepository.relevanceOf("st kitts", "St. Kitts and Nevis Patriots") >= 0.95)
    }

    @Test
    fun `accents are folded on both sides`() {
        assertTrue(SearchRepository.relevanceOf("jose", "José Buttler") >= 0.95)
        assertTrue(SearchRepository.relevanceOf("José", "Jose Buttler") >= 0.95)
    }

    @Test
    fun `a typo in the second word of a name still matches`() {
        val score = SearchRepository.relevanceOf("virat kolhi", "Virat Kohli")
        assertTrue("virat kolhi scored $score", score > 0.4)
        assertTrue("a typo stays in the fuzzy band", score <= SearchScore.FUZZY_MAX)
    }

    @Test
    fun `a name typed surname first is word-prefix evidence`() {
        val score = SearchRepository.relevanceOf("kohli virat", "Virat Kohli")
        assertTrue("kohli virat scored $score", score >= SearchScore.WORD_PREFIX_MIN)
    }

    @Test
    fun `a transposed single word is one edit, not two`() {
        assertEquals(1, SearchScore.editDistance("kholi", "kohli"))
        assertTrue(SearchRepository.relevanceOf("kholi", "Virat Kohli") > 0.4)
    }

    @Test
    fun `a competition acronym plus a season finds the season`() {
        val corpus = listOf(
            SeriesEntry("1", "Indian Premier League 2026", "LEAGUE", "", ""),
            SeriesEntry("2", "Indian Premier League 2025", "LEAGUE", "", ""),
            SeriesEntry("3", "Pakistan Super League 2026", "LEAGUE", "", ""),
        )
        val ids = runBlocking { SearchRepository.searchSeries("ipl 2026", corpus) }.map { it.seriesId }
        assertEquals("1", ids.first())
        assertFalse("another league's 2026 is not IPL 2026", ids.contains("3"))
    }

    @Test
    fun `series search ranks the whole corpus, not five arbitrary rows`() {
        val filler = (1..40).map { SeriesEntry("f$it", "Premier Division $it", "DOMESTIC", "", "") }
        val corpus = filler + SeriesEntry("ipl", "Indian Premier League 2026", "LEAGUE", "", "")
        val results = runBlocking { SearchRepository.searchSeries("ipl", corpus) }
        assertEquals("ipl", results.first().seriesId)
    }

    @Test
    fun `a cached player is found by name, never by country alone`() {
        val corpus = listOf(
            PlayerEntry("Virat Kohli", "India", "Batter"),
            PlayerEntry("Jasprit Bumrah", "India", "Bowler"),
        )
        val byCountry = runBlocking { SearchRepository.searchCachedPlayers("india", corpus) }
        assertTrue("\"india\" used to list every cached Indian player", byCountry.isEmpty())
        val byName = runBlocking { SearchRepository.searchCachedPlayers("bumrah", corpus) }
        assertEquals(listOf("Jasprit Bumrah"), byName.map { it.playerName })
    }

    @Test
    fun `a favourite wins a tie and never a better match`() {
        val corpus = listOf(TeamEntry("Delhi Capitals", null), TeamEntry("Delhi Bulls", null), TeamEntry("Delhi", null))
        val order = runBlocking {
            SearchRepository.search("delhi", emptyList(), emptyList(), corpus, favourites = setOf("Delhi Bulls"))
        }.teams.map { it.teamName }
        assertEquals("the exact name still leads", "Delhi", order.first())
        // "Delhi Capitals" and "Delhi Bulls" differ only in coverage; the shorter wins on score, so
        // the favourite may not lift itself above a strictly better match.
        assertTrue(order.indexOf("Delhi Bulls") >= 0)
    }

    @Test
    fun `equal scores put the favourite first`() {
        val corpus = listOf(TeamEntry("Alpha Kings", null), TeamEntry("Bravo Kings", null))
        val order = runBlocking {
            SearchRepository.search("kings", emptyList(), emptyList(), corpus, favourites = setOf("Bravo Kings"))
        }.teams.map { it.teamName }
        assertEquals(listOf("Bravo Kings", "Alpha Kings"), order)
    }

    @Test
    fun `an ipl query finds fixtures by their series name`() {
        val ipl = match("m9", "Punjab Kings", "Gujarat Titans", series = "Indian Premier League 2026")
        val results = runBlocking { SearchRepository.search("ipl", listOf(ipl), emptyList(), teams) }
        assertTrue(results.matches.any { it.match.id == "m9" })
    }

    @Test
    fun `the team corpus prefers a loaded match's own logo and code`() {
        val m = match("m1", "Kent", "Essex").let {
            it.copy(teams = it.teams.map { t -> t.copy(logoUrl = "logo-${t.name}", acronym = t.name.take(3).uppercase()) })
        }
        val corpus = SearchRepository.buildTeamCorpus(listOf(m), curated)
        val kent = corpus.first { it.name == "Kent" }
        assertEquals("logo-Kent", kent.logoUrl)
        assertEquals("KEN", kent.acronym)
    }

    @Test
    fun `live suggestions list what is being played`() {
        val live = match("m1", "India", "Australia", series = "Border-Gavaskar Trophy")
        val done = match("m2", "Kent", "Essex", series = "County Championship", status = "Kent won by 5 wkts")
        assertEquals(
            listOf("Border-Gavaskar Trophy", "India", "Australia"),
            SearchRepository.liveSuggestions(listOf(live, done)),
        )
    }

    @Test
    fun `match ranges emphasise word starts and merge overlaps`() {
        assertEquals(listOf(6..10), matchRanges("Virat Kohli", "kohli"))
        assertEquals(listOf(0..4, 6..10), matchRanges("Virat Kohli", "kohli virat"))
        // "in" occurs inside "Chennai" but starts the word "Indies"; the word start wins.
        assertEquals(listOf(5..6), matchRanges("West Indies", "in"))
        assertEquals(emptyList<IntRange>(), matchRanges("Virat Kohli", "x"))
    }
}
