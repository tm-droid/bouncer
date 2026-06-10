package com.tanush.bouncer.utils

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

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
