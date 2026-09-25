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

    // ── Throttled upstream conditions ────────────────────────────────────────

    @Test
    fun `first SentryHttpClientException in a window is kept, the repeat is dropped`() {
        val first = buildEvent("SentryHttpClientException" to "HTTP Client Error with status code: 504")
        val repeat = buildEvent("SentryHttpClientException" to "HTTP Client Error with status code: 504")
        assertFalse(config.shouldDropEvent(first))
        assertTrue(config.shouldDropEvent(repeat))
    }

    @Test
    fun `a 5xx from Cricbuzz is dropped - it is their outage, not ours`() {
        // ANDROID-4A/4C/4R: the injected OkHttp integration captures every 5xx on every host.
        val event = buildEvent("SentryHttpClientException" to "HTTP Client Error with status code: 504")
        event.request = io.sentry.protocol.Request().apply { url = "https://www.cricbuzz.com/cricket-match/live-scores" }
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `a 5xx from Appwrite is kept - that backend is ours`() {
        val event = buildEvent("SentryHttpClientException" to "HTTP Client Error with status code: 503")
        event.request = io.sentry.protocol.Request().apply {
            url = "https://sgp.cloud.appwrite.io/v1/storage/buckets/cricket-stats/files/live-winprob/download"
        }
        assertFalse(config.shouldDropEvent(event))
    }

    @Test
    fun `a different upstream failure is not hidden by another's throttle`() {
        val appwrite = buildEvent("ConnectException" to "Failed to connect to sgp.cloud.appwrite.io/151.101.3.52:443")
        val other = buildEvent("ConnectException" to "Failed to connect to api.cricbuzz.com/1.2.3.4:443")
        assertFalse(config.shouldDropEvent(appwrite))
        assertFalse(config.shouldDropEvent(other))
        assertTrue(config.shouldDropEvent(appwrite))
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
        // SocketException extends IOException. Sentry stores the actual runtime type,
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
    fun `ConnectException to non-Sentry host is NOT dropped`() {
        // ConnectException to owned/third-party backends (Appwrite, RapidAPI, etc.) is actionable.
        val event = buildEvent("ConnectException" to "Failed to connect to sgp.cloud.appwrite.io/64.29.17.195:443")
        assertFalse(config.shouldDropEvent(event))
    }


    // ── Scraping / billing noise ─────────────────────────────────────────────

    @Test
    fun `HTTP 403 scraping error is dropped`() {
        val event = buildEvent("IOException" to "HTTP error fetching URL. Status=403")
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `HttpStatusException 403 from Jsoup is dropped`() {
        // Jsoup throws HttpStatusException (subclass of IOException); Sentry serializes
        // the simple class name as type. The value must still be caught by the substring filter.
        val event = buildEvent("HttpStatusException" to "HTTP error fetching URL. Status=403, URL=[https://www.espncricinfo.com/live-cricket-score]")
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `exception chain containing HttpStatusException 403 is dropped`() {
        // When CricInfoScraper wraps or rethrows, the SDK may build a multi-exception event.
        // shouldDropEvent must inspect every entry in the chain, not just the outermost one.
        val event = buildEvent(
            "RuntimeException" to "Failed to find live match",
            "HttpStatusException" to "HTTP error fetching URL. Status=403, URL=[https://www.espncricinfo.com/live-cricket-score]"
        )
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `HTTP 429 rate-limit error is dropped`() {
        val event = buildEvent("HttpStatusException" to "HTTP error fetching URL. Status=429, URL=[https://www.espncricinfo.com/live-cricket-score]")
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
    fun `StreamResetException NO_ERROR is dropped`() {
        val event = buildEvent("StreamResetException" to "stream was reset: NO_ERROR")
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `StreamResetException REFUSED_STREAM is dropped`() {
        val event = buildEvent("StreamResetException" to "stream was reset: REFUSED_STREAM")
        assertTrue(config.shouldDropEvent(event))
    }

    @Test
    fun `StreamResetException INTERNAL_ERROR is NOT dropped`() {
        // INTERNAL_ERROR and PROTOCOL_ERROR indicate real bugs, not lifecycle noise.
        val event = buildEvent("StreamResetException" to "stream was reset: INTERNAL_ERROR")
        assertFalse(config.shouldDropEvent(event))
    }

    // ── Real errors must NOT be dropped ─────────────────────────────────────

    @Test
    fun `HttpStatusException with Status=500 is NOT dropped`() {
        // Only 403/429 substrings are in IGNORED_MESSAGE_SUBSTRINGS; server errors are bugs.
        val event = buildEvent("HttpStatusException" to "HTTP error fetching URL. Status=500, URL=[https://www.espncricinfo.com/]")
        assertFalse(config.shouldDropEvent(event))
    }

    @Test
    fun `ConnectException to Appwrite backend is NOT dropped`() {
        val event = buildEvent("ConnectException" to "Failed to connect to sgp.cloud.appwrite.io/34.87.56.12:443")
        assertFalse(config.shouldDropEvent(event))
    }

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
