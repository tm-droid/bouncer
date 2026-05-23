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
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

private val BALL_PREFIX_REGEX = Regex("^\\d+\\.\\d.*")
private val BALL_NUMBER_PATTERN: Pattern = Pattern.compile("^(\\d{1,3}\\.\\d)")

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

    private suspend fun getBestCommentary(
        cricbuzzUrl: String, 
        cricinfoUrl: String, 
        keyNames: List<String>
    ): String {
        val matchId = cricbuzzUrl.split("/").lastOrNull() ?: "unknown"
        
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.commentary"
            message = "Getting best commentary"
            level = SentryLevel.INFO
            setData("matchId", matchId)
        })
        
        val details = try { cricbuzz.fetchBallByBallAndBatsmen(matchId) } catch (e: Exception) {
            if (e is CancellationException) throw e
            Sentry.captureException(e)
            null
        }
        val ballNumber = details?.overNumbers?.lastOrNull() ?: ""
        val bowlerName = details?.bowler?.name ?: ""
        val batsmanName = details?.batsmen?.firstOrNull { it.isStriker }?.name ?: details?.batsmen?.firstOrNull()?.name ?: ""
        val scoreContext = details?.score ?: ""

        val buzzSnippets = try { cricbuzz.fetchLiveCommentary(cricbuzzUrl) } catch (e: Exception) {
            if (e is CancellationException) throw e
            Sentry.captureException(e)
            emptyList()
        }
        val infoSnippets = try { cricinfo.fetchLiveCommentary(cricinfoUrl) } catch (e: Exception) {
            if (e is CancellationException) throw e
            Sentry.captureException(e)
            emptyList()
        }
        
        val buzzLine = findBestBallSnippet(buzzSnippets, keyNames)
        val infoLine = findBestBallSnippet(infoSnippets, keyNames)
        
        val hasBallContent = scorer.isBallByBallCommentary(buzzLine) || scorer.isBallByBallCommentary(infoLine)
        
        if (!hasBallContent) {
            val commentaryType = scorer.categorizeCommentary(buzzLine.ifEmpty { infoLine })
            return when (commentaryType) {
                CommentaryScorer.CommentaryType.PRE_MATCH -> "⏳ Pre-match: ${buzzLine.take(100)}..."
                CommentaryScorer.CommentaryType.POST_MATCH -> "🎙️ Post-match: ${buzzLine.take(100)}..."
                CommentaryScorer.CommentaryType.MATCH_BREAK -> "☕ Match break: ${buzzLine.take(100)}..."
                else -> if (buzzLine.isNotEmpty()) "📋 ${buzzLine.take(100)}..." else "Waiting for ball-by-ball commentary..."
            }
        }
        
        val over = ballNumber.substringBefore(".").toIntOrNull() ?: 0
        val last18Count = min(max(aiCommentaryHistory.size, 0), 18)
        val last18AI = if (last18Count > 0) aiCommentaryHistory.takeLast(last18Count) else emptyList()
        val oversSummary = try {
            if (last18AI.isNotEmpty()) fusion.summarizeOversWithAI(last18AI, matchId, over) else buzzLine
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("LiveCommentaryManager", "summarizeOversWithAI failed, using raw line", e)
            Sentry.captureException(e)
            buzzLine
        }

        val comparison = scorer.compare(buzzLine, infoLine, keyNames, commentaryHistory)
        val resultValue = when (comparison) {
            CommentaryScorer.ComparisonResult.UseFirst -> buzzLine
            CommentaryScorer.ComparisonResult.UseSecond -> infoLine
            CommentaryScorer.ComparisonResult.FuseBoth -> try {
                fusion.fuse(
                    snippet1 = buzzLine,
                    snippet2 = infoLine,
                    oversSummary = oversSummary,
                    matchId = matchId,
                    over = over,
                    ballNumber = ballNumber,
                    bowler = bowlerName,
                    batsman = batsmanName,
                    scoreContext = scoreContext
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("LiveCommentaryManager", "fusion.fuse failed, using raw snippet", e)
                Sentry.captureException(e)
                "$buzzLine / $infoLine"
            }
        }
        
        if (commentaryHistory.isEmpty() || commentaryHistory.last() != resultValue) {
            commentaryHistory.add(resultValue)
            if (commentaryHistory.size > historyLimit) commentaryHistory.removeAt(0)
        }

        // Ensure the returned string starts with the ball number so the UI can parse over.ball
        val prefixed = if (ballNumber.isNotEmpty() && !resultValue.trimStart().matches(BALL_PREFIX_REGEX)) {
            "$ballNumber $resultValue"
        } else resultValue
        return prefixed
    }

    suspend fun getBestCommentaryForMatch(
        matchTitle: String, 
        cricbuzzId: String, 
        cricbuzzUrl: String, 
        keyNames: List<String>
    ): List<String> = withContext(Dispatchers.IO) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.commentary"
            message = "Getting best commentary for match"
            level = SentryLevel.INFO
            setData("matchTitle", matchTitle)
            setData("cricbuzzId", cricbuzzId)
        })
        
        val mappedCricinfoId = cricinfo.getMappedCricinfoId(cricbuzzId)
        val resultValue: String
        
        if (mappedCricinfoId != null) {
            val cricinfoUrl = "https://www.espncricinfo.com/series/$mappedCricinfoId/commentary"
            if (cricinfo.isCommentaryAvailable(mappedCricinfoId, cricinfoUrl)) {
                resultValue = getBestCommentary(cricbuzzUrl, cricinfoUrl, keyNames)
            } else {
                resultValue = fetchSingleSourceFusion(cricbuzzId, cricbuzzUrl, keyNames)
            }
        } else {
            val found = cricinfo.findClosestLiveMatchUrlAndId(matchTitle)
            if (found != null) {
                val (url, id) = found
                cricinfo.mapMatchIds(cricbuzzId, id)
                resultValue = getBestCommentary(cricbuzzUrl, url, keyNames)
            } else {
                resultValue = fetchSingleSourceFusion(cricbuzzId, cricbuzzUrl, keyNames)
            }
        }
        
        updateHistory(resultValue, cricbuzzId)

        val ballNum = extractBallNumber(resultValue.trimStart())
        val aiEntry = if (ballNum != null && scorer.isBallByBallCommentary(resultValue)) {
            try {
                val raw = fusion.generateBallCommentary(cricbuzzId, ballNum, resultValue.take(200))
                if (!raw.trimStart().matches(BALL_PREFIX_REGEX)) "$ballNum $raw" else raw
            } catch (e: Exception) {
                Sentry.captureException(e)
                "$ballNum ${CommentaryFusionService.FALLBACK_BALL_COMMENTARY}"
            }
        } else null

        val history = aiCommentaryHistory.toList().reversed()
        return@withContext if (aiEntry != null) listOf(aiEntry) + history else history
    }

    private suspend fun fetchSingleSourceFusion(matchId: String, url: String, keyNames: List<String>): String {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.commentary"
            message = "Fetching single source fusion"
            level = SentryLevel.INFO
            setData("matchId", matchId)
        })
        
        val buzz = try { cricbuzz.fetchLiveCommentary(url) } catch (e: Exception) {
            if (e is CancellationException) throw e
            Sentry.captureException(e)
            emptyList()
        }
        val buzzLine = findBestBallSnippet(buzz, keyNames)
        val details = try { cricbuzz.fetchBallByBallAndBatsmen(matchId) } catch (e: Exception) {
            if (e is CancellationException) throw e
            Sentry.captureException(e)
            null
        }
        
        val singleBallNumber = details?.overNumbers?.lastOrNull() ?: ""
        val singleOver = singleBallNumber.substringBefore(".").toIntOrNull() ?: 0
        val fusionResult = try {
            fusion.fuse(
                snippet1 = buzzLine,
                snippet2 = "",
                oversSummary = buzzLine,
                matchId = matchId,
                over = singleOver,
                ballNumber = singleBallNumber,
                bowler = details?.bowler?.name ?: "",
                batsman = details?.batsmen?.firstOrNull()?.name ?: "",
                scoreContext = details?.score ?: ""
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("LiveCommentaryManager", "fusion.fuse failed, using raw snippet", e)
            Sentry.captureException(e)
            buzzLine
        }
        return if (singleBallNumber.isNotEmpty() && !fusionResult.trimStart().matches(BALL_PREFIX_REGEX)) {
            "$singleBallNumber $fusionResult"
        } else fusionResult
    }
    
    /**
     * Accuracy-focused history update and notification trigger.
     */
    private fun updateHistory(resultValue: String, matchId: String? = null) {
        if (aiCommentaryHistory.lastOrNull() == resultValue) return

        // CricBuzz sends progressive updates for the same ball (short → full).
        // Replace any earlier entry for the same ball number with the newer (longer) text.
        val ballNum = extractBallNumber(resultValue.trimStart())
        if (ballNum != null) {
            val idx = aiCommentaryHistory.indexOfLast { extractBallNumber(it.trimStart()) == ballNum }
            if (idx >= 0) {
                if (resultValue.length >= aiCommentaryHistory[idx].length) {
                    aiCommentaryHistory[idx] = resultValue
                }
                // Whether we replaced or kept the longer one, skip adding a duplicate
                // (still fall through to notification logic below)
            } else {
                aiCommentaryHistory.add(resultValue)
            }
        } else {
            aiCommentaryHistory.add(resultValue)
        }

        // Trim to a bounded window so long matches don't accumulate hundreds of balls.
        while (aiCommentaryHistory.size > aiHistoryLimit) aiCommentaryHistory.removeAt(0)

        // Notification Logic - MAGIC PRECISION
        try {
            // 1. Only notify for ball-by-ball commentary
            if (!scorer.isBallByBallCommentary(resultValue)) return
            
            // 2. Extract ball number (e.g., "18.5") to avoid duplicate notifications
            val ballNum = extractBallNumber(resultValue)
            val matchKey = matchId ?: "global"
            if (ballNum != null && lastNotifiedBallMap[matchKey] == ballNum) {
                return // Already notified for this ball
            }
            if (ballNum != null) lastNotifiedBallMap[matchKey] = ballNum

            // 3. Event Detection — delegate to shared TimelineEventExtractor
            when (TimelineEventExtractor.detectBallType(resultValue)) {
                TimelineEventType.WICKET -> {
                    onKeyEvent?.invoke("Wicket!", resultValue)
                    triggerAiInsight(resultValue, matchKey)
                    return
                }
                TimelineEventType.SIX -> {
                    onKeyEvent?.invoke("Six!", resultValue)
                    return
                }
                TimelineEventType.FOUR -> {
                    onKeyEvent?.invoke("Boundary", resultValue)
                    return
                }
                else -> { /* not a key event */ }
            }
            
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
        if (now - last >= aiInsightCooldownMs) {
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
    }

    /**
     * Pre-populate aiCommentaryHistory with historical lines fetched before the polling loop starts.
     * Existing entries are not duplicated; newer/longer entries for the same ball replace older ones.
     */
    fun seedHistory(lines: List<String>) {
        for (line in lines.reversed().filter { it.length > 5 }) {
            if (aiCommentaryHistory.lastOrNull() == line) continue
            val ballNum = extractBallNumber(line.trimStart())
            if (ballNum != null) {
                val idx = aiCommentaryHistory.indexOfLast { extractBallNumber(it.trimStart()) == ballNum }
                if (idx >= 0) {
                    if (line.length >= aiCommentaryHistory[idx].length) aiCommentaryHistory[idx] = line
                } else {
                    aiCommentaryHistory.add(line)
                }
            } else {
                if (!aiCommentaryHistory.contains(line)) aiCommentaryHistory.add(line)
            }
        }
        while (aiCommentaryHistory.size > aiHistoryLimit) aiCommentaryHistory.removeAt(0)
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
