package com.tanush.bouncer.services

import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

/**
 * WebSocket client for receiving live cricket score updates.
 *
 * Exposes incoming messages as a [SharedFlow] and connection lifecycle
 * as a [StateFlow].  Automatically reconnects with exponential backoff
 * on unexpected disconnections.
 */
class LiveScoreWebSocketClient(
    private val wsUrl: String,
    private val client: OkHttpClient = buildDefaultClient()
) {
    companion object {
        private const val TAG = "LiveScoreWS"
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
        private const val NORMAL_CLOSE_CODE = 1000

        private fun buildDefaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MINUTES)  // no read timeout for WS
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // replay=1 so a late/slow consumer gets the last message immediately.
    // Larger buffer cushions bursts when downstream is busy (e.g. waiting on LLM).
    private val _messages = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 128)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var webSocket: WebSocket? = null
    private var backoffMs = INITIAL_BACKOFF_MS
    private val shouldReconnect = AtomicBoolean(false)
    // Tracks the in-flight reconnect delay so disconnect() can cancel it immediately
    // instead of letting it run out (up to MAX_BACKOFF_MS = 30 s) before checking shouldReconnect.
    private var reconnectJob: Job? = null

    // ── Public API ──────────────────────────────────────────────────────

    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING
        ) return

        shouldReconnect.set(true)
        openConnection(isReconnect = false)
    }

    fun disconnect() {
        // Cancel the pending reconnect delay first so the coroutine doesn't outlive this call.
        reconnectJob?.cancel()
        reconnectJob = null
        // Close socket next, then flip the flag. Doing it the other way leaves a
        // small window where an in-flight scheduleReconnect() could fire and re-open.
        val ws = webSocket
        webSocket = null
        ws?.close(NORMAL_CLOSE_CODE, "Client disconnected")
        shouldReconnect.set(false)
        _connectionState.value = ConnectionState.DISCONNECTED
        backoffMs = INITIAL_BACKOFF_MS
        log("Disconnected by caller")
    }

    fun send(message: String): Boolean {
        val ws = webSocket ?: return false
        return ws.send(message)
    }

    // ── Internal ────────────────────────────────────────────────────────

    private fun openConnection(isReconnect: Boolean) {
        _connectionState.value =
            if (isReconnect) ConnectionState.RECONNECTING else ConnectionState.CONNECTING

        val request = Request.Builder().url(wsUrl).build()

        breadcrumb("ws.connect", "Opening WebSocket", SentryLevel.INFO) {
            setData("url", wsUrl)
            setData("reconnect", isReconnect)
        }

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONNECTED
                backoffMs = INITIAL_BACKOFF_MS
                log("Connected")
                breadcrumb("ws.open", "WebSocket connected", SentryLevel.INFO)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                _messages.tryEmit(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                log("Server closing: $code $reason")
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                log("Closed: $code $reason")
                breadcrumb("ws.closed", "WebSocket closed", SentryLevel.INFO) {
                    setData("code", code)
                    setData("reason", reason)
                }
                handleDisconnection()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                breadcrumb("ws.failure", "WebSocket failure", SentryLevel.ERROR) {
                    setData("error", t.message ?: "unknown")
                    setData("errorType", t.javaClass.simpleName)
                }
                Sentry.captureException(t)
                handleDisconnection()
            }
        })
    }

    private fun handleDisconnection() {
        webSocket = null
        if (!shouldReconnect.get()) {
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        // Cancel any stale pending delay before starting a new one (e.g. rapid failure loop).
        reconnectJob?.cancel()
        val delay = backoffMs
        backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        _connectionState.value = ConnectionState.RECONNECTING

        log("Reconnecting in ${delay}ms")
        breadcrumb("ws.reconnect", "Scheduling reconnect", SentryLevel.WARNING) {
            setData("delayMs", delay)
        }

        reconnectJob = scope.launch {
            delay(delay)
            if (shouldReconnect.get()) {
                openConnection(isReconnect = true)
            }
        }
    }

    // ── Logging helpers ─────────────────────────────────────────────────

    private fun log(msg: String) {
        Log.d(TAG, msg)
    }

    private inline fun breadcrumb(
        category: String,
        message: String,
        level: SentryLevel,
        extras: Breadcrumb.() -> Unit = {}
    ) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.category = category
            this.message = message
            this.level = level
            extras()
        })
    }
}
