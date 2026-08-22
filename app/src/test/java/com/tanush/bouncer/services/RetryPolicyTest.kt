package com.tanush.bouncer.services

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RetryPolicyTest {

    private val policy = RetryPolicy(maxAttempts = 3, baseDelayMs = 1000L, multiplier = 2.0)

    @Test
    fun `succeeds on first attempt without retry`() = runTest {
        var callCount = 0
        val result = policy.withRetry { callCount++; "ok" }
        assertEquals("ok", result)
        assertEquals(1, callCount)
    }

    @Test
    fun `retries 3 times on IOException then rethrows`() = runTest {
        var callCount = 0
        val retryAttempts = mutableListOf<Int>()
        val delays = mutableListOf<Long>()

        try {
            policy.withRetry(
                isRetryable = { it is IOException },
                onRetry = { attempt, delayMs, _ ->
                    retryAttempts.add(attempt)
                    delays.add(delayMs)
                }
            ) {
                callCount++
                throw IOException("timeout")
            }
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("timeout", e.message)
        }

        assertEquals(3, callCount)
        assertEquals(listOf(1, 2), retryAttempts)
        // Equal jitter: each delay sits in [d/2, d) of the unjittered 1000/2000 schedule.
        assertInEqualJitterRange(listOf(1000L, 2000L), delays)
    }

    @Test
    fun `exponential backoff delays are 1000 then 2000 then 4000`() = runTest {
        val delays = mutableListOf<Long>()
        val p4 = RetryPolicy(maxAttempts = 4, baseDelayMs = 1000L, multiplier = 2.0)

        try {
            p4.withRetry(
                onRetry = { _, delayMs, _ -> delays.add(delayMs) }
            ) { throw IOException("fail") }
        } catch (_: IOException) {}

        assertInEqualJitterRange(listOf(1000L, 2000L, 4000L), delays)
    }

    @Test
    fun `recovers on second attempt`() = runTest {
        var callCount = 0
        val result = policy.withRetry(isRetryable = { it is IOException }) {
            callCount++
            if (callCount < 2) throw IOException("first fail")
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `non-retryable exception passes through immediately`() = runTest {
        var callCount = 0
        try {
            policy.withRetry(isRetryable = { it is IOException }) {
                callCount++
                throw IllegalStateException("permanent")
            }
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("permanent", e.message)
        }
        assertEquals(1, callCount)
    }

    @Test
    fun `IOException subclass retried 3 times because it extends IOException`() = runTest {
        class HttpException(code: Int, msg: String) : IOException("HTTP $code: $msg")
        var callCount = 0
        val retryAttempts = mutableListOf<Int>()

        try {
            policy.withRetry(
                isRetryable = { it is IOException },
                onRetry = { attempt, _, _ -> retryAttempts.add(attempt) }
            ) {
                callCount++
                throw HttpException(500, "Internal Server Error")
            }
            fail("Expected exception")
        } catch (e: IOException) {
            assertTrue(e.message?.startsWith("HTTP 500") == true)
        }

        assertEquals(3, callCount)
        assertEquals(listOf(1, 2), retryAttempts)
    }

    @Test
    fun `final failure after exhausting retries is the original exception`() = runTest {
        val thrown = IOException("root cause")
        try {
            policy.withRetry { throw thrown }
            fail("Expected IOException")
        } catch (e: IOException) {
            assertSame(thrown, e)
        }
    }

    // ── maxDelayMs cap ──────────────────────────────────────────────────────

    @Test
    fun `the growing delay is capped`() = runTest {
        // `currentDelay * multiplier` compounded without bound, so attempt 10 of a 1-second policy
        // waited over eight minutes.
        val delays = mutableListOf<Long>()
        val p = RetryPolicy(maxAttempts = 8, baseDelayMs = 1000L, multiplier = 2.0, maxDelayMs = 5000L)
        try {
            p.withRetry(onRetry = { _, delayMs, _ -> delays.add(delayMs) }) { throw IOException("fail") }
        } catch (_: IOException) {}

        assertInEqualJitterRange(listOf(1000L, 2000L, 4000L, 5000L, 5000L, 5000L, 5000L), delays)
    }

    @Test
    fun `a base delay above the cap is itself capped`() = runTest {
        val delays = mutableListOf<Long>()
        val p = RetryPolicy(maxAttempts = 2, baseDelayMs = 60_000L, maxDelayMs = 5000L)
        try {
            p.withRetry(onRetry = { _, delayMs, _ -> delays.add(delayMs) }) { throw IOException("fail") }
        } catch (_: IOException) {}

        assertInEqualJitterRange(listOf(5000L), delays)
    }

    // ── default isRetryable ─────────────────────────────────────────────────

    @Test
    fun `the default policy does not retry an Error`() = runTest {
        // The old default of `{ true }` would have retried an OutOfMemoryError twice more, each time
        // after a delay, when the only correct response is to let it propagate.
        var callCount = 0
        try {
            policy.withRetry {
                callCount++
                throw OutOfMemoryError("heap")
            }
            fail("Expected OutOfMemoryError")
        } catch (_: OutOfMemoryError) {}

        assertEquals(1, callCount)
    }

    @Test
    fun `the default policy still retries an ordinary exception`() = runTest {
        var callCount = 0
        try {
            policy.withRetry { callCount++; throw IOException("transient") }
        } catch (_: IOException) {}
        assertEquals(3, callCount)
    }

    // ── backoffDelayMs ──────────────────────────────────────────────────────

    @Test
    fun `backoff doubles then stops at the ceiling`() {
        val base = 1000L
        val unjittered = listOf(1000L, 2000L, 4000L, 8000L, 8000L, 8000L, 8000L, 8000L)
        unjittered.forEachIndexed { i, d ->
            // Equal jitter: the value sits in [d/2, d).
            val actual = backoffDelayMs(i + 1, baseMs = base, maxMs = 8000L)
            assertTrue(
                "attempt ${i + 1} gave $actual, expected [${d / 2}, $d)",
                actual >= d / 2 && actual < d,
            )
        }
    }

    @Test
    fun `backoff spreads across half the delay, not one base period`() {
        // This used to assert the opposite — that every sample landed within one *base* period of
        // the delay. That is what made the jitter useless once the delay had grown: at a 30s
        // delay with a 1s base it was a 3% spread, so a fleet that failed together still retried
        // together. Equal jitter spreads over half the delay at every size.
        val base = 1000L
        val delay = 16_000L
        val samples = (1..400).map { backoffDelayMs(5, baseMs = base, maxMs = 30_000L) }

        assertTrue("no sample is below half the delay", samples.all { it >= delay / 2 })
        assertTrue("no sample reaches the unjittered delay", samples.all { it < delay })
        assertTrue("jitter must actually vary", samples.distinct().size > 1)
        // The spread has to be a real fraction of the delay, not a rounding wobble.
        assertTrue(
            "spread was ${samples.max() - samples.min()}, expected most of ${delay / 2}",
            samples.max() - samples.min() > delay / 4,
        )
    }

    @Test
    fun `a first or zeroth attempt does not shift the exponent negative`() {
        assertTrue(backoffDelayMs(0, baseMs = 1000L) in 500L until 1000L)
        assertTrue(backoffDelayMs(1, baseMs = 1000L) in 500L until 1000L)
    }

    /** Asserts each observed delay is the equal-jitter window [d/2, d) of its unjittered value. */
    private fun assertInEqualJitterRange(unjittered: List<Long>, observed: List<Long>) {
        assertEquals("delay count", unjittered.size, observed.size)
        unjittered.forEachIndexed { i, d ->
            assertTrue(
                "delay $i was ${observed[i]}, expected [${d / 2}, $d)",
                observed[i] >= d / 2 && observed[i] < d,
            )
        }
    }
}
