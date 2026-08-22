package com.tanush.bouncer.data.remote

import com.tanush.bouncer.data.CacheConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The EXECUTIONS quota must pre-emptively rate-cap, not only react to a 402.
 *
 * It was breaker-only until player search existed, and the reason was sound at the time: every
 * function call was driven by a match event or a fixed heartbeat, so the rate was structurally
 * bounded and there was nothing a local cap could catch that the schedule did not already. A
 * search box is the first caller driven by typing. With no cap, a debounce regression turns one
 * lookup into one per keystroke with nothing between it and the org's plan budget — which is the
 * same shape as the read-quota 402 that `AppwriteReadGuard` was written for.
 *
 * A cap nobody has watched engage is a cap that might not exist, hence this rather than a reading
 * of the `when` branch.
 */
class ExecutionsRateCapTest {

    private var now = 0L

    @Before
    fun setUp() {
        AppwriteReadGuard.reset()
        now = 0L
        AppwriteReadGuard.nowMs = { now }
        AppwriteReadGuard.reporter = { _, _ -> }
    }

    @After
    fun tearDown() = AppwriteReadGuard.reset()

    private fun call() =
        AppwriteReadGuard.checkAndCount("fn.bouncer-player-search", AppwriteReadGuard.Quota.EXECUTIONS)

    @Test
    fun `executions under the per-minute cap are allowed`() {
        repeat(CacheConfig.EXECUTIONS_GUARD_MAX_PER_MINUTE) { assertNull("call $it should pass", call()) }
    }

    @Test
    fun `exceeding the per-minute cap rate-caps executions`() {
        repeat(CacheConfig.EXECUTIONS_GUARD_MAX_PER_MINUTE) { call() }
        assertEquals(AppwriteReadGuard.BlockReason.RATE_CAPPED, call())
    }

    @Test
    fun `a rate cap self-clears and does not latch the breaker`() {
        repeat(CacheConfig.EXECUTIONS_GUARD_MAX_PER_MINUTE + 1) { call() }
        assertEquals(AppwriteReadGuard.BlockReason.RATE_CAPPED, call())

        // A rate cap is local evidence of too many calls, not the server saying the budget is gone.
        // It must expire on its own rather than needing a 6h probe like a 402 latch.
        now += CacheConfig.READ_GUARD_RATE_CAP_PAUSE_MS + CacheConfig.READ_GUARD_HOUR_WINDOW_MS + 1
        assertNull("the pause should have self-cleared", call())
    }

    @Test
    fun `the per-hour cap catches a slow drip that never trips the per-minute one`() {
        // One call every 5s is 12 per minute — well under EXECUTIONS_GUARD_MAX_PER_MINUTE (30), so
        // the minute window alone would let this run forever. At 720/hour it is over the hourly
        // cap, and the hour window is what stops it. This is the shape a background retry loop has:
        // never bursty, permanently over budget.
        var allowed = 0
        var blocked = 0
        repeat(CacheConfig.EXECUTIONS_GUARD_MAX_PER_HOUR + 50) {
            if (call() == null) allowed++ else blocked++
            now += 5_000
        }
        assertTrue("a sustained drip must eventually be capped", blocked > 0)
        // Not an equality: the hour window slides while the 5-minute pause runs, so calls resume as
        // old buckets age out. The guarantee is a ceiling on the rate, not on the lifetime total.
        assertTrue(
            "allowed=$allowed should not exceed the hourly cap within the first hour",
            allowed <= CacheConfig.EXECUTIONS_GUARD_MAX_PER_HOUR
        )
    }

    @Test
    fun `capping executions does not touch the database read budget`() {
        repeat(CacheConfig.EXECUTIONS_GUARD_MAX_PER_MINUTE + 1) { call() }
        assertEquals(AppwriteReadGuard.BlockReason.RATE_CAPPED, call())
        // Quotas latch independently; a chatty search box must not switch off match data.
        assertNull(AppwriteReadGuard.checkAndCount("db", AppwriteReadGuard.Quota.DB_READ))
    }
}
