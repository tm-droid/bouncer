package com.tanush.bouncer.data.remote

import android.util.Log
import com.tanush.bouncer.data.CacheConfig
import io.appwrite.services.Realtime
import io.appwrite.services.TablesDB
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
    protected val subscriptionStarted = AtomicBoolean(false)
    private val reconnectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    protected fun initTable(dbIdValue: String, tableIdValue: String) {
        dbId = dbIdValue
        tableId = tableIdValue
        if (!isReady) {
            Log.w(tag, "$tag not configured — remote tier disabled")
        }
    }

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
                    // Park until scope cancellation or subscribe() throws. The Appwrite SDK manages
                    // WebSocket reconnection internally; spinning subscribe() in a tight loop sends
                    // thousands of frames/sec and floods the network.
                    delay(Long.MAX_VALUE)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    realtimeSubscription = null
                    Log.w(tag, "Realtime subscription lost, reconnecting in ${backoffMs}ms: ${e.message}")
                    delay(backoffMs)
                    backoffMs = minOf(backoffMs * 2, CacheConfig.REALTIME_RECONNECT_MAX_MS)
                }
            }
        }
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
