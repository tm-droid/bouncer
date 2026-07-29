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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
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

/**
 * Rough byte size of a Realtime payload, for the bandwidth counter only.
 *
 * The SDK hands the callback an already-parsed object, so the wire size is gone by the time we see
 * it. Summing the string values captures the part that actually matters — a `match-live-detail`
 * event is one 15-80 KB JSON string plus a handful of short fields — and costs one pass over a
 * small map, which is affordable on the callback thread. Never treat it as ground truth.
 */
private fun approxSizeOf(payload: Any?): Int = when (payload) {
    is Map<*, *> -> payload.values.sumOf { (it as? String)?.length ?: APPROX_NON_STRING_FIELD_BYTES }
    is String -> payload.length
    else -> 0
}

private const val APPROX_NON_STRING_FIELD_BYTES = 8

abstract class AppwriteTableClient(private val tag: String) {

    protected var dbId = ""
        private set
    protected var tableId = ""
        private set

    open val isReady: Boolean
        get() = AppwriteClient.isReady && dbId.isNotBlank() && tableId.isNotBlank()

    protected val tablesDB: TablesDB?
        get() = AppwriteClient.client?.let { TablesDB(it) }

    protected val subscriptionStarted = AtomicBoolean(false)
    private val reconnectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Live subscriptions by channel. Guarded by [channelLock]. */
    private val channels = mutableMapOf<String, ChannelSubscription>()
    private val channelLock = Any()

    protected fun initTable(dbIdValue: String, tableIdValue: String) {
        dbId = dbIdValue
        tableId = tableIdValue
        if (!isReady) {
            Log.w(tag, "$tag not configured — remote tier disabled")
        }
    }

    /**
     * Every row in the table. TablesDB tables are backed by Appwrite collections internally, so the
     * standard `databases.{db}.collections.{table}.documents` format is correct.
     */
    protected fun tableChannel(): String = "databases.$dbId.collections.$tableId.documents"

    /**
     * A single row. Appwrite has no query-filtered channels, so this is the only way to receive one
     * match's updates instead of every match anyone is watching.
     */
    protected fun rowChannel(rowId: String): String = "databases.$dbId.tables.$tableId.rows.$rowId"

