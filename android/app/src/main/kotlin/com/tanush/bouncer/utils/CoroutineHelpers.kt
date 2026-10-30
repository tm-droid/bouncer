package com.tanush.bouncer.utils

import io.sentry.Sentry
import kotlinx.coroutines.CoroutineExceptionHandler

val bouncerExceptionHandler = CoroutineExceptionHandler { _, e ->
    Sentry.captureException(e)
}
