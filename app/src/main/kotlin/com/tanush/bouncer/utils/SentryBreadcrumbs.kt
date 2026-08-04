package com.tanush.bouncer.utils

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared replacement for the repeated `Sentry.addBreadcrumb(Breadcrumb().apply { ... })`
 * boilerplate. Category, message, level, and data pass through unchanged.
 */
fun sentryBreadcrumb(
    category: String,
    message: String,
    level: SentryLevel = SentryLevel.INFO,
    data: Map<String, Any> = emptyMap()
) {
    Sentry.addBreadcrumb(Breadcrumb().apply {
        this.category = category
        this.message = message
        this.level = level
        data.forEach { (key, value) -> setData(key, value) }
    })
}

/** Default window between two captures sharing a [sentryCaptureThrottled] key. */
const val SENTRY_THROTTLE_WINDOW_MS = 5 * 60_000L

private val lastCapturedAt = ConcurrentHashMap<String, Long>()

/**
 * Capture a degradation event, at most once per [windowMs] per [key].
 *
 * Every silent path in the commentary pipeline sits on a per-ball or per-poll loop, so an
 * unthrottled capture would send hundreds of identical events an over and evict the ~100-entry
 * breadcrumb ring that crash triage depends on. Throttling is what makes capturing these
 * affordable at all — the alternative that was in place, breadcrumb-only, made a month-long
 * outage invisible.
 *
 * The suppressed calls are not lost: each one still leaves a breadcrumb, so the event that does
 * get through carries the recent history around it.
 *
 * @return true when the event was sent, false when it was suppressed by the window.
 */
fun sentryCaptureThrottled(
    key: String,
    message: String,
    level: SentryLevel = SentryLevel.WARNING,
    windowMs: Long = SENTRY_THROTTLE_WINDOW_MS,
    data: Map<String, Any> = emptyMap()
): Boolean {
    sentryBreadcrumb("degradation", message, level, data)

    val now = System.currentTimeMillis()
    // compute() is what makes check-then-stamp atomic: these callers run on concurrent per-ball
    // coroutines, and a read-then-write would let a burst through the window together.
    var send = false
    lastCapturedAt.compute(key) { _, previous ->
        if (previous != null && now - previous < windowMs) return@compute previous
        send = true
        now
    }
    if (!send) return false

    Sentry.withScope { scope ->
        scope.level = level
        scope.setTag("degradation", key)
        data.forEach { (k, v) -> scope.setContexts(k, v) }
        Sentry.captureMessage(message, level)
    }
    return true
}
