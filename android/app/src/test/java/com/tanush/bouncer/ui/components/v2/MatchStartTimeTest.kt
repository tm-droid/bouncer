package com.tanush.bouncer.ui.components.v2

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchStartTimeTest {

    private val now = ZonedDateTime.of(2026, 10, 17, 12, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun `the status line wins when it parses`() {
        assertEquals(
            LocalDateTime.of(2026, 10, 18, 14, 0),
            matchStartTime("Match starts at Oct 18, 14:00 GMT", startDateMs = 0L, now = now),
        )
    }

    @Test
    fun `a blank status falls back to the list timestamp`() {
        val ms = ZonedDateTime.of(2026, 10, 19, 9, 30, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(LocalDateTime.of(2026, 10, 19, 9, 30), matchStartTime("", ms, now))
    }

    @Test
    fun `any other status keeps the row and gives no start`() {
        assertNull(matchStartTime("Toss delayed due to rain", 1L, now))
        assertNull(matchStartTime("", null, now))
    }

    private val min = 60_000L
    private val hr = 60 * min

    @Test
    fun `under a minute ahead or already past is now`() {
        assertEquals(StartLabel.Now, startLabelOf(startMs = 59_999L, nowMs = 0L))
        assertEquals(StartLabel.Now, startLabelOf(startMs = 0L, nowMs = 0L))
        assertEquals(StartLabel.Now, startLabelOf(startMs = 0L, nowMs = 3 * hr))
    }

    @Test
    fun `minutes band runs one to 59`() {
        assertEquals(StartLabel.InMinutes(1), startLabelOf(min, 0L))
        assertEquals(StartLabel.InMinutes(42), startLabelOf(42 * min + 30_000L, 0L))
        assertEquals(StartLabel.InMinutes(59), startLabelOf(59 * min, 0L))
    }

    @Test
    fun `sixty minutes is whole hours and whole hours carry no minutes`() {
        assertEquals(StartLabel.InHoursMinutes(1, 0), startLabelOf(60 * min, 0L))
        assertEquals(StartLabel.InHoursMinutes(2, 15), startLabelOf(2 * hr + 15 * min, 0L))
        assertEquals(StartLabel.InHoursMinutes(11, 59), startLabelOf(11 * hr + 59 * min, 0L))
    }

    @Test
    fun `twelve hours out is absolute`() {
        assertEquals(StartLabel.Absolute, startLabelOf(12 * hr, 0L))
        assertEquals(StartLabel.Absolute, startLabelOf(40 * hr, 0L))
    }
}
