package com.tanush.bouncer.data

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val exception: AppException, val message: String? = null) : AppResult<Nothing>()
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

    fun onError(action: (AppException, String?) -> Unit): AppResult<T> {
        if (this is Error) action(exception, message)
        return this
    }

    companion object {
        inline fun <T> runCatching(block: () -> T): AppResult<T> {
            return try {
                Success(block())
            } catch (e: AppException) {
                Error(e, e.userMessage)
            } catch (e: java.io.IOException) {
                Error(AppException.NetworkException(cause = e))
            } catch (e: Exception) {
                Error(AppException.UnknownException(cause = e))
            }
        }

        suspend inline fun <T> runCatchingSuspend(crossinline block: suspend () -> T): AppResult<T> {
            return try {
                Success(block())
            } catch (e: AppException) {
                Error(e, e.userMessage)
            } catch (e: java.io.IOException) {
                Error(AppException.NetworkException(cause = e))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Error(AppException.UnknownException(cause = e))
            }
        }
    }
}
