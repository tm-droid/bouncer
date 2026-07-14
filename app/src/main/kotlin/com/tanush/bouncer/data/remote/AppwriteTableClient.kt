package com.tanush.bouncer.data.remote

import android.util.Log
import com.tanush.bouncer.data.CacheConfig
import io.appwrite.services.Realtime
import io.appwrite.services.TablesDB
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

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
            var backoffMs = CacheConfig.REALTIME_RECONNECT_MIN_MS
            while (isActive) {
                try {
                    val client = AppwriteClient.client ?: break
                    val realtime = Realtime(client)
                    // TablesDB tables are backed by Appwrite collections internally, so the standard
                    // databases.{db}.collections.{table}.documents channel format is correct.
                    val channel = "databases.$dbId.collections.$tableId.documents"
                    (realtimeSubscription as? java.io.Closeable)?.close()
                    realtimeSubscription = null
                    @Suppress("UNCHECKED_CAST")
                    realtimeSubscription = realtime.subscribe(channel) { response ->
                        @Suppress("UNCHECKED_CAST")
                        onEvent(response as io.appwrite.models.RealtimeResponseEvent<Map<String, Any>>)
                    }
                    backoffMs = CacheConfig.REALTIME_RECONNECT_MIN_MS
                    realtimeFailureCount = 0
                    // Park until scope cancellation or subscribe() throws. The Appwrite SDK manages
                    // WebSocket reconnection internally; spinning subscribe() in a tight loop sends
                    // thousands of frames/sec and floods the network.
                    delay(Long.MAX_VALUE)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    realtimeSubscription = null
                    realtimeFailureCount++
                    Log.w(tag, "Realtime subscription lost (#$realtimeFailureCount), reconnecting in ${backoffMs}ms: ${e.message}")
                    if (realtimeFailureCount % 3 == 0) Sentry.captureException(e)
                    delay(backoffMs)
                    backoffMs = minOf(backoffMs * 2, CacheConfig.REALTIME_RECONNECT_MAX_MS)
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
