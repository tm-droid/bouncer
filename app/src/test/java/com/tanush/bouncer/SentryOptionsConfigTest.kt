package com.tanush.bouncer

import io.sentry.SentryEvent
import io.sentry.protocol.SentryException
import org.junit.Assert.*
import org.junit.Test

class SentryOptionsConfigTest {

    private val config = SentryOptionsConfig()

    private fun buildEvent(vararg exceptions: Pair<String, String>): SentryEvent {
        val event = SentryEvent()
        event.exceptions = exceptions.map { (type, value) ->
            SentryException().apply {
                this.type = type
                this.value = value
            }
        }.toMutableList()
        return event
    }

    // ── Cancellation filters ─────────────────────────────────────────────────

    @Test
    fun `JobCancellationException is dropped`() {
        val event = buildEvent("JobCancellationException" to "Job was cancelled")
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `CancellationException is dropped`() {
        val event = buildEvent("CancellationException" to "Coroutine was cancelled")
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `JobCancellationException as cause in chain is dropped`() {
        val event = buildEvent(
            "RuntimeException" to "wrapper",
            "JobCancellationException" to "Job was cancelled"
        )
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `value containing 'was cancelled' is dropped (fallback for wrapped exceptions)`() {
        // Some Sentry SDK versions store the fully-qualified class name in `type`,
        // but the message still says "was cancelled". The value fallback catches this.
        val event = buildEvent("kotlinx.coroutines.JobCancellationException" to "StandaloneCoroutine was cancelled")
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `StandaloneCoroutine was cancelled dropped via value fallback`() {
        val event = buildEvent("RuntimeException" to "StandaloneCoroutine was cancelled")
        assertTrue(config.shouldDropEvent(event))
    }

    // ── Network noise filters ────────────────────────────────────────────────

    @Test
    fun `UnknownHostException is dropped`() {
        val event = buildEvent("UnknownHostException" to "Unable to resolve host")
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `IOException with Canceled value is dropped`() {
        val event = buildEvent("IOException" to "Canceled")
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `IOException with Socket closed value is dropped`() {
        val event = buildEvent("IOException" to "Socket closed")
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `SocketException with Socket closed is dropped`() {
        // SocketException extends IOException — Sentry stores actual runtime type,
        // so the type field is "SocketException" not "IOException". Fixes ANDROID-1R.
        val event = buildEvent("SocketException" to "Socket closed")
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `Sentry ingest ConnectException is dropped`() {
        val event = buildEvent("ConnectException" to "Failed to connect to ingest.sentry.io")
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `ConnectException to non-Sentry host is dropped`() {
        val event = buildEvent("ConnectException" to "Failed to connect to api-iota-jade.vercel.app/64.29.17.195:443")
        assertTrue(config.shouldDropEvent(event))
    }

    // ── Scraping / billing noise ─────────────────────────────────────────────

    @Test
    fun `HTTP 403 scraping error is dropped`() {
        val event = buildEvent("IOException" to "HTTP error fetching URL. Status=403")
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `Insufficient credits error is dropped`() {
        val event = buildEvent("RuntimeException" to "Insufficient credits to complete request")
        assertTrue(config.shouldDropEvent(event))
    }

    // ── HTTP/2 stream cancellation ───────────────────────────────────────────

    @Test
    fun `StreamResetException stream was reset CANCEL is dropped`() {
        val event = buildEvent("StreamResetException" to "stream was reset: CANCEL")
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `StreamResetException with other value is dropped`() {
        val event = buildEvent("StreamResetException" to "stream was reset: NO_ERROR")
        assertTrue(config.shouldDropEvent(event))
    }

    // ── Real errors must NOT be dropped ─────────────────────────────────────

    @Test
    fun `NullPointerException is not dropped`() {
        val event = buildEvent("NullPointerException" to "Attempt to invoke method on null")
        assertFalse(config.shouldDropEvent(event))
    }

    @Test
    fun `IllegalStateException is not dropped`() {
        val event = buildEvent("IllegalStateException" to "Fragment not attached")
        assertFalse(config.shouldDropEvent(event))
    }

    @Test
    fun `empty exception list returns false`() {
        val event = SentryEvent()
        event.exceptions = mutableListOf()
        assertFalse(config.shouldDropEvent(event))
    }

    @Test
    fun `null exception list returns false`() {
        val event = SentryEvent()
        event.exceptions = null
        assertFalse(config.shouldDropEvent(event))
    }
}
