package com.tanush.bouncer.network

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tanush.bouncer.models.DrsReviewEvent
import com.tanush.bouncer.models.ImpactSubEvent
import com.tanush.bouncer.models.MatchFlowData
import com.tanush.bouncer.models.PowerplayEvent
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

class CricInfoScraper {

    companion object {
        private const val TAG = "CricInfoScraper"
        private const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        // Circuit breaker thresholds
        private const val CIRCUIT_BREAKER_FAILURE_THRESHOLD = 3
        private const val CIRCUIT_BREAKER_RESET_MS = 120_000L // 2 minutes cool-down
    }

    private val matchIdMap = mutableMapOf<String, String>() // cricbuzzId -> cricinfoId
    // Cached availability with timestamp so we re-check periodically (commentary can come live mid-match).
    private val commentaryAvailabilityCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private val commentaryCacheTtlMs = 5 * 60_000L // 5 min

    // ── Circuit breaker state ────────────────────────────────────────────
    private val consecutiveFailures = AtomicInteger(0)
    private val circuitOpenUntil = AtomicLong(0L)
    @Volatile private var lastReportedToSentry = 0L // only report once per circuit-open window

    /**
     * Returns true when the circuit is open (too many recent failures).
     * Callers should skip the network call entirely and return a cached /
     * empty result instead of hammering a server that's rejecting us.
     *
     * When the cool-down expires, this transitions to "half-open": we also
     * reset the failure counter so a single new failure doesn't immediately
     * re-trip the breaker without giving the source a real second chance.
     */
    private fun isCircuitOpen(): Boolean {
        val now = System.currentTimeMillis()
        val openUntil = circuitOpenUntil.get()
        if (now < openUntil) return true
        // Half-open: clear timer atomically so this block only fires once per cycle,
        // then reset failure counter so a single new failure doesn't immediately re-trip.
        if (openUntil != 0L && circuitOpenUntil.compareAndSet(openUntil, 0L)) {
            consecutiveFailures.set(0)
        }
        return false
    }

    /** Record a successful call — reset the failure counter. */
    private fun recordSuccess() {
        consecutiveFailures.set(0)
        circuitOpenUntil.set(0)
    }

    /**
     * Record a failure. If failures exceed the threshold, open the circuit
     * for [CIRCUIT_BREAKER_RESET_MS] and report a single Sentry event.
     */
    private fun recordFailure(e: Exception) {
        val count = consecutiveFailures.incrementAndGet()
        if (count < CIRCUIT_BREAKER_FAILURE_THRESHOLD) return

        val now = System.currentTimeMillis()
        circuitOpenUntil.set(now + CIRCUIT_BREAKER_RESET_MS)

        // Report to Sentry at most once per circuit-open window
        if (now - lastReportedToSentry <= CIRCUIT_BREAKER_RESET_MS) return
        lastReportedToSentry = now
        Sentry.captureException(e)
        addBreadcrumb(
            category = "network.cricinfo",
            message = "Circuit breaker OPEN after $count consecutive failures — pausing requests for ${CIRCUIT_BREAKER_RESET_MS / 1000}s",
            level = SentryLevel.WARNING,
            data = mapOf("failures" to count, "cooldownMs" to CIRCUIT_BREAKER_RESET_MS),
        )
        Log.w(TAG, "Circuit breaker OPEN — $count consecutive failures, pausing for ${CIRCUIT_BREAKER_RESET_MS / 1000}s")
    }

