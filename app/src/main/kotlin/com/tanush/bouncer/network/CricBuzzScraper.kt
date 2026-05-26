package com.tanush.bouncer.network

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tanush.bouncer.data.AppException
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.models.Scorecard
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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
    val jsonElement = parseLenientJson(scriptContent) ?: return null
    if (!jsonElement.isJsonArray) return null
    val array = jsonElement.asJsonArray
    if (array.size() <= 1 || !array[1].isJsonPrimitive || !array[1].asJsonPrimitive.isString) return null
    return normalizeNextFlightPayload(array[1].asString)
}

internal fun classifyHttpException(e: HttpStatusException): AppException.ApiException =
    AppException.ApiException(
        statusCode = e.statusCode,
        message = "HTTP ${e.statusCode} from ${e.url}",
        cause = e
    )

private fun parseLenientJson(payload: String): JsonElement? = try {
    val reader = com.google.gson.stream.JsonReader(java.io.StringReader(payload))
    reader.isLenient = true
    JsonParser.parseReader(reader)
} catch (_: Exception) {
    null
}

private fun findMatchingParenEnd(html: String, openParenIdx: Int): Int {
    var parenCount = 0
    var inString = false
    var escape = false
    for (i in openParenIdx until html.length) {
        val char = html[i]
        if (escape) { escape = false; continue }
        if (char == '\\') { escape = true; continue }
        if (char == '"') { inString = !inString; continue }
        if (inString) continue
        when (char) {
            '(' -> parenCount++
            ')' -> {
                parenCount--
                if (parenCount == 0) return i
            }
        }
    }
    return -1
}

private inline fun forEachNextFlightPayload(html: String, block: (JsonElement) -> Unit) {
    val startMarker = "self.__next_f.push("
    var startIndex = 0
    while (true) {
        val idx = html.indexOf(startMarker, startIndex)
        if (idx == -1) break
        val openParenIdx = idx + startMarker.length - 1
        val endIdx = findMatchingParenEnd(html, openParenIdx)
        if (endIdx == -1) {
            startIndex = idx + 1
            continue
        }
        val scriptContent = html.substring(openParenIdx + 1, endIdx)
        unwrapNextFlightPayload(scriptContent)
            ?.let(::parseLenientJson)
            ?.let(block)
        startIndex = endIdx + 1
    }
}

