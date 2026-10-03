package com.tanush.bouncer.data.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The trainer keys `h2h-stats` in the sides' original case; the lookup must find a pair whatever
 * case and order the app's own team names arrive in. Keys below are copied from the live file.
 */
class H2HRepositoryKeyTest {

    private val file = JSONObject(
        """
        {
          "Australia|||Sri Lanka": {"team1": "Australia", "team2": "Sri Lanka", "totalMatches": 35},
          "North-West Warriors|||Northern Knights": {"team1": "North-West Warriors", "team2": "Northern Knights", "totalMatches": 12},
          "Australia|||Sri Lanka|female": {"team1": "Australia", "team2": "Sri Lanka", "totalMatches": 9}
        }
        """.trimIndent()
    )

    @Test
    fun `a pair is found in the case the trainer wrote it`() {
        val index = indexH2HPairs(file)
        assertEquals(3, index.size)
        assertEquals(35, index[h2hPairKey("Australia", "Sri Lanka")]?.optInt("totalMatches"))
    }

    @Test
    fun `order and case of the query do not matter`() {
        val index = indexH2HPairs(file)
        assertNotNull(index[h2hPairKey("sri lanka", "AUSTRALIA")])
        assertNotNull(index[h2hPairKey(" Northern Knights ", "North-West Warriors")])
    }

    @Test
    fun `a pair the file does not hold is absent`() {
        assertNull(indexH2HPairs(file)[h2hPairKey("Australia", "India")])
    }

    @Test
    fun `a women's pair has its own record, found from the names the app shows`() {
        val index = indexH2HPairs(file)
        val women = h2hPairKey("Australia Women", "Sri Lanka Women", isWomensPair("Australia Women", "Sri Lanka Women"))
        assertEquals(9, index[women]?.optInt("totalMatches"))
        assertEquals(35, index[h2hPairKey("Australia", "Sri Lanka")]?.optInt("totalMatches"))
        assertEquals("Australia", archiveSideName("Australia Women"))
    }
}
