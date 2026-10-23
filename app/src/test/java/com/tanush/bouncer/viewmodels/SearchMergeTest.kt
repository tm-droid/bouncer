package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.data.SearchRepository
import com.tanush.bouncer.data.SearchRepository.SearchResult.PlayerResult
import com.tanush.bouncer.data.history.HistoryNameRow
import com.tanush.bouncer.network.PlayerSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMergeTest {

    private val kohli = PlayerSearchResult(id = "1413", name = "Virat Kohli", country = "India")

    @Test
    fun `a network player keeps its Cricbuzz id`() {
        val merged = mergePlayers("kohli", emptyList(), listOf(kohli), networkQuery = "kohli")
        assertEquals("1413", merged.single().cricbuzzId)
    }

    @Test
    fun `a network player is scored by the device, not a flat 0_9`() {
        val exact = mergePlayers("virat kohli", emptyList(), listOf(kohli), "virat kohli").single()
        assertEquals(1.0, exact.relevanceScore, 1e-9)
        val substring = mergePlayers("rat", emptyList(), listOf(kohli), "rat").single()
        assertTrue("a substring hit is not a 0.9", substring.relevanceScore < 0.85)
    }

    @Test
    fun `a typo only the server caught gets the floor, not zero`() {
        val merged = mergePlayers("vrt khli", emptyList(), listOf(kohli), "vrt khli")
        assertEquals(NETWORK_TYPO_FLOOR, merged.single().relevanceScore, 1e-9)
    }

    @Test
    fun `a local hit for the same person is enriched, not duplicated`() {
        val local = PlayerResult("Virat Kohli", "", "Batsman", 0.96)
        val merged = mergePlayers("virat", listOf(local), listOf(kohli), "virat")
        assertEquals(1, merged.size)
        assertEquals("1413", merged.single().cricbuzzId)
        assertEquals("India", merged.single().teamName)
        assertEquals("Batsman", merged.single().role)
    }

    @Test
    fun `two players who share a name stay two players`() {
        val other = PlayerSearchResult(id = "9999", name = "Virat Kohli", country = "Delhi")
        val local = PlayerResult("Virat Kohli", "India", "", 1.0, cricbuzzId = "1413")
        val merged = mergePlayers("virat kohli", listOf(local), listOf(kohli, other), "virat kohli")
        assertEquals(setOf("1413", "9999"), merged.map { it.cricbuzzId }.toSet())
    }

    @Test
    fun `stale network results survive a keystroke only where they still match`() {
        val singh = PlayerSearchResult(id = "2", name = "Virat Singh", country = "India")
        val merged = mergePlayers("virat k", emptyList(), listOf(kohli, singh), networkQuery = "virat")
        assertEquals(listOf("Virat Kohli"), merged.map { it.playerName })
    }

    @Test
    fun `stale network results get no floor`() {
        val merged = mergePlayers("zzz", emptyList(), listOf(kohli), networkQuery = "virat")
        assertTrue(merged.isEmpty())
    }

    @Test
    fun `blank server ids and images become null`() {
        val blank = PlayerSearchResult(id = "", name = "Virat Kohli", country = "", imageUrl = "")
        val merged = mergePlayers("kohli", emptyList(), listOf(blank), "kohli").single()
        assertNull(merged.cricbuzzId)
        assertNull(merged.imageUrl)
    }

    @Test
    fun `local sources merge on the normalised name, best score kept`() {
        val live = PlayerResult("José Buttler", "England", "Batsman", 0.9)
        val cached = PlayerResult("Jose Buttler", "England", "Keeper", 0.97)
        assertEquals(listOf(cached), mergeLocalPlayers(listOf(live), listOf(cached)))
    }

    private val histKohli = SearchRepository.SearchResult.PastPlayerResult(
        HistoryNameRow("V Kohli", "ba607b88", "India", 837), 0.5,
    )
    private val cricbuzzKohli = PlayerResult("Virat Kohli", "India", "Batter", 0.8, cricbuzzId = "1413")

    @Test
    fun `one Cricbuzz row for the same person hides the history player`() {
        assertTrue(attachHistory(listOf(histKohli), listOf(cricbuzzKohli)).isEmpty())
    }

    @Test
    fun `no Cricbuzz row, a different side, or two candidates keep the history player`() {
        assertEquals(listOf(histKohli), attachHistory(listOf(histKohli), emptyList()))
        assertEquals(listOf(histKohli), attachHistory(listOf(histKohli), listOf(cricbuzzKohli.copy(teamName = "Germany"))))
        val twin = cricbuzzKohli.copy(cricbuzzId = "9")
        assertEquals(listOf(histKohli), attachHistory(listOf(histKohli), listOf(cricbuzzKohli, twin)))
    }

    @Test
    fun `a perfect history player never makes the Cricbuzz lookup redundant`() {
        val results = SearchRepository.SearchResults(pastPlayers = listOf(histKohli.copy(relevanceScore = 1.0)))
        assertFalse(hasConfidentPlayer(results.players))
        assertFalse(results.isEmpty)
        assertTrue(hasConfidentPlayer(listOf(cricbuzzKohli.copy(relevanceScore = 0.95))))
        assertEquals(listOf(cricbuzzKohli), mergeLocalPlayers(listOf(cricbuzzKohli)))
    }
}
