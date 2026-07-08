package com.tanush.bouncer.data.remote

import androidx.annotation.VisibleForTesting
import com.tanush.bouncer.data.CacheConfig
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.appwrite.exceptions.AppwriteException
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Global guard for all billed Appwrite TablesDB reads.
 *
 * Appwrite bills DB reads at the org level with a monthly budget; once exhausted every
 * read returns 402 `limit_databases_reads_exceeded` project-wide. This guard provides:
 * - Rate caps (per-minute/per-hour fixed windows) so a runaway loop can never burn the
 *   budget — tripping a cap latches the breaker open, since it always means a bug.
 * - A 402 latch: on a real 402 the budget is gone for the month, so all guarded reads
 *   short-circuit; a single probe read is allowed every [CacheConfig.READ_GUARD_PROBE_INTERVAL_MS]
 *   so the app self-heals after the budget resets without a reinstall.
 * - Sentry ERROR alerts (deduped per reason) so the breach is never silent.
 *
 * All state is process-local; a cold start pays at most one read to rediscover a 402.
 */
object AppwriteReadGuard {

    enum class BlockReason { BREAKER_OPEN, RATE_CAPPED }

    private const val HTTP_PAYMENT_REQUIRED = 402
    private const val TYPE_READS_EXCEEDED = "limit_databases_reads_exceeded"
    private const val REASON_BUDGET_402 = "breaker_open_402"
    private const val REASON_RATE_CAPPED = "rate_capped"
    private const val REASON_STORAGE_402 = "storage_budget_402"

    @VisibleForTesting
    internal var nowMs: () -> Long = System::currentTimeMillis

    @VisibleForTesting
    internal var reporter: (message: String, tags: Map<String, String>) -> Unit = ::reportToSentry

    private val lock = Any()
    private var breakerOpenedAtMs = 0L
    private var breakerOpen = false
    private var probeInFlight = false
    private var minuteWindowStartMs = 0L
    private var minuteCount = 0
    private var hourWindowStartMs = 0L
    private var hourCount = 0
    private val lastReportAtMs = mutableMapOf<String, Long>()

    /**
     * Call before every billed DB read. Returns null when the read may proceed (and counts
     * it toward the rate windows), or the [BlockReason] when it must be skipped.
     * Tripping a rate cap latches the breaker open and reports to Sentry.
     */
    fun checkAndCount(name: String): BlockReason? = synchronized(lock) {
        val now = nowMs()
        if (breakerOpen) {
            val probeDue = now - breakerOpenedAtMs >= CacheConfig.READ_GUARD_PROBE_INTERVAL_MS
            if (probeDue && !probeInFlight) {
                probeInFlight = true
                sentryBreadcrumb("appwrite.read_guard", "probe read allowed for $name")
                return null
            }
            return BlockReason.BREAKER_OPEN
        }
        if (now - minuteWindowStartMs >= CacheConfig.READ_GUARD_MINUTE_WINDOW_MS) {
            minuteWindowStartMs = now
            minuteCount = 0
        }
        if (now - hourWindowStartMs >= CacheConfig.READ_GUARD_HOUR_WINDOW_MS) {
            hourWindowStartMs = now
            hourCount = 0
        }
        if (minuteCount >= CacheConfig.READ_GUARD_MAX_READS_PER_MINUTE ||
            hourCount >= CacheConfig.READ_GUARD_MAX_READS_PER_HOUR
        ) {
            openBreaker(REASON_RATE_CAPPED, name, "Appwrite read rate cap tripped by $name — breaker latched open")
            return BlockReason.RATE_CAPPED
        }
        minuteCount++
        hourCount++
        return null
    }

    /** Call after a guarded read succeeds. Closes the breaker if this was the probe read. */
    fun recordSuccess() = synchronized(lock) {
        if (breakerOpen && probeInFlight) {
            breakerOpen = false
            breakerOpenedAtMs = 0L
            sentryBreadcrumb("appwrite.read_guard", "probe succeeded — breaker closed")
        }
        probeInFlight = false
    }

    /**
     * Call when a guarded read throws. A 402 latches the breaker open (budget exhausted
     * for the month). Other errors are not budget evidence and leave the breaker state
     * unchanged (a failed probe simply allows the next probe when due).
     */
    fun recordFailure(name: String, e: Throwable): Unit = synchronized(lock) {
        probeInFlight = false
        if (isBudget402(e)) {
            openBreaker(
                REASON_BUDGET_402,
                name,
                "Appwrite read budget exhausted (402 $TYPE_READS_EXCEEDED) — all DB reads stopped"
            )
        }
    }

    /**
     * Storage-read path: storage is billed separately from DB reads, so a storage 402 is
     * reported to Sentry but never opens the DB breaker.
     */
    fun reportIfBudget402(source: String, e: Throwable) {
        if (!isBudget402(e)) return
        synchronized(lock) {
            reportOncePerInterval(
                REASON_STORAGE_402,
                source,
                "Appwrite storage read hit budget 402 at $source"
            )
        }
    }

    fun isBudget402(e: Throwable): Boolean =
        e is AppwriteException && (e.code == HTTP_PAYMENT_REQUIRED || e.type == TYPE_READS_EXCEEDED)

    // Must be called while holding [lock].
    private fun openBreaker(reason: String, source: String, message: String) {
        if (!breakerOpen) {
            breakerOpen = true
            breakerOpenedAtMs = nowMs()
        } else {
            // Re-latch (failed probe): restart the probe interval from now.
            breakerOpenedAtMs = nowMs()
        }
        sentryBreadcrumb("appwrite.read_guard", "breaker opened ($reason) by $source", SentryLevel.ERROR)
        reportOncePerInterval(reason, source, message)
    }

    // Must be called while holding [lock].
    private fun reportOncePerInterval(reason: String, source: String, message: String) {
        val now = nowMs()
        val last = lastReportAtMs[reason]
        if (last != null && now - last < CacheConfig.READ_GUARD_REPORT_INTERVAL_MS) return
        lastReportAtMs[reason] = now
        reporter(message, mapOf("reason" to reason, "source" to source))
    }

    private fun reportToSentry(message: String, tags: Map<String, String>) {
        Sentry.withScope { scope ->
            tags.forEach { (key, value) -> scope.setTag(key, value) }
            scope.fingerprint = listOf("appwrite-read-guard", tags["reason"] ?: "unknown")
            Sentry.captureMessage(message, SentryLevel.ERROR)
        }
    }

    @VisibleForTesting
    internal fun reset() = synchronized(lock) {
        breakerOpen = false
        breakerOpenedAtMs = 0L
        probeInFlight = false
        minuteWindowStartMs = 0L
        minuteCount = 0
        hourWindowStartMs = 0L
        hourCount = 0
        lastReportAtMs.clear()
        nowMs = System::currentTimeMillis
        reporter = ::reportToSentry
    }
}
