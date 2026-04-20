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
}
