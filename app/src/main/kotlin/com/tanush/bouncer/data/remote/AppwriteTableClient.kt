package com.tanush.bouncer.data.remote

import android.os.SystemClock
import android.util.Log
import com.tanush.bouncer.data.CacheConfig
import com.tanush.bouncer.services.backoffDelayMs
import io.appwrite.services.Realtime
import io.appwrite.services.TablesDB
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Whether a subscription has been silent long enough to be presumed dead.
 *
 * Top-level and pure so it can be tested without an Appwrite client.
 *
 * @param lastEventAt an [android.os.SystemClock.elapsedRealtime] stamp, seeded at subscribe time so
 *   a subscription that never delivers a single event is still covered. Zero or negative means the
 *   clock was never armed, which is not staleness.
 */
internal fun isSubscriptionStale(lastEventAt: Long, now: Long, timeoutMs: Long): Boolean =
    lastEventAt > 0L && now - lastEventAt >= timeoutMs

abstract class AppwriteTableClient(private val tag: String) {

    protected var dbId = ""
        private set
    protected var tableId = ""
        private set

    open val isReady: Boolean
        get() = AppwriteClient.isReady && dbId.isNotBlank() && tableId.isNotBlank()

    protected val tablesDB: TablesDB?
        get() = AppwriteClient.client?.let { TablesDB(it) }

    @Volatile private var realtimeSubscription: Any? = null  // RealtimeSubscription handle
    /** Written from the Realtime callback thread, read from the heartbeat coroutine. */
    @Volatile private var lastEventAt = 0L
    private var realtimeFailureCount = 0
    protected val subscriptionStarted = AtomicBoolean(false)
    private val reconnectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    protected fun initTable(dbIdValue: String, tableIdValue: String) {
        dbId = dbIdValue
        tableId = tableIdValue
        if (!isReady) {
            Log.w(tag, "$tag not configured — remote tier disabled")
        }
    }

    // NOTE: Realtime is intentionally NOT paused by AppwriteReadGuard — websocket events
    // are not billed DB reads; any follow-up fetches they trigger go through guardedRead.
    protected fun startSubscriptionLoop(onEvent: (io.appwrite.models.RealtimeResponseEvent<Map<String, Any>>) -> Unit) {
        reconnectScope.launch {
            while (isActive) {
                try {
                    val client = AppwriteClient.client ?: break
                    val realtime = Realtime(client)
                    // TablesDB tables are backed by Appwrite collections internally, so the standard
                    // databases.{db}.collections.{table}.documents channel format is correct.
                    val channel = "databases.$dbId.collections.$tableId.documents"
                    (realtimeSubscription as? java.io.Closeable)?.close()
                    realtimeSubscription = null
                    // Armed before subscribing so a subscription that never delivers anything is
                    // still measured from a real instant.
                    lastEventAt = SystemClock.elapsedRealtime()
                    @Suppress("UNCHECKED_CAST")
                    realtimeSubscription = realtime.subscribe(channel) { response ->
                        lastEventAt = SystemClock.elapsedRealtime()
                        @Suppress("UNCHECKED_CAST")
                        onEvent(response as io.appwrite.models.RealtimeResponseEvent<Map<String, Any>>)
                    }
                    realtimeFailureCount = 0
                    awaitEventsOrStaleness()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    realtimeSubscription = null
                    realtimeFailureCount++
                    val backoffMs = backoffDelayMs(
                        realtimeFailureCount,
                        CacheConfig.REALTIME_RECONNECT_MIN_MS,
                        CacheConfig.REALTIME_RECONNECT_MAX_MS,
                    )
                    Log.w(tag, "Realtime subscription lost (#$realtimeFailureCount), reconnecting in ${backoffMs}ms: ${e.message}")
                    if (realtimeFailureCount % 3 == 0) Sentry.captureException(e)
                    delay(backoffMs)
                }
            }
        }
    }

    /**
     * Hold the subscription open, waking periodically to check it is still alive.
     *
     * This replaces `delay(Long.MAX_VALUE)`. That parked forever on the assumption — asserted in a
     * comment and validated by nothing — that the Appwrite SDK always reconnects internally. If the
     * socket died *inside* the SDK without throwing out of `subscribe()`, the catch below never fired
     * and this client believed it was subscribed for the rest of the process's life, silently
     * receiving nothing. Returning normally here drops back into the loop, which resubscribes.
     *
     * Silence is genuinely ambiguous — a channel with no live match is quiet for good reasons — so
     * the timeout is deliberately generous and a false positive costs only a websocket handshake.
     */
    private suspend fun awaitEventsOrStaleness() {
        while (currentCoroutineContext().isActive) {
            delay(CacheConfig.REALTIME_HEARTBEAT_MS)
            val stale = isSubscriptionStale(
                lastEventAt,
                SystemClock.elapsedRealtime(),
                CacheConfig.REALTIME_STALE_TIMEOUT_MS,
            )
            if (stale) {
                Log.w(tag, "Realtime subscription silent for ${CacheConfig.REALTIME_STALE_TIMEOUT_MS}ms — resubscribing")
                (realtimeSubscription as? java.io.Closeable)?.close()
                realtimeSubscription = null
                return
            }
        }
    }

    /**
     * Funnel for all billed TablesDB reads. Returns null when the table is not configured
     * or [AppwriteReadGuard] is blocking (breaker open / rate capped) — callers already
     * treat null like the not-ready path. Failures are recorded (402 latches the guard)
     * and then RETHROWN so each caller's existing runCatching + logSilentFailure handling
     * is unchanged — the guard never suppresses errors.
     */
    protected suspend fun <T> guardedRead(name: String, block: suspend (TablesDB) -> T): T? {
        val db = tablesDB ?: return null
        if (AppwriteReadGuard.checkAndCount(name) != null) {
            Log.w(tag, "Appwrite read '$name' blocked by AppwriteReadGuard")
            return null
        }
        val result = runCatching { block(db) }
        result.fold(
            // Appwrite bills one read per document returned; pass the row count so the rate
            // windows reflect true billed volume, not just the single call.
            onSuccess = { value -> AppwriteReadGuard.recordSuccess(rowCountOf(value)) },
            onFailure = { e ->
                if (e !is CancellationException) AppwriteReadGuard.recordFailure(name, e)
            }
        )
        return result.getOrThrow()
    }

    private fun rowCountOf(value: Any?): Int = when (value) {
        null -> 0
        is io.appwrite.models.RowList<*> -> value.rows.size
        else -> 1 // single-row read (getRow) or non-list result
    }

    /** Standard silent-failure log used by all remote-tier operations. */
    protected fun logSilentFailure(prefix: String, e: Throwable) {
        if (e is io.appwrite.exceptions.AppwriteException) {
            Log.d(tag, "$prefix (silent): ${e.message} [${e.code}]")
        } else {
            Log.d(tag, "$prefix (silent): ${e.message}")
        }
    }

    fun stop() {
        reconnectScope.cancel()
        (realtimeSubscription as? java.io.Closeable)?.close()
        realtimeSubscription = null
    }
}
