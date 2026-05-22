package com.tanush.bouncer.network

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.BowlerStats
import com.tanush.bouncer.models.FallOfWicket
import com.tanush.bouncer.models.PartnershipStats
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.managers.TeamAcronymManager
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal fun normalizeNextFlightPayload(rawPayload: String): String? {
    var normalized = rawPayload.trim()
    val colonIndex = normalized.indexOf(':')
    if (colonIndex in 1..9) {
        val prefix = normalized.take(colonIndex)
        if (prefix.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            normalized = normalized.substring(colonIndex + 1)
        }
    }
    if (normalized.startsWith("I")) normalized = normalized.substring(1)
    normalized = normalized.trim()
    return normalized.takeIf { it.startsWith("{") || it.startsWith("[") }
}

internal fun unwrapNextFlightPayload(scriptContent: String): String? {
    val jsonElement = try {
        val reader = com.google.gson.stream.JsonReader(java.io.StringReader(scriptContent))
        reader.isLenient = true
        JsonParser.parseReader(reader)
    } catch (_: Exception) {
        return null
    }
    if (!jsonElement.isJsonArray) return null
    val array = jsonElement.asJsonArray
    if (array.size() <= 1 || !array[1].isJsonPrimitive || !array[1].asJsonPrimitive.isString) return null
    return normalizeNextFlightPayload(array[1].asString)
}

private fun parseLenientJson(payload: String): JsonElement? = try {
    val reader = com.google.gson.stream.JsonReader(java.io.StringReader(payload))
    reader.isLenient = true
    JsonParser.parseReader(reader)
} catch (_: Exception) {
    null
}

private inline fun forEachNextFlightPayload(html: String, block: (JsonElement) -> Unit) {
    val startMarker = "self.__next_f.push("
    var startIndex = 0
    while (true) {
        val idx = html.indexOf(startMarker, startIndex)
        if (idx == -1) break
        val openParenIdx = idx + startMarker.length - 1
        var parenCount = 0
        var inString = false
        var escape = false
        var endIdx = -1
        for (i in openParenIdx until html.length) {
            val char = html[i]
            if (escape) { escape = false; continue }
            if (char == '\\') { escape = true; continue }
            if (char == '"') { inString = !inString; continue }
            if (!inString) {
                if (char == '(') parenCount++
                else if (char == ')') {
                    parenCount--
                    if (parenCount == 0) { endIdx = i; break }
                }
            }
        }
        if (endIdx != -1) {
            val scriptContent = html.substring(openParenIdx + 1, endIdx)
            val nextPayload = unwrapNextFlightPayload(scriptContent)
            if (nextPayload != null) {
                parseLenientJson(nextPayload)?.let(block)
            }
            startIndex = endIdx + 1
        } else {
            startIndex = idx + 1
        }
    }
}

private inline fun crumb(
    msg: String,
    lvl: SentryLevel = SentryLevel.INFO,
    data: Map<String, Any?> = emptyMap()
) {
    Sentry.addBreadcrumb(Breadcrumb().apply {
        category = "network.cricbuzz"
        message = msg
        level = lvl
        data.forEach { (k, v) -> if (v != null) setData(k, v) }
    })
}

class CricBuzzScraper {

    companion object {
        private const val TAG = "CricBuzzScraper"
        private const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val DRIFT_ALARM_THRESHOLD = 3

        private val consecutiveEmptyParses = java.util.concurrent.atomic.AtomicInteger(0)
    }

