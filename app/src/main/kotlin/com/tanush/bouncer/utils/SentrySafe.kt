package com.tanush.bouncer.utils

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException

/**
 * Execute a boundary block and forward failures to Sentry before rethrowing.
 * This keeps behavior explicit while ensuring consistent crash visibility.
 */
inline fun <T> sentryTry(scope: String, block: () -> T): T {
    try {
        return block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Sentry.addBreadcrumb(
            Breadcrumb().apply {
                category = "app.boundary"
                message = "Failure captured in $scope"
                level = SentryLevel.ERROR
                setData("scope", scope)
                setData("errorType", error.javaClass.simpleName)
            }
        )
        Sentry.captureException(error)
        throw error
    }
}

inline fun sentryTryUnit(scope: String, block: () -> Unit) {
    sentryTry(scope, block)
}

suspend inline fun <T> sentryTrySuspend(scope: String, crossinline block: suspend () -> T): T {
    try {
        return block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Sentry.addBreadcrumb(
            Breadcrumb().apply {
                category = "app.boundary"
                message = "Suspend failure captured in $scope"
                level = SentryLevel.ERROR
                setData("scope", scope)
                setData("errorType", error.javaClass.simpleName)
            }
        )
        Sentry.captureException(error)
        throw error
    }
}