    private fun addBreadcrumb(
        category: String,
        message: String,
        level: SentryLevel,
        data: Map<String, Any?> = emptyMap(),
    ) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.category = category
            this.message = message
            this.level = level
            data.forEach { (k, v) -> if (v != null) setData(k, v) }
        })
    }

    private fun connect(url: String) = Jsoup.connect(url)
        .userAgent(USER_AGENT)
        .header("Referer", "https://www.espncricinfo.com/")
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("Cache-Control", "max-age=0")
        .header("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
        .header("Sec-Ch-Ua-Mobile", "?0")
        .header("Sec-Ch-Ua-Platform", "\"macOS\"")
        .header("Sec-Fetch-Dest", "document")
        .header("Sec-Fetch-Mode", "navigate")
        .header("Sec-Fetch-Site", "none")
        .header("Sec-Fetch-User", "?1")
        .header("Upgrade-Insecure-Requests", "1")
        .timeout(15000)

    fun mapMatchIds(cricbuzzId: String, cricinfoId: String) {
        matchIdMap["cricbuzz_$cricbuzzId"] = "cricinfo_$cricinfoId"
        Log.d(TAG, "Mapped cricbuzz_$cricbuzzId to cricinfo_$cricinfoId")

        addBreadcrumb(
            category = "data.cricinfo",
            message = "Mapped match IDs",
            level = SentryLevel.INFO,
            data = mapOf("cricbuzzId" to cricbuzzId, "cricinfoId" to cricinfoId),
        )
    }

    fun getMappedCricinfoId(cricbuzzId: String): String? {
        return matchIdMap["cricbuzz_$cricbuzzId"]
    }

    suspend fun isCommentaryAvailable(matchId: String, url: String): Boolean = withContext(Dispatchers.IO) {
        if (isCircuitOpen()) {
            Log.d(TAG, "Circuit breaker OPEN — skipping commentary check for matchId=$matchId")
            return@withContext commentaryAvailabilityCache[matchId]?.first ?: false
        }

        addBreadcrumb(
            category = "network.cricinfo",
            message = "Checking commentary availability",
            level = SentryLevel.INFO,
            data = mapOf("matchId" to matchId, "url" to url),
        )

        cachedAvailability(matchId)?.let { return@withContext it }

        try {
            val doc = connect(url).get()
            recordSuccess()

            val commentaryExists = detectCommentaryPresence(doc)
            commentaryAvailabilityCache[matchId] = commentaryExists to System.currentTimeMillis()

            addBreadcrumb(
                category = "network.cricinfo",
                message = "Commentary availability checked",
                level = SentryLevel.INFO,
                data = mapOf("matchId" to matchId, "available" to commentaryExists),
            )
            commentaryExists
        } catch (e: Exception) {
            Log.e(TAG, "Error checking commentary availability for matchId=$matchId: ${e.message}")
            // recordFailure captures to Sentry at the circuit-breaker threshold (throttled).
            // Do NOT call captureException again here — that causes double-reporting and
            // bypasses the per-window throttle, producing bursts of identical events.
            recordFailure(e)
            addBreadcrumb(
                category = "network.cricinfo",
                message = "Error checking commentary availability",
                level = SentryLevel.ERROR,
                data = mapOf("matchId" to matchId, "error" to (e.message ?: "Unknown error")),
            )
            false
        }
    }

    /**
     * Honor cache only within TTL — commentary can come live mid-match, so a stale
     * "false" must eventually re-check.
     */
    private fun cachedAvailability(matchId: String): Boolean? {
        val (cached, ts) = commentaryAvailabilityCache[matchId] ?: return null
        return if (System.currentTimeMillis() - ts < commentaryCacheTtlMs) cached else null
    }

    /** Modern Cricinfo Design System (DS) selectors. */
    private fun detectCommentaryPresence(doc: Document): Boolean =
        doc.select("div[class*='ds-border-line']").isNotEmpty() ||
            doc.select("div[class*='commentary']").isNotEmpty() ||
            doc.select("div.ds-text-tight-l").isNotEmpty()

    /**
     * Robust Commentary Extraction for Cricinfo.
     * Uses multiple selectors and attempts to extract ball-by-ball details.
     */
    suspend fun fetchLiveCommentary(matchUrl: String): List<String> = withContext(Dispatchers.IO) {
        if (isCircuitOpen()) {
            Log.d(TAG, "Circuit breaker OPEN — skipping commentary fetch for: $matchUrl")
            return@withContext emptyList()
        }

        addBreadcrumb(
            category = "network.cricinfo",
            message = "Fetching live commentary",
            level = SentryLevel.INFO,
            data = mapOf("url" to matchUrl),
        )

        val commentaryUrl = if (matchUrl.contains("/commentary")) matchUrl else "$matchUrl/commentary"

        try {
            val doc = connect(commentaryUrl).get()
            recordSuccess()

            val fromJson = parseCommentaryFromNextData(doc, commentaryUrl)
            if (fromJson.isNotEmpty()) {
                addBreadcrumb(
                    category = "network.cricinfo",
                    message = "Successfully fetched commentary from __NEXT_DATA__",
                    level = SentryLevel.INFO,
                    data = mapOf("linesCount" to fromJson.size),
                )
                return@withContext fromJson
            }

            val fromSelectors = parseCommentaryFromSelectors(doc)
            Log.d(TAG, "Fetched ${fromSelectors.size} commentary lines from Cricinfo")
            addBreadcrumb(
                category = "network.cricinfo",
                message = "Successfully fetched commentary",
                level = SentryLevel.INFO,
                data = mapOf("linesCount" to fromSelectors.size),
            )
            fromSelectors
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Cricinfo commentary from URL: $matchUrl: ${e.message}")
            // recordFailure captures to Sentry at the circuit-breaker threshold (throttled).
            // Do NOT call captureException again here — that causes double-reporting and
            // bypasses the per-window throttle, producing bursts of identical events.
            recordFailure(e)
            addBreadcrumb(
                category = "network.cricinfo",
                message = "Error fetching commentary",
                level = SentryLevel.ERROR,
                data = mapOf("url" to matchUrl, "error" to (e.message ?: "Unknown error")),
            )
            emptyList()
        }
    }

    /** Try to find the __NEXT_DATA__ JSON block for 100% accuracy. */
    private fun parseCommentaryFromNextData(doc: Document, commentaryUrl: String): List<String> {
        val nextData = doc.select("script#__NEXT_DATA__").first()?.data() ?: return emptyList()
        val lines = mutableListOf<String>()
        try {
            val json = JsonParser.parseString(nextData).asJsonObject
            val items = json.getAsJsonObject("props")
                ?.getAsJsonObject("pageProps")
                ?.getAsJsonObject("data")
                ?.getAsJsonObject("matchCommentary")
                ?.getAsJsonArray("items")

            items?.forEach { item ->
                extractCommentaryLine(item.asJsonObject)?.let { lines.add(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse __NEXT_DATA__ for commentary, falling back to selectors")
            Sentry.captureException(e)
            addBreadcrumb(
                category = "data.parse",
                message = "Failed to parse __NEXT_DATA__ JSON",
                level = SentryLevel.WARNING,
                data = mapOf("url" to commentaryUrl),
            )
        }
        return lines
    }

    private fun extractCommentaryLine(itemObj: JsonObject): String? {
        val commText = itemObj.get("commentText")?.asString ?: return null
        val overNumber = itemObj.get("overNumber")?.asString
        return if (overNumber != null) "$overNumber $commText" else commText
    }

    /**
     * Fallback path: scrape DS selectors, then generic class selectors if those are empty.
     */
    private fun parseCommentaryFromSelectors(doc: Document): List<String> {
        val lines = mutableListOf<String>()

        // 2. Fallback: Modern Design System (DS) Selectors
        val items = doc.select("div.ds-flex.ds-flex-row.ds-items-start")
        for (item in items) {
            val over = item.select("span.ds-text-tight-m.ds-font-bold").first()?.text()
            val text = item.select("div.ds-text-tight-l.ds-flex.ds-px-3.ds-py-2").first()?.text()
                ?: item.select("div.ci-html-content").first()?.text()
            if (text != null) {
                lines.add(if (over != null) "$over $text" else text)
            }
        }

        // 3. Fallback: Generic class selectors
        if (lines.isEmpty()) {
            val genericLines = doc.select("div[class*='ds-text-tight-l'][class*='ds-flex']")
            for (line in genericLines) {
                val t = line.text().trim()
                if (t.length > 10) lines.add(t)
            }
        }
        return lines
    }

    suspend fun findClosestLiveMatchUrlAndId(matchName: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (isCircuitOpen()) {
            Log.d(TAG, "Circuit breaker OPEN — skipping Cricinfo lookup for: $matchName")
            addBreadcrumb(
                category = "network.cricinfo",
                message = "Skipped lookup (circuit breaker open)",
                level = SentryLevel.DEBUG,
                data = mapOf("matchName" to matchName),
            )
            return@withContext null
        }

        addBreadcrumb(
            category = "network.cricinfo",
            message = "Finding closest live match",
            level = SentryLevel.INFO,
            data = mapOf("matchName" to matchName),
        )

        val liveUrl = "https://www.espncricinfo.com/live-cricket-score"

        try {
            val doc = connect(liveUrl).get()
            recordSuccess() // Reset circuit breaker on successful fetch

            val teamNames = extractTeamNames(matchName)
            if (teamNames.size < 2) {
                Log.d(TAG, "Could not extract team names from: $matchName")
                return@withContext null
            }

            Log.d(TAG, "Searching Cricinfo for match between: ${teamNames[0]} vs ${teamNames[1]}")

            val candidates = collectCandidates(doc, teamNames)
            val best = candidates.minByOrNull { it.score }
            val matched = best?.takeIf { it.score <= 120 }?.let { c ->
                extractCricinfoMatchId(c.href)?.let { id -> c to id }
            }

            if (matched != null) {
                val (c, matchId) = matched
                Log.d(TAG, "Matched Cricinfo: ${c.title} (ID: $matchId)")
                addBreadcrumb(
                    category = "network.cricinfo",
                    message = "Found matching live match",
                    level = SentryLevel.INFO,
                    data = mapOf("matchId" to matchId, "url" to c.href),
                )
                return@withContext Pair(c.href, matchId)
            }

            addBreadcrumb(
                category = "network.cricinfo",
                message = "No matching live match found",
                level = SentryLevel.INFO,
                data = mapOf("matchName" to matchName),
            )
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding match: ${e.message}")
            if (e is CancellationException) throw e
            // recordFailure captures to Sentry at the circuit-breaker threshold (throttled).
            // Do NOT call captureException again here — that causes double-reporting and
            // bypasses the per-window throttle, producing bursts of identical events.
            recordFailure(e)
            addBreadcrumb(
                category = "network.cricinfo",
                message = "Error finding match",
                level = SentryLevel.ERROR,
                data = mapOf(
                    "matchName" to matchName,
                    "error" to (e.message ?: "Unknown error"),
                    "consecutiveFailures" to consecutiveFailures.get(),
                ),
            )
            null
        }
    }

    private fun collectCandidates(doc: Document, teamNames: List<String>): List<Candidate> {
        val matchLinks = doc.select("a.ds-no-tap-higlight")
        val candidates = mutableListOf<Candidate>()
        for (link in matchLinks) {
            val title = link.text().lowercase()
            val href = link.attr("href")
            val score = scoreCandidate(title, teamNames)
            if (score < 40) continue
            val fullUrl = if (href.startsWith("http")) href else "https://www.espncricinfo.com$href"
            candidates.add(Candidate(title, "", fullUrl, 200 - score))
        }
        return candidates
    }

    private fun scoreCandidate(title: String, teamNames: List<String>): Int {
        var score = 0
        if (title.contains(teamNames[0])) score += 50
        if (title.contains(teamNames[1])) score += 50
        if (score >= 100) return score
        if (fuzzyTeamMatch(teamNames[0], title)) score += 40
        if (fuzzyTeamMatch(teamNames[1], title)) score += 40
        return score
    }

    /**
     * Stricter fuzzy match: require a substantial prefix overlap, not just
     * 3 characters. "Ind" matched "India", "Indian", "Indies", "Indiana" before.
     * Now we require >=5 chars or a whole-word boundary check.
     */
    private fun fuzzyTeamMatch(team: String, text: String): Boolean {
        if (team.length < 4) return false
        val needle = team.take(minOf(team.length, 6))
        // Whole-word match preferred — surround by word boundaries.
        val wordBoundary = Regex("\\b${Regex.escape(needle)}\\b", RegexOption.IGNORE_CASE)
        return wordBoundary.containsMatchIn(text)
    }

    private data class Candidate(val title: String, val normalized: String, val href: String, val score: Int)

    private fun extractTeamNames(title: String): List<String> {
        val lower = title.lowercase()
        return when {
            lower.contains(" vs ") -> lower.split(" vs ").map { it.trim() }
            lower.contains(" v ") -> lower.split(" v ").map { it.trim() }
            else -> emptyList()
        }
    }

    private fun extractCricinfoMatchId(url: String): String? {
        val matcher = Pattern.compile("(\\d+)(?=/live-cricket-score)").matcher(url)
        if (matcher.find()) return matcher.group(1)

        return url.split("/").asReversed()
            .firstOrNull { it.length >= 5 && it.all { ch -> ch.isDigit() } }
    }

    // ── Match Flow (DRS reviews, powerplay summaries, impact subs) ──────────

    suspend fun fetchMatchFlow(matchUrl: String): MatchFlowData = withContext(Dispatchers.IO) {
        if (isCircuitOpen()) return@withContext MatchFlowData()

        val scorecardUrl = buildScorecardUrl(matchUrl)
        addBreadcrumb(
            category = "network.cricinfo",
            message = "Fetching match flow",
            level = SentryLevel.INFO,
            data = mapOf("url" to scorecardUrl),
        )

        try {
            val doc = connect(scorecardUrl).get()
            recordSuccess()
            val flow = parseMatchFlow(doc)
            addBreadcrumb(
                category = "network.cricinfo",
                message = "Match flow fetched",
                level = SentryLevel.INFO,
                data = mapOf("drs" to flow.drsReviews.size, "pp" to flow.powerplays.size, "subs" to flow.impactSubs.size),
            )
            flow
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            recordFailure(e)
            addBreadcrumb(
                category = "network.cricinfo",
                message = "Error fetching match flow",
                level = SentryLevel.ERROR,
                data = mapOf("url" to scorecardUrl, "error" to (e.message ?: "")),
            )
            MatchFlowData()
        }
    }

    private fun buildScorecardUrl(matchUrl: String): String =
        matchUrl.trimEnd('/')
            .removeSuffix("/live-cricket-score")
            .removeSuffix("/commentary") + "/full-scorecard"

    private fun parseMatchFlow(doc: Document): MatchFlowData {
        val allText = doc.select("p, div.ds-text-tight-s, span.ds-text-tight-s, div[class*='match-flow']")
            .map { it.text().trim() }
            .filter { it.length > 20 }

        val drsReviews = mutableListOf<DrsReviewEvent>()
        val powerplays = mutableListOf<PowerplayEvent>()
        val impactSubs = mutableListOf<ImpactSubEvent>()
        val milestones = mutableListOf<String>()

        for (line in allText) {
            parseDrsLine(line)?.let { drsReviews.add(it) }
            parsePowerplayLine(line)?.let { powerplays.add(it) }
            parseImpactSubLine(line)?.let { impactSubs.add(it) }
            parseMilestoneLine(line)?.let { milestones.add(it) }
        }

        return MatchFlowData(
            drsReviews = drsReviews,
            powerplays = powerplays,
            impactSubs = impactSubs,
            milestones = milestones,
        )
    }

    private val DRS_REGEX = Regex(
        """Over ([\d.]+): Review by (.+?) \((Batting|Bowling)\), Decision Challenged - (.+?), Umpire - (.+?), Batter - (.+?) \((Upheld|Struck down)\)""",
        RegexOption.IGNORE_CASE,
    )

    private val POWERPLAY_REGEX = Regex(
        """Powerplay (\d+): Overs ([\d.]+[–\-][\d.]+).*?(\d+) runs, (\d+) wicket""",
        RegexOption.IGNORE_CASE,
    )

    private val IMPACT_SUB_REGEX = Regex(
        """(.+?) Impact Player Sub: (.+?) in for (.+?) \(.+?, ([\d.]+) ov\)""",
        RegexOption.IGNORE_CASE,
    )

    private val MILESTONE_REGEX = Regex("""([\w .]+): (\d+) off (\d+) balls \(\d+ x 4""")

    private fun parseDrsLine(line: String): DrsReviewEvent? {
        val m = DRS_REGEX.find(line) ?: return null
        return DrsReviewEvent(
            over = m.groupValues[1],
            teamName = m.groupValues[2].trim(),
            reviewType = m.groupValues[3],
            decision = m.groupValues[4].trim(),
            umpireName = m.groupValues[5].trim(),
            batterName = m.groupValues[6].trim(),
            isUpheld = m.groupValues[7].equals("Upheld", ignoreCase = true),
        )
    }

    private fun parsePowerplayLine(line: String): PowerplayEvent? {
        val m = POWERPLAY_REGEX.find(line) ?: return null
        return PowerplayEvent(
            phase = m.groupValues[1].toIntOrNull() ?: 1,
            overs = m.groupValues[2],
            runs = m.groupValues[3].toIntOrNull() ?: 0,
            wickets = m.groupValues[4].toIntOrNull() ?: 0,
        )
    }

    private fun parseImpactSubLine(line: String): ImpactSubEvent? {
        val m = IMPACT_SUB_REGEX.find(line) ?: return null
        return ImpactSubEvent(
            team = m.groupValues[1].trim(),
            playerIn = m.groupValues[2].trim(),
            playerOut = m.groupValues[3].trim(),
            over = m.groupValues[4],
        )
    }

    private fun parseMilestoneLine(line: String): String? {
        val m = MILESTONE_REGEX.find(line) ?: return null
        return m.value.trim()
    }
}
