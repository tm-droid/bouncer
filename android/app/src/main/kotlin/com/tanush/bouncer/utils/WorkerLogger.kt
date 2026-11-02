package com.tanush.bouncer.utils

import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

object WorkerLogger {
    fun enqueued(category: String, message: String) {
        Log.d(category, message)
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.category = category
            this.message = message
            level = SentryLevel.INFO
        })
    }

    fun failed(category: String, message: String, e: Exception) {
        Log.e(category, message, e)
        Sentry.captureException(e)
    }
}
