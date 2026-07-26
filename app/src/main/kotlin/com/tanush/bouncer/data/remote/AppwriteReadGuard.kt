package com.tanush.bouncer.data.remote

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.tanush.bouncer.BuildConfig
import com.tanush.bouncer.data.CacheConfig
import com.tanush.bouncer.utils.PrefsRegistry
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

    /**
     * Persists the 402 breaker latch across process restarts. Without it, a cold start after a
     * budget-exhausted 402 resets the breaker to closed and re-probes on the next read, so a
     * relaunch storm re-hits the (still-exhausted) budget. Abstracted so it needs no Context here.
     */
    interface BreakerStore {
        fun save(open: Boolean, openedAtMs: Long)
        fun load(): BreakerState?
    }

    data class BreakerState(val open: Boolean, val openedAtMs: Long)

    /**
     * Sliding-window counter: [bucketCount] buckets each covering [bucketMs], summed over the
     * whole window. Replaces a fixed window, which reset to zero at an arbitrary instant and so
     * allowed twice the cap through across the boundary.
     */
    private class SlidingCounter(private val bucketCount: Int, private val bucketMs: Long) {
        private val counts = IntArray(bucketCount)
        private val bucketIndexes = LongArray(bucketCount) { Long.MIN_VALUE }

        private fun slot(now: Long): Int {
            val bucket = now / bucketMs
            val index = (bucket % bucketCount).toInt()
            // Reuse of a stale slot means a full window has elapsed for it.
            if (bucketIndexes[index] != bucket) {
                bucketIndexes[index] = bucket
                counts[index] = 0
            }
            return index
        }

        fun add(now: Long, amount: Int) {
            counts[slot(now)] += amount
        }

        fun total(now: Long): Int {
            val oldestBucket = now / bucketMs - bucketCount + 1
            var sum = 0
            for (i in 0 until bucketCount) {
                if (bucketIndexes[i] >= oldestBucket) sum += counts[i]
            }
            return sum
        }

        fun reset() {
            counts.fill(0)
            bucketIndexes.fill(Long.MIN_VALUE)
        }
    }

    private const val MINUTE_BUCKETS = 60
    private const val HOUR_BUCKETS = 60

    private val lock = Any()
    private var breakerOpenedAtMs = 0L
    private var breakerOpen = false
    private var probeInFlight = false
    private val minuteWindow =
        SlidingCounter(MINUTE_BUCKETS, CacheConfig.READ_GUARD_MINUTE_WINDOW_MS / MINUTE_BUCKETS)
    private val hourWindow =
        SlidingCounter(HOUR_BUCKETS, CacheConfig.READ_GUARD_HOUR_WINDOW_MS / HOUR_BUCKETS)
    private var rateCapUntilMs = 0L
    private val lastReportAtMs = mutableMapOf<String, Long>()
    private var store: BreakerStore? = null

    /**
     * Wire a persistence backend (call once at startup). Restores any latched breaker state so a
     * relaunch after a 402 keeps short-circuiting reads until the 6h probe interval elapses.
     */
    fun initPersistence(backend: BreakerStore) = synchronized(lock) {
        store = backend
        backend.load()?.let {
            breakerOpen = it.open
            breakerOpenedAtMs = it.openedAtMs
        }
    }

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
        // A rate-cap pause self-clears. It is deliberately NOT the 402 latch: a rate cap is local
        // evidence of too many reads, a 402 is the server saying the month's budget is gone.
        if (now < rateCapUntilMs) return BlockReason.RATE_CAPPED
        if (minuteWindow.total(now) >= CacheConfig.READ_GUARD_MAX_READS_PER_MINUTE ||
            hourWindow.total(now) >= CacheConfig.READ_GUARD_MAX_READS_PER_HOUR
        ) {
            rateCapUntilMs = now + CacheConfig.READ_GUARD_RATE_CAP_PAUSE_MS
            sentryBreadcrumb("appwrite.read_guard", "rate cap tripped by $name", SentryLevel.ERROR)
            // Still reported at ERROR — hitting the cap always means a bug worth fixing.
            reportOncePerInterval(
                REASON_RATE_CAPPED,
                name,
                "Appwrite read rate cap tripped by $name — reads paused for " +
                    "${CacheConfig.READ_GUARD_RATE_CAP_PAUSE_MS / 60_000}m"
            )
            return BlockReason.RATE_CAPPED
        }
        minuteWindow.add(now, 1)
        hourWindow.add(now, 1)
        return null
    }

    /**
     * Call after a guarded read succeeds. Closes the breaker if this was the probe read, and
     * counts [rows] toward the rate windows. Appwrite bills one read per document returned, but
     * [checkAndCount] only counted the call as 1 — pass the row count so a limit(N) read is
     * billed as N (not 1), keeping the per-minute/per-hour caps aligned with true consumption.
     */
    fun recordSuccess(rows: Int = 1) = synchronized(lock) {
        if (breakerOpen && probeInFlight) {
            breakerOpen = false
            breakerOpenedAtMs = 0L
            store?.save(false, 0L)
            sentryBreadcrumb("appwrite.read_guard", "probe succeeded — breaker closed")
        }
        probeInFlight = false
        if (rows > 1) {
            val extra = rows - 1
            val now = nowMs()
            minuteWindow.add(now, extra)
            hourWindow.add(now, extra)
        }
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
        store?.save(breakerOpen, breakerOpenedAtMs)
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
        // Skip in debug: dev testing routinely trips the read caps, and those alerts are noise.
        // The breaker still latches and breadcrumbs still record; only the Sentry ERROR is gated.
        // Production read-storm alerts are unaffected.
        if (BuildConfig.DEBUG) return
        Sentry.withScope { scope ->
            tags.forEach { (key, value) -> scope.setTag(key, value) }
            scope.fingerprint = listOf("appwrite-read-guard", tags["reason"] ?: "unknown")
            Sentry.captureMessage(message, SentryLevel.ERROR)
        }
    }

    /** SharedPreferences-backed [BreakerStore] — persists the latch across process restarts. */
    class PrefsBreakerStore(context: Context) : BreakerStore {
        private val prefs = context.applicationContext
            .getSharedPreferences(PrefsRegistry.PREFS_APP, Context.MODE_PRIVATE)

        override fun save(open: Boolean, openedAtMs: Long) {
            prefs.edit()
                .putBoolean(PrefsRegistry.ReadGuard.BREAKER_OPEN, open)
                .putLong(PrefsRegistry.ReadGuard.BREAKER_OPENED_AT, openedAtMs)
                .apply()
        }

        override fun load(): BreakerState? {
            if (!prefs.getBoolean(PrefsRegistry.ReadGuard.BREAKER_OPEN, false)) return null
            return BreakerState(
                open = true,
                openedAtMs = prefs.getLong(PrefsRegistry.ReadGuard.BREAKER_OPENED_AT, 0L)
            )
        }
    }

    @VisibleForTesting
    internal fun reset() = synchronized(lock) {
        breakerOpen = false
        breakerOpenedAtMs = 0L
        probeInFlight = false
        minuteWindow.reset()
        hourWindow.reset()
        rateCapUntilMs = 0L
        lastReportAtMs.clear()
        store = null
        nowMs = System::currentTimeMillis
        reporter = ::reportToSentry
    }
}
