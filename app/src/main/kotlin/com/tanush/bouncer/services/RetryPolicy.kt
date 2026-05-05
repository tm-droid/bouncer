package com.tanush.bouncer.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

data class RetryPolicy(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 1000L,
    val multiplier: Double = 2.0
)

suspend fun <T> RetryPolicy.withRetry(
    isRetryable: (Throwable) -> Boolean = { true },
    onRetry: (attempt: Int, delayMs: Long, cause: Throwable) -> Unit = { _, _, _ -> },
    block: suspend () -> T
): T {
    var lastThrowable: Throwable? = null
    var currentDelay = baseDelayMs
    repeat(maxAttempts) { attempt ->
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            lastThrowable = e
            if (!isRetryable(e)) throw e
            if (attempt < maxAttempts - 1) {
                onRetry(attempt + 1, currentDelay, e)
                delay(currentDelay)
                currentDelay = (currentDelay * multiplier).toLong()
            }
        }
    }
    throw lastThrowable!!
}
