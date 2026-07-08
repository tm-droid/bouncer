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

        // Session Replay: capture all debug sessions, sample production sessions.
        options.sessionReplay.setOnErrorSampleRate(1.0)
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
        }

        return false
    }

    private fun describeEvent(event: SentryEvent): String {
        val first = event.exceptions?.firstOrNull()
        return "${first?.type}: ${first?.value?.take(80)}"
    }
}
