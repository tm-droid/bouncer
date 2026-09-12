package com.tanush.bouncer.data.remote

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.tanush.bouncer.BuildConfig
import com.tanush.bouncer.data.CacheConfig
import com.tanush.bouncer.data.prefs.BouncerPreferences
import com.tanush.bouncer.data.prefs.PreferenceStore
import com.tanush.bouncer.utils.PrefsRegistry
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.appwrite.exceptions.AppwriteException
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.util.EnumMap

/**
 * Global guard for billed Appwrite operations, one circuit breaker per plan quota.
 *
 * Appwrite bills several quotas separately at the org level and returns 402 with a body naming the
 * one that blew. Earlier this guard treated *every* 402 as "database reads exhausted", so a
 * bandwidth 402 switched off all DB reads for six hours — two unrelated budgets welded together.
 * Now each [Quota] latches independently:
 * - Rate caps (sliding per-minute/per-hour windows) on the quotas we can count locally, so a
 *   runaway loop can never burn a budget.
 * - A 402 latch per quota, with a single probe allowed every
 *   [CacheConfig.READ_GUARD_PROBE_INTERVAL_MS] so the app self-heals when the budget resets.
 * - Sentry ERROR alerts (deduped per reason+quota) so a breach is never silent.
 *
 * A 402 we cannot attribute is scoped by the funnel that threw it rather than latching everything:
 * see [recordFailure].
 */
object AppwriteReadGuard {

    /** Plan quotas Appwrite bills and caps independently. */
    enum class Quota { DB_READ, DB_WRITE, BANDWIDTH, EXECUTIONS, STORAGE }

    enum class BlockReason { BREAKER_OPEN, RATE_CAPPED }

    private const val REASON_BUDGET_402 = "breaker_open_402"
    private const val REASON_RATE_CAPPED = "rate_capped"
    private const val REASON_RATE_LIMITED_429 = "rate_limited_429"

    @VisibleForTesting
    internal var nowMs: () -> Long = System::currentTimeMillis

    @VisibleForTesting
    internal var reporter: (message: String, tags: Map<String, String>) -> Unit = ::reportToSentry

    /**
     * Persists open 402 latches across process restarts. Without it, a cold start after a
     * budget-exhausted 402 resets every breaker to closed and re-probes on the next call, so a
     * relaunch storm re-hits the (still-exhausted) budget. Abstracted so it needs no Context here.
     */
    interface BreakerStore {
        /** [state] null means "closed" — clear any persisted latch for [quota]. */
        fun save(quota: Quota, state: BreakerState?)
        fun loadAll(): Map<Quota, BreakerState>

        /**
         * Returns the pre-per-quota latch exactly once and clears it. Without this an in-flight
         * 402 latch would silently reset on upgrade, letting a relaunch re-hit a dead budget.
         */
        fun consumeLegacy(): BreakerState?
    }

    data class BreakerState(val openedAtMs: Long, val probeIntervalMs: Long)

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

    /** All mutable state for one quota. Guarded by [lock]. */
    private class Breaker(val quota: Quota) {
        /**
         * Per-minute/per-hour caps, or null when there is no meaningful local unit to count.
         * Bandwidth and storage are billed in bytes, which is server-side accounting we cannot
         * count here, so those two stay breaker-only: they react to a 402 but never pre-emptively
         * pause.
         *
         * Executions used to be in that group. They are countable — one call is one execution —
         * and the reason for leaving them uncapped (every call was driven by a match event or a
         * fixed heartbeat, so the rate was structurally bounded) stopped holding once a
         * user-typing-driven caller existed.
         */
        val rateLimits: Pair<Int, Int>? = when (quota) {
            Quota.DB_READ ->
                CacheConfig.READ_GUARD_MAX_READS_PER_MINUTE to CacheConfig.READ_GUARD_MAX_READS_PER_HOUR
            Quota.DB_WRITE ->
                CacheConfig.WRITE_GUARD_MAX_WRITES_PER_MINUTE to CacheConfig.WRITE_GUARD_MAX_WRITES_PER_HOUR
            Quota.EXECUTIONS ->
                CacheConfig.EXECUTIONS_GUARD_MAX_PER_MINUTE to CacheConfig.EXECUTIONS_GUARD_MAX_PER_HOUR
            Quota.BANDWIDTH, Quota.STORAGE -> null
        }

