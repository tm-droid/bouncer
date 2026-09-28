package com.tanush.bouncer.utils

import com.tanush.bouncer.data.SearchRepository
import org.junit.Assert.*
import org.junit.Test

class SearchRepositoryTest {

    // ═══════════════════════════════════════════════════════════
    // Levenshtein Distance
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `levenshtein distance of identical strings is zero`() {
        assertEquals(0, SearchRepository.levenshteinDistance("india", "india"))
    }

    @Test
    fun `levenshtein distance of empty strings is zero`() {
        assertEquals(0, SearchRepository.levenshteinDistance("", ""))
    }

    @Test
    fun `levenshtein distance with one empty string equals other length`() {
        assertEquals(5, SearchRepository.levenshteinDistance("", "hello"))
        assertEquals(3, SearchRepository.levenshteinDistance("abc", ""))
    }

    @Test
    fun `levenshtein distance single substitution`() {
        assertEquals(1, SearchRepository.levenshteinDistance("cat", "bat"))
    }

    @Test
    fun `levenshtein distance single insertion`() {
        assertEquals(1, SearchRepository.levenshteinDistance("cat", "cats"))
    }

    @Test
    fun `levenshtein distance single deletion`() {
        assertEquals(1, SearchRepository.levenshteinDistance("cats", "cat"))
    }

    @Test
    fun `levenshtein distance completely different strings`() {
        val dist = SearchRepository.levenshteinDistance("abc", "xyz")
        assertEquals(3, dist)
    }

    @Test
    fun `levenshtein distance case sensitive`() {
        val dist = SearchRepository.levenshteinDistance("India", "india")
        assertEquals(1, dist)
    }

    // ═══════════════════════════════════════════════════════════
    // Trending Searches
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `trending searches returns non-empty list`() {
        val trending = SearchRepository.getTrendingSearches()
        assertTrue(trending.isNotEmpty())
    }

    @Test
    fun `trending searches contain well-known cricket terms`() {
        val trending = SearchRepository.getTrendingSearches()
        assertTrue(trending.any { it.contains("India", ignoreCase = true) })
        assertTrue(trending.any { it.contains("IPL", ignoreCase = true) })
    }

    // ═══════════════════════════════════════════════════════════
    // SearchResults Model
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `empty SearchResults reports isEmpty true`() {
        val results = SearchRepository.SearchResults()
        assertTrue(results.isEmpty)
        assertEquals(0, results.totalCount)
    }

    @Test
    fun `SearchResults totalCount sums all categories`() {
        val results = SearchRepository.SearchResults(
            teams = listOf(
                SearchRepository.SearchResult.TeamResult("India", "IND", null, 1.0),
                SearchRepository.SearchResult.TeamResult("Australia", "AUS", null, 0.8),
            ),
        )
        assertFalse(results.isEmpty)
        assertEquals(2, results.totalCount)
    }

    // ═══════════════════════════════════════════════════════════
    // Levenshtein two-row DP correctness
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `levenshtein distance is symmetric`() {
        assertEquals(
            SearchRepository.levenshteinDistance("kitten", "sitting"),
            SearchRepository.levenshteinDistance("sitting", "kitten"),
        )
    }

    @Test
    fun `levenshtein distance for longer vs shorter string`() {
        assertEquals(6, SearchRepository.levenshteinDistance("cricket", "c"))
        assertEquals(6, SearchRepository.levenshteinDistance("c", "cricket"))
    }

    @Test
    fun `levenshtein distance known value kitten to sitting is 3`() {
        assertEquals(3, SearchRepository.levenshteinDistance("kitten", "sitting"))
    }

    @Test
    fun `levenshtein distance is correct for typical cricket names`() {
        assertEquals(1, SearchRepository.levenshteinDistance("kohli", "kohly"))
        assertEquals(1, SearchRepository.levenshteinDistance("india", "indie"))
    }

