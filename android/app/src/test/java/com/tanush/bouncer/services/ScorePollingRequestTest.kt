package com.tanush.bouncer.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the one-time score-poll request against the crash that shipped: a jittered request was
 * built with both an initial delay and `setExpedited`, and WorkManager rejects that pair from
 * `build()` with `IllegalArgumentException: Expedited jobs cannot be delayed`.
 *
 * It threw on FirebaseMessagingService's thread, which has no handler, so every MATCH_STARTED /
 * MATCH_ENDED topic push killed the process — and since those arrive as bursts, Android escalated
 * to "crashed too many times, killing". Only the jitter > 0 path was affected, which is exactly
 * the path no manual test takes: pull-to-refresh and foreground both pass jitter = 0.
 */
class ScorePollingRequestTest {

    @Test
    fun `a jittered request builds - it is the topic-push path that was crashing`() {
        val request = ScorePollingWorker.oneTimeRequest(jitterMs = 4_000L)
        assertTrue(request.workSpec.initialDelay > 0)
        assertEquals(
            "a delayed request must not also be expedited — build() throws on that pair",
            false,
            request.workSpec.expedited,
        )
    }

    @Test
    fun `an unjittered request stays expedited`() {
        val request = ScorePollingWorker.oneTimeRequest(jitterMs = 0L)
        assertTrue("a user-triggered poll should still run as soon as it can", request.workSpec.expedited)
        assertEquals(0L, request.workSpec.initialDelay)
    }

    @Test
    fun `every jitter the topic path can produce builds`() {
        // BouncerFirebaseMessagingService draws jitter from Random.nextLong(0, TOPIC_FANOUT_JITTER_MS),
        // so 0 is reachable too and must take the expedited branch rather than a zero-length delay.
        listOf(0L, 1L, 999L, 30_000L, 5 * 60_000L).forEach { jitter ->
            ScorePollingWorker.oneTimeRequest(jitter)
        }
    }
}
