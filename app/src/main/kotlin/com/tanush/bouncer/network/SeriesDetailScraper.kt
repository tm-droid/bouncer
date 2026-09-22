package com.tanush.bouncer.network

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.tanush.bouncer.data.AppException
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.models.SeriesMatchDay
import com.tanush.bouncer.models.SeriesStatType
import com.tanush.bouncer.models.SeriesStats
import com.tanush.bouncer.models.SeriesStatsRow
import com.tanush.bouncer.models.SeriesStatsTable
import com.tanush.bouncer.services.WORKER_MAX_RETRIES
import com.tanush.bouncer.services.backoffDelayMs
import com.tanush.bouncer.utils.sentryBreadcrumb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * A series' Schedule and Stats tabs, from its App Router pages.
 *
 * Both tabs were hardcoded "coming soon" placeholders and nothing fetched either page. The data is
 * all in the flight stream:
 *
 * - `/cricket-series/{id}/{slug}/matches` carries `matchesData.matchDetails[].matchDetailsMap`, one
 *   date group per heading, each holding the same `{matchInfo, matchScore}` objects the match lists
 *   use — so [CricBuzzScraper.matchOf] reads them. The site-wide live ticker is on this
 *   page too, as bare `matchInfo` objects, so the walk keys on `matchDetailsMap` and never on
 *   `matchInfo` alone, or every series would list whatever is live anywhere.
 * - `/cricket-series/{id}/{slug}/stats?statsType=X` is server-rendered per stat type and carries
 *   `statsTypes` (the whole menu) and `initialStats.{format}StatsList` — one table per format, so a
 *   multi-format tour answers every format in one fetch.
 *
 * The unslugged URL is a 200 with neither, the same trap [PointsTableScraper] records; the slug is
 * read off it once per series and remembered.
 */
class SeriesDetailScraper(
    private val fetch: (String) -> String = { url -> ScrapeFetcher.getString(url, randomUserAgent(), TIMEOUT_MS) },
) {

    suspend fun fetchSchedule(seriesId: String): AppResult<List<SeriesMatchDay>> =
        fetchTab(seriesId, "matches") { parseSeriesSchedule(it) }

    suspend fun fetchStats(seriesId: String, statsType: String?): AppResult<SeriesStats> {
        val tab = statsType?.let { "stats?statsType=${URLEncoder.encode(it, "UTF-8")}" } ?: "stats"
        // A series that has not started has no stats section at all, not an empty one, so absence
        // is the normal answer there. [SeriesDetailViewModel] reports it when the schedule says
        // matches have been played, which is the one case where absence is drift.
        return fetchTab(seriesId, tab) { html -> parseSeriesStats(html) ?: SeriesStats.EMPTY }
    }

    private suspend fun <T> fetchTab(seriesId: String, tab: String, parse: (String) -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            sentryBreadcrumb(category = "network.series_detail", message = "Fetching $tab for series $seriesId")
            var last: AppResult.Error? = null
            for (attempt in 1..WORKER_MAX_RETRIES) {
                val result = AppResult.runCatchingSuspend {
                    parse(fetch("${CricBuzzUrls.BASE}/cricket-series/$seriesId/${slugFor(seriesId)}/$tab"))
                }
                // Only a transport failure is worth another attempt; a page that parsed to nothing
                // parses to nothing again.
                if (result !is AppResult.Error || result.exception !is AppException.NetworkException) return@withContext result
                last = result
                delay(backoffDelayMs(attempt))
            }
            last ?: AppResult.Error(AppException.UnknownException(message = "Series $tab fetch failed"))
        }

    private fun slugFor(seriesId: String): String = slugs[seriesId] ?: run {
        val landing = fetch("${CricBuzzUrls.BASE}/cricket-series/$seriesId/matches")
        val slug = seriesSlugOf(landing, seriesId)
            ?: throw AppException.ParseException(message = "No slugged link on series $seriesId landing page")
        slugs[seriesId] = slug
        slug
    }

    companion object {
        private const val TIMEOUT_MS = 10_000

        // A slug never changes for a series id; remembering it saves one page fetch per tab.
        private val slugs = ConcurrentHashMap<String, String>()
    }
}

/** Every date group on a series' matches page, in page order. Empty when there are none. */
internal fun parseSeriesSchedule(html: String): List<SeriesMatchDay> {
    val scraper = CricBuzzScraper()
    val days = mutableListOf<SeriesMatchDay>()
    forEachNextFlightPayload(html) { payload ->
        collectMatchDays(payload, scraper, days)
    }
    return days
}

private fun collectMatchDays(element: JsonElement, scraper: CricBuzzScraper, into: MutableList<SeriesMatchDay>) {
    when {
        element.isJsonObject -> {
            val obj = element.asJsonObject
            val group = obj.get("matchDetailsMap")?.takeIf { it.isJsonObject }?.asJsonObject
            if (group != null) {
                matchDayOf(group, scraper)?.let(into::add)
                return
            }
            obj.keySet().forEach { collectMatchDays(obj.get(it), scraper, into) }
        }
        element.isJsonArray -> element.asJsonArray.forEach { collectMatchDays(it, scraper, into) }
    }
}

