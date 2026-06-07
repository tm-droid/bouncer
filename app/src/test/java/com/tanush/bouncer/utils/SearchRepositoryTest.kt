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
}
