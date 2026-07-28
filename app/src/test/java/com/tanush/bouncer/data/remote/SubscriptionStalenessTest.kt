package com.tanush.bouncer.data.remote

import com.tanush.bouncer.data.CacheConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `startSubscriptionLoop` used to park on `delay(Long.MAX_VALUE)` after a successful subscribe, on
 * the assumption — asserted in a comment and validated by nothing — that the Appwrite SDK always
 * reconnects internally. If the socket died inside the SDK without throwing out of `subscribe()`,
 * the catch never fired and the client believed it was subscribed for the life of the process.
 */
class SubscriptionStalenessTest {

    private val timeout = CacheConfig.REALTIME_STALE_TIMEOUT_MS

    @Test
    fun `a subscription delivering events is not stale`() {
        assertFalse(isSubscriptionStale(lastEventAt = 1_000L, now = 1_000L + timeout - 1, timeoutMs = timeout))
    }

    @Test
    fun `silence beyond the timeout is stale`() {
        assertTrue(isSubscriptionStale(lastEventAt = 1_000L, now = 1_000L + timeout, timeoutMs = timeout))
        assertTrue(isSubscriptionStale(lastEventAt = 1_000L, now = 1_000L + timeout * 10, timeoutMs = timeout))
    }

    @Test
    fun `an unarmed clock is not stale`() {
        // Before the first subscribe there is nothing to measure, so the loop must not thrash.
        assertFalse(isSubscriptionStale(lastEventAt = 0L, now = Long.MAX_VALUE / 2, timeoutMs = timeout))
        assertFalse(isSubscriptionStale(lastEventAt = -1L, now = Long.MAX_VALUE / 2, timeoutMs = timeout))
    }

    @Test
    fun `a subscription that never delivered an event still goes stale`() {
        // lastEventAt is armed at subscribe time, not on the first event, so a channel that connects
        // and then silently receives nothing is covered.
        val subscribedAt = 5_000L
        assertFalse(isSubscriptionStale(subscribedAt, subscribedAt + 1, timeout))
        assertTrue(isSubscriptionStale(subscribedAt, subscribedAt + timeout, timeout))
    }

    @Test
    fun `the heartbeat is short enough to notice staleness promptly`() {
        assertTrue(
            "a heartbeat longer than the timeout would delay detection by a whole extra period",
            CacheConfig.REALTIME_HEARTBEAT_MS < CacheConfig.REALTIME_STALE_TIMEOUT_MS,
        )
    }

    @Test
    fun `a clock that appears to move backwards is not stale`() {
        assertFalse(isSubscriptionStale(lastEventAt = 10_000L, now = 5_000L, timeoutMs = timeout))
    }
}
