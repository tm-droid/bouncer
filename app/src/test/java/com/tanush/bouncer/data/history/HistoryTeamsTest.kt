package com.tanush.bouncer.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryTeamsTest {

    private val teams = HistoryTeams.parse(
        """
        [["India","male","india",1028],["India","female","india__w",323],["India A","male","india-a",60],
         ["Kings XI Punjab","male","kings-xi-punjab",190],["Punjab Kings","male","punjab-kings",88],
         ["Royal Challengers Bangalore","male","royal-challengers-bangalore",250],
         ["Royal Challengers Bengaluru","male","royal-challengers-bengaluru",20],
         ["Mumbai Indians","male","mumbai-indians",291],["Mumbai Indians","female","mumbai-indians__w",37],
         ["Delhi Capitals","male","delhi-capitals",120],["Delhi Daredevils","male","delhi-daredevils",100]]
        """.trimIndent(),
    )!!

    private fun key(name: String, women: Boolean = false) = HistoryTeams.keyFor(teams, name, women)

    @Test fun `rows decode with gender and popularity`() {
        assertEquals(11, teams.size)
        assertEquals(1028, teams.first().matches)
        assertEquals(true, teams[1].women)
    }

    @Test fun `an exact name resolves`() {
        assertEquals("india", key("India"))
        assertEquals("punjab-kings", key("Punjab Kings"))
    }

    @Test fun `the women's side is the women's key`() {
        assertEquals("india__w", key("India Women", women = true))
        assertEquals("mumbai-indians__w", key("Mumbai Indians", women = true))
        assertEquals("mumbai-indians", key("Mumbai Indians", women = false))
    }

    @Test fun `a qualifier is a different side`() {
        assertEquals("india-a", key("India A"))
    }

    @Test fun `a renamed franchise with one candidate resolves`() {
        assertEquals("royal-challengers-bengaluru", key("Royal Challengers Bengaluru"))
        // Only one stored side shares a significant word with this spelling.
        assertEquals("delhi-capitals", key("Delhi Capitals"))
    }

    @Test fun `an ambiguous name is null`() {
        // Shares "royal" and "challengers" with both stored spellings; neither is exact.
        assertNull(key("Royal Challengers"))
        assertNull(key("Delhi"))
    }

    @Test fun `a team with no women's side is null for women`() {
        assertNull(key("Punjab Kings", women = true))
    }

    @Test fun `an unreadable file is null`() {
        assertNull(HistoryTeams.parse("not json"))
        assertNotNull(HistoryTeams.parse("[]"))
    }
}
