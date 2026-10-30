package com.tanush.bouncer.utils

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class DateRangesTest {

    @Test
    fun `a range collapses what its ends share`() {
        assertEquals("7–20 Aug 2026", formatDateRange(LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 20)))
        assertEquals("7 Aug – 20 Sep 2026", formatDateRange(LocalDate.of(2026, 8, 7), LocalDate.of(2026, 9, 20)))
        assertEquals(
            "28 Dec 2026 – 4 Jan 2027",
            formatDateRange(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 4)),
        )
    }

    @Test
    fun `a missing or equal end is the start alone`() {
        assertEquals("7 Aug 2026", formatDateRange(LocalDate.of(2026, 8, 7), null))
        assertEquals("7 Aug 2026", formatDateRange(LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 7)))
    }

    @Test
    fun `epoch strings are formatted and anything else is shown as it came`() {
        val aug7 = LocalDate.of(2026, 8, 7).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli().toString()
        val aug20 = LocalDate.of(2026, 8, 20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli().toString()
        assertEquals("7–20 Aug 2026", formatEpochDateRange(aug7, aug20, ZoneOffset.UTC))
        assertEquals("7 Aug 2026", formatEpochDateRange(aug7, "", ZoneOffset.UTC))
        assertEquals("TBC", formatEpochDateRange("TBC", "", ZoneOffset.UTC))
        assertEquals("", formatEpochDateRange("", "", ZoneOffset.UTC))
    }
}