        var open = false
        var openedAtMs = 0L
        var probeIntervalMs = CacheConfig.READ_GUARD_PROBE_INTERVAL_MS
        var probeInFlight = false
        var rateCapUntilMs = 0L
        val minuteWindow =
            SlidingCounter(MINUTE_BUCKETS, CacheConfig.READ_GUARD_MINUTE_WINDOW_MS / MINUTE_BUCKETS)
        val hourWindow =
            SlidingCounter(HOUR_BUCKETS, CacheConfig.READ_GUARD_HOUR_WINDOW_MS / HOUR_BUCKETS)

        fun reset() {
            open = false
            openedAtMs = 0L
            probeIntervalMs = CacheConfig.READ_GUARD_PROBE_INTERVAL_MS
            probeInFlight = false
            rateCapUntilMs = 0L
            minuteWindow.reset()
            hourWindow.reset()
        }
    }

    private val lock = Any()
    private val breakers = EnumMap<Quota, Breaker>(Quota::class.java).apply {
        Quota.entries.forEach { put(it, Breaker(it)) }
    }
    private val lastReportAtMs = mutableMapOf<String, Long>()
    private var store: BreakerStore? = null

    /**
     * Wire a persistence backend (call once at startup). Restores any latched breakers so a
     * relaunch after a 402 keeps short-circuiting until the probe interval elapses, and folds the
     * pre-per-quota latch onto [Quota.DB_READ].
     */
    fun initPersistence(backend: BreakerStore) = synchronized(lock) {
        store = backend
        backend.loadAll().forEach { (quota, state) ->
            breakers.getValue(quota).apply {
                open = true
                openedAtMs = state.openedAtMs
                probeIntervalMs = state.probeIntervalMs
            }
        }
        // Legacy latch predates per-quota breakers and could only ever have meant DB reads.
        backend.consumeLegacy()?.let { legacy ->
            val dbRead = breakers.getValue(Quota.DB_READ)
            if (!dbRead.open) {
                dbRead.open = true
                dbRead.openedAtMs = legacy.openedAtMs
                dbRead.probeIntervalMs = legacy.probeIntervalMs
                persist(dbRead)
            }
        }
    }

    /**
     * Why [quota] would refuse an operation right now, or null if it would proceed. Counts nothing
     * and never consumes a probe, so it is safe to call purely to explain a failure after the fact
     * — which is the one thing a caller holding a null result cannot otherwise do.
     */
    fun peek(quota: Quota = Quota.DB_READ): BlockReason? = synchronized(lock) {
        val b = breakers.getValue(quota)
        if (b.open) return BlockReason.BREAKER_OPEN
        if (nowMs() < b.rateCapUntilMs) return BlockReason.RATE_CAPPED
        return null
    }

    /**
     * Call before every billed operation. Returns null when it may proceed (and counts it toward
     * [quota]'s rate windows), or the [BlockReason] when it must be skipped.
     */
    fun checkAndCount(name: String, quota: Quota = Quota.DB_READ): BlockReason? = synchronized(lock) {
        val b = breakers.getValue(quota)
        val now = nowMs()
        if (b.open) {
            val probeDue = now - b.openedAtMs >= b.probeIntervalMs
            if (probeDue && !b.probeInFlight) {
                b.probeInFlight = true
                sentryBreadcrumb("appwrite.read_guard", "probe ${quota.name} allowed for $name")
                return null
            }
            return BlockReason.BREAKER_OPEN
        }
        // A rate-cap pause self-clears. It is deliberately NOT the 402 latch: a rate cap is local
        // evidence of too many operations, a 402 is the server saying the budget is gone.
        if (now < b.rateCapUntilMs) return BlockReason.RATE_CAPPED
        val (perMinute, perHour) = b.rateLimits ?: return null
        if (b.minuteWindow.total(now) >= perMinute || b.hourWindow.total(now) >= perHour) {
            b.rateCapUntilMs = now + CacheConfig.READ_GUARD_RATE_CAP_PAUSE_MS
            sentryBreadcrumb(
                "appwrite.read_guard",
                "rate cap (${quota.name}) tripped by $name",
                SentryLevel.ERROR,
            )
            // Still reported at ERROR — hitting the cap always means a bug worth fixing.
            reportOncePerInterval(
                REASON_RATE_CAPPED,
                name,
                quota,
                "Appwrite ${quota.name} rate cap tripped by $name — paused for " +
                    "${CacheConfig.READ_GUARD_RATE_CAP_PAUSE_MS / 60_000}m"
            )
            return BlockReason.RATE_CAPPED
        }
        b.minuteWindow.add(now, 1)
        b.hourWindow.add(now, 1)
        return null
    }