private fun matchDayOf(group: JsonObject, scraper: CricBuzzScraper): SeriesMatchDay? {
    val matches = group.getAsJsonArray("match")
        ?.filter { it.isJsonObject }
        ?.mapNotNull { scraper.matchOf(it.asJsonObject) }
        .orEmpty()
    if (matches.isEmpty()) return null
    return SeriesMatchDay(label = group.stringOrNull("key").orEmpty(), matches = matches)
}

/** The stat menu and every format's table on a series stats page, or null if it carries none. */
internal fun parseSeriesStats(html: String): SeriesStats? {
    var result: SeriesStats? = null
    forEachNextFlightPayload(html) { payload ->
        if (result == null) result = findStatsHolder(payload)?.let(::statsOf)
    }
    return result
}

/** The object that holds both `initialStats` and `statsTypes`. */
private fun findStatsHolder(element: JsonElement): JsonObject? = when {
    element.isJsonObject -> {
        val obj = element.asJsonObject
        if (obj.has("initialStats") && obj.has("statsTypes")) {
            obj
        } else {
            obj.keySet().asSequence().mapNotNull { findStatsHolder(obj.get(it)) }.firstOrNull()
        }
    }
    element.isJsonArray -> element.asJsonArray.asSequence().mapNotNull { findStatsHolder(it) }.firstOrNull()
    else -> null
}

private fun statsOf(holder: JsonObject): SeriesStats? {
    val typesObj = holder.get("statsTypes")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    // Category headings ("Batting") are entries without a `value`; they carry nothing selectable.
    val types = typesObj.getAsJsonArray("types")
        ?.filter { it.isJsonObject }
        ?.mapNotNull { entry ->
            val o = entry.asJsonObject
            val value = o.stringOrNull("value") ?: return@mapNotNull null
            SeriesStatType(
                value = value,
                header = o.stringOrNull("header")?.trim().orEmpty().ifEmpty { value },
                category = o.stringOrNull("category").orEmpty(),
            )
        }
        .orEmpty()

    val initial = holder.get("initialStats")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    val tables = initial.entrySet()
        .filter { (key, value) -> key.endsWith(STATS_LIST_SUFFIX) && value.isJsonObject }
        .mapNotNull { (key, value) -> statsTableOf(key.removeSuffix(STATS_LIST_SUFFIX).uppercase(), value.asJsonObject) }

    val selectedType = holder.get("initialSelectedStatsType")?.takeIf { it.isJsonObject }
        ?.asJsonObject?.stringOrNull("value")
        ?: types.firstOrNull()?.value.orEmpty()

    return SeriesStats(
        types = types,
        selectedType = selectedType,
        tables = tables,
        defaultFormat = defaultFormatOf(initial, holder, tables),
    )
}

/**
 * The format Cricbuzz selects first: `initialSelectedFilters.matchTypeId` resolved through the
 * `initialStats.filter.matchtype` list to a description ("T20"), kept only if a table has that format.
 */
private fun defaultFormatOf(initial: JsonObject, holder: JsonObject, tables: List<SeriesStatsTable>): String? {
    val selectedId = holder.get("initialSelectedFilters")?.takeIf { it.isJsonObject }
        ?.asJsonObject?.stringOrNull("matchTypeId") ?: return null
    val desc = initial.get("filter")?.takeIf { it.isJsonObject }?.asJsonObject
        ?.getAsJsonArray("matchtype")
        ?.filter { it.isJsonObject }
        ?.map { it.asJsonObject }
        ?.firstOrNull { it.stringOrNull("matchTypeId") == selectedId }
        ?.stringOrNull("matchTypeDesc")
        ?.uppercase()
        ?: return null
    return desc.takeIf { d -> tables.any { it.format == d } }
}

private fun statsTableOf(format: String, list: JsonObject): SeriesStatsTable? {
    val headers = list.getAsJsonArray("headers")?.mapNotNull { runCatching { it.asString }.getOrNull() }
        ?: return null
    if (headers.isEmpty()) return null
    val rows = list.getAsJsonArray("values")
        ?.filter { it.isJsonObject }
        ?.mapNotNull { row ->
            val cells = row.asJsonObject.getAsJsonArray("values")
                ?.map { runCatching { it.asString }.getOrDefault("") }
                ?: return@mapNotNull null
            statsRowOf(cells, headers.size)
        }
        .orEmpty()
    return SeriesStatsTable(format = format, headers = headers, rows = rows)
}

/**
 * A row is `[playerId, name, …one cell per header after PLAYER]` — one longer than the headers,
 * because the id has no column. A row the same length as the headers has no id.
 */
private fun statsRowOf(cells: List<String>, headerCount: Int): SeriesStatsRow? = when (cells.size) {
    headerCount + 1 -> SeriesStatsRow(playerId = cells[0], playerName = cells[1], cells = cells.drop(2))
    headerCount -> SeriesStatsRow(playerId = "", playerName = cells[0], cells = cells.drop(1))
    else -> null
}?.takeIf { it.playerName.isNotBlank() }

private const val STATS_LIST_SUFFIX = "StatsList"

private fun JsonObject.stringOrNull(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString
