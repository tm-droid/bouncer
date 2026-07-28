package com.tanush.bouncer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The fake calendar this replaces computed `deltaYears*365 + deltaMonths*30 + deltaDays`, which is
 * wrong in both directions at a month boundary.
 */
class DateProximityTest {

    @Test
    fun `consecutive days across a month boundary are one day apart`() {
        // The old arithmetic gave (31 - 30) - 30 = ... 0, so these read as the SAME day.
        assertEquals(1L, daysBetween("2024-01-31", "2024-02-01"))
        assertEquals(1L, daysBetween("2024-04-30", "2024-05-01"))
    }

    @Test
    fun `Feb 28 to Mar 1 in a leap year is two days`() {
        // Old arithmetic: 30 - 27 = 3, so these read as not adjacent and lost the proximity score.
        assertEquals(2L, daysBetween("2024-02-28", "2024-03-01"))
    }

    @Test
    fun `Feb 28 to Mar 1 in a non-leap year is one day`() {
        assertEquals(1L, daysBetween("2023-02-28", "2023-03-01"))
    }

    @Test
    fun `new year rollover is one day`() {
        // Old arithmetic: 365 - 11*30 - 30 = 5.
        assertEquals(1L, daysBetween("2023-12-31", "2024-01-01"))
    }

    @Test
    fun `same day is zero and order does not matter`() {
        assertEquals(0L, daysBetween("2024-06-01", "2024-06-01"))
        assertEquals(5L, daysBetween("2024-06-01", "2024-06-06"))
        assertEquals(5L, daysBetween("2024-06-06", "2024-06-01"))
    }

    @Test
    fun `a timestamp suffix is tolerated`() {
        assertEquals(0L, daysBetween("2024-06-01T18:30:00Z", "2024-06-01"))
    }

    @Test
    fun `unparseable input yields null rather than a bogus distance`() {
        // take(10) previously fed whatever it found to an integer split.
        assertNull(daysBetween("not a date", "2024-06-01"))
        assertNull(daysBetween("01/06/2024", "2024-06-01"))
        assertNull(daysBetween("", "2024-06-01"))
        assertNull(daysBetween(null, "2024-06-01"))
        assertNull(daysBetween("2024-02-31", "2024-06-01"))
    }

    @Test
    fun `parseIsoDatePrefix accepts a leading date only`() {
        assertEquals("2024-06-01", parseIsoDatePrefix("2024-06-01 extra")?.toString())
        assertNull(parseIsoDatePrefix("on 2024-06-01"))
    }
}
