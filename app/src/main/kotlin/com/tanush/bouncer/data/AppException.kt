package com.tanush.bouncer.data

sealed class AppException(
    override val message: String,
    override val cause: Throwable? = null,
    val userMessage: String = message,
) : Exception(message, cause) {

    class NetworkException(
        message: String = "Network error",
        cause: Throwable? = null,
    ) : AppException(message, cause, "No internet connection. Please check your network.")

    class ScrapingException(
        val source: String,
        message: String = "Failed to scrape $source",
        cause: Throwable? = null,
    ) : AppException(message, cause, "Could not load data from $source. Please try again.")

    class ApiException(
        val statusCode: Int,
        message: String = "API error ($statusCode)",
        cause: Throwable? = null,
    ) : AppException(
        message, cause, when (statusCode) {
            429 -> "Too many requests. Please wait a moment."
            in 500..599 -> "Server error. Please try again later."
            else -> "Something went wrong. Please try again."
        }
    )

    class CacheException(
        message: String = "Cache error",
        cause: Throwable? = null,
    ) : AppException(message, cause, "Could not load cached data.")

    class ParseException(
        message: String = "Parse error",
        cause: Throwable? = null,
    ) : AppException(message, cause, "Could not process data. Please try again.")

    class UnknownException(
        message: String = "Unknown error",
        cause: Throwable? = null,
    ) : AppException(message, cause, "Something went wrong. Please try again.")
}
