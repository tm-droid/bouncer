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
        assertEquals(listOf(1000L, 2000L), delays)
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

        assertEquals(listOf(1000L, 2000L, 4000L), delays)
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
}