    // ═══════════════════════════════════════════════════════════
    // searchTeams behavior via search()
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `searchTeams returns team when query matches name`() {
        val fakeMatch = com.tanush.bouncer.models.CricketMatch(
            id = "m1", seriesId = "s1", matchTitle = "India vs Australia",
            matchType = "T20", status = "Live",
            teams = listOf(
                com.tanush.bouncer.models.CricketMatch.Team(
                    name = "India", score = "100/2", status = "batting",
                    isBatting = true, logoUrl = "http://logo/india.png",
                ),
                com.tanush.bouncer.models.CricketMatch.Team(
                    name = "Australia", score = "0/0", status = "bowling",
                    isBatting = false, logoUrl = null,
                ),
            ),
            venue = "MCG", date = "2024-01-01", matchTime = "10:00",
            score = null, currentStatus = "IND 100/2", info = "",
        )
        val teamNames = listOf("India", "Australia")
        kotlinx.coroutines.runBlocking {
            val results = SearchRepository.search("india", listOf(fakeMatch), emptyList(), teamNames)
            assertTrue(results.teams.any { it.teamName == "India" })
        }
    }

    @Test
    fun `searchTeams logo url is populated from match data`() {
        val fakeMatch = com.tanush.bouncer.models.CricketMatch(
            id = "m1", seriesId = "s1", matchTitle = "India vs Australia",
            matchType = "T20", status = "Live",
            teams = listOf(
                com.tanush.bouncer.models.CricketMatch.Team(
                    name = "India", score = "100/2", status = "batting",
                    isBatting = true, logoUrl = "http://logo/india.png",
                ),
                com.tanush.bouncer.models.CricketMatch.Team(
                    name = "Australia", score = "0/0", status = "bowling",
                    isBatting = false, logoUrl = null,
                ),
            ),
            venue = "MCG", date = "2024-01-01", matchTime = "10:00",
            score = null, currentStatus = "IND 100/2", info = "",
        )
        kotlinx.coroutines.runBlocking {
            val results = SearchRepository.search("india", listOf(fakeMatch), emptyList(), listOf("India", "Australia"))
            val indiaResult = results.teams.find { it.teamName == "India" }
            assertNotNull(indiaResult)
            assertEquals("http://logo/india.png", indiaResult!!.logoUrl)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // searchPlayers deduplication
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `searchPlayers deduplicates player appearing as both batsman and bowler`() {
        val sharedPlayer = com.tanush.bouncer.models.ScrapedBatsman(
            name = "Kohli", runs = "50", balls = "40", fours = "5", sixes = "1", strikeRate = "125.0"
        )
        val bowler = com.tanush.bouncer.models.ScrapedBowler(
            name = "Kohli", overs = "4.0", maidens = "0", runs = "30", wickets = "1", economy = "7.5"
        )
        val fakeMatch = com.tanush.bouncer.models.CricketMatch(
            id = "m2", seriesId = "s1", matchTitle = "India vs Australia",
            matchType = "T20", status = "Live",
            teams = listOf(
                com.tanush.bouncer.models.CricketMatch.Team("India", "100/2", "batting", true, null),
                com.tanush.bouncer.models.CricketMatch.Team("Australia", "0/0", "bowling", false, null),
            ),
            venue = "Wankhede", date = "2024-01-01", matchTime = "10:00",
            score = null, currentStatus = "IND 100/2", info = "",
            scrapedBatsmen = listOf(sharedPlayer),
            currentBowler = bowler,
        )
        kotlinx.coroutines.runBlocking {
            val results = SearchRepository.search("kohli", listOf(fakeMatch), emptyList(), emptyList<String>())
            val playerCount = results.players.count { it.playerName == "Kohli" }
            assertEquals("Kohli should appear once even when in both roles", 1, playerCount)
        }
    }

    @Test
    fun `searchPlayers returns player from bowler field`() {
        val bowler = com.tanush.bouncer.models.ScrapedBowler(
            name = "Bumrah", overs = "4.0", maidens = "1", runs = "20", wickets = "2", economy = "5.0"
        )
        val fakeMatch = com.tanush.bouncer.models.CricketMatch(
            id = "m3", seriesId = "s1", matchTitle = "India vs Australia",
            matchType = "T20", status = "Live",
            teams = listOf(
                com.tanush.bouncer.models.CricketMatch.Team("India", "100/2", "batting", true, null),
                com.tanush.bouncer.models.CricketMatch.Team("Australia", "0/0", "bowling", false, null),
            ),
            venue = "Wankhede", date = "2024-01-01", matchTime = "10:00",
            score = null, currentStatus = "IND 100/2", info = "",
            scrapedBatsmen = null,
            currentBowler = bowler,
        )
        kotlinx.coroutines.runBlocking {
            val results = SearchRepository.search("bumrah", listOf(fakeMatch), emptyList(), emptyList<String>())
            assertTrue("Bumrah should be found via currentBowler", results.players.any { it.playerName == "Bumrah" })
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Fuzzy ranking — every substring hit used to tie at exactly 1.0
    // ═══════════════════════════════════════════════════════════

    private fun teamScores(query: String, vararg teamNames: String): Map<String, Double> =
        kotlinx.coroutines.runBlocking {
            SearchRepository.search(query, emptyList(), emptyList(), teamNames.toList())
                .teams.associate { it.teamName to it.relevanceScore }
        }

    @Test
    fun `a substring match no longer ties with a prefix match`() {
        // `if (target.contains(query)) return 1.0` came first, so "India", "West Indies" and
        // "Chennai Super Kings" all scored exactly 1.0 for "in" and sortedByDescending degraded to
        // arbitrary input order.
        val scores = teamScores("in", "Chennai Super Kings", "West Indies", "India")
        val india = scores.getValue("India")
        val westIndies = scores.getValue("West Indies")
        val chennai = scores.getValue("Chennai Super Kings")

        assertTrue("India ($india) must outrank West Indies ($westIndies)", india > westIndies)
        assertTrue("West Indies ($westIndies) must outrank Chennai ($chennai)", westIndies > chennai)
    }

    @Test
    fun `results come back in descending relevance order`() {
        val ordered = kotlinx.coroutines.runBlocking {
            SearchRepository.search(
                "in", emptyList(), emptyList(),
                listOf("Chennai Super Kings", "West Indies", "India"),
            ).teams.map { it.teamName }
        }
        assertEquals(listOf("India", "West Indies", "Chennai Super Kings"), ordered)
    }

    @Test
    fun `an exact match scores one`() {
        assertEquals(1.0, teamScores("india", "India").getValue("India"), 1e-9)
    }

    @Test
    fun `the tiers occupy separate bands so a weaker match never outranks a stronger one`() {
        // Prefix ≥ 0.95 > word-prefix ≥ 0.85 > substring ≥ 0.70. The old 0.95 startsWith tier was
        // unreachable dead code, since startsWith implies contains.
        val prefix = teamScores("in", "India").getValue("India")
        val wordPrefix = teamScores("in", "West Indies").getValue("West Indies")
        val substring = teamScores("in", "Chennai Super Kings").getValue("Chennai Super Kings")

        assertTrue("prefix $prefix", prefix >= 0.95)
        assertTrue("word prefix $wordPrefix", wordPrefix in 0.85..0.95)
        assertTrue("substring $substring", substring in 0.70..0.85)
    }

    @Test
    fun `a longer query still tolerates a typo`() {
        // The edit tolerance is proportional, not absolute — it must not have become so tight that
        // real typos stop matching.
        val scores = teamScores("austrelia", "Australia")
        assertTrue("Australia should survive one substitution", scores.containsKey("Australia"))
    }

    // ═══════════════════════════════════════════════════════════
    // generateSuggestion bounds
    // ═══════════════════════════════════════════════════════════

    private fun suggestionFor(query: String, vararg teamNames: String): String? =
        kotlinx.coroutines.runBlocking {
            SearchRepository.search(query, emptyList(), emptyList(), teamNames.toList()).suggestion
        }

    @Test
    fun `a short query does not suggest a distant candidate`() {
        // The flat `dist <= 3` accepted three edits on a four-character query — that is a different
        // word, not a typo. The tolerance is now proportional to query length.
        assertNull(suggestionFor("zzzz", "zzzaaa"))
    }

    @Test
    fun `a near-miss is still suggested`() {
        // Three edits on a six-character query stays inside the proportional tolerance, and scores
        // too low to surface as a team result, so the suggestion path is what answers.
        assertEquals("zzzaaa", suggestionFor("zzzzzz", "zzzaaa"))
    }

    @Test
    fun `the suggestion scan survives a large candidate set`() {
        // Length prefilter + candidate cap bound the empty-results path, which runs on every
        // keystroke. A candidate whose length differs by more than the tolerance cannot be within
        // it, so skipping it changes nothing but the cost.
        val many = (1..5_000).map { "team-name-number-$it" }.toTypedArray()
        assertEquals("zzzaaa", suggestionFor("zzzzzz", "zzzaaa", *many))
    }
}
