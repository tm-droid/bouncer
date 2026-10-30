package com.tanush.bouncer.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchStartTimeTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val singapore = ZoneId.of("Asia/Singapore")

    @Test
    fun `GMT start is converted to the reader's zone`() {
        val now = ZonedDateTime.of(2026, 9, 12, 9, 0, 0, 0, kolkata)
        assertEquals(
            LocalDateTime.of(2026, 9, 12, 13, 0),
            parseMatchStart("Match starts at Sep 12, 07:30 GMT", now),
        )
    }

    @Test
    fun `a late GMT start lands on the next local day`() {
        val now = ZonedDateTime.of(2026, 9, 12, 12, 0, 0, 0, singapore)
        val start = parseMatchStart("Match starts at Sep 12, 23:00 GMT", now)!!
        assertEquals(LocalDateTime.of(2026, 9, 13, 7, 0), start)
        assertEquals(1L, calendarDaysUntil(start, now.toLocalDate()))
    }

    @Test
    fun `the year is the one nearest now across new year`() {
        val now = ZonedDateTime.of(2026, 12, 31, 20, 0, 0, 0, singapore)
        assertEquals(
            LocalDateTime.of(2027, 1, 2, 17, 30),
            parseMatchStart("Match starts at Jan 2, 09:30 GMT", now),
        )
    }

    @Test
    fun `calendar days count dates not hours`() {
        val today = LocalDate.of(2026, 9, 12)
        assertEquals(0L, calendarDaysUntil(LocalDateTime.of(2026, 9, 12, 23, 59), today))
        assertEquals(1L, calendarDaysUntil(LocalDateTime.of(2026, 9, 13, 0, 1), today))
        assertEquals(3L, calendarDaysUntil(LocalDateTime.of(2026, 9, 15, 10, 0), today))
    }

    @Test
    fun `other text is not a start time`() {
        val now = ZonedDateTime.of(2026, 9, 12, 9, 0, 0, 0, kolkata)
        assertNull(parseMatchStart("Uganda opt to bat", now))
        assertNull(parseMatchStart("Match starts at Foo 12, 07:30 GMT", now))
        assertNull(parseMatchStart("Match starts at Feb 31, 07:30 GMT", now))
        assertNull(parseMatchStart("", now))
    }
}
