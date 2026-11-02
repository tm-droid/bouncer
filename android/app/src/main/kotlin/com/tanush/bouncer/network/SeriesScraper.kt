package com.tanush.bouncer.network

import com.tanush.bouncer.services.WORKER_MAX_RETRIES
import com.tanush.bouncer.services.backoffDelayMs
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.models.Series
import com.tanush.bouncer.utils.sentryBreadcrumb
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
        // `/cricket-schedule/series` (no suffix) carries only the site-wide live ticker, so every
        // strategy below returned nothing on it and the Series screen said "No series found".
        private const val SERIES_LIST_URL = "${CricBuzzUrls.BASE}/cricket-schedule/series/all"
        private const val ARCHIVE_URL = "${CricBuzzUrls.BASE}/cricket-scorecard-archives"
        private val ARCHIVE_SECTIONS = setOf("internationalData", "leagueData", "domesticData", "womenData")

        /** The earliest year `/cricket-scorecard-archives` links to. */
        const val FIRST_ARCHIVE_YEAR = 2007
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
                val doc = ScrapeFetcher.get(SERIES_LIST_URL, randomUserAgent(), 10_000)

                val scheduled = parseSeriesListHtml(doc.html())
                if (scheduled.isNotEmpty()) return@withContext scheduled

                // Legacy Pages Router shape, kept as a fallback
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
     * Every series Cricbuzz archived for [year], from `/cricket-scorecard-archives/{year}`.
     *
     * The Series tab only ever listed `/cricket-schedule/series/all`, which holds current and
     * upcoming series alone, so nothing that had finished could be opened from it at all.
     */
    suspend fun fetchArchivedSeries(year: Int): AppResult<List<Series>> = withContext(Dispatchers.IO) {
        sentryBreadcrumb(category = "network.series", message = "Fetching archived series for $year")
        AppResult.runCatchingSuspend {
            parseArchiveHtml(ScrapeFetcher.getString("$ARCHIVE_URL/$year", randomUserAgent(), 10_000))
        }
    }

    /**
     * Every series in the page's App Router flight stream: `seriesScheduleData` is a list of month
     * groups, each `{date, series: [{id, name, startDt, endDt}]}`. `internal` for the fixture test.
     */
    internal fun parseSeriesListHtml(html: String): List<Series> {
        val result = linkedMapOf<String, Series>()
        forEachNextFlightPayload(html) { payload ->
            findScheduleGroups(payload).forEach { group ->
                seriesOfGroups(group).forEach { result.putIfAbsent(it.id, it) }
            }
        }
        return result.values.toList()
    }

    /**
     * An archive year page: `{internationalData, leagueData, domesticData, womenData}`, each holding
     * `seriesMapProto` month groups in the same `{date, series: [{id, name, startDt, endDt}]}` shape
     * as the schedule. The section is Cricbuzz's own classification, so it outranks the name-based
     * guess; `womenData` says only that it is women's cricket, so its category is still guessed.
     * `internal` for the fixture test.
     */
    internal fun parseArchiveHtml(html: String): List<Series> {
        val result = linkedMapOf<String, Series>()
        forEachNextFlightPayload(html) { payload ->
            findArchiveSections(payload).forEach { (section, groups) ->
                seriesOfGroups(groups).forEach { series ->
                    val classified = classifiedBySection(series, section)
                    // Listed in two sections: keep the first section's category, and the flag.
                    result.merge(classified.id, classified) { existing, _ ->
                        existing.copy(isWomens = existing.isWomens || classified.isWomens)
                    }
                }
            }
        }
        return result.values.sortedByDescending { it.startDate.toLongOrNull() ?: 0L }
    }

    private fun classifiedBySection(series: Series, section: String): Series = when (section) {
        "internationalData" -> series.copy(category = Series.SeriesCategory.INTERNATIONAL)
        "leagueData" -> series.copy(category = Series.SeriesCategory.LEAGUE)
        "domesticData" -> series.copy(category = Series.SeriesCategory.DOMESTIC)
        "womenData" -> series.copy(isWomens = true)
        else -> series
    }

    private fun findArchiveSections(element: JsonElement): List<Pair<String, JsonArray>> = when {
        element.isJsonObject -> element.asJsonObject.entrySet().flatMap { (key, value) ->
            val groups = value.takeIf { key in ARCHIVE_SECTIONS && it.isJsonObject }
                ?.asJsonObject?.get("seriesMapProto")?.takeIf { it.isJsonArray }?.asJsonArray
            if (groups != null) listOf(key to groups) else findArchiveSections(value)
        }
        element.isJsonArray -> element.asJsonArray.flatMap { findArchiveSections(it) }
        else -> emptyList()
    }

    private fun findScheduleGroups(element: JsonElement): List<JsonArray> = when {
        element.isJsonObject -> element.asJsonObject.entrySet().flatMap { (key, value) ->
            if (key == "seriesScheduleData" && value.isJsonArray) listOf(value.asJsonArray) else findScheduleGroups(value)
        }
        element.isJsonArray -> element.asJsonArray.flatMap { findScheduleGroups(it) }
        else -> emptyList()
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

            return seriesOfGroups(seriesData)
        } catch (e: Exception) {
            Sentry.captureException(e)
            return emptyList()
        }
    }

    private fun seriesOfGroups(groups: JsonArray): List<Series> = groups
        .filter { it.isJsonObject }
        .flatMap { group -> group.asJsonObject.getAsJsonArray("series")?.toList().orEmpty() }
        .filter { it.isJsonObject }
        .mapNotNull { element ->
            val s = element.asJsonObject
            val id = s.get("id")?.asString ?: s.get("seriesId")?.asString ?: return@mapNotNull null
            val name = s.get("name")?.asString ?: s.get("seriesName")?.asString ?: return@mapNotNull null
            // The category is classified here because this is the path that runs in production; it
            // used to leave every series INTERNATIONAL, so the Women and League filters matched nothing.
            Series(
                id = id,
                name = name,
                startDate = s.get("startDt")?.asString ?: "",
                endDate = s.get("endDt")?.asString ?: "",
                category = seriesCategoryOf(name),
                isWomens = isWomensSeries(name),
            )
        }
}
