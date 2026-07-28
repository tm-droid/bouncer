package com.tanush.bouncer.network

import com.tanush.bouncer.services.WORKER_MAX_RETRIES
import com.tanush.bouncer.services.backoffDelayMs
import com.tanush.bouncer.models.Series
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Scrapes the list of active series/tournaments from CricBuzz.
 */
class SeriesScraper {

    companion object {
        private const val TAG = "SeriesScraper"
        private const val MAX_RETRIES = WORKER_MAX_RETRIES
        private const val SERIES_LIST_URL = "${CricBuzzUrls.BASE}/cricket-schedule/series"
    }

    /**
     * Fetch a list of currently active and upcoming series.
     */
    suspend fun fetchActiveSeries(): List<Series> = withContext(Dispatchers.IO) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.series"
            message = "Fetching active series list"
            level = SentryLevel.INFO
        })

        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            try {
                val doc = Jsoup.connect(SERIES_LIST_URL)
                    .userAgent(randomUserAgent())
                    .timeout(10000)
                    .get()

                // Try Next.js __NEXT_DATA__ first
                val nextDataSeries = parseFromNextJsData(doc.html())
                if (nextDataSeries.isNotEmpty()) return@withContext nextDataSeries

                // Fallback: parse HTML directly
                val seriesList = mutableListOf<Series>()
                val links = doc.select("a[href*=/cricket-series/]")

                for (link in links) {
                    val href = link.attr("href")
                    val seriesIdMatch = Regex("/cricket-series/(\\d+)/").find(href) ?: continue
                    val seriesId = seriesIdMatch.groupValues[1]
                    val name = link.text().trim()
                    if (name.isEmpty() || name.length < 3) continue

                    if (seriesList.none { it.id == seriesId }) {
                        seriesList.add(
                            Series(
                                id = seriesId,
                                name = name,
                                category = seriesCategoryOf(name),
                                isWomens = isWomensSeries(name),
                            )
                        )
                    }
                }

                return@withContext seriesList

            } catch (e: SocketTimeoutException) {
                lastException = e
            } catch (e: UnknownHostException) {
                lastException = e
            } catch (e: Exception) {
                // Non-network error (parse failure, etc.) — rethrow so caller can
                // distinguish real failure from genuine empty response and retry/fail.
                throw e
            }

            delay(backoffDelayMs(attempt))
        }

        // All retries exhausted for network errors — throw so WorkManager retries.
        throw lastException ?: Exception("Series fetch failed after $MAX_RETRIES attempts")
    }

    /**
     * Parse series list from embedded __NEXT_DATA__ JSON.
     */
    private fun parseFromNextJsData(html: String): List<Series> {
        try {
            val regex = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(html) ?: return emptyList()
            val json = com.google.gson.JsonParser.parseString(match.groupValues[1]).asJsonObject
            val pageProps = json.getAsJsonObject("props")?.getAsJsonObject("pageProps") ?: return emptyList()

            val seriesData = pageProps.getAsJsonArray("seriesMapProto")
                ?: pageProps.getAsJsonArray("content")
                ?: return emptyList()

            val result = mutableListOf<Series>()

            for (groupElement in seriesData) {
                val group = groupElement.asJsonObject
                val seriesArray = group.getAsJsonArray("series") ?: continue

                for (seriesElement in seriesArray) {
                    val s = seriesElement.asJsonObject
                    val id = s.get("id")?.asString ?: s.get("seriesId")?.asString ?: continue
                    val name = s.get("name")?.asString ?: s.get("seriesName")?.asString ?: continue

                    // This is the path that actually runs in production (the HTML scrape below is
                    // only a fallback), and it used to classify nothing at all — every series came
                    // back INTERNATIONAL, so the Women and League filters had nothing to match.
                    result.add(
                        Series(
                            id = id,
                            name = name,
                            startDate = s.get("startDt")?.asString ?: "",
                            endDate = s.get("endDt")?.asString ?: "",
                            category = seriesCategoryOf(name),
                            isWomens = isWomensSeries(name),
                        )
                    )
                }
            }

            return result
        } catch (e: Exception) {
            Sentry.captureException(e)
            return emptyList()
        }
    }
}
