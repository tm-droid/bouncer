package com.tanush.bouncer.utils

import com.tanush.bouncer.data.history.HistoryNameRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNameMatchTest {
    private val rows = listOf(
        HistoryNameRow("V Kohli", "a", "India", 837),
        HistoryNameRow("A Kohli", "b", "Germany", 41),
        HistoryNameRow("Nikhil Kohli", "c", "Uttarakhand", 6),
        HistoryNameRow("AB de Villiers", "d", "South Africa", 644),
        HistoryNameRow("Mohammed Shami", "e", "India", 300),
        HistoryNameRow("Md Shamim", "f", "Bangladesh", 120),
        HistoryNameRow("KL Rahul", "g", "India", 400),
        HistoryNameRow("RG Sharma", "h", "India", 900),
        HistoryNameRow("Kohlin Smith", "i", "Wales", 2),
    )

    private fun ids(query: String) = PlayerNameMatch.search(query, rows).map { it.row.id }

    @Test
    fun `surname alone matches exactly, most matches first`() {
        assertEquals(listOf("a", "b", "c"), ids("kohli").take(3))
    }

    @Test
    fun `given name must agree with the initials`() {
        assertEquals(listOf("a"), ids("virat kohli"))
        assertEquals(listOf("a"), ids("v kohli"))
        assertEquals(listOf("b"), ids("a kohli"))
        assertEquals(listOf("c"), ids("nikhil kohli"))
    }

    @Test
    fun `a typo still finds the player and a prefix finds a longer surname`() {
        assertEquals("a", ids("kolhi").first())
        assertTrue("d" in ids("villi"))
    }

    @Test
    fun `an exact surname outranks a prefix even for a smaller player`() {
        val pair = listOf(HistoryNameRow("X Shamim", "p", "A", 1), HistoryNameRow("Y Shamimul", "q", "B", 99999))
        assertEquals(listOf("p", "q"), PlayerNameMatch.search("shamim", pair).map { it.row.id })
    }

    @Test
    fun `particles and initials work`() {
        assertEquals(listOf("d"), ids("ab de villiers"))
        assertEquals(listOf("d"), ids("de villiers"))
        assertEquals(listOf("g"), ids("kl rahul"))
        assertEquals(listOf("h"), ids("rohit sharma"))
    }

    @Test
    fun `Mohammad spellings fold together`() {
        // The exact surname leads; "shami" is also a prefix of "Shamim".
        assertEquals(listOf("e", "f"), ids("mohammad shami"))
        assertEquals(listOf("e", "f"), ids("muhammad shami"))
        assertEquals("f", ids("mohd shamim").first())
    }

    @Test
    fun `letters load the surname file first and cap at three`() {
        assertEquals(listOf('k', 'v'), PlayerNameMatch.letters("virat kohli"))
        assertEquals(listOf('d', 'c', 'b'), PlayerNameMatch.letters("a b c d"))
    }

    @Test
    fun `a short or unrelated surname matches nothing`() {
        assertTrue(ids("zz").isEmpty())
        assertTrue(ids("ko").isEmpty())
    }
}
