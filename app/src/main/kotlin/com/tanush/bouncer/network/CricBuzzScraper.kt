package com.tanush.bouncer.network

import com.tanush.bouncer.services.WORKER_MAX_RETRIES
import com.tanush.bouncer.services.backoffDelayMs
import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tanush.bouncer.data.AppException
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.MatchStatusClassifier
import com.tanush.bouncer.models.StatusSignal
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
import com.tanush.bouncer.utils.BallMetric
import com.tanush.bouncer.utils.ballMetricOf
import com.tanush.bouncer.utils.startsWithBallLabel
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * One Cricbuzz commentary entry, with the ball number the parser used to discard.
 *
 * [ballMetric] is null for anything that is not a delivery — session notes, bowler changes, result
 * lines — which Cricbuzz marks with `"$undefined"`. Null is load-bearing: it routes the item to the
 * event path instead of borrowing a neighbouring ball's number.
 */
internal data class ScrapedCommentaryItem(
    val timestamp: Long,
    val text: String,
    val ballMetric: BallMetric?,
    val inningsId: Int = 0
)

/**
 * The commentary line as it is handed downstream, prefixed with its ball number.
 *
 * Everything past the scraper finds the ball number by reading the head of the line
 * ([ballNumberOf]), so the prefix is what makes a real ball number reach the AI prompt.
 */
internal fun ScrapedCommentaryItem.prefixedText(): String {
    val metric = ballMetric ?: return text
    return if (startsWithBallLabel(text)) text else "${metric.label}: $text"
}

/**
 * RSC back-references — `"$27"`, `"$2a"` — appear where React hoisted the string elsewhere in the
 * flight stream. They are not prose and were previously excluded only by an incidental length
 * filter downstream.
 */
private val RSC_BACK_REFERENCE = """^\$[0-9a-f]+$""".toRegex()

/** Reads a primitive field as its source literal, or null when absent or not a primitive. */
private fun JsonObject.primitiveStringOrNull(key: String): String? {
    val element = get(key) ?: return null
    if (!element.isJsonPrimitive) return null
    return try { element.asString } catch (_: Exception) { null }
}

/**
 * The commentary entry this node represents, or null when it is not one.
 *
 * `ballMetric` is read as its source literal rather than through [Double] — Gson keeps the original
 * text on a lazily parsed number, so "48.6" survives exactly.
 */
