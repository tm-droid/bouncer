package com.tanush.bouncer.network

import com.tanush.bouncer.data.SingleFlight
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.data.remote.FunctionOutcome
import com.tanush.bouncer.services.WORKER_MAX_RETRIES
import com.tanush.bouncer.services.backoffDelayMs
import com.tanush.bouncer.models.FormatStats
import com.tanush.bouncer.models.PlayerProfile
import com.tanush.bouncer.models.RecentInnings
import com.tanush.bouncer.utils.BoundedLruCache
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

data class PlayerSearchResult(
    val id: String,
    val name: String,
    val country: String,
    val imageUrl: String? = null,
)

/**
 * Result of a player-name lookup.
 *
 * "No such player" and "the search backend is down" used to be the same value — an empty list —
 * which is how a dead upstream rendered as a clean "no results" screen for as long as it took
 * someone to notice. They are now separate cases and callers must choose which to show.
 */
sealed interface PlayerSearchOutcome {
    data class Success(val results: List<PlayerSearchResult>) : PlayerSearchOutcome

    /** The lookup could not be performed at all. Says nothing about whether the player exists. */
    data object Unavailable : PlayerSearchOutcome
}

/**
 * Scrapes player profile and stats from CricBuzz.
 */
object PlayerProfileScraper {

    private const val TAG = "PlayerProfileScraper"
    private const val MAX_RETRIES = WORKER_MAX_RETRIES
    private const val PROFILE_TIMEOUT_MS = 15000
    private const val MAX_SEARCH_RESULTS = 10
    private const val SEARCH_FUNCTION_ID = "bouncer-player-search"
    private const val SEARCH_CACHE_ENTRIES = 64

    /** Recent query → answer. Bounded because it is keyed by arbitrary user input. */
    private val searchCache = BoundedLruCache<String, PlayerSearchOutcome>(SEARCH_CACHE_ENTRIES)
    private val searchFlight = SingleFlight<String, PlayerSearchOutcome>()

