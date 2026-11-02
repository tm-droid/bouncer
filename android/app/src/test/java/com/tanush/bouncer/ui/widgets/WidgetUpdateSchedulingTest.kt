package com.tanush.bouncer.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scheduling decision only. Enqueueing itself needs WorkManager and so lives in androidTest;
 * extracting the arithmetic is what makes the part that can be wrong testable at all.
 */
class WidgetUpdateSchedulingTest {

    @Test
    fun `the user preference actually reaches the delay`() {
        // Both arms of `if (isLive) LIVE_INTERVAL_MS else IDLE_INTERVAL_MS` were 30 minutes, and the
        // Refresh Interval setting was read by nothing at all.
        assertEquals(5 * 60_000L, WidgetUpdateWorker.resolveUpdateDelayMs(5))
        assertEquals(15 * 60_000L, WidgetUpdateWorker.resolveUpdateDelayMs(15))
        assertEquals(30 * 60_000L, WidgetUpdateWorker.resolveUpdateDelayMs(30))
        assertEquals(60 * 60_000L, WidgetUpdateWorker.resolveUpdateDelayMs(60))
    }

    @Test
    fun `distinct settings give distinct delays`() {
        val delays = listOf(5, 15, 30, 60).map { WidgetUpdateWorker.resolveUpdateDelayMs(it) }
        assertEquals(delays.size, delays.distinct().size)
    }

    @Test
    fun `an interval below the floor is raised to it`() {
        // A stale preference from when the control was denominated in seconds would otherwise ask
        // for a 5-millisecond-scale self-rescheduling chain.
        assertEquals(
            WidgetUpdateWorker.MIN_INTERVAL_MIN * 60_000L,
            WidgetUpdateWorker.resolveUpdateDelayMs(1),
        )
        assertEquals(
            WidgetUpdateWorker.MIN_INTERVAL_MIN * 60_000L,
            WidgetUpdateWorker.resolveUpdateDelayMs(0),
        )
        assertEquals(
            WidgetUpdateWorker.MIN_INTERVAL_MIN * 60_000L,
            WidgetUpdateWorker.resolveUpdateDelayMs(-30),
        )
    }

    @Test
    fun `an interval above the ceiling is capped so the fallback cannot be disabled`() {
        assertEquals(
            WidgetUpdateWorker.MAX_INTERVAL_MIN * 60_000L,
            WidgetUpdateWorker.resolveUpdateDelayMs(Int.MAX_VALUE),
        )
    }

    @Test
    fun `the floor is below the PeriodicWorkRequest minimum, which is why one-time work is used`() {
        // The file's own header claims sub-15-minute intervals are the reason for the
        // self-rescheduling design; this asserts the floor actually exercises that.
        assertTrue(WidgetUpdateWorker.MIN_INTERVAL_MIN < 15)
    }

    @Test
    fun `scheduled and immediate work use different unique names`() {
        // Sharing one name would make an immediate push-triggered refresh cancel the pending
        // fallback, or be dropped by it.
        assertTrue(WidgetUpdateWorker.WORK_NAME != WidgetUpdateWorker.IMMEDIATE_WORK_NAME)
    }
}
