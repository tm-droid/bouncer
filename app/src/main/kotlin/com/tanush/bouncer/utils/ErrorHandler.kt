package com.tanush.bouncer.utils

import com.tanush.bouncer.data.AppException
import io.sentry.Sentry
import io.sentry.SentryLevel

object ErrorHandler {

    fun handle(exception: Throwable, context: String = ""): String {
        val sentryLevel = when (exception) {
            is AppException.NetworkException -> SentryLevel.INFO
            is AppException.CacheException -> SentryLevel.WARNING
            is AppException.ScrapingException -> SentryLevel.WARNING
            is AppException.ApiException -> SentryLevel.ERROR
            is AppException.ParseException -> SentryLevel.ERROR
            is AppException.UnknownException -> SentryLevel.ERROR
            else -> SentryLevel.ERROR
        }

        Sentry.captureException(exception) { scope ->
            scope.level = sentryLevel
            if (context.isNotBlank()) {
                scope.setTag("error_context", context)
            }
        }

        return when (exception) {
            is AppException -> exception.userMessage
            is java.io.IOException -> "Network error. Please check your connection."
            is java.net.SocketTimeoutException -> "Request timed out. Please try again."
            else -> "Something went wrong. Please try again."
        }
    }

    fun getUserMessage(exception: Throwable): String = when (exception) {
        is AppException -> exception.userMessage
        is java.io.IOException -> "No internet connection."
        is java.net.SocketTimeoutException -> "Request timed out."
        else -> "Something went wrong."
    }
}
