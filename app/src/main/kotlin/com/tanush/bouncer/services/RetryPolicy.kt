package com.tanush.bouncer.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Shared cap for runAttemptCount-based retry in WorkManager Workers. */
const val WORKER_MAX_RETRIES = 3

/** Ceiling for [backoffDelayMs] and [RetryPolicy.maxDelayMs] unless a caller overrides it. */
const val DEFAULT_MAX_BACKOFF_MS = 30_000L

/** Base delay shared by the retry loops in the scrapers. */
const val DEFAULT_BASE_BACKOFF_MS = 1000L

/**
 * Bound on the shift so `baseMs shl doublings` cannot overflow — Long shifts use only the low six
 * bits of the count, so an unbounded attempt number would wrap around to a *small* delay. Set high
 * enough that `maxMs` is always the effective ceiling rather than the shift being it.
 */
private const val MAX_DOUBLINGS = 30

/**
 * Capped, jittered exponential backoff for a 1-based attempt number.
 *
 * The four scrapers each carried their own `MAX_RETRIES = 3` / `INITIAL_BACKOFF_MS = 1000L` /
 * `backoff *= 2` block, none of them capped and none of them jittered. Uncapped doubling is a
 * problem for any loop that might run more than a handful of attempts, and unjittered backoff
 * synchronises every device that failed at the same moment — which, for a push-triggered fan-out,
 * is all of them.
 *
 * The jittered form here is the one CommentaryFusionService already used for provider cooldowns,
 * verbatim and in two places; both now call this.
 */
fun backoffDelayMs(
    attempt: Int,
    baseMs: Long = DEFAULT_BASE_BACKOFF_MS,
    maxMs: Long = DEFAULT_MAX_BACKOFF_MS,
): Long {
    val doublings = (attempt - 1).coerceIn(0, MAX_DOUBLINGS)
    val capped = minOf(baseMs shl doublings, maxMs)
    return capped + Random.nextLong(0, baseMs.coerceAtLeast(1L))
}

/**
 * @param maxDelayMs ceiling on the growing delay. Without it `currentDelay * multiplier` compounded
 *   without bound, so a policy with many attempts eventually waited for minutes between them.
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 1000L,
    val multiplier: Double = 2.0,
    val maxDelayMs: Long = DEFAULT_MAX_BACKOFF_MS,
)

/**
 * @param isRetryable defaults to retrying every [Exception] but no [Error]. The previous default of
 *   `{ true }` would have retried an `OutOfMemoryError` or `StackOverflowError` twice more, each
 *   time after a delay, when the only correct response is to let it propagate.
 */
suspend fun <T> RetryPolicy.withRetry(
    isRetryable: (Throwable) -> Boolean = { it !is Error },
    onRetry: (attempt: Int, delayMs: Long, cause: Throwable) -> Unit = { _, _, _ -> },
    block: suspend () -> T
): T {
    var lastThrowable: Throwable? = null
    var currentDelay = baseDelayMs.coerceAtMost(maxDelayMs)
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
                currentDelay = (currentDelay * multiplier).toLong().coerceAtMost(maxDelayMs)
            }
        }
    }
    throw lastThrowable ?: IllegalStateException("RetryPolicy exhausted ${maxAttempts} attempts with no recorded exception")
}
