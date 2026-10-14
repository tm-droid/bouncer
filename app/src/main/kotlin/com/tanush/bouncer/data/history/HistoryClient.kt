package com.tanush.bouncer.data.history

import android.util.Log
import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.network.HttpClientProvider
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import okhttp3.CacheControl
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Reads the historical-match site: every Cricsheet match, published weekly to GitHub Pages by
 * `.github/workflows/build-history.yml` (`.claude/rules/history-data.md`).
 *
 * Not Appwrite, so no quota, no guard funnel and no usage tracking: GitHub Pages bills nothing.
 * The URL is a constant, not a `local.properties` id, because it is public and a blank template
 * value would only switch the feature off in the Play build.
 *
 * Every failure is null. A missing file is a match Cricsheet does not have, the normal case for
 * most fixtures, and the caller falls back to what it showed before.
 */
object HistoryClient {
    private const val TAG = "HistoryClient"
    const val BASE_URL = "https://tm-droid.github.io/bouncer-history/"

    // The site is rebuilt weekly and a match file never changes once its match is over, so the
    // request-side max-age is a day. A request-side max-age is what bounds OkHttp's cache: the
    // lesson of AppwriteClient.downloadFile, where a server header froze a file for 45 days.
    private const val MATCH_MAX_AGE_SECONDS = 24 * 60 * 60
    private const val INDEX_MAX_AGE_SECONDS = 24 * 60 * 60
    private const val HTTP_NOT_FOUND = 404

    suspend fun match(id: String): HistoryMatch? {
        if (!id.all(Char::isDigit)) return null
        val bytes = fetch("m/$id.json", MATCH_MAX_AGE_SECONDS) ?: return null
        return runCatching { AppJson.decodeFromString<HistoryMatch>(bytes.decodeToString()) }
            .onFailure { sentryBreadcrumb("history", "Unreadable match $id: ${it.message}", SentryLevel.WARNING) }
            .getOrNull()
    }

    /** The matches Cricsheet has for one month, `yyyy-MM`. Empty when it has none. */
    suspend fun month(yearMonth: String): List<HistoryIndexRow>? {
        if (!MONTH.matches(yearMonth)) return null
        val bytes = fetch("idx/m/$yearMonth.json", INDEX_MAX_AGE_SECONDS) ?: return null
        return runCatching {
            AppJson.parseToJsonElement(bytes.decodeToString()).let { it as JsonArray }
                .mapNotNull { (it as? JsonArray)?.let(HistoryIndexRow::of) }
        }.getOrNull()
    }

    private val MONTH = Regex("""\d{4}-\d{2}""")

    private suspend fun fetch(path: String, maxAgeSeconds: Int): ByteArray? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BASE_URL + path)
            .cacheControl(CacheControl.Builder().maxAge(maxAgeSeconds, TimeUnit.SECONDS).build())
            .build()
        try {
            HttpClientProvider.sharedClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> response.body?.bytes()
                    // A month with no matches has no index file, and most fixtures have no match file.
                    response.code == HTTP_NOT_FOUND -> null
                    else -> {
                        sentryBreadcrumb("history", "GET $path: HTTP ${response.code}", SentryLevel.WARNING)
                        null
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            Log.d(TAG, "GET $path failed: ${e.message}")
            null
        }
    }
}