internal inline fun crumb(
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
        private const val LIST_TIMEOUT_MS = 10000
        private const val DETAILS_TIMEOUT_MS = 10000
        private const val COMMENTARY_TIMEOUT_MS = 12000
        private const val SCORECARD_TIMEOUT_MS = 15000

        private val consecutiveEmptyParses = java.util.concurrent.atomic.AtomicInteger(0)
    }

    suspend fun fetchLiveMatches(url: String, matchState: String): List<CricketMatch> = withContext(Dispatchers.IO) {
        crumb("Fetching live matches", data = mapOf("url" to url, "matchState" to matchState))

        var lastException: Exception? = null
        var backoff = INITIAL_BACKOFF_MS

        for (attempt in 1..MAX_RETRIES) {
            try {
                val doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(LIST_TIMEOUT_MS).get()
                val matches = parseNextJsData(doc.html())
                val filteredMatches = filterMatches(matches, matchState)
                Log.d(TAG, "Found ${matches.size} total matches, filtered down to ${filteredMatches.size} for state: $matchState")

                checkForParseDrift(matches, url, matchState)

                crumb("Successfully fetched live matches",
                    data = mapOf("totalMatches" to matches.size, "filteredMatches" to filteredMatches.size))
                return@withContext filteredMatches
            } catch (e: UnknownHostException) {
                Log.e(TAG, "No internet connection fetching $url", e)
                crumb("No internet connection", SentryLevel.WARNING, mapOf("url" to url))
                return@withContext emptyList()
            } catch (e: HttpStatusException) {
                val classified = classifyHttpException(e)
                crumb("HTTP ${e.statusCode} — no retry", SentryLevel.WARNING,
                    mapOf("url" to url, "status" to e.statusCode))
                Sentry.captureException(classified)
                throw classified
            } catch (e: SocketTimeoutException) {
                lastException = e
                Log.w(TAG, "Timeout fetching $url (attempt $attempt/$MAX_RETRIES)", e)
                crumb("Socket timeout fetching matches", SentryLevel.WARNING,
                    mapOf("url" to url, "attempt" to attempt))
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Error fetching matches from $url (attempt $attempt/$MAX_RETRIES)", e)
                crumb("Error fetching matches", SentryLevel.ERROR,
                    mapOf("url" to url, "attempt" to attempt, "error" to (e.message ?: "Unknown error")))
            }

            backoff = waitForRetry(attempt, backoff)
        }

        Log.e(TAG, "All $MAX_RETRIES attempts failed for $url", lastException)
        lastException?.let { Sentry.captureException(it) }
        emptyList()
    }

    private fun checkForParseDrift(matches: List<CricketMatch>, url: String, matchState: String) {
        if (matches.isNotEmpty()) {
            consecutiveEmptyParses.set(0)
            return
        }
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
    }

    private suspend fun waitForRetry(attempt: Int, backoff: Long): Long {
        if (attempt < MAX_RETRIES) {
            Log.d(TAG, "Retrying in ${backoff}ms...")
            delay(backoff)
            return backoff * 2
        }
        return backoff
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

    private data class MatchScoreFields(
        val team1Score: String,
        val team2Score: String,
        val team1Overs: String,
        val team2Overs: String,
        val team1IsBatting: Boolean,
        val team2IsBatting: Boolean
    ) {
        companion object {
            val EMPTY = MatchScoreFields("", "", "", "", false, false)
        }
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

            val venue = parseVenue(info)
            val matchType = mapMatchFormat(matchFormat).ifBlank {
                determineMatchType("$team1 vs $team2 $matchDesc")
            }
            val competitionType = classifyCompetitionType(seriesName, matchDesc)

            val scoreFields = parseMatchScoreFields(obj, info)

            val teams = listOf(
                CricketMatch.Team(team1, scoreFields.team1Score, "", scoreFields.team1IsBatting,
                    TeamLogoManager.getLogoUrl(team1), TeamAcronymManager.getAcronym(team1), scoreFields.team1Overs),
                CricketMatch.Team(team2, scoreFields.team2Score, "", scoreFields.team2IsBatting,
                    TeamLogoManager.getLogoUrl(team2), TeamAcronymManager.getAcronym(team2), scoreFields.team2Overs)
            )

            val matchScore = buildMatchScore(scoreFields)

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

    private fun parseVenue(info: JsonObject): String = try {
        val venueInfo = info.getAsJsonObject("venueInfo")
        val ground = venueInfo?.get("ground")?.asString ?: ""
        val city = venueInfo?.get("city")?.asString ?: ""
        if (ground.isNotBlank() && city.isNotBlank()) "$ground, $city"
        else ground.ifBlank { city }
    } catch (e: Exception) {
        Sentry.captureException(e)
        ""
    }

    private fun parseMatchScoreFields(obj: JsonObject, info: JsonObject): MatchScoreFields {
        if (!obj.has("matchScore")) return MatchScoreFields.EMPTY
        val scoreObj = obj.getAsJsonObject("matchScore")
        val s1 = latestInnings(scoreObj, "team1Score")
        val s2 = latestInnings(scoreObj, "team2Score")
        val battingId = scoreObj.get("battingTeamId")?.asString
        val team1IsBatting = battingId != null && info.getAsJsonObject("team1").get("teamId")?.asString == battingId
        val team2IsBatting = battingId != null && info.getAsJsonObject("team2").get("teamId")?.asString == battingId
        return MatchScoreFields(
            team1Score = s1?.let { "${it.get("runs")?.asString ?: ""}/${it.get("wickets")?.asString ?: "0"}" } ?: "",
            team2Score = s2?.let { "${it.get("runs")?.asString ?: ""}/${it.get("wickets")?.asString ?: "0"}" } ?: "",
            team1Overs = s1?.get("overs")?.asString ?: "",
            team2Overs = s2?.get("overs")?.asString ?: "",
            team1IsBatting = team1IsBatting,
            team2IsBatting = team2IsBatting
        )
    }

    private fun latestInnings(scoreObj: JsonObject, key: String): JsonObject? {
        if (!scoreObj.has(key)) return null
        val t = scoreObj.getAsJsonObject(key)
        return when {
            t.has("inngs2") -> t.getAsJsonObject("inngs2")
            t.has("inngs1") -> t.getAsJsonObject("inngs1")
            else -> null
        }
    }

    private fun buildMatchScore(fields: MatchScoreFields): CricketMatch.MatchScore {
        val hasScore = fields.team1Score.isNotBlank() || fields.team2Score.isNotBlank()
        val currentInnings = when {
            !hasScore -> ""
            fields.team1IsBatting -> "1"
            else -> "2"
        }
        val overs = when {
            !hasScore -> null
            fields.team1IsBatting -> fields.team1Overs
            else -> fields.team2Overs
        }
        return CricketMatch.MatchScore(
            currentInnings = currentInnings,
            batsmen = emptyList(),
            bowlers = emptyList(),
            overs = overs,
            currentRunRate = null,
            requiredRunRate = null,
            lastWicket = null,
            recentBalls = null
        )
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
        return when {
            matchesInternational(combined) -> "International"
            matchesLeague(combined) -> "League"
            else -> "Domestic"
        }
    }

    private fun matchesInternational(combined: String): Boolean {
        val keywords = listOf(
            "tour of", "world cup", "champions trophy", "asia cup",
            "icc ", "tri-series", "tri series", "bilateral",
            "\\d+(?:st|nd|rd|th) test", "\\d+(?:st|nd|rd|th) odi", "\\d+(?:st|nd|rd|th) t20i"
        )
        return keywords.any { keyword ->
            if (keyword.contains("\\d")) Regex(keyword).containsMatchIn(combined)
            else combined.contains(keyword)
        }
    }

    private fun matchesLeague(combined: String): Boolean {
        val keywords = listOf(
            "ipl", "bbl", "big bash", "psl", "cpl", "caribbean premier",
            "sa20", "lpl", "bpl", "ilt20", "mlc", "major league cricket",
            "the hundred", "hundred", "vitality blast", "t20 blast",
            "syed mushtaq ali", "vijay hazare"
        )
        return keywords.any { combined.contains(it) }
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
                val doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(DETAILS_TIMEOUT_MS).get()
                return@withContext buildMatchDetails(doc)
            } catch (e: UnknownHostException) {
                Log.e(TAG, "No internet fetching match details for $matchId", e)
                crumb("No internet fetching match details", SentryLevel.ERROR, mapOf("matchId" to matchId))
                Sentry.captureException(e)
                return@withContext emptyMatchDetails()
            } catch (e: HttpStatusException) {
                val classified = classifyHttpException(e)
                crumb("HTTP ${e.statusCode} fetching match details — no retry", SentryLevel.WARNING,
                    mapOf("matchId" to matchId, "status" to e.statusCode))
                Sentry.captureException(classified)
                throw classified
            } catch (e: SocketTimeoutException) {
                lastException = e
                Log.w(TAG, "Timeout fetching match details for $matchId (attempt $attempt/$MAX_RETRIES)")
                crumb("Timeout fetching match details", SentryLevel.WARNING,
                    mapOf("matchId" to matchId, "attempt" to attempt))
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Error fetching match details for $matchId (attempt $attempt/$MAX_RETRIES)", e)
                crumb("Error fetching match details", SentryLevel.ERROR,
                    mapOf("matchId" to matchId, "attempt" to attempt, "error" to (e.message ?: "Unknown error")))
            }

            backoff = waitForRetry(attempt, backoff)
        }

        Log.e(TAG, "All $MAX_RETRIES attempts failed for match details $matchId", lastException)
        lastException?.let { Sentry.captureException(it) }
        emptyMatchDetails()
    }

    private fun emptyMatchDetails() = MatchDetails(emptyList(), emptyList(), emptyList(), null, null, null)

    private fun buildMatchDetails(doc: Document): MatchDetails {
        val batsmen = parseBatsmenFromDoc(doc)
        val bowler = parseBowlerFromDoc(doc)
        val cssRecentBalls = parseRecentBallsFromDoc(doc)
        val recentBalls = cssRecentBalls.ifEmpty { parseRecentBallsFromNextJs(doc.html()) }

        val score = doc.select("span.cb-font-20.text-bold").first()?.text()
            ?: doc.select("div.cb-min-bat-rw span").first()?.text()

        val (team1Score, team1Overs, team2Score, team2Overs) = parseMiniScoreRowsFromDoc(doc)
        val (currentRunRate, requiredRunRate) = parseRunRatesFromDoc(doc)

        return MatchDetails(
            recentBalls = recentBalls,
            overNumbers = emptyList(),
            batsmen = batsmen,
            bowler = bowler,
            recentBallsSummary = null,
            score = score,
            team1Score = team1Score,
            team2Score = team2Score,
            team1Overs = team1Overs,
            team2Overs = team2Overs,
            currentRunRate = currentRunRate,
            requiredRunRate = requiredRunRate
        )
    }

    suspend fun fetchLiveCommentary(matchUrl: String): List<String> = withContext(Dispatchers.IO) {
        crumb("Fetching live commentary", data = mapOf("url" to matchUrl))

        try {
            val doc = Jsoup.connect(matchUrl).userAgent(USER_AGENT).timeout(COMMENTARY_TIMEOUT_MS).get()
            val html = doc.html()

            val nextJsLines = parseCommentaryFromNextJs(html)
            if (nextJsLines.isNotEmpty()) {
                Log.d(TAG, "Fetched ${nextJsLines.size} commentary lines from Next.js RSC data")
                crumb("Successfully fetched commentary from Next.js RSC",
                    data = mapOf("linesCount" to nextJsLines.size))
                return@withContext nextJsLines.filter { it.length > 5 }
            }

            val lines = parseCommentaryLegacyCss(doc)

            Log.d(TAG, "Fetched ${lines.size} commentary lines from legacy CSS selectors")
            crumb("Successfully fetched commentary", data = mapOf("linesCount" to lines.size))
            lines
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
        forEachNextFlightPayload(html) { data -> collectKeyedPairs(data, "commText", "timestamp", results) { it.asLong } }
        return results.sortedByDescending { it.first }.map { it.second }
    }

    private fun parseRecentBallsFromNextJs(html: String): List<String> {
        val overData = mutableListOf<Pair<Long, String>>()
        forEachNextFlightPayload(html) { data -> collectKeyedPairs(data, "overSummary", "overNumber", overData) { it.asLong } }
        if (overData.isEmpty()) return emptyList()
        val latestSummary = overData.maxByOrNull { it.first }?.second ?: return emptyList()
        return latestSummary.trim().split(" ").filter { it.isNotBlank() }
    }

    internal fun collectKeyedPairs(
        element: JsonElement,
        valueKey: String,
        orderKey: String,
        results: MutableList<Pair<Long, String>>,
        orderExtractor: (JsonElement) -> Long
    ) {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            if (obj.has(valueKey) && obj.has(orderKey)) {
                try {
                    val value = obj.get(valueKey).asString
                    val order = orderExtractor(obj.get(orderKey))
                    if (value.isNotBlank()) results.add(order to value)
                } catch (_: Exception) { }
            }
            obj.keySet().forEach { collectKeyedPairs(obj.get(it), valueKey, orderKey, results, orderExtractor) }
        } else if (element.isJsonArray) {
            element.asJsonArray.forEach { collectKeyedPairs(it, valueKey, orderKey, results, orderExtractor) }
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
            val doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(SCORECARD_TIMEOUT_MS).get()
            val inningsList = mutableListOf<Innings>()

            for (header in doc.select("div[id^=team-][id*=-innings-]")) {
                val content = doc.getElementById("scard-${header.id()}") ?: continue
                inningsList.add(parseInningsHeader(header, content))
            }

            crumb("Successfully fetched scorecard",
                data = mapOf("matchId" to matchId, "inningsCount" to inningsList.size))

            Scorecard(inningsList)
        } catch (e: HttpStatusException) {
            val classified = classifyHttpException(e)
            crumb("HTTP ${e.statusCode} fetching scorecard — no retry", SentryLevel.WARNING,
                mapOf("matchId" to matchId, "status" to e.statusCode))
            Sentry.captureException(classified)
            throw classified
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching scorecard", e)
            crumb("Error fetching scorecard", SentryLevel.ERROR,
                mapOf("matchId" to matchId, "url" to url, "error" to (e.message ?: "Unknown error")))
            Sentry.captureException(e)
            Scorecard(emptyList())
        }
    }

}
