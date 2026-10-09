package com.tanush.bouncer.data

import com.tanush.bouncer.network.HttpClientProvider
import com.tanush.bouncer.network.isUpstreamOutage
import io.sentry.Sentry

/**
 * Reports a failed result, unless the failure is the device being offline or the scraped site
 * answering 5xx. Both leave the caller on its cache and neither is something a release can fix.
 */
fun AppResult.Error.report() {
    if (HttpClientProvider.isConnectivityException(exception) || isUpstreamOutage(exception)) return
    Sentry.captureException(exception)
}

@PublishedApi internal fun Throwable.hasOomCause(): Boolean {
    var t: Throwable? = this
    repeat(10) { // guard against cycles
        if (t is OutOfMemoryError) return true
        t = t?.cause
    }
    return false
}

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val exception: AppException) : AppResult<Nothing>() {
        val message: String? get() = exception.userMessage ?: exception.message
    }
    data object Loading : AppResult<Nothing>()

    val isSuccess get() = this is Success
    val isError get() = this is Error
    val isLoading get() = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        else -> default
    }

    fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> Loading
    }

    suspend fun <R> flatMap(transform: suspend (T) -> AppResult<R>): AppResult<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
        is Loading -> Loading
    }

    fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) action(data)
        return this
    }

    fun onError(action: (AppException) -> Unit): AppResult<T> {
        if (this is Error) action(exception)
        return this
    }

    companion object {
        inline fun <T> runCatching(block: () -> T): AppResult<T> {
            return try {
                Success(block())
            } catch (e: AppException) {
                Error(e)
            } catch (e: java.io.IOException) {
                // OkHttp cancels with IOException when OOM occurs during response read.
                // Walk the cause chain to distinguish resource exhaustion from network failure.
                if (e.hasOomCause()) {
                    Error(AppException.UnknownException(message = "Out of memory", cause = e))
                } else {
                    Error(AppException.NetworkException(cause = e))
                }
            } catch (e: Exception) {
                Error(AppException.UnknownException(cause = e))
            }
        }

        suspend inline fun <T> runCatchingSuspend(crossinline block: suspend () -> T): AppResult<T> {
            return try {
                Success(block())
            } catch (e: AppException) {
                Error(e)
            } catch (e: java.io.IOException) {
                // OkHttp cancels with IOException when OOM occurs during response read.
                // Walk the cause chain to distinguish resource exhaustion from network failure.
                if (e.hasOomCause()) {
                    Error(AppException.UnknownException(message = "Out of memory", cause = e))
                } else {
                    Error(AppException.NetworkException(cause = e))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Error(AppException.UnknownException(cause = e))
            }
        }
    }
}
