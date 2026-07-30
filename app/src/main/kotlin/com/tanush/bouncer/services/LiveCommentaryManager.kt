package com.tanush.bouncer.services

import com.tanush.bouncer.utils.BallMetric
import com.tanush.bouncer.utils.BoundedLruCache
import com.tanush.bouncer.utils.ballMetricOfLine
import com.tanush.bouncer.utils.startsWithBallLabel
import android.util.Log
import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.models.TimelineEventType
import com.tanush.bouncer.network.CricBuzzScraper
import com.tanush.bouncer.network.CricInfoScraper
import com.tanush.bouncer.network.ScrapedCommentaryItem
import com.tanush.bouncer.network.prefixedText
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

/** Matches whose history, scrape context and recap state are kept. Well above the watchable count. */
private const val MAX_TRACKED_MATCHES = 8

/**
 * Deliveries that must be bowled before the recap is regenerated.
 *
 * The recap used to be regenerated on every poll over a rolling 18-line window, so consecutive
 * recaps overlapped by 17 of 18 deliveries and each one re-described the same passage of play.
 * One full over is the smallest window over which a recap has genuinely new material.
 */
private const val RECAP_INTERVAL_BALLS = CricketConstants.BALLS_PER_OVER

/** Deliveries handed to the recap prompt. */
private const val RECAP_WINDOW_LINES = 18

private const val WAITING_FOR_COMMENTARY = "Waiting for ball-by-ball commentary..."

