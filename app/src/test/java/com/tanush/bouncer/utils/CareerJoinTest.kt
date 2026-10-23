package com.tanush.bouncer.utils

import com.tanush.bouncer.data.history.HistoryNameRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CareerJoinTest {
    private val kohli = HistoryNameRow("V Kohli", "ba607b88", "India", 837)

    @Test
    fun `surname, first initial and side give the link`() {
        val rows = listOf(kohli, HistoryNameRow("A Kohli", "eb9082b2", "Germany", 41))
        assertEquals(kohli, CareerJoin.link("Virat Kohli", "India", rows))
    }

    @Test
    fun `a different side or initial gives none`() {
        val rows = listOf(kohli)
        assertNull(CareerJoin.link("Virat Kohli", "Australia", rows))
        assertNull(CareerJoin.link("Anushka Kohli", "India", rows))
        assertNull(CareerJoin.link("Virat Kohli", "", rows))
    }

    @Test
    fun `an ambiguous name gives no link`() {
        val twins = listOf(HistoryNameRow("S Sharma", "1", "India", 10), HistoryNameRow("S Sharma", "2", "India", 90))
        assertNull(CareerJoin.link("Sanjay Sharma", "India", twins))
    }

    @Test
    fun `women's side name and Mohammad spellings still join`() {
        val rows = listOf(HistoryNameRow("M Shami", "z", "India Women", 5))
        assertEquals(rows[0], CareerJoin.link("Mohammed Shami", "India", rows))
        val md = listOf(HistoryNameRow("Md Rizwan", "y", "Pakistan", 5))
        assertEquals(md[0], CareerJoin.link("Mohammad Rizwan", "Pakistan", md))
    }

    @Test
    fun `a Cricbuzz id from the register beats the name test`() {
        val milne = HistoryNameRow("AF Milne", "350bb1b1", "New Zealand", 300, "7625")
        assertEquals(milne, CareerJoin.link("Adam Milne", "Wellington", listOf(milne), "7625"))
        assertNull(CareerJoin.link("Adam Milne", "Wellington", listOf(milne), "1"))
    }

    @Test
    fun `an alias row is the same person, not a second candidate`() {
        val rows = listOf(HistoryNameRow("S Sharma", "1", "India", 10), HistoryNameRow("S Sharma", "1", "India", 10))
        assertEquals(rows[0], CareerJoin.link("Sanjay Sharma", "India", rows))
    }
}