    /** One channel's socket, reconnect state and listeners. */
    private inner class ChannelSubscription(val channel: String) {
        @Volatile var handle: Any? = null // RealtimeSubscription
        /** Written from the Realtime callback thread, read from the heartbeat coroutine. */
        @Volatile var lastEventAt = 0L
        var failureCount = 0
        var job: Job? = null
        var lingerJob: Job? = null

        /**
         * All callers currently subscribed to this channel. A list rather than a single callback
         * because several screens can share one channel — with a single callback, only the caller
         * that happened to open the socket would ever receive an event.
         *
         * Copy-on-write: mutated under [channelLock] but iterated on the Realtime callback thread.
         */
        val listeners = java.util.concurrent.CopyOnWriteArrayList<
            (io.appwrite.models.RealtimeResponseEvent<Map<String, Any>>) -> Unit
            >()

        fun start(onEvent: (io.appwrite.models.RealtimeResponseEvent<Map<String, Any>>) -> Unit) {
            job = reconnectScope.launch {
                while (isActive) {
                    try {
                        val client = AppwriteClient.client ?: break
                        val realtime = Realtime(client)
                        (handle as? java.io.Closeable)?.close()
                        handle = null
                        // Armed before subscribing so a subscription that never delivers anything
                        // is still measured from a real instant.
                        lastEventAt = SystemClock.elapsedRealtime()
                        @Suppress("UNCHECKED_CAST")
                        handle = realtime.subscribe(channel) { response ->
                            lastEventAt = SystemClock.elapsedRealtime()
                            @Suppress("UNCHECKED_CAST")
                            val typed = response as io.appwrite.models.RealtimeResponseEvent<Map<String, Any>>
                            AppwriteUsageTracker.recordRealtimeMessage(approxSizeOf(typed.payload))
                            // One listener throwing must not starve the others.
                            listeners.forEach { listener ->
                                runCatching { listener(typed) }.onFailure {
                                    Log.w(tag, "Realtime listener failed on $channel: ${it.message}")
                                }
                            }
                        }
                        failureCount = 0
                        awaitEventsOrStaleness()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        handle = null
                        failureCount++
                        val backoffMs = backoffDelayMs(
                            failureCount,
                            CacheConfig.REALTIME_RECONNECT_MIN_MS,
                            CacheConfig.REALTIME_RECONNECT_MAX_MS,
                        )
                        Log.w(tag, "Realtime $channel lost (#$failureCount), reconnecting in ${backoffMs}ms: ${e.message}")
                        if (failureCount % 3 == 0) Sentry.captureException(e)
                        delay(backoffMs)
                    }
                }
            }
        }

        fun close() {
            job?.cancel()
            job = null
            (handle as? java.io.Closeable)?.close()
            handle = null
            listeners.clear()
        }

        /**
         * Hold the subscription open, waking periodically to check it is still alive.
         *
         * This replaces `delay(Long.MAX_VALUE)`. That parked forever on the assumption — asserted in
         * a comment and validated by nothing — that the Appwrite SDK always reconnects internally.
         * If the socket died *inside* the SDK without throwing out of `subscribe()`, the catch above
         * never fired and this client believed it was subscribed for the rest of the process's life,
         * silently receiving nothing. Returning normally here drops back into the loop, which
         * resubscribes.
         *
         * Silence is genuinely ambiguous — a channel with no live match is quiet for good reasons —
         * so the timeout is deliberately generous and a false positive costs only a handshake.
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
                    Log.w(tag, "Realtime $channel silent for ${CacheConfig.REALTIME_STALE_TIMEOUT_MS}ms — resubscribing")
                    (handle as? java.io.Closeable)?.close()
                    handle = null
                    return
                }
            }
        }
    }

    /**
     * Subscribe to [channel], sharing one socket between all callers, and return a handle that
     * releases this caller's claim. The always-on clients ignore the handle and release via [stop].
     *
     * Release is deferred by [CacheConfig.REALTIME_LINGER_MS] rather than closing immediately:
     * without it a screen rotation or tab switch tears the websocket down and re-handshakes it on
     * every ViewModel recreation.
     *
     * NOTE: Realtime is intentionally NOT paused by AppwriteReadGuard — websocket events are not
     * billed DB reads; any follow-up fetches they trigger go through [guardedRead].
     */
    protected fun acquireChannel(
        channel: String,
        onEvent: (io.appwrite.models.RealtimeResponseEvent<Map<String, Any>>) -> Unit,
    ): Closeable {
        synchronized(channelLock) {
            val existing = channels[channel]
            if (existing != null) {
                existing.listeners.add(onEvent)
                existing.lingerJob?.cancel()
                existing.lingerJob = null
            } else {
                val created = ChannelSubscription(channel)
                created.listeners.add(onEvent)
                channels[channel] = created
                created.start(onEvent)
            }
        }
        val released = AtomicBoolean(false)
        return Closeable {
            // Guard against a caller closing twice, which would drop someone else's claim.
            if (released.compareAndSet(false, true)) release(channel, onEvent)
        }
    }

    private fun release(
        channel: String,
        onEvent: (io.appwrite.models.RealtimeResponseEvent<Map<String, Any>>) -> Unit,
    ): Unit = synchronized(channelLock) {
        val sub = channels[channel] ?: return
        sub.listeners.remove(onEvent)
        if (sub.listeners.isNotEmpty()) return
        sub.lingerJob = reconnectScope.launch {
            delay(CacheConfig.REALTIME_LINGER_MS)
            synchronized(channelLock) {
                // Re-check: someone may have re-acquired during the linger.
                if (sub.listeners.isEmpty()) {
                    sub.close()
                    channels.remove(channel)
                }
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
            onSuccess = { value ->
                val rows = rowCountOf(value)
                AppwriteReadGuard.recordSuccess(rows)
                AppwriteUsageTracker.recordRead(rows)
            },
            onFailure = { e ->
                if (e !is CancellationException) AppwriteReadGuard.recordFailure(name, e)
            }
        )
        return result.getOrThrow()
    }

    /**
     * Funnel for billed TablesDB writes, mirroring [guardedRead] but on the DB_WRITE quota.
     *
     * Writes were entirely ungated: only reads had a breaker, so a write loop or an exhausted write
     * budget had nothing standing in front of it. Returns null when the table is not configured or
     * the guard is blocking; failures are recorded (a 402 latches the write breaker) and rethrown so
     * each caller's existing handling is unchanged.
     */
    protected suspend fun <T> guardedWrite(name: String, block: suspend (TablesDB) -> T): T? {
        val db = tablesDB ?: return null
        if (AppwriteReadGuard.checkAndCount(name, AppwriteReadGuard.Quota.DB_WRITE) != null) {
            Log.w(tag, "Appwrite write '$name' blocked by AppwriteReadGuard")
            return null
        }
        val result = runCatching { block(db) }
        result.fold(
            onSuccess = { AppwriteUsageTracker.recordWrite() },
            onFailure = { e ->
                if (e !is CancellationException) {
                    AppwriteReadGuard.recordFailure(name, e, AppwriteReadGuard.Quota.DB_WRITE)
                }
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

    /**
     * Close every channel and reset the start latch so the client can be restarted.
     *
     * Cancels the scope's CHILDREN, not the scope. `reconnectScope.cancel()` put the scope into a
     * permanently cancelled state, so nothing could ever be relaunched on it — the first
     * background/foreground cycle would have killed realtime for the rest of the process.
     */
    fun stop() = synchronized(channelLock) {
        reconnectScope.coroutineContext.cancelChildren()
        channels.values.forEach { it.close() }
        channels.clear()
        subscriptionStarted.set(false)
    }
}