class LiveCommentaryManager(
    private val summarizer: suspend (List<String>, String, Int) -> String = { lines, matchId, over -> CommentaryFusionService().summarizeOversWithAI(lines, matchId, over) }
) {
    private val cricbuzz = CricBuzzScraper()
    private val cricinfo = CricInfoScraper()
    private val scorer = CommentaryScorer()
    private val fusion = CommentaryFusionService()

    // Guards commentaryHistory, aiCommentaryHistories, lastNotifiedBallMap — never hold across suspension.
    private val stateMutex = Mutex()
    // Guards currentStreamJob cancel+reassign to prevent two concurrent streams.
    private val streamJobMutex = Mutex()

    private val commentaryHistory = mutableListOf<String>()
    private val historyLimit = 10
    // Scoped by match id. This was one flat list, so a single manager instance serving two matches
    // would have interleaved their deliveries into one recap window and one dedup window. The
    // manager is constructed per LiveActivityViewModel today, which made that latent rather than
    // active — scoping removes the hazard instead of relying on the construction site never moving.
    private val aiCommentaryHistories = BoundedLruCache<String, MutableList<String>>(MAX_TRACKED_MATCHES)
    private val aiHistoryLimit = 50

    // Per-ball live streaming commentary — resets on each new ball, accumulates tokens as they arrive
    private val _streamingCommentary = MutableStateFlow("")
    val streamingCommentary: StateFlow<String> = _streamingCommentary.asStateFlow()

    // Tracks the last ball number notified to avoid double alerts (e.g., "18.5")
    private val lastNotifiedBallMap = mutableMapOf<String, String>()

    /** Bowler, batsman and score as last scraped for one match. */
    private data class FetchedContext(val bowler: String, val batsman: String, val score: String) {
        companion object {
            val EMPTY = FetchedContext("", "", "")

            /** Prefers the striker — they are the one the delivery is bowled to. */
            fun of(details: CricBuzzScraper.ScrapedMatchDetails?): FetchedContext = FetchedContext(
                bowler = details?.bowler?.name ?: "",
                batsman = details?.batsmen?.firstOrNull { it.isStriker }?.name
                    ?: details?.batsmen?.firstOrNull()?.name ?: "",
                score = details?.score ?: ""
            )
        }
    }

    // Written by getBestCommentary / fetchSingleSourceFusion, read later by buildAiEntry. These were
    // three @Volatile scalars shared across every match the instance handled, so two concurrent
    // matches would have attributed one match's bowler to the other's ball.
    private val lastFetched = BoundedLruCache<String, FetchedContext>(MAX_TRACKED_MATCHES)

    /** The recap last generated for a match, and the balls-bowled count it was generated at. */
    private data class RecapState(val inningsId: Int, val ballsBowled: Int, val summary: String)
    private val recapStates = BoundedLruCache<String, RecapState>(MAX_TRACKED_MATCHES)

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

    /**
     * [findBestBallSnippet] over items rather than strings, so the chosen line keeps its timestamp
     * and innings — the identity the fuse cache key is built from. Same selection rule.
     */
    private fun findBestBallItem(
        items: List<ScrapedCommentaryItem>,
        keyNames: List<String>
    ): ScrapedCommentaryItem? {
        if (items.isEmpty()) return null
        return items.firstOrNull { scorer.isBallByBallCommentary(it.prefixedText()) }
            ?: items.maxByOrNull { scorer.score(it.prefixedText(), keyNames, emptyList(), false) }
            ?: items.first()
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
        return if (ballNumber.isNotEmpty() && !startsWithBallLabel(text)) {
            "$ballNumber $text"
        } else text
    }

    /**
     * The stored recap for [matchId] when it is still current, or null when one must be generated.
     *
     * A new innings always regenerates: the ball count restarts, so the elapsed-balls test would
     * otherwise read as "no progress" and serve the previous innings' recap.
     */
    private fun reusableRecap(matchId: String, inningsId: Int, ballsBowled: Int): String? {
        val prior = recapStates[matchId] ?: return null
        if (prior.inningsId != inningsId) return null
        if (ballsBowled - prior.ballsBowled >= RECAP_INTERVAL_BALLS) return null
        addBreadcrumb(
            "Recap reused — innings has not advanced a full over",
            mapOf(
                "matchId" to matchId,
                "ballsBowled" to ballsBowled.toString(),
                "generatedAtBall" to prior.ballsBowled.toString()
            )
        )
        return prior.summary
    }

    /**
     * The recent-overs recap, regenerated only once the innings has advanced a full over.
     *
     * This used to call the LLM on every poll. The window is the last [RECAP_WINDOW_LINES]
     * deliveries and a poll shifts it by at most one, so successive recaps described almost exactly
     * the same passage of play — that is the "re-summarizes the same handful of deliveries" symptom.
     * It was also what made the fuse cache key churn, because the recap was hashed into it.
     */
    private suspend fun computeOversSummary(
        matchId: String,
        metric: BallMetric?,
        inningsId: Int,
        fallback: String,
        isMatchComplete: Boolean
    ): String {
        val ballsBowled = metric?.ballsBowled ?: 0
        reusableRecap(matchId, inningsId, ballsBowled)?.let { return it }

        val recent = stateMutex.withLock {
            val history = historyForLocked(matchId)
            history.takeLast(min(history.size, RECAP_WINDOW_LINES))
        }
        if (recent.isEmpty()) return fallback

        val over = (metric as? BallMetric.OverBall)?.over ?: 0
        return try {
            val summary = fusion.summarizeOversWithAI(
                commentaryLines = recent,
                matchId = matchId,
                over = over,
                isMatchComplete = isMatchComplete,
                recapKey = "$inningsId:$ballsBowled"
            )
            // The placeholder means generation failed and was deliberately not cached. Storing it
            // as the recap state would serve it back for a full over.
            if (summary == AICacheService.UNAVAILABLE_PLACEHOLDER) return fallback
            recapStates[matchId] = RecapState(inningsId, ballsBowled, summary)
            addBreadcrumb(
                "Recap regenerated",
                mapOf("matchId" to matchId, "ballsBowled" to ballsBowled.toString())
            )
            summary
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "summarizeOversWithAI failed, using raw line", e)
            Sentry.captureException(e)
            fallback
        }
    }

    /**
     * Fuse the two sources described by [context].
     *
     * Takes the same [CommentaryFusionService.MatchContext] the streaming job takes. Both call sites
     * used to spell out fourteen arguments twice over — once here and once for the stream — which is
     * how the streaming path ended up keyed on `over:ballNumber` while the fusion path was keyed on
     * a content hash.
     */
    private suspend fun fuseLines(context: CommentaryFusionService.MatchContext): String {
        return try {
            fusion.fuse(
                snippet1 = context.snippet1,
                snippet2 = context.snippet2,
                oversSummary = context.oversSummary,
                matchId = context.matchId,
                over = context.over,
                ballNumber = context.ballNumber,
                bowler = context.bowler,
                batsman = context.batsman,
                scoreContext = context.scoreContext,
                isMatchComplete = context.isMatchComplete,
                currentRunRate = context.currentRunRate,
                requiredRunRate = context.requiredRunRate,
                inningsId = context.inningsId,
                deliveryTimestamp = context.deliveryTimestamp
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "fusion.fuse failed, using raw snippet", e)
            Sentry.captureException(e)
            if (context.snippet2.isEmpty()) context.snippet1
            else "${context.snippet1} / ${context.snippet2}"
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
        val scraped = FetchedContext.of(details)
        lastFetched[matchId] = scraped

        val buzzItems = safeFetch(emptyList<ScrapedCommentaryItem>()) {
            cricbuzz.fetchLiveCommentaryItems(cricbuzzUrl)
        }
        val infoSnippets = safeFetch(emptyList<String>()) { cricinfo.fetchLiveCommentary(cricinfoUrl) }

        val buzzItem = findBestBallItem(buzzItems, keyNames)
        val buzzLine = buzzItem?.prefixedText() ?: ""
        val infoLine = findBestBallSnippet(infoSnippets, keyNames)

        // Read the metric, not the raw string: ballNumberOf only knows `over.ball`, so routing on it
        // would send every Hundred delivery ("Ball 72: ...") to the event path. The item's own field
        // is preferred over re-reading the prefix we just wrote; the line parse is the fallback for
        // the legacy CSS scrape, which has no per-item metadata.
        val ballMetric = buzzItem?.ballMetric
            ?: ballMetricOfLine(buzzLine.trimStart())
            ?: ballMetricOfLine(infoLine.trimStart())

        // A line with no ball number is not a delivery. Cricbuzz says so explicitly — non-deliveries
        // carry "$undefined" in ballMetric (see ballMetricOf) — and that beats the text heuristic,
        // which reads a Playing XI as ball-by-ball because it contains a ball-indicator keyword.
        // Before this, a pre-match squad list reached the ball prompt, the model was told to narrate
        // it as a delivery, and the hallucination check then failed it into the cache as a raw line.
        val looksLikeBall = hasBallContent(buzzLine, infoLine)
        if (ballMetric == null || !looksLikeBall) {
            if (ballMetric == null && looksLikeBall) reportScorerDisagreement(matchId, buzzLine)
            return rewriteAsEvent(matchId, buzzLine.ifEmpty { infoLine }, isMatchComplete)
        }

        val ballNumber = ballMetric.label
        // The Hundred has no overs; 0 is the honest answer, not over zero.
        val over = (ballMetric as? BallMetric.OverBall)?.over ?: 0
        val inningsId = buzzItem?.inningsId ?: 0
        val oversSummary = computeOversSummary(matchId, ballMetric, inningsId, buzzLine, isMatchComplete)

        val context = CommentaryFusionService.MatchContext(
            matchId = matchId,
            over = over,
            ballNumber = ballNumber,
            snippet1 = buzzLine,
            snippet2 = infoLine,
            oversSummary = oversSummary,
            bowler = scraped.bowler,
            batsman = scraped.batsman,
            scoreContext = scraped.score,
            isMatchComplete = isMatchComplete,
            currentRunRate = details?.currentRunRate?.toDoubleOrNull(),
            requiredRunRate = details?.requiredRunRate?.toDoubleOrNull(),
            inningsId = inningsId,
            deliveryTimestamp = buzzItem?.timestamp ?: 0L
        )
        launchStreamingJob(context)

        val snapshotHistory = stateMutex.withLock { commentaryHistory.toList() }
        val resultValue = when (scorer.compare(buzzLine, infoLine, keyNames, snapshotHistory)) {
            CommentaryScorer.ComparisonResult.UseFirst -> buzzLine
            CommentaryScorer.ComparisonResult.UseSecond -> infoLine
            CommentaryScorer.ComparisonResult.FuseBoth -> fuseLines(context)
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
        val context = lastFetched[cricbuzzId] ?: FetchedContext.EMPTY
        return try {
            val raw = fusion.generateBallCommentary(
                matchId = cricbuzzId,
                ballNumber = ballNum,
                rawSnippet = resultValue.take(200),
                isMatchComplete = isMatchComplete,
                bowler = context.bowler,
                batsman = context.batsman,
                scoreContext = context.score,
            )
            if (!startsWithBallLabel(raw)) "$ballNum $raw" else raw
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
        val history = stateMutex.withLock { historyForLocked(cricbuzzId).toList().reversed() }
        return@withContext if (aiEntry != null) listOf(aiEntry) + history else history
    }

    private suspend fun fetchSingleSourceFusion(
        matchId: String,
        url: String,
        keyNames: List<String>,
        isMatchComplete: Boolean
    ): String {
        addBreadcrumb("Fetching single source fusion", mapOf("matchId" to matchId))

        val buzzItems = safeFetch(emptyList<ScrapedCommentaryItem>()) {
            cricbuzz.fetchLiveCommentaryItems(url)
        }
        val buzzItem = findBestBallItem(buzzItems, keyNames)
        val buzzLine = buzzItem?.prefixedText() ?: ""
        val details: CricBuzzScraper.ScrapedMatchDetails? = safeFetch(null) {
            cricbuzz.fetchBallByBallAndBatsmen(matchId)
        }

        // Same rule as getBestCommentary: no ball number means it is not a delivery, so it must not
        // reach the ball prompt. Cricbuzz is the only source on this path, so its "$undefined" is
        // decisive.
        val singleMetric = buzzItem?.ballMetric ?: ballMetricOfLine(buzzLine.trimStart())
        if (singleMetric == null) {
            if (scorer.isBallByBallCommentary(buzzLine)) reportScorerDisagreement(matchId, buzzLine)
            return rewriteAsEvent(matchId, buzzLine, isMatchComplete)
        }
        val singleBallNumber = singleMetric.label
        val singleOver = (singleMetric as? BallMetric.OverBall)?.over ?: 0
        val scraped = FetchedContext.of(details)
        lastFetched[matchId] = scraped

        val singleInningsId = buzzItem?.inningsId ?: 0
        val oversSummary =
            computeOversSummary(matchId, singleMetric, singleInningsId, buzzLine, isMatchComplete)

        val context = CommentaryFusionService.MatchContext(
            matchId = matchId,
            over = singleOver,
            ballNumber = singleBallNumber,
            snippet1 = buzzLine,
            snippet2 = "",
            oversSummary = oversSummary,
            bowler = scraped.bowler,
            batsman = scraped.batsman,
            scoreContext = scraped.score,
            isMatchComplete = isMatchComplete,
            currentRunRate = details?.currentRunRate?.toDoubleOrNull(),
            requiredRunRate = details?.requiredRunRate?.toDoubleOrNull(),
            inningsId = singleInningsId,
            deliveryTimestamp = buzzItem?.timestamp ?: 0L
        )
        launchStreamingJob(context)

        return prefixBallNumber(singleBallNumber, fuseLines(context))
    }

    /** The AI commentary history for [matchId]. Caller MUST hold stateMutex. */
    private fun historyForLocked(matchId: String): MutableList<String> =
        aiCommentaryHistories.getOrPut(matchId) { mutableListOf() }

    /**
     * Insert or replace entry for the same ball number; replacement only if newer line is at least as long.
     * Caller MUST hold stateMutex.
     */
    private fun upsertAiHistoryEntryLocked(matchId: String, line: String) {
        val history = historyForLocked(matchId)
        val ballNum = extractBallNumber(line.trimStart())
        if (ballNum == null) {
            history.add(line)
            return
        }
        val idx = history.indexOfLast { extractBallNumber(it.trimStart()) == ballNum }
        if (idx >= 0) {
            if (line.length >= history[idx].length) {
                history[idx] = line
            }
        } else {
            history.add(line)
        }
    }

    /** Caller MUST hold stateMutex. */
    private fun trimAiHistoryLocked(matchId: String) {
        val history = historyForLocked(matchId)
        while (history.size > aiHistoryLimit) history.removeAt(0)
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
        val matchKey = matchId ?: "global"
        stateMutex.withLock {
            if (historyForLocked(matchKey).lastOrNull() == resultValue) return@withLock

            // CricBuzz sends progressive updates for the same ball (short → full).
            // Replace any earlier entry for the same ball number with the newer (longer) text.
            upsertAiHistoryEntryLocked(matchKey, resultValue)

            // Trim to a bounded window so long matches don't accumulate hundreds of balls.
            trimAiHistoryLocked(matchKey)
        }

        // Notification Logic - MAGIC PRECISION (outside mutex — no state access needed after here)
        try {
            if (shouldSkipNotification(resultValue, matchKey)) return
            dispatchKeyEvent(resultValue, matchKey)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Sentry.captureException(e)
        }
    }

    /**
     * The ball label at the head of [text] — `"48.6"` or `"Ball 72"` — or null when there is none.
     *
     * Goes through [ballMetricOfLine] rather than `ballNumberOf` so history dedup, notification
     * dedup and the AI entry all work for The Hundred instead of treating every Hundred delivery
     * as unnumbered.
     */
    private fun extractBallNumber(text: String): String? = ballMetricOfLine(text)?.label

    /** Route a non-delivery line to the event rewrite, or report that nothing has arrived yet. */
    private suspend fun rewriteAsEvent(matchId: String, line: String, isMatchComplete: Boolean): String =
        if (line.isEmpty()) WAITING_FOR_COMMENTARY
        else fusion.rewriteEventCommentary(matchId, line, isMatchComplete)

    /**
     * Whether either source line reads as an actual delivery.
     *
     * Only consulted once a ball number exists — the ball number is the authoritative signal and
     * this is the secondary text check.
     */
    private fun hasBallContent(buzzLine: String, infoLine: String): Boolean =
        scorer.isBallByBallCommentary(buzzLine) || scorer.isBallByBallCommentary(infoLine)

    /**
     * Records that [CommentaryScorer.isBallByBallCommentary] called a line a delivery while
     * Cricbuzz marked it `"$undefined"`.
     *
     * This is a scorer-accuracy signal, **not** an extraction-failure alarm, and a small non-zero
     * count is expected. The keyword heuristic reads bowler changes ("Ashton Agar [1.0-0-23-0] is
     * back into the attack") and arrival notices ("Shimron Hetmyer, left handed bat, comes to the
     * crease") as ball-by-ball; the ball number is authoritative and overrides it, so these are
     * correctly routed to the event path. Worth watching because a sharp rise on lines that really
     * are deliveries would mean `ballMetric` extraction regressed.
     */
    private fun reportScorerDisagreement(matchId: String, line: String) {
        addBreadcrumb(
            "Scorer called a non-delivery ball-by-ball — ball number overrides",
            mapOf("matchId" to matchId, "linePrefix" to line.take(40))
        )
    }

    private fun triggerAiInsight(commentary: String, matchId: String) {
        if (!aiInsightsEnabled) return
        val now = System.currentTimeMillis()
        val last = lastInsightAt[matchId] ?: 0L
        if (now - last < aiInsightCooldownMs) return

        lastInsightAt[matchId] = now
        val insightOver = (ballMetricOfLine(commentary) as? BallMetric.OverBall)?.over ?: 0
        scope.launch {
            try {
                // Snapshot history under mutex before the suspension point in summarizer.
                val snapshot = stateMutex.withLock { historyForLocked(matchId).takeLast(6) }
                val insight = summarizer(snapshot, matchId, insightOver)
                onKeyEvent?.invoke("AI Strategic Insight", insight)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Sentry.captureException(e)
            }
        }
    }

    /**
     * Pre-populate [matchId]'s AI history with lines fetched before the polling loop starts.
     * Existing entries are not duplicated; newer/longer entries for the same ball replace older ones.
     */
    suspend fun seedHistory(matchId: String, lines: List<String>) {
        stateMutex.withLock {
            val history = historyForLocked(matchId)
            for (line in lines.reversed().filter { it.length > 5 }) {
                if (history.lastOrNull() == line) continue
                val ballNum = extractBallNumber(line.trimStart())
                if (ballNum == null) {
                    if (!history.contains(line)) history.add(line)
                } else {
                    upsertAiHistoryEntryLocked(matchId, line)
                }
            }
            trimAiHistoryLocked(matchId)
        }
    }

    suspend fun getSeededHistory(matchId: String): List<String> =
        stateMutex.withLock { historyForLocked(matchId).toList().reversed() }

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
        // All three are LRU-bounded, so this is about forgetting a match promptly rather than about
        // bounding growth. Safe without stateMutex: BoundedLruCache synchronizes every method, and a
        // concurrent reader simply gets a fresh empty history, which is the intended post-clear state.
        aiCommentaryHistories.remove(matchId)
        lastFetched.remove(matchId)
        recapStates.remove(matchId)
    }
}
