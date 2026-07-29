package com.tanush.bouncer.data.remote

import io.appwrite.exceptions.AppwriteException

/**
 * Works out *which* plan quota a 402 is complaining about.
 *
 * Appwrite bills several budgets separately and returns 402 with a body naming the one that blew.
 * Treating them as interchangeable is what let a bandwidth 402 switch off all database reads, so
 * attribution lives here, apart from the breaker bookkeeping in [AppwriteReadGuard].
 */
object Appwrite402Classifier {

    private const val HTTP_PAYMENT_REQUIRED = 402
    private const val HTTP_TOO_MANY_REQUESTS = 429

    /** Appwrite's machine-readable error types. Preferred over prose. */
    private val TYPE_TO_QUOTA = mapOf(
        "limit_databases_reads_exceeded" to AppwriteReadGuard.Quota.DB_READ,
        "limit_databases_writes_exceeded" to AppwriteReadGuard.Quota.DB_WRITE,
        "limit_bandwidth_exceeded" to AppwriteReadGuard.Quota.BANDWIDTH,
        "limit_executions_exceeded" to AppwriteReadGuard.Quota.EXECUTIONS,
        "limit_storage_exceeded" to AppwriteReadGuard.Quota.STORAGE,
    )

    /** Fallback keyword scan, ordered so the more specific words win over the generic ones. */
    private val MESSAGE_KEYWORDS = listOf(
        "bandwidth" to AppwriteReadGuard.Quota.BANDWIDTH,
        "execution" to AppwriteReadGuard.Quota.EXECUTIONS,
        "read" to AppwriteReadGuard.Quota.DB_READ,
        "write" to AppwriteReadGuard.Quota.DB_WRITE,
        "storage" to AppwriteReadGuard.Quota.STORAGE,
    )

    /**
     * The quota this 402 names, or null when it cannot be attributed. Returning null rather than
     * guessing matters: the caller scopes an unattributed 402 narrowly instead of latching
     * everything — see [AppwriteReadGuard.recordFailure].
     */
    fun classify(e: AppwriteException): AppwriteReadGuard.Quota? =
        TYPE_TO_QUOTA[e.type] ?: classifyByMessage(e.message)

    private fun classifyByMessage(message: String?): AppwriteReadGuard.Quota? {
        val lower = message?.lowercase() ?: return null
        return MESSAGE_KEYWORDS.firstOrNull { (keyword, _) -> lower.contains(keyword) }?.second
    }

    /** A plan-budget rejection: retrying is pointless until the budget resets. */
    fun isBudget402(e: Throwable): Boolean =
        e is AppwriteException && (e.code == HTTP_PAYMENT_REQUIRED || e.type?.startsWith("limit_") == true)

    /** Short-term abuse protection, unrelated to any budget — pause and retry, never latch. */
    fun isRateLimited(e: Throwable): Boolean =
        e is AppwriteException && e.code == HTTP_TOO_MANY_REQUESTS
}