    private val FORMATS_JSON = listOf("Test" to "test", "ODI" to "odi", "T20I" to "t20i", "IPL" to "ipl")
    private val DEFAULT_FORMATS = listOf("Test", "ODI", "T20I", "IPL")
    private val NEXT_DATA_REGEX = Regex(
        """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL
    )
    private val SLUG_REGEX = Regex("[^a-z0-9]+")

    private fun breadcrumb(category: String, message: String, level: SentryLevel = SentryLevel.INFO) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.category = category
            this.message = message
            this.level = level
        })
    }

    /**
     * Search for players by name, dropping the failure distinction.
     *
     * Kept for the callers that genuinely cannot act on "unavailable" —
     * [scrapeProfile] and `PlayerCacheWorker` both just skip the player either way. UI callers
     * should use [searchPlayersDetailed] so they can tell an empty result from a dead backend.
     */
    suspend fun searchPlayers(query: String): List<PlayerSearchResult> =
        (searchPlayersDetailed(query) as? PlayerSearchOutcome.Success)?.results.orEmpty()

    /**
     * Resolve a player name via `bouncer-player-search`.
     *
     * This used to scrape `cricbuzz.com/api/search/results` with a Cricinfo-shaped JSON parser and
     * fall back to `cricbuzz.com/search`. Both paths returned 404 — the whole player pipeline
     * (this, profile scraping, the comparison screen, `PlayerCacheWorker`, and therefore the shared
     * `player-profiles` cache that could have covered for them) was dead. Resolution now happens
     * server-side, where it can be fixed without an app release when the upstream moves again.
     *
     * Repeat and concurrent queries collapse onto one execution: [searchCache] holds recent
     * answers and [searchFlight] shares an in-progress one. A search box is otherwise a very
     * effective way to spend an executions quota.
     */
    suspend fun searchPlayersDetailed(query: String): PlayerSearchOutcome {
        val key = query.trim().lowercase()
        if (key.isEmpty()) return PlayerSearchOutcome.Success(emptyList())
        searchCache[key]?.let { return it }

        return searchFlight.run(key) {
            breadcrumb("network.player_search", "Searching players: $query")
            val body = JSONObject().put("q", query.trim()).toString()
            val outcome = when (val response = AppwriteClient.executeFunctionDetailed(SEARCH_FUNCTION_ID, body)) {
                is FunctionOutcome.Success -> parseSearchResponse(response.body)
                is FunctionOutcome.HttpError -> {
                    // The function answers 200 with an empty list when it finds nothing, so a
                    // non-2xx is always a real failure and never "no such player".
                    Sentry.captureMessage(
                        "player-search returned HTTP ${response.statusCode}: ${response.body.take(200)}",
                        SentryLevel.ERROR,
                    )
                    PlayerSearchOutcome.Unavailable
                }
                // Already reported by AppwriteClient (uninitialised SDK, guard-blocked, or threw).
                FunctionOutcome.Unavailable -> PlayerSearchOutcome.Unavailable
            }
            // Only successes are cached. Caching Unavailable would keep a transient outage on
            // screen after it cleared.
            if (outcome is PlayerSearchOutcome.Success) searchCache[key] = outcome
            outcome
        }
    }

    private fun parseSearchResponse(body: String): PlayerSearchOutcome = try {
        val players = JSONObject(body).optJSONArray("players")
        val results = buildList {
            for (i in 0 until (players?.length() ?: 0)) {
                itemToSearchResult(players!!.getJSONObject(i))?.let { add(it) }
            }
        }
        PlayerSearchOutcome.Success(results.take(MAX_SEARCH_RESULTS))
    } catch (e: org.json.JSONException) {
        // A body we cannot parse is a contract break between app and function, not an empty result.
        Sentry.captureException(e)
        PlayerSearchOutcome.Unavailable
    }

    private fun itemToSearchResult(item: JSONObject): PlayerSearchResult? {
        val id = item.optString("id", "")
        val name = item.optString("name", "")
        if (id.isEmpty() || name.isEmpty()) return null
        return PlayerSearchResult(
            id = id,
            name = name,
            country = item.optString("country", ""),
            imageUrl = item.optString("imageUrl", "").ifEmpty { null },
        )
    }

    /**
     * Scrape full player profile and stats from CricBuzz.
     */
    suspend fun scrapeProfile(playerName: String): PlayerProfile? = withContext(Dispatchers.IO) {
        breadcrumb("network.player_profile", "Scraping profile for: $playerName")

        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            val outcome = attemptScrape(playerName)
            when (outcome) {
                is ScrapeOutcome.Success -> return@withContext outcome.profile
                is ScrapeOutcome.NotFound -> return@withContext null
                is ScrapeOutcome.Retry -> lastException = outcome.error
                is ScrapeOutcome.Fatal -> {
                    lastException = outcome.error
                    break
                }
            }
            delay(backoffDelayMs(attempt))
        }

        Sentry.captureException(lastException ?: Exception("Player profile fetch failed for $playerName"))
        null
    }

    private sealed class ScrapeOutcome {
        data class Success(val profile: PlayerProfile) : ScrapeOutcome()
        object NotFound : ScrapeOutcome()
        data class Retry(val error: Exception) : ScrapeOutcome()
        data class Fatal(val error: Exception) : ScrapeOutcome()
    }

    private suspend fun attemptScrape(playerName: String): ScrapeOutcome {
        return try {
            val bestMatch = searchPlayers(playerName).firstOrNull()
                ?: return ScrapeOutcome.NotFound

            val profileUrl = buildProfileUrl(bestMatch)
            val doc = ScrapeFetcher.get(profileUrl, randomUserAgent(), PROFILE_TIMEOUT_MS)

            val html = doc.html()
            // RSC first: Cricbuzz serves profiles from the App Router now, and the two parsers
            // below are both dead against that markup — `__NEXT_DATA__` is absent, and the Jsoup
            // selectors are old-site class names that match nothing. They stay as strategies
            // rather than being deleted because a parser that answers null costs one call, while
            // having no fallback at all when the upstream moves again costs a release.
            val profile = PlayerProfileRsc.parse(html, bestMatch.id, bestMatch.name)
                ?: parseFromNextJsData(html, bestMatch.id, bestMatch.name)
                ?: parseFromHtml(doc, bestMatch.id, bestMatch.name)
            ScrapeOutcome.Success(profile)
        } catch (e: java.net.SocketTimeoutException) {
            ScrapeOutcome.Retry(e)
        } catch (e: java.net.UnknownHostException) {
            ScrapeOutcome.Retry(e)
        } catch (e: Exception) {
            ScrapeOutcome.Fatal(e)
        }
    }

    private fun buildProfileUrl(match: PlayerSearchResult): String {
        val slug = match.name.lowercase().replace(SLUG_REGEX, "-").trim('-')
        return "${CricBuzzUrls.BASE}/profiles/${match.id}/$slug"
    }

    /**
     * Parse player profile from __NEXT_DATA__ JSON embedded in the page.
     */
    private fun parseFromNextJsData(html: String, playerId: String, fallbackName: String): PlayerProfile? {
        return try {
            val match = NEXT_DATA_REGEX.find(html) ?: return null
            val json = JSONObject(match.groupValues[1])
            val pageProps = json.optJSONObject("props")?.optJSONObject("pageProps") ?: return null
            val playerData = extractPlayerData(pageProps) ?: return null
            buildProfileFromJson(playerData, playerId, fallbackName)
        } catch (e: Exception) {
            Sentry.captureException(e)
            breadcrumb("network.player_profile", "Failed to parse __NEXT_DATA__: ${e.message}", SentryLevel.WARNING)
            null
        }
    }

    private fun extractPlayerData(pageProps: JSONObject): JSONObject? {
        return pageProps.optJSONObject("player")
            ?: pageProps.optJSONObject("data")?.optJSONObject("player")
            ?: pageProps.optJSONObject("appData")?.optJSONObject("player")
    }

    private fun buildProfileFromJson(
        playerData: JSONObject,
        playerId: String,
        fallbackName: String,
    ): PlayerProfile {
        val imageId = playerData.optString("faceImageId", "")
        val imageUrl = if (imageId.isNotEmpty()) {
            "https://www.cricbuzz.com/a/img/v1/152x152/i1/c$imageId/player.jpg"
        } else null

        val stats = mutableMapOf<String, FormatStats>()
        for ((label, key) in FORMATS_JSON) {
            val formatStats = parseStatsFromJson(playerData, key)
            if (formatStats != null) stats[label] = formatStats
        }

        return PlayerProfile(
            id = playerId,
            name = playerData.optString("name", fallbackName),
            country = playerData.optString("intlTeam", "").ifEmpty { playerData.optString("country", "") },
            role = playerData.optString("role", "").ifEmpty { playerData.optString("bat", "") },
            dateOfBirth = playerData.optString("DoB", "").ifEmpty { playerData.optString("dob", "") },
            battingStyle = playerData.optString("bat", "").ifEmpty { playerData.optString("battingStyle", "") },
            bowlingStyle = playerData.optString("bowl", "").ifEmpty { playerData.optString("bowlingStyle", "") },
            imageUrl = imageUrl,
            bio = playerData.optString("bio", ""),
            teams = extractTeams(playerData),
            stats = stats,
            recentForm = extractRecentForm(playerData),
        )
    }

    private fun extractTeams(playerData: JSONObject): List<String> {
        val teams = mutableListOf<String>()
        val teamsArray = playerData.optJSONArray("teams") ?: return teams
        for (i in 0 until teamsArray.length()) {
            val teamObj = teamsArray.optJSONObject(i)
            val teamName = teamObj?.optString("name", "") ?: teamsArray.optString(i, "")
            if (teamName.isNotEmpty()) teams.add(teamName)
        }
        return teams
    }

    private fun extractRecentForm(playerData: JSONObject): List<RecentInnings> {
        val formArray = playerData.optJSONArray("battingForm")
            ?: playerData.optJSONObject("batting")?.optJSONArray("form")
            ?: playerData.optJSONArray("recentForm")
            ?: return emptyList()

        return buildList {
            for (i in 0 until minOf(formArray.length(), 10)) {
                val entry = formArray.optJSONObject(i) ?: continue
                val runs = entry.optInt("runs", entry.optInt("score", -1))
                if (runs < 0) continue
                add(RecentInnings(
                    runs = runs,
                    balls = entry.optInt("balls", 0),
                    opponent = entry.optString("opposingTeam", entry.optString("opponent", "")),
                    format = entry.optString("matchFormat", entry.optString("format", "")),
                    date = entry.optString("matchDate", entry.optString("date", "")),
                ))
            }
        }
    }

    /**
     * Extract format stats from the JSON data.
     */
    private fun parseStatsFromJson(playerData: JSONObject, formatKey: String): FormatStats? {
        val batting = playerData.optJSONObject("batting")?.optJSONObject(formatKey)
            ?: playerData.optJSONObject("stats")?.optJSONObject("batting")?.optJSONObject(formatKey)
        val bowling = playerData.optJSONObject("bowling")?.optJSONObject(formatKey)
            ?: playerData.optJSONObject("stats")?.optJSONObject("bowling")?.optJSONObject(formatKey)

        if (batting == null && bowling == null) return null

        return FormatStats(
            matches = batting?.optInt("matches", 0) ?: bowling?.optInt("matches", 0) ?: 0,
            innings = batting?.optInt("innings", 0) ?: 0,
            runs = batting?.optInt("runs", 0) ?: 0,
            highScore = batting?.optString("highestScore", "-") ?: "-",
            average = batting?.optDouble("average", 0.0) ?: 0.0,
            strikeRate = batting?.optDouble("strikeRate", 0.0) ?: 0.0,
            hundreds = batting?.optInt("hundreds", 0) ?: 0,
            fifties = batting?.optInt("fifties", 0) ?: 0,
            fours = batting?.optInt("fours", 0) ?: 0,
            sixes = batting?.optInt("sixes", 0) ?: 0,
            wickets = bowling?.optInt("wickets", 0) ?: 0,
            bowlingAverage = bowling?.optDouble("average", 0.0) ?: 0.0,
            economy = bowling?.optDouble("economy", 0.0) ?: 0.0,
            bestBowling = bowling?.optString("bestInnings", "-") ?: "-",
            fiveWickets = bowling?.optInt("fiveWickets", 0) ?: 0,
            catches = batting?.optInt("catches", 0) ?: 0,
        )
    }

    /**
     * Fallback: parse player profile from HTML using Jsoup selectors.
     */
    private fun parseFromHtml(doc: Document, playerId: String, fallbackName: String): PlayerProfile {
        val name = doc.select("h1.cb-font-40, h1.player-name, .cb-col-100 h1")
            .firstOrNull()?.text()?.trim() ?: fallbackName
        val info = extractPersonalInfo(doc)
        val bio = doc.select(".cb-plyr-bio, .cb-col-100 .cb-col-100 p").firstOrNull()?.text()?.trim().orEmpty()
        val imageUrl = extractImageUrl(doc)
        val stats = extractHtmlStats(doc)

        return PlayerProfile(
            id = playerId,
            name = name,
            country = info.country,
            role = info.role,
            dateOfBirth = info.dob,
            battingStyle = info.battingStyle,
            bowlingStyle = info.bowlingStyle,
            imageUrl = imageUrl,
            bio = bio,
            stats = stats,
        )
    }

    private data class PersonalInfo(
        val country: String,
        val role: String,
        val dob: String,
        val battingStyle: String,
        val bowlingStyle: String,
    )

    private fun extractPersonalInfo(doc: Document): PersonalInfo {
        var country = ""
        var role = ""
        var dob = ""
        var battingStyle = ""
        var bowlingStyle = ""

        val infoItems = doc.select(".cb-col-60 .cb-lst-itm, .cb-plyr-inf .cb-lst-itm, .cb-col-40 .cb-lst-itm")
        for (item in infoItems) {
            val label = item.select(".cb-col-40, .cb-lst-itm-sm").text().trim().lowercase()
            val value = item.select(".cb-col-60, .cb-lst-itm-lg").text().trim()
            when {
                label.contains("team") || label.contains("country") -> country = value
                label.contains("role") -> role = value
                label.contains("born") || label.contains("dob") || label.contains("birth") -> dob = value
                label.contains("batting") -> battingStyle = value
                label.contains("bowling") -> bowlingStyle = value
            }
        }
        return PersonalInfo(country, role, dob, battingStyle, bowlingStyle)
    }

    private fun extractImageUrl(doc: Document): String? {
        val src = doc.select("img.cb-plyr-img, .cb-col-27 img, .player-photo img")
            .firstOrNull()?.attr("src") ?: return null
        return if (src.startsWith("http")) src else "${CricBuzzUrls.BASE}$src"
    }

    private fun extractHtmlStats(doc: Document): Map<String, FormatStats> {
        val stats = mutableMapOf<String, FormatStats>()
        val statTables = doc.select("table.cb-plyr-thead, table.table")
        val formatNames = parseFormatNames(doc)

        for ((index, table) in statTables.withIndex()) {
            val format = formatNames.getOrElse(index) { "Test" }
            val rows = table.select("tr")
            if (rows.size < 2) continue

            val headers = rows[0].select("th, td").map { it.text().trim().lowercase() }
            val values = rows.getOrNull(1)?.select("td")?.map { it.text().trim() } ?: continue
            stats[format] = parseHtmlRowStats(headers, values)
        }
        return stats
    }

    private fun parseFormatNames(doc: Document): List<String> {
        val formatNames = mutableListOf<String>()
        for (header in doc.select(".cb-plyr-tbl .cb-nav-tab a, .cb-col-100 .cb-nav-tab a")) {
            val text = header.text().trim()
            when {
                text.contains("Test", true) -> formatNames.add("Test")
                text.contains("ODI", true) -> formatNames.add("ODI")
                text.contains("T20I", true) || text.contains("T20", true) -> formatNames.add("T20I")
                text.contains("IPL", true) -> formatNames.add("IPL")
            }
        }
        if (formatNames.isEmpty()) formatNames.addAll(DEFAULT_FORMATS)
        return formatNames
    }

    private fun parseHtmlRowStats(headers: List<String>, values: List<String>): FormatStats {
        fun getVal(vararg keys: String): String {
            for (key in keys) {
                val idx = headers.indexOfFirst { it.contains(key, true) }
                if (idx in values.indices) return values[idx]
            }
            return ""
        }

        return FormatStats(
            matches = getVal("match", "mat").toIntOrNull() ?: 0,
            innings = getVal("inn").toIntOrNull() ?: 0,
            runs = getVal("run").toIntOrNull() ?: 0,
            highScore = getVal("hs", "high").ifEmpty { "-" },
            average = getVal("avg", "ave").toDoubleOrNull() ?: 0.0,
            strikeRate = getVal("sr", "strike").toDoubleOrNull() ?: 0.0,
            hundreds = getVal("100").toIntOrNull() ?: 0,
            fifties = getVal("50").toIntOrNull() ?: 0,
            fours = getVal("4s", "four").toIntOrNull() ?: 0,
            sixes = getVal("6s", "six").toIntOrNull() ?: 0,
            wickets = getVal("wkt", "wick").toIntOrNull() ?: 0,
            bowlingAverage = getVal("bowl avg", "b avg").toDoubleOrNull() ?: 0.0,
            economy = getVal("econ").toDoubleOrNull() ?: 0.0,
            bestBowling = getVal("bbi", "best").ifEmpty { "-" },
            fiveWickets = getVal("5w").toIntOrNull() ?: 0,
            catches = getVal("ct", "catch").toIntOrNull() ?: 0,
        )
    }
}
