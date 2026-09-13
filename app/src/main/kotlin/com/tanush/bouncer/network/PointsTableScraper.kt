package com.tanush.bouncer.network

import com.tanush.bouncer.services.WORKER_MAX_RETRIES
import com.tanush.bouncer.services.backoffDelayMs
import com.tanush.bouncer.managers.TeamAcronymManager
import com.tanush.bouncer.models.PointsTableEntry
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Scrapes tournament points tables from Cricbuzz.
 *
 * Rewritten because the screen was empty on every series, for three independent reasons at once:
 *
 * 1. The URL had no series slug. `/cricket-series/{id}/points-table` answers **200 with no points
 *    table in it**; only `/cricket-series/{id}/{slug}/points-table` carries the data. Navigation
 *    passes the id alone, so the slug is read off the unslugged page, which links to its own
 *    slugged self.
 * 2. The primary parser selected `<table>` rows. The page is App Router and has no `<table>` at all.
 * 3. The fallback read `__NEXT_DATA__`, which App Router pages do not emit — the same trap
 *    [PlayerProfileRsc] was written to escape.
 *
 * Nothing threw through any of it: an empty table reads as "this series has no standings".
 */
class PointsTableScraper {

    companion object {
        private const val TAG = "PointsTableScraper"
        private const val MAX_RETRIES = WORKER_MAX_RETRIES
        private const val TIMEOUT_MS = 10_000
    }

    suspend fun fetchPointsTable(seriesId: String): List<PointsTableEntry> = withContext(Dispatchers.IO) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.points_table"
            message = "Fetching points table for series $seriesId"
            level = SentryLevel.INFO
        })

        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            try {
                return@withContext fetchOnce(seriesId)
            } catch (e: SocketTimeoutException) {
                lastException = e
            } catch (e: UnknownHostException) {
                lastException = e
            } catch (e: Exception) {
                lastException = e
                break
            }

            delay(backoffDelayMs(attempt))
        }

        Sentry.captureException(lastException ?: Exception("Points table fetch failed"))
        emptyList()
    }

    private fun fetchOnce(seriesId: String): List<PointsTableEntry> {
        val landingUrl = "${CricBuzzUrls.BASE}/cricket-series/$seriesId/points-table"
        val landing = ScrapeFetcher.getString(landingUrl, randomUserAgent(), TIMEOUT_MS)

        // The unslugged page is a real 200 with no standings on it, so parse it only to find the
        // slugged URL. If it already carries a table (Cricbuzz has changed its mind before), use it.
        val direct = parsePointsTableFlight(landing)
        if (direct.isNotEmpty()) return direct

        val slugged = sluggedPointsTableUrl(landing, seriesId) ?: run {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.points_table"
                message = "No slugged points-table link on $landingUrl"
                level = SentryLevel.WARNING
            })
            return emptyList()
        }

        return parsePointsTableFlight(ScrapeFetcher.getString(slugged, randomUserAgent(), TIMEOUT_MS))
    }
}

/**
 * The `/cricket-series/{id}/{slug}/points-table` URL the unslugged page points at, or null.
 *
 * The links live in the flight stream rather than in `<a>` elements, so this reads the markup
 * rather than selecting on the parsed document.
 */
internal fun sluggedPointsTableUrl(html: String, seriesId: String): String? {
    val pattern = Regex("""/cricket-series/${Regex.escape(seriesId)}/([a-z0-9\-]+)/points-table""")
    val slug = pattern.find(html)?.groupValues?.get(1) ?: return null
    return "${CricBuzzUrls.BASE}/cricket-series/$seriesId/$slug/points-table"
}

/** Every standings row on the page, in the order Cricbuzz lists them. */
internal fun parsePointsTableFlight(html: String): List<PointsTableEntry> {
    val entries = mutableListOf<PointsTableEntry>()
    forEachNextFlightPayload(html) { data ->
        runCatching { collectPointsTableGroups(data, entries) }
            .onFailure { Sentry.captureException(Exception("Unparseable points-table segment", it)) }
    }
    return entries
}

/** Walks the flight payload for any object holding a `pointsTableInfo` array. */
private fun collectPointsTableGroups(element: JsonElement, into: MutableList<PointsTableEntry>) {
    if (element.isJsonObject) {
        val obj = element.asJsonObject
        obj.getAsJsonArray("pointsTableInfo")?.forEach { row ->
            if (row.isJsonObject) parsePointsTableRow(row.asJsonObject, into.size + 1)?.let(into::add)
        }
        obj.keySet().forEach { collectPointsTableGroups(obj.get(it), into) }
    } else if (element.isJsonArray) {
        element.asJsonArray.forEach { collectPointsTableGroups(it, into) }
    }
}

private fun parsePointsTableRow(row: JsonObject, position: Int): PointsTableEntry? {
    // `teamName` on this row is the short code ("GAW"); the full name is `teamFullName`.
    val name = row.get("teamFullName")?.asString?.takeIf { it.isNotBlank() } ?: return null
    val shortName = row.get("teamName")?.asString?.takeIf { it.isNotBlank() }
    val imageId = row.get("teamImageId")?.asString?.takeIf { it.isNotBlank() }

    return PointsTableEntry(
        position = position,
        teamName = name,
        teamAcronym = shortName ?: TeamAcronymManager.getAcronym(name),
        teamId = row.get("teamId")?.asString ?: "",
        logoUrl = imageId?.let { cricbuzzTeamLogoUrl(it) },
        matches = row.intOr("matchesPlayed"),
        won = row.intOr("matchesWon"),
        lost = row.intOr("matchesLost"),
        tied = row.intOr("matchesTied"),
        noResult = row.intOr("noRes"),
        // Cricbuzz writes the rate signed, "+1.029", which parses only as a Double.
        netRunRate = row.get("nrr")?.asString?.toDoubleOrNull() ?: 0.0,
        points = row.intOr("points"),
        recentForm = row.getAsJsonArray("form")?.mapNotNull {
            runCatching { it.asString }.getOrNull()
        } ?: emptyList(),
        qualified = row.get("teamQualifyStatus")?.asString.equals("Q", ignoreCase = true),
    )
}

private fun JsonObject.intOr(key: String, fallback: Int = 0): Int =
    runCatching { get(key)?.asInt }.getOrNull() ?: fallback
