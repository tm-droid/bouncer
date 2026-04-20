package com.tanush.bouncer.network

import com.tanush.bouncer.models.FormatStats
import com.tanush.bouncer.models.PlayerProfile
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup

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

    /**
     * Search for players by name. Returns basic search results for the player picker.
     */
    suspend fun searchPlayers(query: String): List<PlayerSearchResult> = withContext(Dispatchers.IO) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.player_search"
            message = "Searching players: $query"
            level = SentryLevel.INFO
        })

        try {
            // Try the CricBuzz search API
            val searchUrl = "${CricBuzzUrls.BASE}/api/search/results?q=${
                java.net.URLEncoder.encode(query, "UTF-8")
            }&type=player"
            val response = Jsoup.connect(searchUrl)
                .userAgent(USER_AGENT)
                .ignoreContentType(true)
                .timeout(10000)
                .execute()

            val results = mutableListOf<PlayerSearchResult>()
            try {
                val json = JSONObject(response.body())
                val categories = json.optJSONArray("categories") ?: json.optJSONArray("results")
                if (categories != null) {
                    for (i in 0 until categories.length()) {
                        val category = categories.getJSONObject(i)
                        val items = category.optJSONArray("items")
                            ?: category.optJSONArray("results")
                            ?: continue
                        for (j in 0 until items.length()) {
                            val item = items.getJSONObject(j)
                            val id = item.optString("objectId", "")
                                .ifEmpty { item.optString("id", "") }
                            val name = item.optString("title", "")
                                .ifEmpty { item.optString("name", "") }
                            if (id.isNotEmpty() && name.isNotEmpty()) {
                                results.add(
                                    PlayerSearchResult(
                                        id = id,
                                        name = name,
                                        country = item.optString("country", ""),
                                        imageUrl = item.optString("image", null),
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Sentry.captureException(e)
            }

            // If JSON approach failed, try scraping the search HTML page
            if (results.isEmpty()) {
                val htmlUrl = "${CricBuzzUrls.BASE}/search?q=${
                    java.net.URLEncoder.encode(query, "UTF-8")
                }"
                val doc = Jsoup.connect(htmlUrl)
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .get()

                val playerLinks = doc.select("a[href*=/profiles/]")
                for (link in playerLinks) {
                    val href = link.attr("href")
                    val idMatch = Regex("""/profiles/(\d+)/""").find(href)
                    val playerId = idMatch?.groupValues?.getOrNull(1) ?: continue
                    val playerName = link.text().trim()
                    if (playerName.isNotEmpty() && results.none { it.id == playerId }) {
                        results.add(
                            PlayerSearchResult(
                                id = playerId,
                                name = playerName,
                                country = "",
                            )
                        )
                    }
                }
            }

            return@withContext results.take(10)
        } catch (e: Exception) {
            Sentry.captureException(e)
            return@withContext emptyList()
        }
    }

    /**
     * Scrape full player profile and stats from CricBuzz.
     */
    suspend fun scrapeProfile(playerName: String): PlayerProfile? = withContext(Dispatchers.IO) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.player_profile"
            message = "Scraping profile for: $playerName"
            level = SentryLevel.INFO
        })

        var lastException: Exception? = null
        var backoff = INITIAL_BACKOFF_MS

        for (attempt in 1..MAX_RETRIES) {
            try {
                // Step 1: Find the player's profile URL via search
                val searchResults = searchPlayers(playerName)
                val bestMatch = searchResults.firstOrNull() ?: return@withContext null

                // Step 2: Scrape the profile page
                val slug = bestMatch.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                val profileUrl = "${CricBuzzUrls.BASE}/profiles/${bestMatch.id}/$slug"
                val doc = Jsoup.connect(profileUrl)
                    .userAgent(USER_AGENT)
                    .timeout(15000)
                    .get()

                // Try __NEXT_DATA__ first (more structured)
                val nextDataProfile = parseFromNextJsData(doc.html(), bestMatch.id, bestMatch.name)
                if (nextDataProfile != null) return@withContext nextDataProfile

                // Fallback: parse HTML directly
                return@withContext parseFromHtml(doc, bestMatch.id, bestMatch.name)

            } catch (e: java.net.SocketTimeoutException) {
                lastException = e
            } catch (e: java.net.UnknownHostException) {
                lastException = e
            } catch (e: Exception) {
                lastException = e
                break
            }

            delay(backoff)
            backoff *= 2
        }

        Sentry.captureException(lastException ?: Exception("Player profile fetch failed for $playerName"))
        null
    }

    /**
     * Parse player profile from __NEXT_DATA__ JSON embedded in the page.
     */
    private fun parseFromNextJsData(html: String, playerId: String, fallbackName: String): PlayerProfile? {
        try {
            val regex = Regex(
                """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
                RegexOption.DOT_MATCHES_ALL
            )
            val match = regex.find(html) ?: return null
            val json = JSONObject(match.groupValues[1])
            val pageProps = json.optJSONObject("props")?.optJSONObject("pageProps") ?: return null

            // Try different JSON structures CricBuzz may use
            val playerData = pageProps.optJSONObject("player")
                ?: pageProps.optJSONObject("data")?.optJSONObject("player")
                ?: pageProps.optJSONObject("appData")?.optJSONObject("player")
                ?: return null

            val name = playerData.optString("name", fallbackName)
            val country = playerData.optString("intlTeam", "")
                .ifEmpty { playerData.optString("country", "") }
            val role = playerData.optString("role", "")
                .ifEmpty { playerData.optString("bat", "") }
            val dob = playerData.optString("DoB", "")
                .ifEmpty { playerData.optString("dob", "") }
            val battingStyle = playerData.optString("bat", "")
                .ifEmpty { playerData.optString("battingStyle", "") }
            val bowlingStyle = playerData.optString("bowl", "")
                .ifEmpty { playerData.optString("bowlingStyle", "") }
            val bio = playerData.optString("bio", "")

            val imageId = playerData.optString("faceImageId", "")
            val imageUrl = if (imageId.isNotEmpty()) {
                "https://www.cricbuzz.com/a/img/v1/152x152/i1/c$imageId/player.jpg"
            } else null

            val teams = mutableListOf<String>()
            val teamsArray = playerData.optJSONArray("teams")
            if (teamsArray != null) {
                for (i in 0 until teamsArray.length()) {
                    val teamObj = teamsArray.optJSONObject(i)
                    val teamName = teamObj?.optString("name", "") ?: teamsArray.optString(i, "")
                    if (teamName.isNotEmpty()) teams.add(teamName)
                }
            }

            // Parse stats
            val stats = mutableMapOf<String, FormatStats>()
            val formats = listOf("Test" to "test", "ODI" to "odi", "T20I" to "t20i", "IPL" to "ipl")
            for ((label, key) in formats) {
                val formatStats = parseStatsFromJson(playerData, key)
                if (formatStats != null) stats[label] = formatStats
            }

            return PlayerProfile(
                id = playerId,
                name = name,
                country = country,
                role = role,
                dateOfBirth = dob,
                battingStyle = battingStyle,
                bowlingStyle = bowlingStyle,
                imageUrl = imageUrl,
                bio = bio,
                teams = teams,
                stats = stats,
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.player_profile"
                message = "Failed to parse __NEXT_DATA__: ${e.message}"
                level = SentryLevel.WARNING
            })
            return null
        }
    }

    /**
     * Extract format stats from the JSON data.
     */
    private fun parseStatsFromJson(playerData: JSONObject, formatKey: String): FormatStats? {
        // CricBuzz stores stats under different possible paths
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
    private fun parseFromHtml(doc: org.jsoup.nodes.Document, playerId: String, fallbackName: String): PlayerProfile {
        // Player name from page title
        val name = doc.select("h1.cb-font-40, h1.player-name, .cb-col-100 h1").firstOrNull()?.text()?.trim()
            ?: fallbackName

        // Personal info section
        val infoItems = doc.select(".cb-col-60 .cb-lst-itm, .cb-plyr-inf .cb-lst-itm, .cb-col-40 .cb-lst-itm")
        var country = ""
        var role = ""
        var dob = ""
        var battingStyle = ""
        var bowlingStyle = ""
        var bio = ""

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

        // Bio
        val bioElement = doc.select(".cb-plyr-bio, .cb-col-100 .cb-col-100 p").firstOrNull()
        if (bioElement != null) bio = bioElement.text().trim()

        // Image
        val imgEl = doc.select("img.cb-plyr-img, .cb-col-27 img, .player-photo img").firstOrNull()
        val imageUrl = imgEl?.attr("src")?.let {
            if (it.startsWith("http")) it else "${CricBuzzUrls.BASE}$it"
        }

        // Stats tables
        val stats = mutableMapOf<String, FormatStats>()
        val statTables = doc.select("table.cb-plyr-thead, table.table")
        val formatHeaders = doc.select(".cb-plyr-tbl .cb-nav-tab a, .cb-col-100 .cb-nav-tab a")

        // Try to map each table to a format
        val formatNames = mutableListOf<String>()
        for (header in formatHeaders) {
            val text = header.text().trim()
            when {
                text.contains("Test", true) -> formatNames.add("Test")
                text.contains("ODI", true) -> formatNames.add("ODI")
                text.contains("T20I", true) || text.contains("T20", true) -> formatNames.add("T20I")
                text.contains("IPL", true) -> formatNames.add("IPL")
            }
        }

        // If we couldn't find format headers, use defaults
        if (formatNames.isEmpty()) {
            formatNames.addAll(listOf("Test", "ODI", "T20I", "IPL"))
        }

        // Parse stat tables
        for ((index, table) in statTables.withIndex()) {
            val format = formatNames.getOrElse(index) { "Test" }
            val rows = table.select("tr")
            if (rows.size < 2) continue

            val headers = rows[0].select("th, td").map { it.text().trim().lowercase() }
            val values = rows.getOrNull(1)?.select("td")?.map { it.text().trim() } ?: continue

            fun getVal(vararg keys: String): String {
                for (key in keys) {
                    val idx = headers.indexOfFirst { it.contains(key, true) }
                    if (idx in values.indices) return values[idx]
                }
                return ""
            }

            stats[format] = FormatStats(
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

        return PlayerProfile(
            id = playerId,
            name = name,
            country = country,
            role = role,
            dateOfBirth = dob,
            battingStyle = battingStyle,
            bowlingStyle = bowlingStyle,
            imageUrl = imageUrl,
            bio = bio,
            stats = stats,
        )
    }
}
