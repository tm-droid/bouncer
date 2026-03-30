package com.tanush.bouncer.network

import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLogLevel
import io.sentry.SentryLevel
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Singleton provider for a shared [OkHttpClient] instance.
 * All network consumers (Retrofit, Coil, raw OkHttp calls) should use
 * [sharedClient] to avoid duplicating connection pools, dispatchers, and
 * thread pools.
 */
object HttpClientProvider {
    private const val TAG = "HttpClientProvider"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    /**
     * Returns true if the exception is a benign cancellation (e.g. Coil/OkHttp
     * canceling an image load when the view is recycled). These should never
     * be reported to Sentry as real errors.
     */
    private fun isCancellationException(e: Exception): Boolean {
        if (e is IOException && e.message == "Canceled") return true
        if (e is java.util.concurrent.CancellationException) return true
        // OkHttp wraps cancellations in IOException
        val cause = e.cause
        if (cause is java.util.concurrent.CancellationException) return true
        return false
    }

    /**
     * Interceptor that adds Sentry breadcrumbs for all HTTP requests/responses.
     * Captures request URL, method, response code, and timing information.
     * 
     * Benign cancellations (e.g. Coil image loads canceled on scroll) are
     * logged at DEBUG level and NOT reported to Sentry.
     */
    private val sentryBreadcrumbInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        val method = request.method
        val startTime = System.currentTimeMillis()
        
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.http"
            message = "HTTP $method request"
            level = SentryLevel.INFO
            setData("url", url)
            setData("method", method)
        })
        Sentry.metrics().count("http.request", 1.0)
        
        var response: Response? = null
        try {
            response = chain.proceed(request)
            val duration = System.currentTimeMillis() - startTime
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.http"
                message = "HTTP $method response"
                level = if (response.isSuccessful) SentryLevel.INFO else SentryLevel.WARNING
                setData("url", url)
                setData("method", method)
                setData("statusCode", response.code)
                setData("durationMs", duration)
            })
            Sentry.metrics().distribution("http.duration_ms", duration.toDouble())
            Sentry.metrics().count(
                "http.response",
                1.0,
                null
            )
            Sentry.logger().log(
                if (response.isSuccessful) SentryLogLevel.INFO else SentryLogLevel.WARN,
                "HTTP %s %s -> %d (%dms)",
                method,
                request.url.encodedPath,
                response.code,
                duration
            )
            
            if (!response.isSuccessful) {
                Log.w(TAG, "HTTP $method $url returned ${response.code} in ${duration}ms")
            }
            
            response
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime

            if (isCancellationException(e)) {
                // Benign cancellation — log at debug level only, never report to Sentry
                Log.d(TAG, "HTTP $method $url canceled after ${duration}ms (lifecycle cancellation)")
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "network.http"
                    message = "HTTP $method request canceled (lifecycle)"
                    level = SentryLevel.DEBUG
                    setData("url", url)
                    setData("method", method)
                    setData("durationMs", duration)
                })
            } else {
                Log.e(TAG, "HTTP $method $url failed after ${duration}ms", e)
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "network.http"
                    message = "HTTP $method request failed"
                    level = SentryLevel.ERROR
                    setData("url", url)
                    setData("method", method)
                    setData("durationMs", duration)
                    setData("error", e.message ?: "Unknown error")
                    setData("errorType", e.javaClass.simpleName)
                })
                Sentry.metrics().count("http.failure", 1.0)
                Sentry.logger().error("HTTP %s %s failed after %dms: %s", method, request.url.encodedPath, duration, e.message ?: "unknown")
                Sentry.captureException(e)
            }
            throw e
        }
    }

    /**
     * Single shared OkHttpClient used across the entire application.
     */
    val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(sentryBreadcrumbInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }
}
