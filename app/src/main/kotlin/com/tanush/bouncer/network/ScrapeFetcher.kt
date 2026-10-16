package com.tanush.bouncer.network

import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

/**
 * Fetches HTML for scraping through the shared [HttpClientProvider.sharedClient], then hands the
 * bytes to Jsoup to parse.
 *
 * Every scraper used to call `Jsoup.connect(url).get()`, which uses Jsoup's own
 * `HttpURLConnection` stack. That meant the app's highest-volume traffic — nine sites across the
 * Cricbuzz, Cricinfo, series, points-table, player-profile and news scrapers — bypassed the one
 * client `HttpClientProvider` documents as mandatory, and therefore got:
 *
 *  - none of the 10 MB disk cache, so a repeated page was refetched in full every time;
 *  - none of the Sentry breadcrumb interceptor, so the HTTP metrics under-reported by most of the
 *    app's requests and a scrape failure left no trace on the timeline;
 *  - no HTTP/2, no connection-pool reuse and no TLS session reuse, on a host being polled every
 *    six seconds.
 *
 * Jsoup is a parser, not an HTTP client. It is used as one here.
 */
object ScrapeFetcher {

    /**
     * GETs [url] and parses the response as HTML.
     *
     * [timeoutMs] is accepted for call-site parity with the `Jsoup.connect(...).timeout(...)` this
     * replaces, but per-call timeouts are the shared client's `callTimeout`; a value longer than
     * that cannot extend it. Kept so the intent at each site stays readable.
     *
     * @throws IOException on a transport failure or a non-2xx response, matching what
     *   `Jsoup.connect().get()` threw, so existing catch blocks keep working unchanged.
     */
    @Throws(IOException::class)
    fun get(
        url: String,
        userAgent: String,
        @Suppress("UNUSED_PARAMETER") timeoutMs: Int = 0,
        headers: Map<String, String> = emptyMap(),
    ): Document =
        // baseUri matters: Jsoup resolves relative hrefs against it, and the scrapers rely on
        // absUrl() for match and player links.
        Jsoup.parse(getString(url, userAgent, timeoutMs, headers), url)

    /**
     * GETs [url] and returns the raw body, for callers that only read embedded script data.
     *
     * The flight-payload parsers (`self.__next_f.push` chunks) work on a string. Going through [get]
     * built a full DOM of a several-hundred-KB page and then serialised it straight back with
     * `doc.html()` — two passes over the page for nothing, on the Live tab's six-second poll. The
     * parsers' tests already run against raw captured markup, which is exactly this body.
     */
    @Throws(IOException::class)
    fun getString(
        url: String,
        userAgent: String,
        @Suppress("UNUSED_PARAMETER") timeoutMs: Int = 0,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()

        HttpClientProvider.sharedClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ScrapeHttpException(response.code, url)
            }
            return response.body?.string() ?: throw IOException("Empty body fetching $url")
        }
    }
}

/**
 * A scraped page that answered with a non-2xx status. An [IOException] with the message the plain
 * one carried, so every existing catch block and message check behaves as before; the status is a
 * field so a reporting site can tell the site's own outage from a page that changed.
 */
class ScrapeHttpException(val code: Int, val url: String) : IOException("HTTP $code fetching $url")

/**
 * True when [e] is, or wraps, a 5xx from a scraped site. That is the site's outage: every caller
 * already falls back to what it has cached, and nothing in the app can change the answer. Cricbuzz
 * 504s reached Sentry as three issues (ANDROID-54, ANDROID-56 and, before the scorecard path
 * checked for it, ANDROID-48). A 4xx is not covered — a 404 on a URL the app built is a defect.
 */
internal fun isUpstreamOutage(e: Throwable): Boolean {
    var t: Throwable? = e
    repeat(MAX_CAUSE_DEPTH) {
        val current = t ?: return false
        if (current is ScrapeHttpException && current.code >= HTTP_SERVER_ERROR) return true
        t = current.cause
    }
    return false
}

private const val MAX_CAUSE_DEPTH = 6
private const val HTTP_SERVER_ERROR = 500

internal fun classifyHttpException(e: ScrapeHttpException): com.tanush.bouncer.data.AppException.ApiException =
    com.tanush.bouncer.data.AppException.ApiException(
        statusCode = e.code,
        message = "HTTP ${e.code} from ${e.url}",
        cause = e
    )

/**
 * Sentry gets a scrape failure only when the app could do something about it. Offline and a
 * Cricbuzz 5xx (ANDROID-54/56/5R: 504s) are states the caller already degrades from, by retrying
 * and then showing what it has cached; reporting them filled the issue list with Cricbuzz's
 * outages.
 */
internal fun isReportableScrapeFailure(e: Throwable): Boolean =
    !HttpClientProvider.isConnectivityException(e) && !isUpstreamOutage(e)

/**
 * Ends a scrape's retry loop for what another attempt cannot change: cancellation, and a 4xx on a
 * URL the app built, which is a defect and is reported. Anything else returns, to be retried.
 *
 * The loops used to catch Jsoup's HttpStatusException for this, which ScrapeFetcher never throws,
 * so a 4xx was retried three times and a 5xx was reported as a defect.
 */
internal fun Exception.rethrowIfNotRetryable(context: Map<String, Any?>) {
    if (this is kotlinx.coroutines.CancellationException) throw this
    if (this is ScrapeHttpException && code in HTTP_CLIENT_ERRORS) {
        val classified = classifyHttpException(this)
        crumb("HTTP $code — no retry", io.sentry.SentryLevel.WARNING, context + ("status" to code))
        io.sentry.Sentry.captureException(classified)
        throw classified
    }
}

private val HTTP_CLIENT_ERRORS = 400..499