    suspend fun fetchLiveMatches(url: String, matchState: String): List<CricketMatch> = withContext(Dispatchers.IO) {
        crumb("Fetching live matches", data = mapOf("url" to url, "matchState" to matchState))

        var lastException: Exception? = null
        var backoff = INITIAL_BACKOFF_MS

        for (attempt in 1..MAX_RETRIES) {
            try {
                val doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(10000).get()
                val matches = parseNextJsData(doc.html())
                val filteredMatches = filterMatches(matches, matchState)
                Log.d(TAG, "Found ${matches.size} total matches, filtered down to ${filteredMatches.size} for state: $matchState")

                if (matches.isEmpty()) {
                    val count = consecutiveEmptyParses.incrementAndGet()
                    crumb("Parse returned 0 matches (consecutive: $count)", SentryLevel.WARNING,
                        mapOf("url" to url, "matchState" to matchState, "consecutiveEmpty" to count))
                    if (count >= DRIFT_ALARM_THRESHOLD) {
                        Sentry.captureMessage(
                            "scrape_drift: CricBuzz returned 0 matches for $matchState ($count consecutive). DOM structure may have changed.",
                            SentryLevel.WARNING
                        )
                        consecutiveEmptyParses.set(0)
                        throw com.tanush.bouncer.data.AppException.ScrapingException(
                            source = "CricBuzz",
                            message = "CricBuzz parse drift: $count consecutive empty results for $matchState"
                        )
                    }
                } else {
                    consecutiveEmptyParses.set(0)
                }

                crumb("Successfully fetched live matches",
                    data = mapOf("totalMatches" to matches.size, "filteredMatches" to filteredMatches.size))
                return@withContext filteredMatches
            } catch (e: SocketTimeoutException) {
                lastException = e
                Log.w(TAG, "Timeout fetching $url (attempt $attempt/$MAX_RETRIES)", e)
                crumb("Socket timeout fetching matches", SentryLevel.WARNING,
                    mapOf("url" to url, "attempt" to attempt))
            } catch (e: UnknownHostException) {
                Log.e(TAG, "No internet connection fetching $url", e)
                crumb("No internet connection", SentryLevel.WARNING, mapOf("url" to url))
                return@withContext emptyList()
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Error fetching matches from $url (attempt $attempt/$MAX_RETRIES)", e)
                crumb("Error fetching matches", SentryLevel.ERROR,
                    mapOf("url" to url, "attempt" to attempt, "error" to (e.message ?: "Unknown error")))
            }

            if (attempt < MAX_RETRIES) {
                Log.d(TAG, "Retrying in ${backoff}ms...")
                delay(backoff)
                backoff *= 2
            }
        }

        Log.e(TAG, "All $MAX_RETRIES attempts failed for $url", lastException)
        lastException?.let { Sentry.captureException(it) }
        emptyList()
    }

    private fun filterMatches(matches: List<CricketMatch>, state: String): List<CricketMatch> {
        return matches.filter { match ->
            val matchStatus = match.info.lowercase().trim()
            val isUpcoming = matchStatus.contains("preview") || matchStatus.contains("upcoming") || matchStatus.contains("scheduled")
            val isRecent = matchStatus.contains("complete") || matchStatus.contains("abandon") || matchStatus.contains("result") || matchStatus.contains("tied")
            when (state) {
                "Upcoming" -> isUpcoming
                "Recent" -> isRecent
                "Live" -> !isUpcoming && !isRecent
                else -> true
            }
        }.distinctBy { it.id }
    }

    private fun parseNextJsData(html: String): List<CricketMatch> {
        val matches = mutableListOf<CricketMatch>()
        forEachNextFlightPayload(html) { data ->
            try {
                matches.addAll(findMatchesInJson(data))
            } catch (e: Exception) {
                Log.d(TAG, "Skipping unparseable Next.js data segment", e)
            }
        }
        return matches
    }

