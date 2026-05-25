package com.tanush.bouncer.services

import android.util.Log
import com.tanush.bouncer.models.TimelineEventType
import com.tanush.bouncer.network.CricBuzzScraper
import com.tanush.bouncer.network.CricInfoScraper
import com.tanush.bouncer.utils.TimelineEventExtractor
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

private val BALL_PREFIX_REGEX = Regex("^\\d+\\.\\d.*")
private val BALL_NUMBER_PATTERN: Pattern = Pattern.compile("^(\\d{1,3}\\.\\d)")
private const val TAG = "LiveCommentaryManager"

class LiveCommentaryManager(
    private val summarizer: suspend (List<String>, String, Int) -> String = { lines, matchId, over -> CommentaryFusionService().summarizeOversWithAI(lines, matchId, over) }
) {
    private val cricbuzz = CricBuzzScraper()
    private val cricinfo = CricInfoScraper()
    private val scorer = CommentaryScorer()
    private val fusion = CommentaryFusionService()

    private val commentaryHistory = mutableListOf<String>()
    private val historyLimit = 10
    private val aiCommentaryHistory = mutableListOf<String>()
    private val aiHistoryLimit = 50

    // Per-ball live streaming commentary — resets on each new ball, accumulates tokens as they arrive
    private val _streamingCommentary = MutableStateFlow("")
    val streamingCommentary: StateFlow<String> = _streamingCommentary.asStateFlow()

    // Tracks the last ball number notified to avoid double alerts (e.g., "18.5")
    private val lastNotifiedBallMap = mutableMapOf<String, String>()

    var onKeyEvent: ((title: String, body: String) -> Unit)? = null
    var aiInsightsEnabled: Boolean = false
    var aiInsightCooldownMs: Long = 120_000L
    private val lastInsightAt = mutableMapOf<String, Long>()

    private val supervisorJob = kotlinx.coroutines.SupervisorJob()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + supervisorJob)

    fun generateOversSummary(commentaryLines: List<String>): String {
        if (commentaryLines.isEmpty()) return "No recent commentary available."
        val uniqueLines = commentaryLines.distinct()
        val totalBalls = min(max(uniqueLines.size, 0), 18)
        val lines = if (totalBalls > 0) uniqueLines.take(totalBalls) else emptyList()
        return lines.mapIndexed { index, s -> "${index + 1}. $s" }.joinToString("\n")
    }

    private fun findBestBallSnippet(snippets: List<String>, keyNames: List<String>): String {
        if (snippets.isEmpty()) return ""
        val ballLine = snippets.firstOrNull { scorer.isBallByBallCommentary(it) }
        if (ballLine != null) return ballLine
        return snippets.maxByOrNull { scorer.score(it, keyNames, emptyList(), false) } ?: snippets.first()
    }

    private inline fun <T> safeFetch(fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Sentry.captureException(e)
            fallback
        }
    }

    private fun addBreadcrumb(message: String, data: Map<String, String> = emptyMap()) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.commentary"
            this.message = message
            level = SentryLevel.INFO
            data.forEach { (k, v) -> setData(k, v) }
        })
    }

    private fun prefixBallNumber(ballNumber: String, text: String): String {
        return if (ballNumber.isNotEmpty() && !text.trimStart().matches(BALL_PREFIX_REGEX)) {
            "$ballNumber $text"
        } else text
    }

    private fun formatNonBallCommentary(buzzLine: String, infoLine: String): String {
        val sourceLine = buzzLine.ifEmpty { infoLine }
        return when (scorer.categorizeCommentary(sourceLine)) {
            CommentaryScorer.CommentaryType.PRE_MATCH -> "⏳ Pre-match: ${buzzLine.take(100)}..."
            CommentaryScorer.CommentaryType.POST_MATCH -> "🎙️ Post-match: ${buzzLine.take(100)}..."
            CommentaryScorer.CommentaryType.MATCH_BREAK -> "☕ Match break: ${buzzLine.take(100)}..."
            else -> if (buzzLine.isNotEmpty()) "📋 ${buzzLine.take(100)}..." else "Waiting for ball-by-ball commentary..."
        }
    }

    private suspend fun computeOversSummary(matchId: String, over: Int, fallback: String): String {
        val last18Count = min(max(aiCommentaryHistory.size, 0), 18)
        val last18AI = if (last18Count > 0) aiCommentaryHistory.takeLast(last18Count) else emptyList()
        return try {
            if (last18AI.isNotEmpty()) fusion.summarizeOversWithAI(last18AI, matchId, over) else fallback
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "summarizeOversWithAI failed, using raw line", e)
            Sentry.captureException(e)
            fallback
        }
    }

    private suspend fun fuseLines(
        buzzLine: String,
        infoLine: String,
        oversSummary: String,
        matchId: String,
        over: Int,
        ballNumber: String,
        bowler: String,
        batsman: String,
        scoreContext: String
    ): String {
        return try {
            fusion.fuse(
                snippet1 = buzzLine,
                snippet2 = infoLine,
                oversSummary = oversSummary,
                matchId = matchId,
                over = over,
                ballNumber = ballNumber,
                bowler = bowler,
                batsman = batsman,
                scoreContext = scoreContext
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "fusion.fuse failed, using raw snippet", e)
            Sentry.captureException(e)
            if (infoLine.isEmpty()) buzzLine else "$buzzLine / $infoLine"
        }
    }

    private fun recordCommentary(resultValue: String) {
        if (commentaryHistory.isEmpty() || commentaryHistory.last() != resultValue) {
            commentaryHistory.add(resultValue)
            if (commentaryHistory.size > historyLimit) commentaryHistory.removeAt(0)
        }
    }

    /**
     * Resets [streamingCommentary] and starts collecting tokens from [CommentaryFusionService.streamCommentary].
     * Appends each token to [_streamingCommentary] as it arrives.
     * Must be called from within a coroutine scope on Dispatchers.IO.
     */
    private suspend fun streamBallCommentary(matchContext: CommentaryFusionService.MatchContext) {
        _streamingCommentary.value = ""
        try {
            fusion.streamCommentary(matchContext).collect { token ->
                _streamingCommentary.update { it + token }
            }
            _streamingCommentary.value = ""  // clear when stream finishes normally
        } catch (e: CancellationException) {
            _streamingCommentary.value = ""
            throw e
        } catch (e: Exception) {
            _streamingCommentary.value = ""
            Log.w(TAG, "streamBallCommentary failed", e)
            Sentry.captureException(e)
        }
    }

    private suspend fun getBestCommentary(
        cricbuzzUrl: String,
        cricinfoUrl: String,
        keyNames: List<String>
    ): String {
        val matchId = cricbuzzUrl.split("/").lastOrNull() ?: "unknown"
        addBreadcrumb("Getting best commentary", mapOf("matchId" to matchId))

        val details: CricBuzzScraper.MatchDetails? = safeFetch(null) {
            cricbuzz.fetchBallByBallAndBatsmen(matchId)
        }
        val ballNumber = details?.overNumbers?.lastOrNull() ?: ""
        val bowlerName = details?.bowler?.name ?: ""
        val batsmanName = details?.batsmen?.firstOrNull { it.isStriker }?.name
            ?: details?.batsmen?.firstOrNull()?.name ?: ""
        val scoreContext = details?.score ?: ""

        val buzzSnippets = safeFetch(emptyList<String>()) { cricbuzz.fetchLiveCommentary(cricbuzzUrl) }
        val infoSnippets = safeFetch(emptyList<String>()) { cricinfo.fetchLiveCommentary(cricinfoUrl) }

        val buzzLine = findBestBallSnippet(buzzSnippets, keyNames)
        val infoLine = findBestBallSnippet(infoSnippets, keyNames)

        val hasBallContent = scorer.isBallByBallCommentary(buzzLine) || scorer.isBallByBallCommentary(infoLine)
        if (!hasBallContent) return formatNonBallCommentary(buzzLine, infoLine)

        val over = ballNumber.substringBefore(".").toIntOrNull() ?: 0
        val oversSummary = computeOversSummary(matchId, over, buzzLine)

        scope.launch {
            streamBallCommentary(
                CommentaryFusionService.MatchContext(
                    matchId = matchId,
                    over = over,
                    ballNumber = ballNumber,
                    snippet1 = buzzLine,
                    snippet2 = infoLine,
                    oversSummary = oversSummary,
                    bowler = bowlerName,
                    batsman = batsmanName,
                    scoreContext = scoreContext
                )
            )
        }

        val resultValue = when (scorer.compare(buzzLine, infoLine, keyNames, commentaryHistory)) {
            CommentaryScorer.ComparisonResult.UseFirst -> buzzLine
            CommentaryScorer.ComparisonResult.UseSecond -> infoLine
            CommentaryScorer.ComparisonResult.FuseBoth -> fuseLines(
                buzzLine, infoLine, oversSummary, matchId, over, ballNumber,
                bowlerName, batsmanName, scoreContext
            )
        }

        recordCommentary(resultValue)
        return prefixBallNumber(ballNumber, resultValue)
    }

    private suspend fun resolveBestCommentary(
        matchTitle: String,
        cricbuzzId: String,
        cricbuzzUrl: String,
        keyNames: List<String>
    ): String {
        val mappedCricinfoId = cricinfo.getMappedCricinfoId(cricbuzzId)
        if (mappedCricinfoId != null) {
            val cricinfoUrl = "https://www.espncricinfo.com/series/$mappedCricinfoId/commentary"
            return if (cricinfo.isCommentaryAvailable(mappedCricinfoId, cricinfoUrl)) {
                getBestCommentary(cricbuzzUrl, cricinfoUrl, keyNames)
            } else {
                fetchSingleSourceFusion(cricbuzzId, cricbuzzUrl, keyNames)
            }
        }
        val found = cricinfo.findClosestLiveMatchUrlAndId(matchTitle)
        if (found != null) {
            val (url, id) = found
            cricinfo.mapMatchIds(cricbuzzId, id)
            return getBestCommentary(cricbuzzUrl, url, keyNames)
        }
        return fetchSingleSourceFusion(cricbuzzId, cricbuzzUrl, keyNames)
    }

    private suspend fun buildAiEntry(cricbuzzId: String, resultValue: String): String? {
        val ballNum = extractBallNumber(resultValue.trimStart()) ?: return null
        if (!scorer.isBallByBallCommentary(resultValue)) return null
        return try {
            val raw = fusion.generateBallCommentary(cricbuzzId, ballNum, resultValue.take(200))
            if (!raw.trimStart().matches(BALL_PREFIX_REGEX)) "$ballNum $raw" else raw
        } catch (e: Exception) {
            Sentry.captureException(e)
            "$ballNum ${CommentaryFusionService.FALLBACK_BALL_COMMENTARY}"
        }
    }

    suspend fun getBestCommentaryForMatch(
        matchTitle: String,
        cricbuzzId: String,
        cricbuzzUrl: String,
        keyNames: List<String>
    ): List<String> = withContext(Dispatchers.IO) {
        addBreadcrumb(
            "Getting best commentary for match",
            mapOf("matchTitle" to matchTitle, "cricbuzzId" to cricbuzzId)
        )

        val resultValue = resolveBestCommentary(matchTitle, cricbuzzId, cricbuzzUrl, keyNames)
        updateHistory(resultValue, cricbuzzId)

        val aiEntry = buildAiEntry(cricbuzzId, resultValue)
        val history = aiCommentaryHistory.toList().reversed()
        return@withContext if (aiEntry != null) listOf(aiEntry) + history else history
    }

    private suspend fun fetchSingleSourceFusion(matchId: String, url: String, keyNames: List<String>): String {
        addBreadcrumb("Fetching single source fusion", mapOf("matchId" to matchId))

        val buzz = safeFetch(emptyList<String>()) { cricbuzz.fetchLiveCommentary(url) }
        val buzzLine = findBestBallSnippet(buzz, keyNames)
        val details: CricBuzzScraper.MatchDetails? = safeFetch(null) {
            cricbuzz.fetchBallByBallAndBatsmen(matchId)
        }

        val singleBallNumber = details?.overNumbers?.lastOrNull() ?: ""
        val singleOver = singleBallNumber.substringBefore(".").toIntOrNull() ?: 0
        val singleBowler = details?.bowler?.name ?: ""
        val singleBatsman = details?.batsmen?.firstOrNull()?.name ?: ""
        val singleScore = details?.score ?: ""

        scope.launch {
            streamBallCommentary(
                CommentaryFusionService.MatchContext(
                    matchId = matchId,
                    over = singleOver,
                    ballNumber = singleBallNumber,
                    snippet1 = buzzLine,
                    snippet2 = "",
                    oversSummary = buzzLine,
                    bowler = singleBowler,
                    batsman = singleBatsman,
                    scoreContext = singleScore
                )
            )
        }

        val fusionResult = fuseLines(
            buzzLine = buzzLine,
            infoLine = "",
            oversSummary = buzzLine,
            matchId = matchId,
            over = singleOver,
            ballNumber = singleBallNumber,
            bowler = singleBowler,
            batsman = singleBatsman,
            scoreContext = singleScore
        )
        return prefixBallNumber(singleBallNumber, fusionResult)
    }

    /**
     * Insert or replace entry for the same ball number; replacement only if newer line is at least as long.
     */
    private fun upsertAiHistoryEntry(line: String) {
        val ballNum = extractBallNumber(line.trimStart())
        if (ballNum == null) {
            aiCommentaryHistory.add(line)
            return
        }
        val idx = aiCommentaryHistory.indexOfLast { extractBallNumber(it.trimStart()) == ballNum }
        if (idx >= 0) {
            if (line.length >= aiCommentaryHistory[idx].length) {
                aiCommentaryHistory[idx] = line
            }
        } else {
            aiCommentaryHistory.add(line)
        }
    }

    private fun trimAiHistory() {
        while (aiCommentaryHistory.size > aiHistoryLimit) aiCommentaryHistory.removeAt(0)
    }

    private fun shouldSkipNotification(resultValue: String, matchKey: String): Boolean {
        if (!scorer.isBallByBallCommentary(resultValue)) return true
        val ballNum = extractBallNumber(resultValue)
        if (ballNum != null && lastNotifiedBallMap[matchKey] == ballNum) return true
        if (ballNum != null) lastNotifiedBallMap[matchKey] = ballNum
        return false
    }

    private fun dispatchKeyEvent(resultValue: String, matchKey: String) {
        when (TimelineEventExtractor.detectBallType(resultValue)) {
            TimelineEventType.WICKET -> {
                onKeyEvent?.invoke("Wicket!", resultValue)
                triggerAiInsight(resultValue, matchKey)
            }
            TimelineEventType.SIX -> onKeyEvent?.invoke("Six!", resultValue)
            TimelineEventType.FOUR -> onKeyEvent?.invoke("Boundary", resultValue)
            else -> { /* not a key event */ }
        }
    }

    /**
     * Accuracy-focused history update and notification trigger.
     */
    private fun updateHistory(resultValue: String, matchId: String? = null) {
        if (aiCommentaryHistory.lastOrNull() == resultValue) return

        // CricBuzz sends progressive updates for the same ball (short → full).
        // Replace any earlier entry for the same ball number with the newer (longer) text.
        upsertAiHistoryEntry(resultValue)

        // Trim to a bounded window so long matches don't accumulate hundreds of balls.
        trimAiHistory()

        // Notification Logic - MAGIC PRECISION
        try {
            val matchKey = matchId ?: "global"
            if (shouldSkipNotification(resultValue, matchKey)) return
            dispatchKeyEvent(resultValue, matchKey)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Sentry.captureException(e)
        }
    }

    private fun extractBallNumber(text: String): String? {
        val matcher = BALL_NUMBER_PATTERN.matcher(text)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun triggerAiInsight(commentary: String, matchId: String) {
        if (!aiInsightsEnabled) return
        val now = System.currentTimeMillis()
        val last = lastInsightAt[matchId] ?: 0L
        if (now - last < aiInsightCooldownMs) return

        lastInsightAt[matchId] = now
        val insightOver = extractBallNumber(commentary)?.substringBefore(".")?.toIntOrNull() ?: 0
        scope.launch {
            try {
                val insight = summarizer(aiCommentaryHistory.takeLast(6), matchId, insightOver)
                onKeyEvent?.invoke("AI Strategic Insight", insight)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Sentry.captureException(e)
            }
        }
    }

    /**
     * Pre-populate aiCommentaryHistory with historical lines fetched before the polling loop starts.
     * Existing entries are not duplicated; newer/longer entries for the same ball replace older ones.
     */
    fun seedHistory(lines: List<String>) {
        for (line in lines.reversed().filter { it.length > 5 }) {
            if (aiCommentaryHistory.lastOrNull() == line) continue
            val ballNum = extractBallNumber(line.trimStart())
            if (ballNum == null) {
                if (!aiCommentaryHistory.contains(line)) aiCommentaryHistory.add(line)
            } else {
                upsertAiHistoryEntry(line)
            }
        }
        trimAiHistory()
    }

    fun getSeededHistory(): List<String> = aiCommentaryHistory.toList().reversed()

    fun getMappedCricinfoId(cricbuzzId: String): String? = cricinfo.getMappedCricinfoId(cricbuzzId)
    fun getHistory(): List<String> = commentaryHistory
    fun isBallByBallCommentary(snippet: String): Boolean = scorer.isBallByBallCommentary(snippet)

    /**
     * Cancel all coroutines to prevent leaks.
     * Call this when the manager is no longer needed.
     */
    fun close() {
        supervisorJob.cancel()
    }
}
