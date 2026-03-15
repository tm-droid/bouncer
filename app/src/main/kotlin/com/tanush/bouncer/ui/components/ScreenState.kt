package com.tanush.bouncer.ui.components

// ==========================================
// ScreenState — Unified screen state model
// ==========================================

/**
 * Sealed hierarchy representing every state a screen can be in.
 * Used across ViewModels so the UI layer can pattern-match once
 * and render Loading / Partial / Success / Empty / Error.
 */
sealed class ScreenState<out T> {
    object Loading : ScreenState<Nothing>()

    /** Data arrived but may be stale (e.g. from cache). */
    data class Partial<T>(val data: T, val staleAtMillis: Long) : ScreenState<T>()

    data class Success<T>(val data: T) : ScreenState<T>()

    data class Empty(val hint: String) : ScreenState<Nothing>()

    data class Error(
        val cause: ErrorCause,
        val message: String,
        val lastCachedData: Any? = null,
        val retryAction: (() -> Unit)? = null
    ) : ScreenState<Nothing>()
}

enum class ErrorCause {
    NETWORK,
    SERVER,
    PARSING,
    TIMEOUT,
    UNKNOWN
}

// ==========================================
// Extension helpers
// ==========================================

/** Returns the wrapped data for [Success] or [Partial], null otherwise. */
fun <T> ScreenState<T>.dataOrNull(): T? = when (this) {
    is ScreenState.Success -> data
    is ScreenState.Partial -> data
    else -> null
}

/** True when the state is [ScreenState.Loading]. */
val <T> ScreenState<T>.isLoading: Boolean
    get() = this is ScreenState.Loading

/** True when the state is [ScreenState.Error]. */
val <T> ScreenState<T>.isError: Boolean
    get() = this is ScreenState.Error
