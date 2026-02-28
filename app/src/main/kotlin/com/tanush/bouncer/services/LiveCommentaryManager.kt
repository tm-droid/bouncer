package com.tanush.bouncer.services

import android.util.Log
import com.tanush.bouncer.network.CricBuzzScraper
import com.tanush.bouncer.network.CricInfoScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

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
    
    // Tracks the last ball number notified to avoid double alerts (e.g., "18.5")
    private val lastNotifiedBallMap = mutableMapOf<String, String>()
    
    var onKeyEvent: ((title: String, body: String) -> Unit)? = null
    var aiInsightsEnabled: Boolean = false
    var aiInsightCooldownMs: Long = 120_000L
    private val lastInsightAt = mutableMapOf<String, Long>()

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    
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
        
        val details = try { cricbuzz.fetchBallByBallAndBatsmen(matchId) } catch (e: Exception) { null }
        val ballNumber = details?.overNumbers?.lastOrNull() ?: ""
        val bowlerName = details?.bowler?.name ?: ""
        val batsmanName = details?.batsmen?.firstOrNull { it.isStriker }?.name ?: details?.batsmen?.firstOrNull()?.name ?: ""
        val scoreContext = details?.score ?: ""

        val buzzSnippets = try { cricbuzz.fetchLiveCommentary(cricbuzzUrl) } catch (e: Exception) { emptyList() }
        val infoSnippets = try { cricinfo.fetchLiveCommentary(cricinfoUrl) } catch (e: Exception) { emptyList() }
        
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
        
        val last18Count = min(max(aiCommentaryHistory.size, 0), 18)
        val last18AI = if (last18Count > 0) aiCommentaryHistory.takeLast(last18Count) else emptyList()
        val oversSummary = if (last18AI.isNotEmpty()) {
            fusion.summarizeOversWithAI(last18AI, matchId, 0)
        } else {
            buzzLine
        }
        
        val comparison = scorer.compare(buzzLine, infoLine, keyNames, commentaryHistory)
        val resultValue = when (comparison) {
            CommentaryScorer.ComparisonResult.UseFirst -> buzzLine
            CommentaryScorer.ComparisonResult.UseSecond -> infoLine
            CommentaryScorer.ComparisonResult.FuseBoth -> fusion.fuse(
                snippet1 = buzzLine,
                snippet2 = infoLine,
                oversSummary = oversSummary,
                matchId = matchId,
                over = 0,
                ballNumber = ballNumber,
                bowler = bowlerName,
                batsman = batsmanName,
                scoreContext = scoreContext
            )
        }
        
        if (commentaryHistory.isEmpty() || commentaryHistory.last() != resultValue) {
            commentaryHistory.add(resultValue)
            if (commentaryHistory.size > historyLimit) commentaryHistory.removeAt(0)
        }
        
        return resultValue
    }

    suspend fun getBestCommentaryForMatch(
        matchTitle: String, 
        cricbuzzId: String, 
        cricbuzzUrl: String, 
        keyNames: List<String>
    ): List<String> = withContext(Dispatchers.IO) {
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
        return@withContext aiCommentaryHistory.toList().reversed()
    }

    private suspend fun fetchSingleSourceFusion(matchId: String, url: String, keyNames: List<String>): String {
        val buzz = try { cricbuzz.fetchLiveCommentary(url) } catch (e: Exception) { emptyList() }
        val buzzLine = findBestBallSnippet(buzz, keyNames)
        val details = try { cricbuzz.fetchBallByBallAndBatsmen(matchId) } catch (e: Exception) { null }
        
        return fusion.fuse(
            snippet1 = buzzLine, 
            snippet2 = "", 
            oversSummary = buzzLine, 
            matchId = matchId, 
            over = 0, 
            ballNumber = details?.overNumbers?.lastOrNull() ?: "", 
            bowler = details?.bowler?.name ?: "", 
            batsman = details?.batsmen?.firstOrNull()?.name ?: "", 
            scoreContext = details?.score ?: ""
        )
    }
    
    /**
     * Accuracy-focused history update and notification trigger.
     */
    private fun updateHistory(resultValue: String, matchId: String? = null) {
        if (aiCommentaryHistory.lastOrNull() == resultValue) return
        
        aiCommentaryHistory.add(resultValue)
        
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

            // 3. Stricter Event Detection (Regex-based)
            val lower = resultValue.lowercase()
            
            // WICKET Detection: Must be a live dismissal, not summary text
            if (resultValue.contains(Regex("\\bWICKET\\b", RegexOption.IGNORE_CASE)) || 
                resultValue.contains(Regex("\\bOUT\\b!", RegexOption.IGNORE_CASE)) ||
                lower.contains("bowled") || lower.contains("lbw") || lower.contains("stumped")) {
                onKeyEvent?.invoke("Wicket!", resultValue)
                triggerAiInsight(resultValue, matchKey)
                return
            }
            
            // SIX Detection
            if (resultValue.contains(Regex("\\bSIX\\b", RegexOption.IGNORE_CASE)) || lower.contains("6 runs")) {
                onKeyEvent?.invoke("Six!", resultValue)
                return
            }
            
            // FOUR Detection
            if (resultValue.contains(Regex("\\bFOUR\\b", RegexOption.IGNORE_CASE)) || lower.contains("4 runs")) {
                onKeyEvent?.invoke("Boundary", resultValue)
                return
            }
            
        } catch (_: Exception) {}
    }

    private fun extractBallNumber(text: String): String? {
        val pattern = Pattern.compile("^(\\d{1,3}\\.\\d)")
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun triggerAiInsight(commentary: String, matchId: String) {
        if (!aiInsightsEnabled) return
        val now = System.currentTimeMillis()
        val last = lastInsightAt[matchId] ?: 0L
        if (now - last >= aiInsightCooldownMs) {
            lastInsightAt[matchId] = now
            scope.launch {
                try {
                    val insight = summarizer(aiCommentaryHistory.takeLast(6), matchId, 0)
                    onKeyEvent?.invoke("AI Strategic Insight", insight)
                } catch (_: Exception) {}
            }
        }
    }

    fun getMappedCricinfoId(cricbuzzId: String): String? = cricinfo.getMappedCricinfoId(cricbuzzId)
    fun getHistory(): List<String> = commentaryHistory
    fun isBallByBallCommentary(snippet: String): Boolean = scorer.isBallByBallCommentary(snippet)
}
