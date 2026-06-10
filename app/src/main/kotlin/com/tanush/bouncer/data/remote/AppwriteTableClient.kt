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
                    @Suppress("UNCHECKED_CAST")
                    realtimeSubscription = realtime.subscribe(channel) { response ->
                        @Suppress("UNCHECKED_CAST")
                        onEvent(response as io.appwrite.models.RealtimeResponseEvent<Map<String, Any>>)
                    }
                    backoffMs = CacheConfig.REALTIME_RECONNECT_MIN_MS  // reset on successful subscribe
                    // Appwrite Realtime is push-based; subscription stays alive until an error
                    // or cancellation. Re-looping immediately after subscribe() returns is safe
                    // — if the socket is healthy the SDK won't throw and we just re-subscribe
                    // (which the SDK deduplicates). If the socket died, we reconnect after backoff.
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

    fun stop() {
        reconnectScope.cancel()
        realtimeSubscription = null
    }
}
