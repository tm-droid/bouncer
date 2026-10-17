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
}
