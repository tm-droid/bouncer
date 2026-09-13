package com.tanush.bouncer

import android.util.Log
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroidOptions

/**
 * Sentry options configuration applied during auto-init.
 *
 * Responsibilities:
 * - Filters out noisy / non-actionable events before they consume quota
 *   (e.g. OkHttp cancellations, 403 scraping errors, emulator IPv6 failures)
 * - Sets reasonable connection / read timeouts so Sentry transport doesn't
 *   block the thread pool on flaky emulator networks
 */
class SentryOptionsConfig : Sentry.OptionsConfiguration<SentryAndroidOptions> {

    companion object {
        private const val TAG = "SentryOptionsConfig"

        /** Exception messages that indicate benign cancellations, not real errors. */
        private val IGNORED_EXCEPTION_MESSAGES = setOf(
            "Canceled",          // OkHttp / Coil lifecycle cancellation
            "Socket closed",     // Connection closed by peer
        )

        /** Exception message substrings that indicate non-actionable scraping errors. */
        private val IGNORED_MESSAGE_SUBSTRINGS = listOf(
            "HTTP error fetching URL. Status=403",  // Cricinfo blocking scrapers
            "HTTP error fetching URL. Status=429",  // Rate-limited
            "Insufficient credits",                 // OpenRouter billing
        )
    }

    override fun configure(options: SentryAndroidOptions) {
        val isDebugBuild = BuildConfig.DEBUG

        // ── Transport timeouts ───────────────────────────────────────────
        // Shorter timeouts prevent Sentry transport threads from hanging on
        // flaky emulator networks (the default 5s is too generous for IPv6
        // failures that never resolve).
        options.connectionTimeoutMillis = 5_000
        options.readTimeoutMillis = 5_000
        options.setEnableBackpressureHandling(true)

        // ── Core telemetry behavior ──────────────────────────────────────
        options.isDebug = isDebugBuild
        options.setSendDefaultPii(true)
        options.logs.setEnabled(true)
        options.metrics.setEnabled(true)

        // Keep traces enabled so AI/LLM and performance spans are retained.
        options.setTracesSampleRate(if (isDebugBuild) 1.0 else 0.2)

        // Session Replay: capture all debug sessions, sample production sessions. The on-error rate
        // decides which sessions keep the rolling screenshot buffer running in the background, so
        // 1.0 meant every production session paid that CPU and battery cost waiting for an error.
        options.sessionReplay.setOnErrorSampleRate(if (isDebugBuild) 1.0 else 0.25)
        options.sessionReplay.setSessionSampleRate(if (isDebugBuild) 1.0 else 0.1)
        options.sessionReplay.setMaskAllText(true)
        options.sessionReplay.setMaskAllImages(true)
        options.sessionReplay.setDebug(isDebugBuild)

        // ── Event filtering ──────────────────────────────────────────────
        options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
            if (shouldDropEvent(event)) {
                Log.d(TAG, "Dropping noise event: ${describeEvent(event)}")
                null // returning null drops the event
            } else {
                event
            }
        }

        Log.d(
            TAG,
            "Sentry configured: replay(session=${if (isDebugBuild) 1.0 else 0.1}, error=1.0), logs/metrics enabled"
        )
    }

    /**
     * Returns true for events that are non-actionable noise and should be silently dropped
     * to preserve Sentry quota and keep the Issues feed clean.
     *
     * Visible for testing: `internal` so unit tests in the same module can call it directly
     * without reflection, giving regression coverage on the noise-filter rules.
     */
    internal fun shouldDropEvent(event: SentryEvent): Boolean {
        val exceptions = event.exceptions ?: return false

        for (sentryException in exceptions) {
            val type = sentryException.type ?: ""
            val value = sentryException.value ?: ""

            // 1. Benign cancellations (Coil image loads, OkHttp lifecycle, coroutines)
            //    - type check: catches JobCancellationException, CancellationException, etc.
            //    - value fallback: catches cases where the SDK stores a fully-qualified class
            //      name or wraps the exception so the type field doesn't match the simple name.
            //    - IGNORED_EXCEPTION_MESSAGES also covers SocketException (IOException subclass):
            //      Sentry serializes the actual runtime type ("SocketException"), not "IOException".
            if (value in IGNORED_EXCEPTION_MESSAGES) return true
            if (type.endsWith("CancellationException")) return true
            if (value.contains("was cancelled")) return true
            if (type == "UnknownHostException") return true
            // HTTP/2 stream reset — only drop benign codes; INTERNAL_ERROR/PROTOCOL_ERROR are bugs
            if (type == "StreamResetException" &&
                (value.contains("NO_ERROR") || value.contains("CANCEL") || value.contains("REFUSED_STREAM"))
            ) return true

            // 2. Non-actionable scraping / API errors (403/429 message substrings cover HttpStatusException)
            for (substring in IGNORED_MESSAGE_SUBSTRINGS) {
                if (value.contains(substring)) return true
            }

            // 3. Transient connectivity errors — offline device, DNS failure, TCP refused.
            //    ConnectException is only dropped for Sentry's own ingest endpoint: failures
            //    reaching owned/third-party backends (Appwrite, RapidAPI, …) are actionable.
            if (type == "ConnectException" && value.contains("sentry.io")) return true
            if (type == "SocketTimeoutException") return true
            if (type == "SocketException") return true

            // 4. Upstream conditions that are real but repeat per request: an unreachable backend
            //    and a 5xx from one. These are not dropped — the first one in each window is sent
            //    with its full detail — but a phone on a dead connection re-issues the same call on
            //    a six-second poll loop, and the twentieth identical event costs quota and evicts
            //    the breadcrumb ring without adding a fact. Throttled, not suppressed.
            if (type in throttledExceptionTypes && !allowThrottled("$type|${value.take(40)}")) {
                return true
            }
        }

        return false
    }

    /**
     * True when this key has not been seen within [throttleWindowMs]. Deliberately keyed on the
     * message prefix as well as the type, so two different hosts failing do not hide each other.
     */
    private fun allowThrottled(key: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        var allow = false
        throttleState.compute(key) { _, previous ->
            if (previous != null && nowMs - previous < throttleWindowMs) return@compute previous
            allow = true
            nowMs
        }
        return allow
    }

    /** Exception types that are real but repeat per request while the condition lasts. */
    private val throttledExceptionTypes = setOf(
        "SentryHttpClientException",
        "ConnectException",
    )

    private val throttleWindowMs = 5 * 60_000L

    private val throttleState = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun describeEvent(event: SentryEvent): String {
        val first = event.exceptions?.firstOrNull()
        return "${first?.type}: ${first?.value?.take(80)}"
    }
}