    private fun findMatchesInJson(element: JsonElement): List<CricketMatch> {
        val matches = mutableListOf<CricketMatch>()
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            parseMatchObject(obj)?.let { matches.add(it) }
            obj.keySet().forEach { matches.addAll(findMatchesInJson(obj.get(it))) }
        } else if (element.isJsonArray) {
            element.asJsonArray.forEach { matches.addAll(findMatchesInJson(it)) }
        }
        return matches
    }

    private fun parseMatchObject(obj: JsonObject): CricketMatch? {
        try {
            if (!obj.has("matchInfo")) return null
            val info = obj.getAsJsonObject("matchInfo")
            val matchId = info.get("matchId").asString
            val team1 = info.getAsJsonObject("team1").get("teamName").asString
            val team2 = info.getAsJsonObject("team2").get("teamName").asString
            val status = info.get("status").asString
            val state = info.get("state")?.asString ?: "Live"

            val matchFormat = info.get("matchFormat")?.asString ?: ""
            val matchDesc = info.get("matchDesc")?.asString ?: ""
            val seriesName = info.get("seriesName")?.asString ?: ""
            val seriesId = info.get("seriesId")?.asString ?: ""

            val venue = try {
                val venueInfo = info.getAsJsonObject("venueInfo")
                val ground = venueInfo?.get("ground")?.asString ?: ""
                val city = venueInfo?.get("city")?.asString ?: ""
                if (ground.isNotBlank() && city.isNotBlank()) "$ground, $city"
                else ground.ifBlank { city }
            } catch (e: Exception) {
                Sentry.captureException(e)
                ""
            }

            val matchType = mapMatchFormat(matchFormat).ifBlank {
                determineMatchType("$team1 vs $team2 $matchDesc")
            }
            val competitionType = classifyCompetitionType(seriesName, matchDesc)

            var team1Score = ""
            var team2Score = ""
            var team1Overs = ""
            var team2Overs = ""
            var team1IsBatting = false
            var team2IsBatting = false

            if (obj.has("matchScore")) {
                val scoreObj = obj.getAsJsonObject("matchScore")
                fun latestInnings(key: String): JsonObject? {
                    if (!scoreObj.has(key)) return null
                    val t = scoreObj.getAsJsonObject(key)
                    return when {
                        t.has("inngs2") -> t.getAsJsonObject("inngs2")
                        t.has("inngs1") -> t.getAsJsonObject("inngs1")
                        else -> null
                    }
                }
                latestInnings("team1Score")?.let { s1 ->
                    team1Score = "${s1.get("runs")?.asString ?: ""}/${s1.get("wickets")?.asString ?: "0"}"
                    team1Overs = s1.get("overs")?.asString ?: ""
                }
                latestInnings("team2Score")?.let { s2 ->
                    team2Score = "${s2.get("runs")?.asString ?: ""}/${s2.get("wickets")?.asString ?: "0"}"
                    team2Overs = s2.get("overs")?.asString ?: ""
                }
                val battingId = scoreObj.get("battingTeamId")?.asString
                if (battingId != null) {
                    team1IsBatting = info.getAsJsonObject("team1").get("teamId")?.asString == battingId
                    team2IsBatting = info.getAsJsonObject("team2").get("teamId")?.asString == battingId
                }
            }

            val teams = listOf(
                CricketMatch.Team(team1, team1Score, "", team1IsBatting, TeamLogoManager.getLogoUrl(team1), TeamAcronymManager.getAcronym(team1), team1Overs),
                CricketMatch.Team(team2, team2Score, "", team2IsBatting, TeamLogoManager.getLogoUrl(team2), TeamAcronymManager.getAcronym(team2), team2Overs)
            )

            val hasScore = team1Score.isNotBlank() || team2Score.isNotBlank()
            val matchScore = CricketMatch.MatchScore(
                currentInnings = if (hasScore) (if (team1IsBatting) "1" else "2") else "",
                batsmen = emptyList(),
                bowlers = emptyList(),
                overs = if (hasScore) (if (team1IsBatting) team1Overs else team2Overs) else null,
                currentRunRate = null,
                requiredRunRate = null,
                lastWicket = null,
                recentBalls = null
            )

            return CricketMatch(
                id = matchId,
                seriesId = seriesId,
                matchTitle = "$team1 vs $team2",
                matchType = matchType,
                status = status,
                teams = teams,
                venue = venue,
                date = "Today",
                matchTime = "",
                score = matchScore,
                currentStatus = status,
                info = state,
                category = competitionType,
                gender = "Men",
                competitionType = competitionType
            )
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse match from element", e)
            Sentry.captureException(e)
            return null
        }
    }

    private fun mapMatchFormat(format: String): String = when (format.uppercase()) {
        "TEST" -> "Test"
        "ODI" -> "ODI"
        "T20", "T20I" -> "T20"
        "FC" -> "First Class"
        "LIST_A" -> "List A"
        else -> ""
    }

    private fun classifyCompetitionType(seriesName: String, matchDesc: String): String {
        val combined = "$seriesName $matchDesc".lowercase()
        val internationalKeywords = listOf(
            "tour of", "world cup", "champions trophy", "asia cup",
            "icc ", "tri-series", "tri series", "bilateral",
            "\\d+(?:st|nd|rd|th) test", "\\d+(?:st|nd|rd|th) odi", "\\d+(?:st|nd|rd|th) t20i"
        )
        val leagueKeywords = listOf(
            "ipl", "bbl", "big bash", "psl", "cpl", "caribbean premier",
            "sa20", "lpl", "bpl", "ilt20", "mlc", "major league cricket",
            "the hundred", "hundred", "vitality blast", "t20 blast",
            "syed mushtaq ali", "vijay hazare"
        )

        if (internationalKeywords.any { keyword ->
            if (keyword.contains("\\d")) Regex(keyword).containsMatchIn(combined)
            else combined.contains(keyword)
        }) return "International"

        if (leagueKeywords.any { combined.contains(it) }) return "League"

        return "Domestic"
    }

    data class MatchDetails(
        val recentBalls: List<String>,
        val overNumbers: List<String>,
        val batsmen: List<ScrapedBatsman>,
        val bowler: ScrapedBowler?,
        val recentBallsSummary: String?,
        val score: String? = null,
        val team1Score: String? = null,
        val team2Score: String? = null,
        val team1Overs: String? = null,
        val team2Overs: String? = null,
        val currentRunRate: String? = null,
        val requiredRunRate: String? = null
    )

    suspend fun fetchBallByBallAndBatsmen(matchId: String): MatchDetails = withContext(Dispatchers.IO) {
        crumb("Fetching ball-by-ball and batsmen data", data = mapOf("matchId" to matchId))

        val url = CricBuzzUrls.liveScoresUrl(matchId)
        var lastException: Exception? = null
        var backoff = INITIAL_BACKOFF_MS

        for (attempt in 1..MAX_RETRIES) {
        try {
            val doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(10000).get()

            val batsmen = mutableListOf<ScrapedBatsman>()
            val batRows = doc.select("div.cb-col.cb-col-100.cb-min-itm-rw")
            for (row in batRows) {
                val nameLink = row.select("div.cb-col-50 a").first() ?: continue
                val name = nameLink.text()
                val stats = row.select("div.cb-col-10, div.cb-col-14")
                if (stats.size >= 4) {
                    val isStriker = nameLink.parent()?.text()?.contains("*") == true || row.text().contains("*")
                    try {
                        batsmen.add(ScrapedBatsman(
                            name = name.replace("*", "").trim(),
                            runs = stats.getOrNull(0)?.text() ?: "0",
                            balls = stats.getOrNull(1)?.text() ?: "0",
                            fours = stats.getOrNull(2)?.text() ?: "0",
                            sixes = stats.getOrNull(3)?.text() ?: "0",
                            strikeRate = stats.getOrNull(stats.size - 1)?.text() ?: "-",
                            isStriker = isStriker
                        ))
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing batsman stats for $name: ${e.message}")
                        Sentry.captureException(e)
                    }
                }
            }

            var bowler: ScrapedBowler? = null
            val bowlerHeader = doc.select("div.cb-col-50:contains(Bowler)").first()
            if (bowlerHeader != null) {
                var current = bowlerHeader.parent()?.nextElementSibling()
                while (current != null && current.hasClass("cb-min-itm-rw")) {
                    val name = current.select("div.cb-col-50 a").text()
                    val stats = current.select("div.cb-col-10")
                    if (stats.size >= 4) {
                        try {
                            bowler = ScrapedBowler(
                                name,
                                stats.getOrNull(0)?.text() ?: "0",
                                stats.getOrNull(1)?.text() ?: "0",
                                stats.getOrNull(2)?.text() ?: "0",
                                stats.getOrNull(3)?.text() ?: "0",
                                stats.getOrNull(stats.size - 1)?.text() ?: "-"
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing bowler stats for $name: ${e.message}")
                            Sentry.captureException(e)
                        }
                        break
                    }
                    current = current.nextElementSibling()
                }
            }

            val recentBalls = mutableListOf<String>()
            val overNumbers = mutableListOf<String>()
            doc.select("div.cb-min-rcnt span").forEach {
                val text = it.text().trim()
                if (text.isNotEmpty()) recentBalls.add(text)
            }
            if (recentBalls.isEmpty()) {
                recentBalls.addAll(parseRecentBallsFromNextJs(doc.html()))
            }

            val score = doc.select("span.cb-font-20.text-bold").first()?.text()
                ?: doc.select("div.cb-min-bat-rw span").first()?.text()

            val miniScoreRows = doc.select("div.cb-min-bat-rw")
            var team1Score: String? = null
            var team2Score: String? = null
            var team1Overs: String? = null
            var team2Overs: String? = null
            miniScoreRows.forEachIndexed { index, row ->
                val scoreText = row.select("span.cb-font-20").text().trim()
                val oversText = row.select("span.cb-text-gray, span.cb-font-12").text()
                    .replace("(", "").replace(")", "").replace("Ov", "").trim()
                when (index) {
                    0 -> { team1Score = scoreText.ifBlank { null }; team1Overs = oversText.ifBlank { null } }
                    1 -> { team2Score = scoreText.ifBlank { null }; team2Overs = oversText.ifBlank { null } }
                }
            }

            val statusText = doc.select("div.cb-text-complete, div.cb-text-live, div.cb-text-inprogress, div.cb-min-inf").text()
            val currentRunRate = Regex("CRR\\s*:?\\s*([\\d.]+)").find(statusText)?.groupValues?.get(1)
            val requiredRunRate = Regex("RRR\\s*:?\\s*([\\d.]+)").find(statusText)?.groupValues?.get(1)

            return@withContext MatchDetails(recentBalls, overNumbers, batsmen, bowler, null, score,
                team1Score = team1Score, team2Score = team2Score,
                team1Overs = team1Overs, team2Overs = team2Overs,
                currentRunRate = currentRunRate, requiredRunRate = requiredRunRate)
        } catch (e: SocketTimeoutException) {
            lastException = e
            Log.w(TAG, "Timeout fetching match details for $matchId (attempt $attempt/$MAX_RETRIES)")
            crumb("Timeout fetching match details", SentryLevel.WARNING,
                mapOf("matchId" to matchId, "attempt" to attempt))
        } catch (e: UnknownHostException) {
            Log.e(TAG, "No internet fetching match details for $matchId", e)
            crumb("No internet fetching match details", SentryLevel.ERROR, mapOf("matchId" to matchId))
            Sentry.captureException(e)
            return@withContext MatchDetails(emptyList(), emptyList(), emptyList(), null, null, null)
        } catch (e: Exception) {
            lastException = e
            Log.e(TAG, "Error fetching match details for $matchId (attempt $attempt/$MAX_RETRIES)", e)
            crumb("Error fetching match details", SentryLevel.ERROR,
                mapOf("matchId" to matchId, "attempt" to attempt, "error" to (e.message ?: "Unknown error")))
        }

            if (attempt < MAX_RETRIES) {
                delay(backoff)
                backoff *= 2
            }
        }

        Log.e(TAG, "All $MAX_RETRIES attempts failed for match details $matchId", lastException)
        lastException?.let { Sentry.captureException(it) }
        MatchDetails(emptyList(), emptyList(), emptyList(), null, null, null)
    }

    suspend fun fetchLiveCommentary(matchUrl: String): List<String> = withContext(Dispatchers.IO) {
        crumb("Fetching live commentary", data = mapOf("url" to matchUrl))

        try {
            val doc = Jsoup.connect(matchUrl).userAgent(USER_AGENT).timeout(12000).get()
            val html = doc.html()

            val nextJsLines = parseCommentaryFromNextJs(html)
            if (nextJsLines.isNotEmpty()) {
                Log.d(TAG, "Fetched ${nextJsLines.size} commentary lines from Next.js RSC data")
                crumb("Successfully fetched commentary from Next.js RSC",
                    data = mapOf("linesCount" to nextJsLines.size))
                return@withContext nextJsLines.filter { it.length > 5 }
            }

            val lines = mutableListOf<String>()
            doc.select("p.cb-com-ln").forEach { lines.add(it.text().trim()) }
            doc.select("div.cb-col.cb-col-100.cb-com-ln").forEach { lines.add(it.text().trim()) }

            Log.d(TAG, "Fetched ${lines.size} commentary lines from legacy CSS selectors")
            crumb("Successfully fetched commentary", data = mapOf("linesCount" to lines.size))
            lines.filter { it.length > 5 }.distinct()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Cricbuzz commentary", e)
            crumb("Error fetching commentary", SentryLevel.ERROR,
                mapOf("url" to matchUrl, "error" to (e.message ?: "Unknown error")))
            Sentry.captureException(e)
            emptyList()
        }
    }

    private fun parseCommentaryFromNextJs(html: String): List<String> {
        val results = mutableListOf<Pair<Long, String>>()
        forEachNextFlightPayload(html) { data -> findCommentaryInJson(data, results) }
        return results.sortedByDescending { it.first }.map { it.second }
    }

    private fun parseRecentBallsFromNextJs(html: String): List<String> {
        val overData = mutableListOf<Pair<Long, String>>()
        forEachNextFlightPayload(html) { data -> findOverSummaryInJson(data, overData) }
        if (overData.isEmpty()) return emptyList()
        val latestSummary = overData.maxByOrNull { it.first }?.second ?: return emptyList()
        return latestSummary.trim().split(" ").filter { it.isNotBlank() }
    }

    private fun findCommentaryInJson(element: JsonElement, results: MutableList<Pair<Long, String>>) {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            if (obj.has("commText") && obj.has("timestamp")) {
                try {
                    val commText = obj.get("commText").asString
                    val timestamp = obj.get("timestamp").asLong
                    if (commText.isNotBlank()) results.add(timestamp to commText)
                } catch (_: Exception) { }
            }
            obj.keySet().forEach { findCommentaryInJson(obj.get(it), results) }
        } else if (element.isJsonArray) {
            element.asJsonArray.forEach { findCommentaryInJson(it, results) }
        }
    }

    private fun findOverSummaryInJson(element: JsonElement, results: MutableList<Pair<Long, String>>) {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            if (obj.has("overSummary") && obj.has("overNumber")) {
                try {
                    val summary = obj.get("overSummary").asString
                    val orderKey = obj.get("overNumber").asLong
                    if (summary.isNotBlank()) results.add(orderKey to summary)
                } catch (_: Exception) { }
            }
            obj.keySet().forEach { findOverSummaryInJson(obj.get(it), results) }
        } else if (element.isJsonArray) {
            element.asJsonArray.forEach { findOverSummaryInJson(it, results) }
        }
    }

    private fun determineMatchType(title: String): String {
        val t = title.lowercase()
        return when {
            t.contains("test") -> "Test"
            t.contains("odi") -> "ODI"
            t.contains("t20") -> "T20"
            t.contains("first class") || t.contains("county") || t.contains("ranji") ||
                t.contains("sheffield shield") || t.contains("plunket shield") -> "First Class"
            t.contains("list a") -> "List A"
            else -> "Other"
        }
    }

    suspend fun fetchScorecard(matchId: String): Scorecard = withContext(Dispatchers.IO) {
        crumb("Fetching scorecard", data = mapOf("matchId" to matchId))

        val url = CricBuzzUrls.scorecardUrl(matchId)
        try {
            val doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(15000).get()
            val inningsList = mutableListOf<Innings>()

            val inningsHeaders = doc.select("div[id^=team-][id*=-innings-]")
            for (header in inningsHeaders) {
                val headerId = header.id()
                val content = doc.getElementById("scard-$headerId") ?: continue

                val teamName = header.select("div.font-bold").first()?.text() ?: "Unknown"
                val score = header.select("span.font-bold").text()
                val overs = header.select("span:contains(Ov)").text().replace("(", "").replace(")", "").trim()

                val batsmen = mutableListOf<BatsmanStats>()
                content.select("div.grid.scorecard-bat-grid").forEach { row ->
                    val name = row.select("a").first()?.text() ?: ""
                    if (name.isNotEmpty() && !name.contains("Batter")) {
                        val stats = row.children()
                        if (stats.size >= 6) {
                            try {
                                batsmen.add(BatsmanStats(
                                    name,
                                    row.select("div.text-cbTxtSec").text(),
                                    stats.getOrNull(1)?.text() ?: "0",
                                    stats.getOrNull(2)?.text() ?: "0",
                                    stats.getOrNull(3)?.text() ?: "0",
                                    stats.getOrNull(4)?.text() ?: "0",
                                    stats.getOrNull(5)?.text() ?: "0"
                                ))
                            } catch (e: Exception) {
                                Log.w(TAG, "Error parsing scorecard batsman: $name", e)
                                Sentry.captureException(e)
                            }
                        }
                    }
                }

                val bowlers = mutableListOf<BowlerStats>()
                content.select("div.grid.scorecard-bowl-grid").forEach { row ->
                    val name = row.select("a").first()?.text() ?: ""
                    if (name.isNotEmpty() && !name.contains("Bowler")) {
                        val stats = row.children()
                        if (stats.size >= 5) {
                            try {
                                bowlers.add(BowlerStats(
                                    name,
                                    stats.getOrNull(1)?.text() ?: "0",
                                    stats.getOrNull(2)?.text() ?: "0",
                                    stats.getOrNull(3)?.text() ?: "0",
                                    stats.getOrNull(4)?.text() ?: "0",
                                    "0",
                                    "0",
                                    stats.lastOrNull()?.text() ?: ""
                                ))
                            } catch (e: Exception) {
                                Log.w(TAG, "Error parsing scorecard bowler: $name", e)
                                Sentry.captureException(e)
                            }
                        }
                    }
                }

                val fows = mutableListOf<FallOfWicket>()
                val fowSection = content.select("div:contains(Fall of Wickets)").first()
                if (fowSection != null) {
                    val fowText = fowSection.nextElementSibling()?.text() ?: ""
                    val fowItems = fowText.split("),")
                    fowItems.forEachIndexed { idx, item ->
                        val parts = item.trim().split(" (")
                        if (parts.size >= 2) {
                            val scoreParts = parts[0].split("-")
                            val detailParts = parts[1].replace(")", "").split(", ")
                            if (scoreParts.size >= 2 && detailParts.size >= 2) {
                                fows.add(FallOfWicket(
                                    score = parts[0],
                                    overs = detailParts[1],
                                    batsman = detailParts[0],
                                    number = idx + 1
                                ))
                            }
                        }
                    }
                }

                val partnerships = mutableListOf<PartnershipStats>()
                content.select("div:contains(Partnerships)").first()

                inningsList.add(Innings(teamName, score, overs, batsmen, bowlers, fows, partnerships, "", ""))
            }

            crumb("Successfully fetched scorecard",
                data = mapOf("matchId" to matchId, "inningsCount" to inningsList.size))

            Scorecard(inningsList)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching scorecard", e)
            crumb("Error fetching scorecard", SentryLevel.ERROR,
                mapOf("matchId" to matchId, "url" to url, "error" to (e.message ?: "Unknown error")))
            Sentry.captureException(e)
            Scorecard(emptyList())
        }
    }
}
