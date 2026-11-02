package com.tanush.bouncer.data.history

import android.util.Log
import com.tanush.bouncer.network.HttpClientProvider
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * One GET against a GitHub Pages site: the Cricsheet history ([HistoryClient]) or the match archive
 * ([ArchiveClient]).
 *
 * Not Appwrite, so no quota, no guard funnel and no usage tracking: GitHub Pages bills nothing.
 * The max-age is set on the request, which is what bounds OkHttp's cache: the lesson of
 * `AppwriteClient.downloadFile`, where a server header froze a file for 45 days.
 *
 * Every failure is null. A 404 is the normal answer for most matches and is not reported.
 */
internal object StaticSite {
    private const val TAG = "StaticSite"
    private const val HTTP_NOT_FOUND = 404

    /**
     * [maxStaleSeconds] lets a cached copy up to that much past its freshness stand in without a
     * request, which also serves it offline: the weekly indexes pass seven days.
     */
    suspend fun fetch(
        baseUrl: String,
        path: String,
        maxAgeSeconds: Int,
        maxStaleSeconds: Int = 0,
    ): ByteArray? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl + path)
            .cacheControl(
                CacheControl.Builder()
                    .maxAge(maxAgeSeconds, TimeUnit.SECONDS)
                    .apply { if (maxStaleSeconds > 0) maxStale(maxStaleSeconds, TimeUnit.SECONDS) }
                    .build(),
            )
            .build()
        try {
            HttpClientProvider.staticSiteClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> response.body?.bytes()
                    response.code == HTTP_NOT_FOUND -> null
                    else -> {
                        sentryBreadcrumb("history", "GET $baseUrl$path: HTTP ${response.code}", SentryLevel.WARNING)
                        null
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            Log.d(TAG, "GET $baseUrl$path failed: ${e.message}")
            null
        }
    }
}
