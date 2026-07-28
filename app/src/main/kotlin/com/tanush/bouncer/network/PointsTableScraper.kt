package com.tanush.bouncer.network

import com.tanush.bouncer.services.WORKER_MAX_RETRIES
import com.tanush.bouncer.services.backoffDelayMs
import com.tanush.bouncer.managers.TeamAcronymManager
import com.tanush.bouncer.models.PointsTableEntry
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
 * Scrapes tournament points table from CricBuzz.
 */
class PointsTableScraper {

    companion object {
        private const val TAG = "PointsTableScraper"
        private const val MAX_RETRIES = WORKER_MAX_RETRIES
    }

    /**
     * Fetch points table for a given series.
     * URL pattern: https://www.cricbuzz.com/cricket-series/{seriesId}/{slug}/points-table
     */
    suspend fun fetchPointsTable(seriesId: String): List<PointsTableEntry> = withContext(Dispatchers.IO) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.points_table"
            message = "Fetching points table for series $seriesId"
            level = SentryLevel.INFO
        })

        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            try {
                val url = "${CricBuzzUrls.BASE}/cricket-series/$seriesId/points-table"
                val doc = Jsoup.connect(url)
                    .userAgent(randomUserAgent())
                    .timeout(10000)
                    .get()

                val entries = mutableListOf<PointsTableEntry>()

                // Parse the table rows — Cricbuzz uses cb-srs-pnts structure
                val tableRows = doc.select("table.cb-srs-pnts tbody tr, table.table tbody tr, .cb-col-100 table tr")
                var position = 1

                for (row in tableRows) {
                    val cells = row.select("td")
                    if (cells.size < 7) continue

                    val teamName = cells[0].text().trim()
                    if (teamName.isEmpty() || teamName.equals("Team", ignoreCase = true)) continue

                    try {
                        val entry = PointsTableEntry(
                            position = position,
                            teamName = teamName,
                            teamAcronym = TeamAcronymManager.getAcronym(teamName),
                            matches = cells.getOrNull(1)?.text()?.trim()?.toIntOrNull() ?: 0,
                            won = cells.getOrNull(2)?.text()?.trim()?.toIntOrNull() ?: 0,
                            lost = cells.getOrNull(3)?.text()?.trim()?.toIntOrNull() ?: 0,
                            tied = cells.getOrNull(4)?.text()?.trim()?.toIntOrNull() ?: 0,
                            noResult = cells.getOrNull(5)?.text()?.trim()?.toIntOrNull() ?: 0,
                            points = cells.getOrNull(6)?.text()?.trim()?.toIntOrNull() ?: 0,
                            netRunRate = cells.getOrNull(7)?.text()?.trim()?.toDoubleOrNull() ?: 0.0,
                        )
                        entries.add(entry)
                        position++
                    } catch (e: Exception) {
                        Sentry.captureException(e)
                    }
                }

                // If structured table failed, try Next.js data
                if (entries.isEmpty()) {
                    return@withContext parseFromNextJsData(doc.html(), seriesId)
                }

                return@withContext entries

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

    /**
     * Fallback: parse points data from embedded __NEXT_DATA__ JSON.
     */
    private fun parseFromNextJsData(html: String, seriesId: String): List<PointsTableEntry> {
        try {
            val regex = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(html) ?: return emptyList()
            val json = com.google.gson.JsonParser.parseString(match.groupValues[1]).asJsonObject
            val pageProps = json.getAsJsonObject("props")?.getAsJsonObject("pageProps") ?: return emptyList()

            // Navigate the JSON tree for points table data
            val pointsData = pageProps.getAsJsonArray("pointsTable")
                ?: pageProps.getAsJsonObject("data")?.getAsJsonArray("pointsTable")
                ?: return emptyList()

            val entries = mutableListOf<PointsTableEntry>()
            var position = 1

            for (groupElement in pointsData) {
                val group = groupElement.asJsonObject
                val tableArray = group.getAsJsonArray("pointsTableInfo") ?: continue

                for (teamElement in tableArray) {
                    val team = teamElement.asJsonObject
                    val teamInfo = team.getAsJsonObject("teamInfo") ?: continue

                    val teamName = teamInfo.get("name")?.asString ?: continue
                    entries.add(
                        PointsTableEntry(
                            position = position,
                            teamName = teamName,
                            teamAcronym = teamInfo.get("shortName")?.asString
                                ?: TeamAcronymManager.getAcronym(teamName),
                            matches = team.get("matchesPlayed")?.asInt ?: 0,
                            won = team.get("matchesWon")?.asInt ?: 0,
                            lost = team.get("matchesLost")?.asInt ?: 0,
                            tied = team.get("matchesTied")?.asInt ?: 0,
                            noResult = team.get("noResult")?.asInt ?: 0,
                            points = team.get("points")?.asInt ?: 0,
                            netRunRate = team.get("nrr")?.asDouble ?: 0.0,
                        )
                    )
                    position++
                }
            }

            return entries
        } catch (e: Exception) {
            Sentry.captureException(e)
            return emptyList()
        }
    }
}