private fun commentaryItemOf(obj: JsonObject): ScrapedCommentaryItem? {
    if (!obj.has("commText") || !obj.has("timestamp")) return null
    return try {
        val text = obj.get("commText").asString
        if (text.isBlank() || RSC_BACK_REFERENCE.matches(text)) return null
        ScrapedCommentaryItem(
            timestamp = obj.get("timestamp").asLong,
            text = text,
            ballMetric = ballMetricOf(obj.primitiveStringOrNull("ballMetric")),
            inningsId = obj.primitiveStringOrNull("inningsId")?.toIntOrNull() ?: 0
        )
    } catch (_: Exception) {
        null
    }
}

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
        private const val MAX_RETRIES = WORKER_MAX_RETRIES
        private const val DRIFT_ALARM_THRESHOLD = 1
        private const val LIST_TIMEOUT_MS = 10000
        private const val DETAILS_TIMEOUT_MS = 10000
        private const val COMMENTARY_TIMEOUT_MS = 12000
        private const val SCORECARD_TIMEOUT_MS = 15000

        private val consecutiveEmptyParses = java.util.concurrent.atomic.AtomicInteger(0)
    }

    suspend fun fetchLiveMatches(url: String, matchState: String): List<CricketMatch> = withContext(Dispatchers.IO) {
        crumb("Fetching live matches", data = mapOf("url" to url, "matchState" to matchState))

        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            try {
                val doc = Jsoup.connect(url).userAgent(randomUserAgent()).timeout(LIST_TIMEOUT_MS).get()
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

            waitForRetry(attempt)
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

    private suspend fun waitForRetry(attempt: Int) {
        if (attempt >= MAX_RETRIES) return
        val wait = backoffDelayMs(attempt)
        Log.d(TAG, "Retrying in ${wait}ms...")
        delay(wait)
    }

    private fun filterMatches(matches: List<CricketMatch>, state: String): List<CricketMatch> {
        return matches.filter { match ->
            val signal = MatchStatusClassifier.classify(match.info)
            when (state) {
                "Upcoming" -> signal == StatusSignal.PRE_MATCH
                "Recent" -> signal == StatusSignal.TERMINAL
                // Live stays the catch-all it has always been: an unrecognised `info` string
                // (including a blank one) surfaces in Live rather than vanishing from every tab.
                "Live" -> signal != StatusSignal.PRE_MATCH && signal != StatusSignal.TERMINAL
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
            val matchId = info.get("matchId")?.asString ?: return null
            val team1 = info.getAsJsonObject("team1")?.get("teamName")?.asString ?: return null
            val team2 = info.getAsJsonObject("team2")?.get("teamName")?.asString ?: return null
            val status = info.get("status")?.asString ?: return null
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

            val tossObj = runCatching { info.getAsJsonObject("tossResults") }.getOrNull()
            val tossWinner = tossObj?.get("tossWinnerName")?.asString?.takeIf { it.isNotBlank() }
            val tossDecision = tossObj?.get("decision")?.asString?.takeIf { it.isNotBlank() }
            val ump1 = runCatching { info.getAsJsonObject("umpire1")?.get("name")?.asString }.getOrNull()?.takeIf { it.isNotBlank() }
            val ump2 = runCatching { info.getAsJsonObject("umpire2")?.get("name")?.asString }.getOrNull()?.takeIf { it.isNotBlank() }

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
                competitionType = competitionType,
                tossWinner = tossWinner,
                tossDecision = tossDecision,
                umpire1 = ump1,
                umpire2 = ump2,
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

    // `overNumbers: List<String>` was removed here. It was never populated (always emptyList()),
    // yet sat first in the ball-number fallback chain in LiveCommentaryManager, so that branch was
    // dead code. Its intended contents were *over* numbers, one per recent ball — the debug fixture
    // filled it with listOf("17","17","18","18") — so had it ever been populated, `lastOrNull()`
    // would have labelled a delivery "18" with no ball digit. The per-line `ballMetric` is now the
    // single source; see ScrapedCommentaryItem.
    data class ScrapedMatchDetails(
        val recentBalls: List<String>,
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

    suspend fun fetchBallByBallAndBatsmen(matchId: String): ScrapedMatchDetails = withContext(Dispatchers.IO) {
        crumb("Fetching ball-by-ball and batsmen data", data = mapOf("matchId" to matchId))

        val url = CricBuzzUrls.liveScoresUrl(matchId)
        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            try {
                val doc = Jsoup.connect(url).userAgent(randomUserAgent()).timeout(DETAILS_TIMEOUT_MS).get()
                return@withContext buildMatchDetails(doc)
            } catch (e: UnknownHostException) {
                Log.w(TAG, "No internet fetching match details for $matchId")
                crumb("No internet fetching match details", SentryLevel.WARNING, mapOf("matchId" to matchId))
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

            waitForRetry(attempt)
        }

        Log.e(TAG, "All $MAX_RETRIES attempts failed for match details $matchId", lastException)
        lastException?.let { Sentry.captureException(it) }
        emptyMatchDetails()
    }

    private fun emptyMatchDetails() = ScrapedMatchDetails(emptyList(), emptyList(), null, null, null)

    private fun buildMatchDetails(doc: Document): ScrapedMatchDetails {
        val batsmen = parseBatsmenFromDoc(doc)
        val bowler = parseBowlerFromDoc(doc)
        val cssRecentBalls = parseRecentBallsFromDoc(doc)
        val recentBalls = cssRecentBalls.ifEmpty { parseRecentBallsFromNextJs(doc.html()) }

        val score = doc.select("span.cb-font-20.text-bold").first()?.text()
            ?: doc.select("div.cb-min-bat-rw span").first()?.text()

        val (team1Score, team1Overs, team2Score, team2Overs) = parseMiniScoreRowsFromDoc(doc)
        val (currentRunRate, requiredRunRate) = parseRunRatesFromDoc(doc)

        return ScrapedMatchDetails(
            recentBalls = recentBalls,
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

    suspend fun fetchLiveCommentary(matchUrl: String): List<String> =
        fetchLiveCommentaryItems(matchUrl).map { it.prefixedText() }

    /**
     * Commentary with each line's delivery identity — timestamp and innings — still attached.
     *
     * The string form throws both away, which is what forced the fuse cache key to be built from a
     * fingerprint of the snippets plus an LLM-generated recap. That recap is regenerated on every
     * poll, so the key changed every poll and the cache never hit once in 602 rows. `timestamp` is
     * the only value unique per delivery: `ballMetric` repeats across a no-ball and its re-bowl.
     *
     * Legacy CSS lines carry `timestamp = 0` because that path has no per-item metadata; callers
     * must treat a zero timestamp as "no identity" rather than as a shared one.
     */
    internal suspend fun fetchLiveCommentaryItems(matchUrl: String): List<ScrapedCommentaryItem> =
        withContext(Dispatchers.IO) {
            crumb("Fetching live commentary", data = mapOf("url" to matchUrl))

            try {
                val doc = Jsoup.connect(matchUrl).userAgent(randomUserAgent()).timeout(COMMENTARY_TIMEOUT_MS).get()
                val html = doc.html()

                val items = parseCommentaryItemsFromNextJs(html)
                if (items.isNotEmpty()) {
                    Log.d(TAG, "Fetched ${items.size} commentary lines from Next.js RSC data")
                    crumb("Successfully fetched commentary from Next.js RSC",
                        data = mapOf("linesCount" to items.size))
                    return@withContext items.filter { it.prefixedText().length > 5 }
                }

                val lines = parseCommentaryLegacyCss(doc)

                Log.d(TAG, "Fetched ${lines.size} commentary lines from legacy CSS selectors")
                crumb("Successfully fetched commentary", data = mapOf("linesCount" to lines.size))
                lines.map { ScrapedCommentaryItem(timestamp = 0L, text = it, ballMetric = null) }
            } catch (e: UnknownHostException) {
                Log.w(TAG, "No internet fetching commentary: $matchUrl")
                crumb("No internet fetching commentary", SentryLevel.WARNING, mapOf("url" to matchUrl))
                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Cricbuzz commentary", e)
                crumb("Error fetching commentary", SentryLevel.ERROR,
                    mapOf("url" to matchUrl, "error" to (e.message ?: "Unknown error")))
                Sentry.captureException(e)
                emptyList()
            }
        }

    /**
     * Commentary items with their ball numbers still attached.
     *
     * [collectKeyedPairs] keeps only the value and order keys, which threw `ballMetric` away on
     * every delivery — the whole reason no commentary line has ever carried a real ball number.
     * Newest-first, deduplicated by timestamp (unique per delivery, including across the two
     * entries a no-ball produces at the same `ballMetric`).
     */
    internal fun parseCommentaryItemsFromNextJs(html: String): List<ScrapedCommentaryItem> {
        val results = mutableListOf<ScrapedCommentaryItem>()
        forEachNextFlightPayload(html) { data -> collectCommentaryItems(data, results) }
        return results.distinctBy { it.timestamp }.sortedByDescending { it.timestamp }
    }

    /**
     * Walks the flight payload for commentary nodes, keeping `ballMetric` alongside the text.
     *
     * Kept separate from [collectKeyedPairs] rather than generalising it: that one is a generic
     * two-key collector still used for `overSummary`/`overNumber`, and widening it would push an
     * unused third key onto that call site.
     */
    internal fun collectCommentaryItems(element: JsonElement, results: MutableList<ScrapedCommentaryItem>) {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            commentaryItemOf(obj)?.let(results::add)
            obj.keySet().forEach { collectCommentaryItems(obj.get(it), results) }
        } else if (element.isJsonArray) {
            element.asJsonArray.forEach { collectCommentaryItems(it, results) }
        }
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
            val doc = Jsoup.connect(url).userAgent(randomUserAgent()).timeout(SCORECARD_TIMEOUT_MS).get()
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
            if (HttpClientProvider.isConnectivityException(e)) {
                Log.w(TAG, "No internet fetching scorecard for $matchId")
                crumb("No internet fetching scorecard", SentryLevel.WARNING, mapOf("matchId" to matchId))
            } else {
                Log.e(TAG, "Error fetching scorecard", e)
                crumb("Error fetching scorecard", SentryLevel.ERROR,
                    mapOf("matchId" to matchId, "url" to url, "error" to (e.message ?: "Unknown error")))
                Sentry.captureException(e)
            }
            throw e
        }
    }

}
