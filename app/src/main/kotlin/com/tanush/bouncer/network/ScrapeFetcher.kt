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
                throw IOException("HTTP ${response.code} fetching $url")
            }
            return response.body?.string() ?: throw IOException("Empty body fetching $url")
        }
    }
}
