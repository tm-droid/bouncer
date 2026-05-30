package com.tanush.bouncer.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LiveScoreWebSocketClientTest {

    private lateinit var mockOkHttp: OkHttpClient
    private lateinit var mockWs: WebSocket

    // Captures the listener from each newWebSocket() call (last call wins).
    private val listenerSlot = slot<WebSocketListener>()

    @Before
    fun setUp() {
        mockWs = mockk(relaxed = true)
        mockOkHttp = mockk()
        every { mockOkHttp.newWebSocket(any(), capture(listenerSlot)) } returns mockWs
    }

    /**
     * disconnect() must cancel the pending reconnect coroutine so the socket
     * doesn't re-open after the caller has explicitly stopped it.
     */
    @Test
    fun disconnect_cancelsReconnectJob_stateRemainsDisconnected() = runTest {
        val client = LiveScoreWebSocketClient("ws://localhost:8080", mockOkHttp, backgroundScope)
        client.connect()

        // Simulate unexpected failure — triggers RECONNECTING with 1 s backoff.
        listenerSlot.captured.onFailure(mockWs, IOException("dropped"), null)
        advanceUntilIdle()
        assertEquals(ConnectionState.RECONNECTING, client.connectionState.value)

        // Caller tears down before the backoff fires.
        client.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)

        // Advance well past the max backoff — no reconnect should fire.
        advanceTimeBy(35_000L)
        advanceUntilIdle()
        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
        // newWebSocket only called once (initial connect, no reconnect)
        verify(exactly = 1) { mockOkHttp.newWebSocket(any(), any()) }
    }

    /**
     * Each successive failure must double the backoff delay, capped at 30 s.
     * Expected sequence: 1 s → 2 s → 4 s → 8 s → 16 s → 30 s (capped).
     */
    @Test
    fun backoff_doublesEachFailure_capsAt30s() = runTest {
        val scheduler = testScheduler
        val connectTimes = mutableListOf<Long>()

        every { mockOkHttp.newWebSocket(any(), capture(listenerSlot)) } answers {
            connectTimes.add(scheduler.currentTime)
            mockWs
        }

        val client = LiveScoreWebSocketClient("ws://localhost:8080", mockOkHttp, backgroundScope)
        client.connect()
        advanceUntilIdle()

        val expectedDelays = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000)

        for (expectedDelay in expectedDelays) {
            listenerSlot.captured.onFailure(mockWs, IOException("fail"), null)
            advanceUntilIdle()           // let reconnect coroutine reach delay()
            advanceTimeBy(expectedDelay + 1)  // fire the delay
            advanceUntilIdle()           // let openConnection() run
        }

        // connectTimes[0] = initial connect; [1..6] = reconnects
        assertEquals(7, connectTimes.size)
        for (i in expectedDelays.indices) {
            val actual = connectTimes[i + 1] - connectTimes[i]
            assert(actual >= expectedDelays[i]) {
                "Reconnect $i: expected delay ≥ ${expectedDelays[i]} ms but was $actual ms"
            }
        }
    }

    /**
     * onNetworkAvailable() must cancel the pending backoff coroutine and
     * reconnect immediately, without waiting for the remaining delay.
     */
    @Test
    fun onNetworkAvailable_bypassesRemainingBackoffDelay() = runTest {
        val client = LiveScoreWebSocketClient("ws://localhost:8080", mockOkHttp, backgroundScope)
        client.connect()

        // Simulate failure → 1 s backoff pending.
        listenerSlot.captured.onFailure(mockWs, IOException("dropped"), null)
        advanceUntilIdle()  // reconnect coroutine runs to delay(1000) and suspends

        // Advance only halfway — second connect must NOT have fired yet.
        advanceTimeBy(500L)
        verify(exactly = 1) { mockOkHttp.newWebSocket(any(), any()) }
        assertEquals(ConnectionState.RECONNECTING, client.connectionState.value)

        // Network recovers — onNetworkAvailable() cancels the delay and calls
        // openConnection() synchronously, so the second connect fires immediately.
        client.onNetworkAvailable()
        verify(exactly = 2) { mockOkHttp.newWebSocket(any(), any()) }
    }
}