    /**
     * Call after a guarded operation succeeds. Closes [quota]'s breaker if this was its probe, and
     * counts [units] toward its rate windows. Appwrite bills one read per document returned, but
     * [checkAndCount] only counted the call as 1 — pass the row count so a limit(N) read is billed
     * as N, keeping the caps aligned with true consumption.
     */
    fun recordSuccess(units: Int = 1, quota: Quota = Quota.DB_READ) {
        val closed = synchronized(lock) {
            val b = breakers.getValue(quota)
            val didClose = b.open && b.probeInFlight
            if (didClose) {
                b.open = false
                b.openedAtMs = 0L
                b.probeIntervalMs = CacheConfig.READ_GUARD_PROBE_INTERVAL_MS
                persist(b)
                sentryBreadcrumb("appwrite.read_guard", "probe succeeded — ${quota.name} breaker closed")
            }
            b.probeInFlight = false
            if (units > 1 && b.rateLimits != null) {
                val extra = units - 1
                val now = nowMs()
                b.minuteWindow.add(now, extra)
                b.hourWindow.add(now, extra)
            }
            didClose
        }
        // Outside the lock: the listener issues Appwrite calls, which re-enter this object.
        if (closed) onBreakerClosed?.invoke(quota)
    }

    /**
     * Notified when a quota's breaker closes after a successful probe — the budget that was
     * rejecting operations is demonstrably serving again.
     *
     * A property rather than a listener list because there is exactly one consumer
     * ([com.tanush.bouncer.services.AICacheService], replaying writes that were dropped while the
     * write budget was latched) and a registry would outlive its usefulness. Always invoked off the
     * guard's lock.
     */
    @Volatile
    var onBreakerClosed: ((Quota) -> Unit)? = null

    /**
     * Call when a guarded operation throws. [quota] is the *calling funnel's* class, used only to
     * scope a 402 we cannot attribute.
     *
     * - A 402 naming its quota latches exactly that one, whichever funnel saw it. This is what
     *   stops a bandwidth 402 from switching off database reads.
     * - A 402 we cannot classify latches the calling funnel's own quota plus [Quota.BANDWIDTH]
     *   (the shared API+Storage+Realtime pool, which any path can breach) — and nothing else.
     *   Freezing every quota on one ambiguous message is far more damage than the guess is worth.
     *   Those breakers re-probe after [CacheConfig.READ_GUARD_UNKNOWN_402_PROBE_INTERVAL_MS]
     *   rather than the full interval, because the attribution is partly inference.
     * - A 429 is short-term abuse protection, not a budget: it pauses, it never latches.
     * - Anything else is not budget evidence and leaves breaker state unchanged (a failed probe
     *   simply allows the next probe when due).
     */
    fun recordFailure(name: String, e: Throwable, quota: Quota = Quota.DB_READ): Unit = synchronized(lock) {
        breakers.getValue(quota).probeInFlight = false
        if (Appwrite402Classifier.isRateLimited(e)) {
            val b = breakers.getValue(quota)
            b.rateCapUntilMs = nowMs() + CacheConfig.READ_GUARD_RATE_CAP_PAUSE_MS
            AppwriteUsageTracker.record429(name)
            reportOncePerInterval(
                REASON_RATE_LIMITED_429,
                name,
                quota,
                "Appwrite returned 429 for $name (${quota.name}) — paused, not latched"
            )
            return
        }
        if (!Appwrite402Classifier.isBudget402(e)) return
        val classified = Appwrite402Classifier.classify(e as AppwriteException)
        val targets = when (classified) {
            null -> setOf(quota, Quota.BANDWIDTH)
            else -> setOf(classified)
        }
        val probeInterval = if (classified == null) {
            CacheConfig.READ_GUARD_UNKNOWN_402_PROBE_INTERVAL_MS
        } else {
            CacheConfig.READ_GUARD_PROBE_INTERVAL_MS
        }
        val detail = classified?.name ?: "unattributed (scoped to ${quota.name}+BANDWIDTH)"
        targets.forEach { target ->
            AppwriteUsageTracker.record402(target, name)
            openBreaker(
                target,
                name,
                probeInterval,
                "Appwrite budget exhausted (402 $detail) — ${target.name} operations stopped"
            )
        }
    }

    // Must be called while holding [lock].
    private fun openBreaker(quota: Quota, source: String, probeIntervalMs: Long, message: String) {
        val b = breakers.getValue(quota)
        // Re-latch on a failed probe restarts the interval from now, so a still-dead budget is not
        // re-probed immediately.
        b.open = true
        b.openedAtMs = nowMs()
        b.probeIntervalMs = probeIntervalMs
        b.probeInFlight = false
        persist(b)
        sentryBreadcrumb(
            "appwrite.read_guard",
            "${quota.name} breaker opened by $source",
            SentryLevel.ERROR,
        )
        reportOncePerInterval(REASON_BUDGET_402, source, quota, message)
    }

