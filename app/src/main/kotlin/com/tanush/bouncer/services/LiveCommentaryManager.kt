package com.tanush.bouncer.services

import com.tanush.bouncer.utils.ballNumberOf
import com.tanush.bouncer.utils.startsWithBallNumber
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private const val TAG = "LiveCommentaryManager"

class LiveCommentaryManager(
    private val summarizer: suspend (List<String>, String, Int) -> String = { lines, matchId, over -> CommentaryFusionService().summarizeOversWithAI(lines, matchId, over) }
) {
    private val cricbuzz = CricBuzzScraper()
    private val cricinfo = CricInfoScraper()
    private val scorer = CommentaryScorer()
    private val fusion = CommentaryFusionService()

    // Guards commentaryHistory, aiCommentaryHistory, lastNotifiedBallMap — never hold across suspension.
    private val stateMutex = Mutex()
    // Guards currentStreamJob cancel+reassign to prevent two concurrent streams.
    private val streamJobMutex = Mutex()

    private val commentaryHistory = mutableListOf<String>()
    private val historyLimit = 10
    private val aiCommentaryHistory = mutableListOf<String>()
    private val aiHistoryLimit = 50

    // Per-ball live streaming commentary — resets on each new ball, accumulates tokens as they arrive
    private val _streamingCommentary = MutableStateFlow("")
    val streamingCommentary: StateFlow<String> = _streamingCommentary.asStateFlow()

    // Tracks the last ball number notified to avoid double alerts (e.g., "18.5")
    private val lastNotifiedBallMap = mutableMapOf<String, String>()

    // Set by getBestCommentary / fetchSingleSourceFusion; read by buildAiEntry in the same call chain.
    @Volatile private var lastFetchedBowler: String = ""
    @Volatile private var lastFetchedBatsman: String = ""
    @Volatile private var lastFetchedScore: String = ""

    var onKeyEvent: ((title: String, body: String) -> Unit)? = null
    var aiInsightsEnabled: Boolean = false
    var aiInsightCooldownMs: Long = 120_000L
    private val lastInsightAt = mutableMapOf<String, Long>()

    private val supervisorJob = kotlinx.coroutines.SupervisorJob()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + supervisorJob)

    /** Tracks the active streaming job so stale streams can be cancelled before launching a new one. */
    private var currentStreamJob: Job? = null

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
        return if (ballNumber.isNotEmpty() && !startsWithBallNumber(text)) {
            "$ballNumber $text"
        } else text
    }

    private suspend fun computeOversSummary(
        matchId: String,
        over: Int,
        fallback: String,
        isMatchComplete: Boolean
    ): String {
        val last18AI = stateMutex.withLock {
            val count = min(max(aiCommentaryHistory.size, 0), 18)
            if (count > 0) aiCommentaryHistory.takeLast(count) else emptyList()
        }
        return try {
            if (last18AI.isNotEmpty()) fusion.summarizeOversWithAI(last18AI, matchId, over, isMatchComplete) else fallback
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
        scoreContext: String,
        isMatchComplete: Boolean,
        currentRunRate: Double? = null,
        requiredRunRate: Double? = null
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
                scoreContext = scoreContext,
                isMatchComplete = isMatchComplete,
                currentRunRate = currentRunRate,
                requiredRunRate = requiredRunRate
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "fusion.fuse failed, using raw snippet", e)
            Sentry.captureException(e)
            if (infoLine.isEmpty()) buzzLine else "$buzzLine / $infoLine"
        }
    }

    private suspend fun recordCommentary(resultValue: String) {
        stateMutex.withLock {
            if (commentaryHistory.isEmpty() || commentaryHistory.last() != resultValue) {
                commentaryHistory.add(resultValue)
                if (commentaryHistory.size > historyLimit) commentaryHistory.removeAt(0)
            }
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
        keyNames: List<String>,
        isMatchComplete: Boolean
    ): String {
        val matchId = cricbuzzUrl.split("/").lastOrNull() ?: "unknown"
        addBreadcrumb("Getting best commentary", mapOf("matchId" to matchId))

        val details: CricBuzzScraper.ScrapedMatchDetails? = safeFetch(null) {
            cricbuzz.fetchBallByBallAndBatsmen(matchId)
        }
        val bowlerName = details?.bowler?.name ?: ""
        val batsmanName = details?.batsmen?.firstOrNull { it.isStriker }?.name
            ?: details?.batsmen?.firstOrNull()?.name ?: ""
        val scoreContext = details?.score ?: ""
        lastFetchedBowler = bowlerName
        lastFetchedBatsman = batsmanName
        lastFetchedScore = scoreContext

        val buzzSnippets = safeFetch(emptyList<String>()) { cricbuzz.fetchLiveCommentary(cricbuzzUrl) }
        val infoSnippets = safeFetch(emptyList<String>()) { cricinfo.fetchLiveCommentary(cricinfoUrl) }

        val buzzLine = findBestBallSnippet(buzzSnippets, keyNames)
        val infoLine = findBestBallSnippet(infoSnippets, keyNames)

        val ballNumber = details?.overNumbers?.lastOrNull()
            ?: extractBallNumber(buzzLine.trimStart())
            ?: extractBallNumber(infoLine.trimStart())
            ?: ""

        val hasBallContent = scorer.isBallByBallCommentary(buzzLine) || scorer.isBallByBallCommentary(infoLine)
        if (!hasBallContent) {
            val sourceLine = buzzLine.ifEmpty { infoLine }
            if (sourceLine.isEmpty()) return "Waiting for ball-by-ball commentary..."
            return fusion.rewriteEventCommentary(matchId, sourceLine, isMatchComplete)
        }

        val over = ballNumber.substringBefore(".").toIntOrNull() ?: 0
        val oversSummary = computeOversSummary(matchId, over, buzzLine, isMatchComplete)

        launchStreamingJob(
            CommentaryFusionService.MatchContext(
                matchId = matchId,
                over = over,
                ballNumber = ballNumber,
                snippet1 = buzzLine,
                snippet2 = infoLine,
                oversSummary = oversSummary,
                bowler = bowlerName,
                batsman = batsmanName,
                scoreContext = scoreContext,
                isMatchComplete = isMatchComplete,
                currentRunRate = details?.currentRunRate?.toDoubleOrNull(),
                requiredRunRate = details?.requiredRunRate?.toDoubleOrNull()
            )
        )

        val snapshotHistory = stateMutex.withLock { commentaryHistory.toList() }
        val resultValue = when (scorer.compare(buzzLine, infoLine, keyNames, snapshotHistory)) {
            CommentaryScorer.ComparisonResult.UseFirst -> buzzLine
            CommentaryScorer.ComparisonResult.UseSecond -> infoLine
            CommentaryScorer.ComparisonResult.FuseBoth -> fuseLines(
                buzzLine, infoLine, oversSummary, matchId, over, ballNumber,
                bowlerName, batsmanName, scoreContext, isMatchComplete,
                currentRunRate = details?.currentRunRate?.toDoubleOrNull(),
                requiredRunRate = details?.requiredRunRate?.toDoubleOrNull()
            )
        }

        recordCommentary(resultValue)
        return prefixBallNumber(ballNumber, resultValue)
    }

    // Synchronize cancel+reassign so concurrent calls can't race on currentStreamJob.
    private suspend fun launchStreamingJob(context: CommentaryFusionService.MatchContext) {
        streamJobMutex.withLock {
            currentStreamJob?.cancel()
            currentStreamJob = scope.launch { streamBallCommentary(context) }
        }
    }

    private suspend fun resolveBestCommentary(
        matchTitle: String,
        cricbuzzId: String,
        cricbuzzUrl: String,
        keyNames: List<String>,
        isMatchComplete: Boolean
    ): String {
        val mappedCricinfoId = cricinfo.getMappedCricinfoId(cricbuzzId)
        if (mappedCricinfoId != null) {
            val cricinfoUrl = "https://www.espncricinfo.com/series/$mappedCricinfoId/commentary"
            return if (cricinfo.isCommentaryAvailable(mappedCricinfoId, cricinfoUrl)) {
                getBestCommentary(cricbuzzUrl, cricinfoUrl, keyNames, isMatchComplete)
            } else {
                fetchSingleSourceFusion(cricbuzzId, cricbuzzUrl, keyNames, isMatchComplete)
            }
        }
        val found = cricinfo.findClosestLiveMatchUrlAndId(matchTitle)
        if (found != null) {
            val (url, id) = found
            cricinfo.mapMatchIds(cricbuzzId, id)
            return getBestCommentary(cricbuzzUrl, url, keyNames, isMatchComplete)
        }
        return fetchSingleSourceFusion(cricbuzzId, cricbuzzUrl, keyNames, isMatchComplete)
    }

    private suspend fun buildAiEntry(cricbuzzId: String, resultValue: String, isMatchComplete: Boolean): String? {
        val ballNum = extractBallNumber(resultValue.trimStart()) ?: return null
        if (!scorer.isBallByBallCommentary(resultValue)) return null
        return try {
            val raw = fusion.generateBallCommentary(
                matchId = cricbuzzId,
                ballNumber = ballNum,
                rawSnippet = resultValue.take(200),
                isMatchComplete = isMatchComplete,
                bowler = lastFetchedBowler,
                batsman = lastFetchedBatsman,
                scoreContext = lastFetchedScore,
            )
            if (!startsWithBallNumber(raw)) "$ballNum $raw" else raw
        } catch (e: Exception) {
            Sentry.captureException(e)
            "$ballNum ${CommentaryFusionService.FALLBACK_BALL_COMMENTARY}"
        }
    }

    suspend fun getBestCommentaryForMatch(
        matchTitle: String,
        cricbuzzId: String,
        cricbuzzUrl: String,
        keyNames: List<String>,
        isMatchComplete: Boolean = false
    ): List<String> = withContext(Dispatchers.IO) {
        // Capture isMatchComplete as a local val at call-start and thread it explicitly through all
        // downstream calls so no async job ever reads a stale shared field value.
        val matchComplete = isMatchComplete
        addBreadcrumb(
            "Getting best commentary for match",
            mapOf("matchTitle" to matchTitle, "cricbuzzId" to cricbuzzId)
        )

        val resultValue = resolveBestCommentary(matchTitle, cricbuzzId, cricbuzzUrl, keyNames, matchComplete)
        updateHistory(resultValue, cricbuzzId)

        val aiEntry = buildAiEntry(cricbuzzId, resultValue, matchComplete)
        val history = stateMutex.withLock { aiCommentaryHistory.toList().reversed() }
        return@withContext if (aiEntry != null) listOf(aiEntry) + history else history
    }

    private suspend fun fetchSingleSourceFusion(
        matchId: String,
        url: String,
        keyNames: List<String>,
        isMatchComplete: Boolean
    ): String {
        addBreadcrumb("Fetching single source fusion", mapOf("matchId" to matchId))

        val buzz = safeFetch(emptyList<String>()) { cricbuzz.fetchLiveCommentary(url) }
        val buzzLine = findBestBallSnippet(buzz, keyNames)
        val details: CricBuzzScraper.ScrapedMatchDetails? = safeFetch(null) {
            cricbuzz.fetchBallByBallAndBatsmen(matchId)
        }

        val singleBallNumber = details?.overNumbers?.lastOrNull()
            ?: extractBallNumber(buzzLine.trimStart())
            ?: ""
        val singleOver = singleBallNumber.substringBefore(".").toIntOrNull() ?: 0
        val singleBowler = details?.bowler?.name ?: ""
        val singleBatsman = details?.batsmen?.firstOrNull()?.name ?: ""
        val singleScore = details?.score ?: ""
        lastFetchedBowler = singleBowler
        lastFetchedBatsman = singleBatsman
        lastFetchedScore = singleScore

        val oversSummary = computeOversSummary(matchId, singleOver, buzzLine, isMatchComplete)

        // Synchronize cancel+reassign so concurrent calls can't race on currentStreamJob.
        streamJobMutex.withLock {
            currentStreamJob?.cancel()
            currentStreamJob = scope.launch {
                streamBallCommentary(
                    CommentaryFusionService.MatchContext(
                        matchId = matchId,
                        over = singleOver,
                        ballNumber = singleBallNumber,
                        snippet1 = buzzLine,
                        snippet2 = "",
                        oversSummary = oversSummary,
                        bowler = singleBowler,
                        batsman = singleBatsman,
                        scoreContext = singleScore,
                        isMatchComplete = isMatchComplete,
                        currentRunRate = details?.currentRunRate?.toDoubleOrNull(),
                        requiredRunRate = details?.requiredRunRate?.toDoubleOrNull()
                    )
                )
            }
        }

        val fusionResult = fuseLines(
            buzzLine = buzzLine,
            infoLine = "",
            oversSummary = oversSummary,
            matchId = matchId,
            over = singleOver,
            ballNumber = singleBallNumber,
            bowler = singleBowler,
            batsman = singleBatsman,
            scoreContext = singleScore,
            isMatchComplete = isMatchComplete,
            currentRunRate = details?.currentRunRate?.toDoubleOrNull(),
            requiredRunRate = details?.requiredRunRate?.toDoubleOrNull()
        )
        return prefixBallNumber(singleBallNumber, fusionResult)
    }

    /**
     * Insert or replace entry for the same ball number; replacement only if newer line is at least as long.
     * Caller MUST hold stateMutex.
     */
    private fun upsertAiHistoryEntryLocked(line: String) {
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

    /** Caller MUST hold stateMutex. */
    private fun trimAiHistoryLocked() {
        while (aiCommentaryHistory.size > aiHistoryLimit) aiCommentaryHistory.removeAt(0)
    }

    private suspend fun shouldSkipNotification(resultValue: String, matchKey: String): Boolean {
        if (!scorer.isBallByBallCommentary(resultValue)) return true
        val ballNum = extractBallNumber(resultValue)
        return stateMutex.withLock {
            if (ballNum != null && lastNotifiedBallMap[matchKey] == ballNum) return@withLock true
            if (ballNum != null) lastNotifiedBallMap[matchKey] = ballNum
            false
        }
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
    private suspend fun updateHistory(resultValue: String, matchId: String? = null) {
        stateMutex.withLock {
            if (aiCommentaryHistory.lastOrNull() == resultValue) return@withLock

            // CricBuzz sends progressive updates for the same ball (short → full).
            // Replace any earlier entry for the same ball number with the newer (longer) text.
            upsertAiHistoryEntryLocked(resultValue)

            // Trim to a bounded window so long matches don't accumulate hundreds of balls.
            trimAiHistoryLocked()
        }

        // Notification Logic - MAGIC PRECISION (outside mutex — no state access needed after here)
        try {
            val matchKey = matchId ?: "global"
            if (shouldSkipNotification(resultValue, matchKey)) return
            dispatchKeyEvent(resultValue, matchKey)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Sentry.captureException(e)
        }
    }

    private fun extractBallNumber(text: String): String? = ballNumberOf(text)

    private fun triggerAiInsight(commentary: String, matchId: String) {
        if (!aiInsightsEnabled) return
        val now = System.currentTimeMillis()
        val last = lastInsightAt[matchId] ?: 0L
        if (now - last < aiInsightCooldownMs) return

        lastInsightAt[matchId] = now
        val insightOver = extractBallNumber(commentary)?.substringBefore(".")?.toIntOrNull() ?: 0
        scope.launch {
            try {
                // Snapshot history under mutex before the suspension point in summarizer.
                val snapshot = stateMutex.withLock { aiCommentaryHistory.takeLast(6) }
                val insight = summarizer(snapshot, matchId, insightOver)
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
    suspend fun seedHistory(lines: List<String>) {
        stateMutex.withLock {
            for (line in lines.reversed().filter { it.length > 5 }) {
                if (aiCommentaryHistory.lastOrNull() == line) continue
                val ballNum = extractBallNumber(line.trimStart())
                if (ballNum == null) {
                    if (!aiCommentaryHistory.contains(line)) aiCommentaryHistory.add(line)
                } else {
                    upsertAiHistoryEntryLocked(line)
                }
            }
            trimAiHistoryLocked()
        }
    }

    suspend fun getSeededHistory(): List<String> = stateMutex.withLock { aiCommentaryHistory.toList().reversed() }

    fun getMappedCricinfoId(cricbuzzId: String): String? = cricinfo.getMappedCricinfoId(cricbuzzId)
    // Returns a snapshot; callers on any thread get a consistent immutable copy.
    suspend fun getHistory(): List<String> = stateMutex.withLock { commentaryHistory.toList() }
    fun isBallByBallCommentary(snippet: String): Boolean = scorer.isBallByBallCommentary(snippet)

    /**
     * Cancel all coroutines to prevent leaks.
     * Call this when the manager is no longer needed.
     */
    fun close() {
        supervisorJob.cancel()
    }

    fun clearMatchState(matchId: String) {
        lastNotifiedBallMap.remove(matchId)
        lastInsightAt.remove(matchId)
    }
}
