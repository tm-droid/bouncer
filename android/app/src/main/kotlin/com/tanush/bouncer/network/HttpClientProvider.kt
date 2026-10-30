package com.tanush.bouncer.network

import android.util.Log
import com.tanush.bouncer.BuildConfig
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Singleton provider for a shared [OkHttpClient] instance.
 * All network consumers (Retrofit, Coil, raw OkHttp calls) should use
 * [sharedClient] to avoid duplicating connection pools, dispatchers, and
 * thread pools.
 */
object HttpClientProvider {
    private const val TAG = "HttpClientProvider"
    private const val CACHE_SIZE_BYTES = 10L * 1024 * 1024
    private const val STATIC_SITE_CACHE_SIZE_BYTES = 32L * 1024 * 1024
    // A conditional GET that found nothing new. Not successful by OkHttp's 2xx test, and not a failure.
    private const val HTTP_NOT_MODIFIED = 304

    @Volatile private var cacheDir: java.io.File? = null

    fun initialize(cacheDir: java.io.File) {
        this.cacheDir = cacheDir
    }

    // Release builds log nothing. This was added unconditionally, so every request URL the app
    // made was printed to logcat on a shipped device.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
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
     * Returns true if the exception is a transient connectivity error (e.g. no
     * network, captive portal, DNS unavailable). The device may be on a Wi-Fi
     * network without actual internet access (EAI_NODATA), causing DNS resolution
     * to fail for all external hosts. These errors are expected and handled by
     * callers; they should not be reported to Sentry as actionable exceptions.
     */
    internal fun isConnectivityException(e: Throwable): Boolean {
        if (e is UnknownHostException) return true
        if (e is ConnectException) return true
        if (e is java.net.SocketTimeoutException) return true
        if (e is java.net.SocketException) return true
        // The server closed the connection before a status line arrived. OkHttp reports it as a
        // bare IOException("unexpected end of stream") whose cause is this (ANDROID-5A).
        if (e is java.io.EOFException || e.cause is java.io.EOFException) return true
        val cause = e.cause
        if (cause is UnknownHostException) return true
        if (cause is ConnectException) return true
        return false
    }

    /**
     * Interceptor that records request timing, and leaves a Sentry breadcrumb for a request that
     * failed or answered with an error status. A successful request leaves none.
     * 
     * Benign cancellations (e.g. Coil image loads canceled on scroll) are
     * logged at DEBUG level and NOT reported to Sentry.
     */
    private val sentryBreadcrumbInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        val method = request.method
        val startTime = System.currentTimeMillis()
        
        var response: Response? = null
        try {
            response = chain.proceed(request)
            val duration = System.currentTimeMillis() - startTime
            
            Sentry.metrics().distribution("http.duration_ms", duration.toDouble())
            // Only a response worth reading about leaves a breadcrumb. One per request, plus one
            // per response, plus a log line, on a client that also carries every image load and
            // the list poll, filled Sentry's ~100-entry ring in about two minutes — a crash report
            // then held nothing but poll traffic.
            if (!response.isSuccessful && response.code != HTTP_NOT_MODIFIED) {
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "network.http"
                    message = "HTTP $method response"
                    level = SentryLevel.WARNING
                    setData("url", url)
                    setData("method", method)
                    setData("statusCode", response.code)
                    setData("durationMs", duration)
                })
                Log.w(TAG, "HTTP $method $url returned ${response.code} in ${duration}ms")
            }
            
            response
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime

            if (isCancellationException(e)) {
                // Benign cancellation — log at debug level only, never report to Sentry
                Log.d(TAG, "HTTP $method $url canceled after ${duration}ms (lifecycle cancellation)")
            } else if (isConnectivityException(e)) {
                // Transient connectivity error (e.g. no internet, captive portal, DNS failure).
                // The device may be connected to a network without actual internet access —
                // WorkManager's CONNECTED constraint does not guarantee DNS resolution works.
                // These are expected, handled by callers, and not actionable in Sentry.
                Log.w(TAG, "HTTP $method $url connectivity failure after ${duration}ms: ${e.message}")
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "network.http"
                    message = "HTTP $method request failed (no connectivity)"
                    level = SentryLevel.WARNING
                    setData("url", url)
                    setData("method", method)
                    setData("durationMs", duration)
                    setData("error", e.message ?: "Unknown error")
                    setData("errorType", e.javaClass.simpleName)
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
            // The only timeout that bounds a whole call. Without it, connect/read/write reset per
            // hop, and `retryOnConnectionFailure` plus redirects leave total wall-clock unbounded —
            // which on a 6s poll loop means requests can outlive the interval that scheduled them.
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                cacheDir?.let { dir ->
                    cache(okhttp3.Cache(java.io.File(dir, "okhttp"), CACHE_SIZE_BYTES))
                }
            }
            .addInterceptor(sentryBreadcrumbInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * [sharedClient] with a disk cache of its own for the GitHub Pages sites (`StaticSite`), so the
     * weekly indexes cannot be evicted by Cricbuzz pages and images filling the 10 MB shared one.
     */
    val staticSiteClient: OkHttpClient by lazy {
        sharedClient.newBuilder()
            .cache(cacheDir?.let { okhttp3.Cache(java.io.File(it, "static-sites"), STATIC_SITE_CACHE_SIZE_BYTES) })
            .build()
    }
}
