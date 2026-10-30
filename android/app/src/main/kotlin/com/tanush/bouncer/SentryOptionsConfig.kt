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
class SentryOptionsConfig(
    private val lowEndDevice: Boolean = false,
) : Sentry.OptionsConfiguration<SentryAndroidOptions> {

    companion object {
        private const val TAG = "SentryOptionsConfig"
        private const val CAUSE_DEPTH = 6
        internal const val UI_LOAD_DEADLINE_MS = 15_000L
        private const val TRACES_SAMPLE_RATE = 0.2
        private const val LOW_END_TRACES_SAMPLE_RATE = 0.05
        private const val REPLAY_SESSION_SAMPLE_RATE = 0.1
        private const val REPLAY_ON_ERROR_SAMPLE_RATE = 0.25
        private const val LOW_END_REPLAY_ON_ERROR_SAMPLE_RATE = 0.1

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
        // Off. On, the SDK attaches the client IP address and request detail to every event, and
        // nothing in a cricket scores app's reports needs to say who sent them.
        options.setSendDefaultPii(false)
        options.logs.setEnabled(true)
        options.metrics.setEnabled(true)

        // Keep traces enabled so AI/LLM and performance spans are retained.
        val lowEnd = lowEndDevice && !isDebugBuild
        applySampling(options, isDebugBuild, lowEnd)

        // The activity's `ui.load` transaction ends when it goes idle, but every new child span
        // resets the idle timer, and polling, image loads and Room writes never pause. So it ran
        // to the SDK's 30 s deadline (averaging 27.3 s), collected up to 1000 spans of unrelated
        // work, and Sentry rejected it as `too_large:transaction`. Time to initial display
        // averages 7.6 s, so 15 s still covers what the transaction is for.
        options.deadlineTimeout = UI_LOAD_DEADLINE_MS

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
            "Sentry configured: lowEnd=$lowEnd, replay(session=${options.sessionReplay.sessionSampleRate}), logs/metrics enabled"
        )
    }

    /**
     * Trace and replay rates. A low-end phone samples less: every traced span and replay frame is
     * work on two cores that the user waits behind (DeviceClass). Whole-session replay captures
     * and encodes a frame every second, so a low-end phone records only around an error, at low
     * quality. Session Replay: capture all debug sessions, sample production sessions. The on-error
     * rate decides which sessions keep the rolling screenshot buffer running in the background, so
     * 1.0 meant every production session paid that CPU and battery cost waiting for an error.
     */
    private fun applySampling(options: SentryAndroidOptions, isDebugBuild: Boolean, lowEnd: Boolean) {
        fun rate(debug: Double, lowEndRate: Double, normal: Double) = when {
            isDebugBuild -> debug
            lowEnd -> lowEndRate
            else -> normal
        }
        options.setTracesSampleRate(rate(1.0, LOW_END_TRACES_SAMPLE_RATE, TRACES_SAMPLE_RATE))
        options.sessionReplay.setOnErrorSampleRate(
            rate(1.0, LOW_END_REPLAY_ON_ERROR_SAMPLE_RATE, REPLAY_ON_ERROR_SAMPLE_RATE)
        )
        options.sessionReplay.setSessionSampleRate(rate(1.0, 0.0, REPLAY_SESSION_SAMPLE_RATE))
        if (lowEnd) options.sessionReplay.quality = io.sentry.SentryReplayOptions.SentryReplayQuality.LOW
    }

    /**
     * Returns true for events that are non-actionable noise and should be silently dropped
     * to preserve Sentry quota and keep the Issues feed clean.
     *
     * Visible for testing: `internal` so unit tests in the same module can call it directly
     * without reflection, giving regression coverage on the noise-filter rules.
     */
    internal fun shouldDropEvent(event: SentryEvent): Boolean {
        if (isCancellation(event.throwable)) return true
        val exceptions = event.exceptions ?: return false

        for (sentryException in exceptions) {
            val type = sentryException.type ?: ""
            val value = sentryException.value ?: ""

            // 0. A 5xx from a host we do not run. The OkHttp integration the Gradle plugin injects
            //    captures every 5xx on every host (its targets are `.*` and are set only through
            //    the interceptor's constructor, which the bytecode instrumentation never calls),
            //    so Cricbuzz's own outages — the live-scores page, its image CDN — became five
            //    issues (ANDROID-4A/4C/4R…) that no change here could ever fix. The scrapers
            //    already degrade and breadcrumb on them. Appwrite's 5xx stay: that host is ours.
            if (type == "SentryHttpClientException" && !isOwnBackend(event.request?.url)) return true

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
     * True when the captured throwable is, or wraps, a cancellation. The type-name test below
     * cannot be the only one: in a release build R8 renames library-internal classes, so
     * Compose's `LeftCompositionCancellationException` arrives here under a two-letter name and
     * is only called that again once the server has deobfuscated it (ANDROID-53).
     */
    private fun isCancellation(throwable: Throwable?): Boolean {
        var t = throwable
        repeat(CAUSE_DEPTH) {
            val current = t ?: return false
            if (current is java.util.concurrent.CancellationException) return true
            t = current.cause
        }
        return false
    }

    /** Hosts whose 5xx are ours to act on. Everything else is upstream state. */
    private fun isOwnBackend(url: String?): Boolean {
        val host = url?.let { runCatching { java.net.URI(it).host }.getOrNull() } ?: return true
        return host == "appwrite.io" || host.endsWith(".appwrite.io")
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
