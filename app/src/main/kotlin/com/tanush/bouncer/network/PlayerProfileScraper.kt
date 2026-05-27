package com.tanush.bouncer.network

import com.tanush.bouncer.models.FormatStats
import com.tanush.bouncer.models.PlayerProfile
import com.tanush.bouncer.models.RecentInnings
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
 * Scrapes player profile and stats from CricBuzz.
 */
object PlayerProfileScraper {

    private const val TAG = "PlayerProfileScraper"
    private const val USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val MAX_RETRIES = 3
    private const val INITIAL_BACKOFF_MS = 1000L
    private const val SEARCH_TIMEOUT_MS = 10000
    private const val PROFILE_TIMEOUT_MS = 15000
    private const val MAX_SEARCH_RESULTS = 10

    private val FORMATS_JSON = listOf("Test" to "test", "ODI" to "odi", "T20I" to "t20i", "IPL" to "ipl")
    private val DEFAULT_FORMATS = listOf("Test", "ODI", "T20I", "IPL")
    private val PROFILE_ID_REGEX = Regex("""/profiles/(\d+)/""")
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
     * Search for players by name. Returns basic search results for the player picker.
     */
    suspend fun searchPlayers(query: String): List<PlayerSearchResult> = withContext(Dispatchers.IO) {
        breadcrumb("network.player_search", "Searching players: $query")

        try {
            val results = searchPlayersViaApi(query).toMutableList()
            if (results.isEmpty()) {
                results.addAll(searchPlayersViaHtml(query, results))
            }
            results.take(MAX_SEARCH_RESULTS)
        } catch (e: Exception) {
            Sentry.captureException(e)
            emptyList()
        }
    }

    private fun searchPlayersViaApi(query: String): List<PlayerSearchResult> {
        val searchUrl = "${CricBuzzUrls.BASE}/api/search/results?q=${encode(query)}&type=player"
        val response = Jsoup.connect(searchUrl)
            .userAgent(USER_AGENT)
            .ignoreContentType(true)
            .timeout(SEARCH_TIMEOUT_MS)
            .execute()

        return try {
            val json = JSONObject(response.body())
            val categories = json.optJSONArray("categories") ?: json.optJSONArray("results")
            extractApiResults(categories)
        } catch (e: Exception) {
            Sentry.captureException(e)
            emptyList()
        }
    }

    private fun extractApiResults(categories: JSONArray?): List<PlayerSearchResult> {
        if (categories == null) return emptyList()
        val results = mutableListOf<PlayerSearchResult>()
        for (i in 0 until categories.length()) {
            val category = categories.getJSONObject(i)
            val items = category.optJSONArray("items")
                ?: category.optJSONArray("results")
                ?: continue
            for (j in 0 until items.length()) {
                val result = itemToSearchResult(items.getJSONObject(j))
                if (result != null) results.add(result)
            }
        }
        return results
    }

    private fun itemToSearchResult(item: JSONObject): PlayerSearchResult? {
        val id = item.optString("objectId", "").ifEmpty { item.optString("id", "") }
        val name = item.optString("title", "").ifEmpty { item.optString("name", "") }
        if (id.isEmpty() || name.isEmpty()) return null
        return PlayerSearchResult(
            id = id,
            name = name,
            country = item.optString("country", ""),
            imageUrl = item.optString("image", null),
        )
    }

    private fun searchPlayersViaHtml(
        query: String,
        existing: List<PlayerSearchResult>,
    ): List<PlayerSearchResult> {
        val htmlUrl = "${CricBuzzUrls.BASE}/search?q=${encode(query)}"
        val doc = Jsoup.connect(htmlUrl)
            .userAgent(USER_AGENT)
            .timeout(SEARCH_TIMEOUT_MS)
            .get()

        val seenIds = existing.map { it.id }.toMutableSet()
        val results = mutableListOf<PlayerSearchResult>()
        for (link in doc.select("a[href*=/profiles/]")) {
            val playerId = PROFILE_ID_REGEX.find(link.attr("href"))?.groupValues?.getOrNull(1) ?: continue
            val playerName = link.text().trim()
            if (playerName.isEmpty() || playerId in seenIds) continue
            seenIds.add(playerId)
            results.add(PlayerSearchResult(id = playerId, name = playerName, country = ""))
        }
        return results
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    /**
     * Scrape full player profile and stats from CricBuzz.
     */
    suspend fun scrapeProfile(playerName: String): PlayerProfile? = withContext(Dispatchers.IO) {
        breadcrumb("network.player_profile", "Scraping profile for: $playerName")

        var lastException: Exception? = null
        var backoff = INITIAL_BACKOFF_MS

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
            delay(backoff)
            backoff *= 2
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
            val doc = Jsoup.connect(profileUrl)
                .userAgent(USER_AGENT)
                .timeout(PROFILE_TIMEOUT_MS)
                .get()

            val profile = parseFromNextJsData(doc.html(), bestMatch.id, bestMatch.name)
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