    // Must be called while holding [lock].
    private fun persist(b: Breaker) {
        store?.save(b.quota, if (b.open) BreakerState(b.openedAtMs, b.probeIntervalMs) else null)
    }

    // Must be called while holding [lock]. Deduped per reason+quota so one blown quota cannot
    // suppress the alert for another.
    private fun reportOncePerInterval(reason: String, source: String, quota: Quota, message: String) {
        val now = nowMs()
        val key = "$reason:${quota.name}"
        val last = lastReportAtMs[key]
        if (last != null && now - last < CacheConfig.READ_GUARD_REPORT_INTERVAL_MS) return
        lastReportAtMs[key] = now
        reporter(message, mapOf("reason" to reason, "source" to source, "quota" to quota.name))
    }

    private fun reportToSentry(message: String, tags: Map<String, String>) {
        // Skip in debug: dev testing routinely trips the caps, and those alerts are noise.
        // The breaker still latches and breadcrumbs still record; only the Sentry ERROR is gated.
        // Production alerts are unaffected.
        if (BuildConfig.DEBUG) return
        Sentry.withScope { scope ->
            tags.forEach { (key, value) -> scope.setTag(key, value) }
            scope.fingerprint = listOf(
                "appwrite-read-guard",
                tags["reason"] ?: "unknown",
                tags["quota"] ?: "unknown",
            )
            Sentry.captureMessage(message, SentryLevel.ERROR)
        }
    }

    /**
     * [BreakerStore] over the app's main preference store — persists latches across process
     * restarts, so a cold start after a 402 does not re-probe a budget that is still spent.
     */
    class PrefsBreakerStore(context: Context) : BreakerStore {
        private val prefs = BouncerPreferences.also { PreferenceStore.initialize(context) }

        override fun save(quota: Quota, state: BreakerState?) {
            val openKey = PrefsRegistry.ReadGuard.BREAKER_OPEN_PREFIX + quota.name
            val atKey = PrefsRegistry.ReadGuard.BREAKER_OPENED_AT_PREFIX + quota.name
            val intervalKey = PrefsRegistry.ReadGuard.BREAKER_PROBE_INTERVAL_PREFIX + quota.name
            if (state == null) {
                prefs.remove(openKey, atKey, intervalKey)
            } else {
                prefs.putBoolean(openKey, true)
                prefs.putLong(atKey, state.openedAtMs)
                prefs.putLong(intervalKey, state.probeIntervalMs)
            }
        }

        override fun loadAll(): Map<Quota, BreakerState> = buildMap {
            Quota.entries.forEach { quota ->
                val openKey = PrefsRegistry.ReadGuard.BREAKER_OPEN_PREFIX + quota.name
                if (!prefs.getBoolean(openKey, false)) return@forEach
                put(
                    quota,
                    BreakerState(
                        openedAtMs = prefs.getLong(
                            PrefsRegistry.ReadGuard.BREAKER_OPENED_AT_PREFIX + quota.name, 0L
                        ),
                        probeIntervalMs = prefs.getLong(
                            PrefsRegistry.ReadGuard.BREAKER_PROBE_INTERVAL_PREFIX + quota.name,
                            CacheConfig.READ_GUARD_PROBE_INTERVAL_MS,
                        ),
                    )
                )
            }
        }

        override fun consumeLegacy(): BreakerState? {
            if (!prefs.getBoolean(PrefsRegistry.ReadGuard.LEGACY_BREAKER_OPEN, false)) return null
            val openedAt = prefs.getLong(PrefsRegistry.ReadGuard.LEGACY_BREAKER_OPENED_AT, 0L)
            prefs.remove(
                PrefsRegistry.ReadGuard.LEGACY_BREAKER_OPEN,
                PrefsRegistry.ReadGuard.LEGACY_BREAKER_OPENED_AT,
            )
            return BreakerState(openedAt, CacheConfig.READ_GUARD_PROBE_INTERVAL_MS)
        }
    }

    @VisibleForTesting
    internal fun reset() = synchronized(lock) {
        breakers.values.forEach { it.reset() }
        lastReportAtMs.clear()
        store = null
        onBreakerClosed = null
        nowMs = System::currentTimeMillis
        reporter = ::reportToSentry
    }
}
