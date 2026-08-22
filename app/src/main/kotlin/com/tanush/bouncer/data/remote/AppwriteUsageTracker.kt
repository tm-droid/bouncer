package com.tanush.bouncer.data.remote

import androidx.annotation.VisibleForTesting
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Counts what the app actually consumes from Appwrite, by category, so quota trends are visible
 * before they become another 402 rather than after.
 *
 * Every counter is fed from an existing funnel ([AppwriteTableClient.guardedRead] /
 * `guardedWrite`, [AppwriteClient.downloadFile], [AppwriteClient.executeFunction], the Realtime
 * callback), so instrumenting costs one line per funnel and cannot drift from what is really
 * billed. Read counts are ROWS, not calls — Appwrite bills per document returned.
 */
object AppwriteUsageTracker {

    private val dbRowsRead = AtomicLong()
    private val dbWrites = AtomicLong()
    private val functionExecutions = AtomicLong()

    /**
     * Player-search executions specifically.
     *
     * A subset of [functionExecutions], counted separately because it is the only execution driven
     * directly by typing: commentary and presence fire on match events and a fixed heartbeat, so
     * their volume is predictable and this one's is not. Aggregated together, a search box that
     * regressed into firing per keystroke would look like ordinary growth.
     */
    private val playerSearches = AtomicLong()
    private val storageRequests = AtomicLong()
    private val storageBytesDown = AtomicLong()
    private val realtimeMessages = AtomicLong()

    /**
     * Approximate: the SDK hands the callback an already-parsed Map, so the wire size is not
     * available. Named so nobody later mistakes it for ground truth.
     */
    private val realtimeBytesApprox = AtomicLong()

    private val quota402 = EnumMap<AppwriteReadGuard.Quota, AtomicLong>(AppwriteReadGuard.Quota::class.java)
        .apply { AppwriteReadGuard.Quota.entries.forEach { put(it, AtomicLong()) } }
    private val http429 = AtomicLong()

    @VisibleForTesting
    internal var emit: (name: String, value: Double) -> Unit = { name, value ->
        Sentry.metrics().gauge(name, value)
    }

    fun recordRead(rows: Int) { dbRowsRead.addAndGet(rows.toLong()) }
    fun recordWrite() { dbWrites.incrementAndGet() }
    /**
     * One function execution. Counted at the funnel rather than by callers, so a new caller cannot
     * forget to report itself and [functionId] cannot disagree with what was actually billed.
     */
    fun recordFunctionExecution(functionId: String = "") {
        functionExecutions.incrementAndGet()
        if (functionId == PLAYER_SEARCH_FUNCTION_ID) playerSearches.incrementAndGet()
    }

    fun recordStorageDownload(bytes: Int) {
        storageRequests.incrementAndGet()
        if (bytes > 0) storageBytesDown.addAndGet(bytes.toLong())
    }

    fun recordRealtimeMessage(approxBytes: Int) {
        realtimeMessages.incrementAndGet()
        realtimeBytesApprox.addAndGet(approxBytes.toLong())
    }

    /**
     * A quota rejection. Breadcrumbed immediately — unlike the per-operation counters, these are
     * rare and are exactly the context wanted when triaging a degraded session.
     */
    fun record402(quota: AppwriteReadGuard.Quota, source: String) {
        quota402.getValue(quota).incrementAndGet()
        sentryBreadcrumb(
            "appwrite.usage",
            "402 ${quota.name} at $source",
            SentryLevel.WARNING,
            mapOf("quota" to quota.name, "source" to source),
        )
    }

    fun record429(source: String) {
        http429.incrementAndGet()
        sentryBreadcrumb(
            "appwrite.usage",
            "429 at $source",
            SentryLevel.WARNING,
            mapOf("source" to source),
        )
    }

    /**
     * Periodically publish and reset the counters.
     *
     * Deliberately NOT a breadcrumb per operation: Sentry's breadcrumb ring buffer holds ~100
     * entries, so a crumb per read would evict everything useful for crash triage long before the
     * crash. Gauges follow the existing `Sentry.metrics()` idiom in CricketRepository.
     */
    fun startPeriodicFlush(scope: CoroutineScope, intervalMs: Long = FLUSH_INTERVAL_MS) {
        scope.launch {
            while (isActive) {
                delay(intervalMs)
                flush()
            }
        }
    }

    @VisibleForTesting
    internal fun flush() {
        emit("appwrite.db.rows_read", dbRowsRead.getAndSet(0).toDouble())
        emit("appwrite.db.writes", dbWrites.getAndSet(0).toDouble())
        emit("appwrite.functions.executions", functionExecutions.getAndSet(0).toDouble())
        emit("appwrite.functions.player_searches", playerSearches.getAndSet(0).toDouble())
        emit("appwrite.storage.requests", storageRequests.getAndSet(0).toDouble())
        emit("appwrite.storage.bytes_down", storageBytesDown.getAndSet(0).toDouble())
        emit("appwrite.realtime.messages", realtimeMessages.getAndSet(0).toDouble())
        emit("appwrite.realtime.bytes_approx", realtimeBytesApprox.getAndSet(0).toDouble())
        emit("appwrite.http.429", http429.getAndSet(0).toDouble())
        quota402.forEach { (quota, counter) ->
            emit("appwrite.quota_402.${quota.name.lowercase()}", counter.getAndSet(0).toDouble())
        }
    }

    @VisibleForTesting
    internal fun reset() {
        dbRowsRead.set(0); dbWrites.set(0); functionExecutions.set(0); playerSearches.set(0)
        storageRequests.set(0); storageBytesDown.set(0)
        realtimeMessages.set(0); realtimeBytesApprox.set(0)
        http429.set(0)
        quota402.values.forEach { it.set(0) }
    }

    private const val FLUSH_INTERVAL_MS = 300_000L // 5 minutes
    private const val PLAYER_SEARCH_FUNCTION_ID = "bouncer-player-search"
}
