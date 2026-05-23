package com.tanush.bouncer.network

import android.util.Log
import com.google.gson.JsonParser
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
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
        if (count >= CIRCUIT_BREAKER_FAILURE_THRESHOLD) {
            val now = System.currentTimeMillis()
            circuitOpenUntil.set(now + CIRCUIT_BREAKER_RESET_MS)

            // Report to Sentry at most once per circuit-open window
            if (now - lastReportedToSentry > CIRCUIT_BREAKER_RESET_MS) {
                lastReportedToSentry = now
                Sentry.captureException(e)
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "network.cricinfo"
                    message = "Circuit breaker OPEN after $count consecutive failures — pausing requests for ${CIRCUIT_BREAKER_RESET_MS / 1000}s"
                    level = SentryLevel.WARNING
                    setData("failures", count)
                    setData("cooldownMs", CIRCUIT_BREAKER_RESET_MS)
                })
                Log.w(TAG, "Circuit breaker OPEN — $count consecutive failures, pausing for ${CIRCUIT_BREAKER_RESET_MS / 1000}s")
            }
        }
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
        
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "data.cricinfo"
            message = "Mapped match IDs"
            level = SentryLevel.INFO
            setData("cricbuzzId", cricbuzzId)
            setData("cricinfoId", cricinfoId)
        })
    }
    
    fun getMappedCricinfoId(cricbuzzId: String): String? {
        return matchIdMap["cricbuzz_$cricbuzzId"]
    }

    suspend fun isCommentaryAvailable(matchId: String, url: String): Boolean = withContext(Dispatchers.IO) {
        // ── Circuit breaker check ────────────────────────────────────────
        if (isCircuitOpen()) {
            Log.d(TAG, "Circuit breaker OPEN — skipping commentary check for matchId=$matchId")
            return@withContext commentaryAvailabilityCache[matchId]?.first ?: false
        }

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.cricinfo"
            message = "Checking commentary availability"
            level = SentryLevel.INFO
            setData("matchId", matchId)
            setData("url", url)
        })

        // Honor cache only within TTL — commentary can come live mid-match, so a stale
        // "false" must eventually re-check.
        commentaryAvailabilityCache[matchId]?.let { (cached, ts) ->
            if (System.currentTimeMillis() - ts < commentaryCacheTtlMs) {
                return@withContext cached
            }
        }

        try {
            val doc = connect(url).get()
            recordSuccess()

            // Modern Cricinfo Design System (DS) selectors
            val commentaryExists = doc.select("div[class*='ds-border-line']").isNotEmpty() ||
                                 doc.select("div[class*='commentary']").isNotEmpty() ||
                                 doc.select("div.ds-text-tight-l").isNotEmpty()

            commentaryAvailabilityCache[matchId] = commentaryExists to System.currentTimeMillis()
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.cricinfo"
                message = "Commentary availability checked"
                level = SentryLevel.INFO
                setData("matchId", matchId)
                setData("available", commentaryExists)
            })
            
            return@withContext commentaryExists
        } catch (e: Exception) {
            Log.e(TAG, "Error checking commentary availability for matchId=$matchId: ${e.message}")
            // recordFailure captures to Sentry at the circuit-breaker threshold (throttled).
            // Do NOT call captureException again here — that causes double-reporting and
            // bypasses the per-window throttle, producing bursts of identical events.
            recordFailure(e)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.cricinfo"
                message = "Error checking commentary availability"
                level = SentryLevel.ERROR
                setData("matchId", matchId)
                setData("error", e.message ?: "Unknown error")
            })
            return@withContext false
        }
    }

    /**
     * Robust Commentary Extraction for Cricinfo.
     * Uses multiple selectors and attempts to extract ball-by-ball details.
     */
    suspend fun fetchLiveCommentary(matchUrl: String): List<String> = withContext(Dispatchers.IO) {
        // ── Circuit breaker check ────────────────────────────────────────
        if (isCircuitOpen()) {
            Log.d(TAG, "Circuit breaker OPEN — skipping commentary fetch for: $matchUrl")
            return@withContext emptyList()
        }

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.cricinfo"
            message = "Fetching live commentary"
            level = SentryLevel.INFO
            setData("url", matchUrl)
        })
        
        val commentaryUrl = if (matchUrl.contains("/commentary")) matchUrl else "$matchUrl/commentary"
        
        try {
            val doc = connect(commentaryUrl).get()
            recordSuccess()
            
            val commentaryLines = mutableListOf<String>()

            // 1. Try to find the __NEXT_DATA__ JSON block for 100% accuracy
            try {
                val nextData = doc.select("script#__NEXT_DATA__").first()?.data()
                if (nextData != null) {
                    val json = JsonParser.parseString(nextData).asJsonObject
                    // Navigate through the complex Cricinfo Next.js structure
                    val items = json.getAsJsonObject("props")
                        ?.getAsJsonObject("pageProps")
                        ?.getAsJsonObject("data")
                        ?.getAsJsonObject("matchCommentary")
                        ?.getAsJsonArray("items")
                    
                    items?.forEach { item ->
                        val itemObj = item.asJsonObject
                        val commText = itemObj.get("commentText")?.asString
                        val overNumber = itemObj.get("overNumber")?.asString
                        if (commText != null) {
                            val line = if (overNumber != null) "$overNumber $commText" else commText
                            commentaryLines.add(line)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse __NEXT_DATA__ for commentary, falling back to selectors")
                Sentry.captureException(e)
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "data.parse"
                    message = "Failed to parse __NEXT_DATA__ JSON"
                    level = SentryLevel.WARNING
                    setData("url", commentaryUrl)
                })
            }

            if (commentaryLines.isNotEmpty()) {
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "network.cricinfo"
                    message = "Successfully fetched commentary from __NEXT_DATA__"
                    level = SentryLevel.INFO
                    setData("linesCount", commentaryLines.size)
                })
                return@withContext commentaryLines
            }

            // 2. Fallback: Modern Design System (DS) Selectors
            val items = doc.select("div.ds-flex.ds-flex-row.ds-items-start")
            for (item in items) {
                val over = item.select("span.ds-text-tight-m.ds-font-bold").first()?.text()
                val text = item.select("div.ds-text-tight-l.ds-flex.ds-px-3.ds-py-2").first()?.text() 
                           ?: item.select("div.ci-html-content").first()?.text()
                
                if (text != null) {
                    val line = if (over != null) "$over $text" else text
                    commentaryLines.add(line)
                }
            }

            // 3. Fallback: Generic class selectors
            if (commentaryLines.isEmpty()) {
                val genericLines = doc.select("div[class*='ds-text-tight-l'][class*='ds-flex']")
                for (line in genericLines) {
                    val t = line.text().trim()
                    if (t.length > 10) commentaryLines.add(t)
                }
            }

            Log.d(TAG, "Fetched ${commentaryLines.size} commentary lines from Cricinfo")
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.cricinfo"
                message = "Successfully fetched commentary"
                level = SentryLevel.INFO
                setData("linesCount", commentaryLines.size)
            })
            
            commentaryLines
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Cricinfo commentary from URL: $matchUrl: ${e.message}")
            // recordFailure captures to Sentry at the circuit-breaker threshold (throttled).
            // Do NOT call captureException again here — that causes double-reporting and
            // bypasses the per-window throttle, producing bursts of identical events.
            recordFailure(e)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.cricinfo"
                message = "Error fetching commentary"
                level = SentryLevel.ERROR
                setData("url", matchUrl)
                setData("error", e.message ?: "Unknown error")
            })
            emptyList()
        }
    }

    suspend fun findClosestLiveMatchUrlAndId(matchName: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        // ── Circuit breaker check ────────────────────────────────────────
        if (isCircuitOpen()) {
            Log.d(TAG, "Circuit breaker OPEN — skipping Cricinfo lookup for: $matchName")
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.cricinfo"
                message = "Skipped lookup (circuit breaker open)"
                level = SentryLevel.DEBUG
                setData("matchName", matchName)
            })
            return@withContext null
        }

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.cricinfo"
            message = "Finding closest live match"
            level = SentryLevel.INFO
            setData("matchName", matchName)
        })
        
        val liveUrl = "https://www.espncricinfo.com/live-cricket-score"
        
        try {
            val doc = connect(liveUrl).get()
            recordSuccess() // Reset circuit breaker on successful fetch
            
            val matchLinks = doc.select("a.ds-no-tap-higlight")
            val teamNames = extractTeamNames(matchName)
            
            if (teamNames.size < 2) {
                Log.d(TAG, "Could not extract team names from: $matchName")
                return@withContext null
            }

            Log.d(TAG, "Searching Cricinfo for match between: ${teamNames[0]} vs ${teamNames[1]}")
            
            val candidates = mutableListOf<Candidate>()

            for (link in matchLinks) {
                val title = link.text().lowercase()
                val href = link.attr("href")
                
                var score = 0
                if (title.contains(teamNames[0])) score += 50
                if (title.contains(teamNames[1])) score += 50
                
                if (score < 100) {
                    if (fuzzyTeamMatch(teamNames[0], title)) score += 40
                    if (fuzzyTeamMatch(teamNames[1], title)) score += 40
                }

                if (score >= 40) {
                    val fullUrl = if (href.startsWith("http")) href else "https://www.espncricinfo.com$href"
                    candidates.add(Candidate(title, "", fullUrl, 200 - score))
                }
            }

            val bestCandidate = candidates.minByOrNull { it.score }
            if (bestCandidate != null && bestCandidate.score <= 120) {
                val matchId = extractCricinfoMatchId(bestCandidate.href)
                if (matchId != null) {
                    Log.d(TAG, "Matched Cricinfo: ${bestCandidate.title} (ID: $matchId)")
                    
                    Sentry.addBreadcrumb(Breadcrumb().apply {
                        category = "network.cricinfo"
                        message = "Found matching live match"
                        level = SentryLevel.INFO
                        setData("matchId", matchId)
                        setData("url", bestCandidate.href)
                    })
                    
                    return@withContext Pair(bestCandidate.href, matchId)
                }
            }

            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.cricinfo"
                message = "No matching live match found"
                level = SentryLevel.INFO
                setData("matchName", matchName)
            })
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding match: ${e.message}")
            if (e is CancellationException) throw e
            // recordFailure captures to Sentry at the circuit-breaker threshold (throttled).
            // Do NOT call captureException again here — that causes double-reporting and
            // bypasses the per-window throttle, producing bursts of identical events.
            recordFailure(e)

            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.cricinfo"
                message = "Error finding match"
                level = SentryLevel.ERROR
                setData("matchName", matchName)
                setData("error", e.message ?: "Unknown error")
                setData("consecutiveFailures", consecutiveFailures.get())
            })
            null
        }
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
}
