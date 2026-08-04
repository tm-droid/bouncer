package com.tanush.bouncer.services

import android.util.Log
import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.utils.BoundedLruCache
import com.tanush.bouncer.utils.BallMetric
import com.tanush.bouncer.utils.CommentaryFormatter
import com.tanush.bouncer.utils.ballMetricOfLine
import com.tanush.bouncer.utils.contentFingerprint
import com.tanush.bouncer.utils.sentryBreadcrumb
import com.tanush.bouncer.utils.sentryCaptureThrottled
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Commentary generation, entirely server-side.
 *
 * Every generation goes through `bouncer-generate-commentary`. The device used to hold Groq,
 * Cerebras and SambaNova keys and call them directly as a fallback chain — but those keys were
 * compiled into `BuildConfig`, which put them in clear text in the shipped APK, extractable with
 * `unzip` + `strings`. A fallback that leaks credentials to every user is not a fallback worth
 * having, so the chain, its cooldown breaker and the on-device hallucination check all moved into
 * the function, which is the only place a key can live without being handed out.
 *
 * When the function is unavailable, generation fails and the caller falls back to the raw source
 * line — reported, never silent. That is the deliberate trade: degraded commentary for nobody
 * holding a key.
 */
class CommentaryFusionService(
    // For testing: stub the one remote call this class makes. Returns the generated line, or null
    // to exercise the degraded path.
    private val generateRemote: (suspend (String, String, Boolean, Int, Double, String, Boolean, MatchContext?) -> String?)? = null
) {

    // Concurrent access from per-ball coroutines — all access is under bufferMutex so reads and
    // writes never see a torn list.
    //
    // The inner list has always been trimmed to maxBufferSize, but the outer map only ever gained
    // keys: one per match id, for the life of the process. Browsing through a day of fixtures left
    // a buffer behind for every match visited. Bounded by LRU rather than removed on completion,
    // because there is no completion event to hang the removal on — a completed match still has
    // commentary generated for it during backfill, and each of those balls wants the previous ones
    // for context.
    private val previousBallsBuffer = BoundedLruCache<String, MutableList<String>>(MAX_TRACKED_MATCHES)
    private val bufferMutex = Mutex()
    private val maxBufferSize = 6

    // In-flight dedup: concurrent fuse() calls for the same cache key share one LLM call.
    // Map is guarded by inFlightMutex for check-then-put atomicity. Deferred is launched
    // from serviceScope (not the caller's scope) so cancellation of one caller never
    // cancels shared work in progress for other callers.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightFuse = ConcurrentHashMap<String, Deferred<String>>()
    private val inFlightMutex = Mutex()

    // 7b: Phase-adaptive prompt selection
    private enum class PromptPhase { POWERPLAY, MIDDLE, DEATH, LAST_BALL }

    /**
     * Phase for a delivery, or null when the ball is unknown.
     *
     * Takes a [BallMetric] rather than a [Double] for two reasons. A Hundred counter (`72`) read as
     * `over.ball` becomes over 72 of a 20-over innings — past the end of the match, so every ball
     * scored as LAST_BALL. And the previous `ballNumber.toDoubleOrNull() ?: over.toDouble()`
     * defaulted a missing ball to `0.0`, which is why "With field restrictions in place" — the
     * POWERPLAY instruction — appears on death-over commentary throughout the cache.
     */
    private fun selectPhase(metric: BallMetric?, totalOvers: Int): PromptPhase? {
        if (metric == null) return null
        val ballsBowled = metric.ballsBowled
        val totalBalls = inningsBallsFor(metric, totalOvers)
        val ballsRemaining = totalBalls - ballsBowled
        val format = CricketConstants.formatFromInningsBalls(totalBalls)
        return when {
            ballsBowled < CricketConstants.powerplayEndBall(format) -> PromptPhase.POWERPLAY
            ballsRemaining <= 1 -> PromptPhase.LAST_BALL
            ballsRemaining <= deathBallsFor(format) -> PromptPhase.DEATH
            else -> PromptPhase.MIDDLE
        }
    }

    /** The Hundred is a fixed 100 balls and has no over count to scale from. */
    private fun inningsBallsFor(metric: BallMetric, totalOvers: Int): Int = when (metric) {
        is BallMetric.BallCount -> CricketConstants.HUNDRED_INNINGS_BALLS
        is BallMetric.OverBall -> totalOvers * CricketConstants.BALLS_PER_OVER
    }

    private fun deathBallsFor(format: CricketConstants.MatchFormat): Int = when (format) {
        CricketConstants.MatchFormat.ODI ->
            CricketConstants.ODI_DEATH_OVERS * CricketConstants.BALLS_PER_OVER
        else -> CricketConstants.T20_DEATH_OVERS * CricketConstants.BALLS_PER_OVER
    }

    companion object {
        const val FALLBACK_BALL_COMMENTARY = "Ball delivered. Play continues."

        /** Matches whose previous-ball context is kept. Well above the number watchable at once. */
        private const val MAX_TRACKED_MATCHES = 8

        // Sent as role:"system" in all ball-by-ball LLM calls.
        // Kept byte-identical to SYSTEM_INSTRUCTION in appwrite/functions/match-sync/src/llm.js —
        // the same ball can be narrated by either, so a drift between them shows up as two different
        // house styles in one match.
        // (generate-commentary takes the instruction from the request body, so there is no third copy.)
        //
        // There is deliberately NO example ball number here. The previous wording ended
        // "(e.g., 18.5: ...)", and when the ball number was missing from the user prompt the model
        // did the only thing a STRICT FORMAT rule with one example allows: it emitted the example.
        // That single token became 33% of the commentary cache, spread across a year of unrelated
        // matches. An example of a field the model must fill is an invitation to hallucinate it.
        const val SYSTEM_INSTRUCTION =
            "You are a professional cricket broadcast commentator. Write exactly one vivid, natural commentary line.\n" +
            "STRICT FORMAT: Begin with the ball number exactly as given in the input, followed by a colon. Never invent, guess or alter it; if the input has no ball number, begin with the action instead. One sentence only. No ellipsis at the end.\n" +
            "HALLUCINATION RULES: Use ONLY player names and match facts present in the input. Never invent runs, wickets, boundaries, or events not stated in the input. The atmosphere is yours — the facts are not.\n" +
            "STYLE: Cinematic, broadcast-quality. Sharp active verbs. Specific detail from the input. No clichés.\n" +
            "Output ONLY the commentary line itself — no preamble, no explanation, no 'Since there is...' framing."

        // Sent as role:"system" for non-ball event rewrites (fielding changes, session updates, etc.)
        const val EVENT_SYSTEM_INSTRUCTION =
            "You are a professional cricket broadcast commentator. Rewrite this match event as one clean broadcast sentence.\n" +
            "HALLUCINATION RULES: Describe ONLY what the input states. No invented statistics, history, or outcomes.\n" +
            "STYLE: Natural, present-tense, professional. No ball number prefix."

        // Collapsed from 4 FUSION_PROMPT_* and 4 SINGLE_SOURCE_PROMPT_* constants.
        // System instruction is sent separately; these are role:"user" content only.
        val FUSION_USER_TEMPLATE = """
            Commentary from source 1: {snippet1}
            Commentary from source 2: {snippet2}

            {ball_field}Bowler: {bowler} | Batsman: {batsman}
            Score: {score_context}
            {context_extras}
            Recent context (last 3 overs):
            {overs_summary}
            {previous_balls_context}

            Do not simply merge the two sources; pick the best or rephrase for freshness.
            {phase_instruction}""".trimIndent()

        val SINGLE_SOURCE_USER_TEMPLATE = """
            Ball commentary: {snippet}

            {ball_field}Bowler: {bowler} | Batsman: {batsman}
            Score: {score_context}
            {context_extras}
            {previous_balls_context}

            {phase_instruction}""".trimIndent()

        val VERIFICATION_PROMPT_TEMPLATE = """
            You are a cricket commentary fact-checker. Verify that the AI-generated commentary does not hallucinate.

            Source 1: {snippet1}
            Source 2: {snippet2}
            AI output: {ai_output}
            {ball_field}Bowler: {bowler} | Batsman: {batsman}

            Answer ONLY "PASS" if accurate and uses only correct names from the sources, or "FAIL" if it contains hallucinated names, invented details, or unsupported information.
            One word: PASS or FAIL.""".trimIndent()

        // Legacy aliases kept for any external callers referencing the old constants
        val FUSION_PROMPT_TEMPLATE = FUSION_USER_TEMPLATE
        val SINGLE_SOURCE_PROMPT_TEMPLATE = SINGLE_SOURCE_USER_TEMPLATE
    }

    // 7b: Select the right prompt template based on phase
    // No phase means no phase instruction. Previously an unknown ball scored as POWERPLAY and the
    // model was told "Field restrictions apply" regardless of where the innings actually was.
    private fun phaseInstructionFor(phase: PromptPhase?): String = when (phase) {
        null -> ""
        PromptPhase.POWERPLAY -> "Field restrictions apply. Focus on aggressive intent, early wicket stakes, and powerplay context."
        PromptPhase.MIDDLE    -> "Focus on partnership momentum, dot ball pressure, and any turning-point significance."
        PromptPhase.DEATH     -> "Death overs: capture yorker/slog sweep tension and required rate urgency."
        PromptPhase.LAST_BALL -> "Last ball: use maximum tension language — match outcome on the line."
    }

    private fun fusionPromptForPhase(phase: PromptPhase?): String =
        FUSION_USER_TEMPLATE.replace("{phase_instruction}", phaseInstructionFor(phase))

    private fun singleSourcePromptForPhase(phase: PromptPhase?): String =
        SINGLE_SOURCE_USER_TEMPLATE.replace("{phase_instruction}", phaseInstructionFor(phase))

    suspend fun addBallToBuffer(matchId: String, ball: String) {
        bufferMutex.withLock {
            val buffer = previousBallsBuffer.getOrPut(matchId) { mutableListOf() }
            buffer.add(ball)
            if (buffer.size > maxBufferSize) buffer.removeAt(0)
        }
    }

    suspend fun getPreviousBalls(matchId: String): List<String> = bufferMutex.withLock {
        previousBallsBuffer[matchId]?.toList() ?: emptyList()
    }

    suspend fun generateBallCommentary(
        matchId: String,
        ballNumber: String,
        rawSnippet: String,
        isMatchComplete: Boolean = false,
        bowler: String = "",
        batsman: String = "",
        scoreContext: String = "",
    ): String =
        withContext(Dispatchers.IO) {
            val cacheKey = "ball_ai:$matchId:$ballNumber"
            AICacheService.getCommentary(cacheKey)?.let { return@withContext it }

            val prompt = buildBallPrompt(matchId, ballNumber, rawSnippet, bowler, batsman, scoreContext)

            val result = callGenerateCommentaryFunction(
                cacheKey, prompt, isMatchComplete,
                maxTokens = 100, temperature = 0.7, systemInstruction = SYSTEM_INSTRUCTION
            ) ?: run {
                // "Ball delivered. Play continues." is the most visible degradation in the app and
                // used to report nothing. It now means the generation function is unreachable or
                // could not produce a usable line — there is no second path to try.
                sentryCaptureThrottled(
                    key = "commentary.ball.placeholder",
                    message = "Ball commentary fell back to the fixed placeholder — generation produced nothing",
                    data = mapOf("matchId" to matchId, "ballNumber" to ballNumber)
                )
                return@withContext FALLBACK_BALL_COMMENTARY
            }
            AICacheService.setCommentary(result, cacheKey, permanent = isMatchComplete)
            addBallToBuffer(matchId, result)
            result
        }

    private suspend fun buildBallPrompt(
        matchId: String,
        ballNumber: String,
        rawSnippet: String,
        bowler: String,
        batsman: String,
        scoreContext: String,
    ): String {
        val previousBalls = getPreviousBalls(matchId)
        val recentContext = if (previousBalls.isNotEmpty())
            "\nRecent: ${previousBalls.takeLast(3).joinToString(" | ")}" else ""
        val cleanSnippet = CommentaryFormatter.sanitize(rawSnippet)
        val contextLine = listOfNotNull(
            bowler.takeIf { it.isNotEmpty() }?.let { "Bowler: $it" },
            batsman.takeIf { it.isNotEmpty() }?.let { "Batsman: $it" },
            scoreContext.takeIf { it.isNotEmpty() }?.let { "Score: $it" },
        ).joinToString(" | ")
        // An absent ball number omits the field and the instruction rather than interpolating "".
        // "Start with : at the beginning." is what taught the model to reach for the prompt's own
        // example; a field the model cannot see is one it cannot be pressured into inventing.
        return buildString {
            // The label, verbatim — not "Ball $ballNumber". A Hundred label is already "Ball 72",
            // so prefixing produced "Ball Ball 72: " and asked the model to open with a string that
            // does not match the "Start with ..." instruction three lines later.
            if (ballNumber.isNotEmpty()) append("$ballNumber: ")
            append(cleanSnippet)
            if (contextLine.isNotEmpty()) append("\n$contextLine")
            if (recentContext.isNotEmpty()) append(recentContext)
            append("\nWrite one vivid broadcast commentary line.")
            if (ballNumber.isNotEmpty()) append(" Start with $ballNumber: at the beginning.")
        }
    }

    suspend fun rewriteEventCommentary(
        matchId: String,
        rawEvent: String,
        isMatchComplete: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val cleaned = CommentaryFormatter.sanitize(rawEvent)
        if (cleaned.isBlank()) return@withContext rawEvent
        val cacheKey = "event_ai:$matchId:${contentFingerprint(cleaned)}"
        AICacheService.getCommentary(cacheKey)?.let { return@withContext it }

        val prompt = "Match event: $cleaned\nRewrite as one broadcast commentary sentence."
        val result = callGenerateCommentaryFunction(
            cacheKey, prompt, isMatchComplete,
            maxTokens = 80, temperature = 0.6, systemInstruction = EVENT_SYSTEM_INSTRUCTION
        ) ?: run {
            // Returning the scraper's own sentence — same class of silent passthrough as the
            // fusion path, and the reason non-delivery lines can read as raw site text.
            sentryCaptureThrottled(
                key = "commentary.event.raw-fallback",
                message = "Event rewrite fell back to the raw source line — generation produced nothing",
                data = mapOf("matchId" to matchId)
            )
            return@withContext cleaned
        }
        AICacheService.setCommentary(result, cacheKey, permanent = isMatchComplete)
        result
    }

    /**
     * The cache key identifying one delivery.
     *
     * The key this replaces was `matchId:over:ballNumber:contentFingerprint(snippet1, snippet2,
     * oversSummary)`. `oversSummary` is itself an LLM paragraph regenerated at temperature 0.4 on
     * every poll, so the fingerprint — and therefore the key — changed every poll. Measured: one
     * delivery drew 54 distinct keys in four minutes, and across 602 cached rows not a single one
     * had `$updatedAt != $createdAt`, i.e. the cache never hit once. Never hash a generated value
     * into a key meant to identify an input.
     *
     * [deliveryTimestamp] is the identity because a ball number is not unique — a no-ball and its
     * re-bowl both arrive as `ballMetric=72`, so keying on the ball would serve the first
     * delivery's commentary for the second. Innings is in the key because Cricbuzz restarts the
     * ball count each innings.
     *
     * A zero timestamp means the source had no per-item metadata (legacy CSS scrape). That falls
     * back to a fingerprint of the snippets alone — still stable across polls, unlike the old key,
     * because nothing generated goes into it.
     */
    private fun deliveryCacheKey(
        prefix: String,
        matchId: String,
        inningsId: Int,
        deliveryTimestamp: Long,
        snippet1: String,
        snippet2: String
    ): String = if (deliveryTimestamp > 0L) {
        "$prefix:$matchId:$inningsId:$deliveryTimestamp"
    } else {
        "$prefix:$matchId:$inningsId:${contentFingerprint(snippet1, snippet2)}"
    }

    /**
     * The `Ball: ...` prompt field for [ballNumber], or empty when there is no ball number.
     *
     * Omitted entirely when unknown rather than rendered as `"Ball:  | Bowler: ..."` — an empty
     * labelled field is what the model used to fill from the prompt's own example.
     *
     * A Hundred label is already `"Ball 72"`, so the field name is dropped for it instead of
     * producing `"Ball: Ball 72"`.
     */
    private fun ballFieldOf(ballNumber: String): String = when {
        ballNumber.isEmpty() -> ""
        ballNumber.startsWith("Ball ") -> "$ballNumber | "
        else -> "Ball: $ballNumber | "
    }

    /**
     * The raw source line, returned when generation could not produce a usable one.
     *
     * Two things this deliberately does that its two former inline copies did not:
     *
     * 1. **Reports.** Falling back to the scraper's own words is a degradation, not a result. Both
     *    call sites used to `Log.w` and return, so a dead provider chain or a verifier that could
     *    not run rendered raw Cricbuzz text into the UI with nothing anywhere to say so.
     * 2. **Does not cache.** The verification-failure path wrote this string into the AI cache under
     *    the delivery's key with `permanent = isMatchComplete`, so one failed generation for a
     *    finished match pinned raw text in the shared Appwrite cache with a 2099 expiry and served
     *    it to every device forever. An unverified line is a failed generation; the retry on the
     *    next poll is worth more than a cached surrender. Mirrors the reasoning already applied to
     *    [summarizeOversWithAI]'s placeholder.
     */
    private fun rawFallback(
        reason: String,
        matchId: String,
        ballNumber: String,
        snippet1: String,
        snippet2: String
    ): String {
        val fallback = CommentaryFormatter.sanitize(snippet1.ifEmpty { snippet2 })
        Log.w("CommentaryFusionService", "Raw snippet fallback ($reason) — not cached")
        sentryCaptureThrottled(
            key = "commentary.fusion.raw-fallback",
            message = "Commentary fusion fell back to the raw source line: $reason",
            data = mapOf(
                "matchId" to matchId,
                "ballNumber" to ballNumber,
                "reason" to reason
            )
        )
        return fallback
    }

    /**
     * Records whether a fusion was served from cache, joined an in-flight call, or had to generate.
     *
     * The hit rate was structurally zero and nothing said so — the symptom only surfaced in an
     * Appwrite CSV export months later. One crumb per fusion is affordable; per-read crumbs are not
     * (see the note in `.claude/rules/appwrite-patterns.md` about the ~100-entry ring buffer).
     */
    private fun reportCacheOutcome(outcome: String, cacheKey: String) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.commentary"
            message = "Fuse cache $outcome"
            level = SentryLevel.INFO
            setData("outcome", outcome)
            setData("cacheKey", cacheKey)
        })
    }

    suspend fun fuse(
        snippet1: String,
        snippet2: String,
        oversSummary: String,
        matchId: String,
        over: Int,
        ballNumber: String,
        bowler: String,
        batsman: String,
        scoreContext: String,
        totalOvers: Int = 20,
        partnershipContext: String = "",
        matchupContext: String = "",
        isMatchComplete: Boolean = false,
        currentRunRate: Double? = null,
        requiredRunRate: Double? = null,
        inningsId: Int = 0,
        deliveryTimestamp: Long = 0L
    ): String {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.commentary"
            message = "Fusing commentary snippets"
            level = SentryLevel.INFO
            setData("matchId", matchId)
            setData("over", over)
            setData("ballNumber", ballNumber)
        })

        val cacheKey = deliveryCacheKey(
            "ai_commentary", matchId, inningsId, deliveryTimestamp, snippet1, snippet2
        )
        AICacheService.getCommentary(cacheKey)?.let {
            reportCacheOutcome("hit", cacheKey)
            return it
        }

        // In-flight dedup: if another coroutine is already computing this key, await its result
        // instead of firing a duplicate LLM call. serviceScope.async() parents the Deferred to
        // the class-level scope so cancellation of any individual caller does not cancel the
        // shared work — each awaiter observes cancellation independently on await().
        val deferred: Deferred<String> = inFlightMutex.withLock {
            val joined = inFlightFuse[cacheKey]
            reportCacheOutcome(if (joined != null) "in-flight-join" else "miss", cacheKey)
            inFlightFuse.getOrPut(cacheKey) {
                serviceScope.async {
                    try {
                        computeFuse(
                            snippet1, snippet2, oversSummary, matchId, over, ballNumber,
                            bowler, batsman, scoreContext, totalOvers,
                            partnershipContext, matchupContext, isMatchComplete, cacheKey,
                            currentRunRate, requiredRunRate
                        )
                    } finally {
                        // NonCancellable ensures the map entry is always removed even if this
                        // coroutine is cancelled while inFlightMutex is contended — without it,
                        // withLock would throw CancellationException and leave a stale entry.
                        withContext(NonCancellable) {
                            inFlightMutex.withLock { inFlightFuse.remove(cacheKey) }
                        }
                    }
                }
            }
        }
        return deferred.await()
    }

    /**
     * The filled prompt for one delivery — single-source when either snippet is missing, fusion
     * otherwise.
     *
     * Extracted because [computeFuse] and [streamCommentary] built this the same way in two places,
     * differing only in where the fields came from. Two copies of a template fill is how the two
     * paths drift into narrating the same ball differently.
     */
    private suspend fun buildDeliveryPrompt(context: MatchContext): String {
        val previousBalls = getPreviousBalls(context.matchId)
        val previousContext = if (previousBalls.isEmpty()) ""
            else "\nPrevious balls: ${previousBalls.joinToString(", ")}"

        // 7b: Select phase-adaptive template. Parsed from the label, so "Ball 72" stays a Hundred
        // counter instead of being read as over 72.
        val phase = selectPhase(ballMetricOfLine(context.ballNumber), context.totalOvers)

        val contextExtras = buildString {
            if (context.partnershipContext.isNotEmpty()) appendLine("Partnership: ${context.partnershipContext}")
            if (context.matchupContext.isNotEmpty()) appendLine("H2H: ${context.matchupContext}")
            if (context.currentRunRate != null) appendLine("CRR: ${"%.2f".format(context.currentRunRate)}")
            if (context.requiredRunRate != null) appendLine("RRR: ${"%.2f".format(context.requiredRunRate)}")
        }.trimEnd()

        val ballField = ballFieldOf(context.ballNumber)

        return if (context.snippet1.isEmpty() || context.snippet2.isEmpty()) {
            singleSourcePromptForPhase(phase)
                .replace("{snippet}", context.snippet1.ifEmpty { context.snippet2 })
                .replace("{ball_field}", ballField)
                .replace("{bowler}", context.bowler)
                .replace("{batsman}", context.batsman)
                .replace("{score_context}", context.scoreContext)
                .replace("{context_extras}", contextExtras)
                .replace("{previous_balls_context}", previousContext)
        } else {
            fusionPromptForPhase(phase)
                .replace("{snippet1}", context.snippet1)
                .replace("{snippet2}", context.snippet2)
                .replace("{ball_field}", ballField)
                .replace("{bowler}", context.bowler)
                .replace("{batsman}", context.batsman)
                .replace("{score_context}", context.scoreContext)
                .replace("{context_extras}", contextExtras)
                .replace("{overs_summary}", context.oversSummary)
                .replace("{previous_balls_context}", previousContext)
        }
    }

    @Suppress("LongParameterList")
    private suspend fun computeFuse(
        snippet1: String,
        snippet2: String,
        oversSummary: String,
        matchId: String,
        over: Int,
        ballNumber: String,
        bowler: String,
        batsman: String,
        scoreContext: String,
        totalOvers: Int,
        partnershipContext: String,
        matchupContext: String,
        isMatchComplete: Boolean,
        cacheKey: String,
        currentRunRate: Double? = null,
        requiredRunRate: Double? = null
    ): String {
        val context = MatchContext(
            matchId = matchId,
            over = over,
            ballNumber = ballNumber,
            snippet1 = snippet1,
            snippet2 = snippet2,
            oversSummary = oversSummary,
            bowler = bowler,
            batsman = batsman,
            scoreContext = scoreContext,
            totalOvers = totalOvers,
            partnershipContext = partnershipContext,
            matchupContext = matchupContext,
            isMatchComplete = isMatchComplete,
            currentRunRate = currentRunRate,
            requiredRunRate = requiredRunRate,
        )
        val prompt = buildDeliveryPrompt(context)

        // Generation and its hallucination check both happen inside the function now — including
        // the retry with a different provider on FAIL. A null here means it exhausted every option
        // it had, so there is nothing left to try locally: the raw source line is the answer, and
        // rawFallback reports it rather than passing it off as generated.
        val result = callGenerateCommentaryFunction(
            cacheKey = cacheKey,
            prompt = prompt,
            matchComplete = isMatchComplete,
            maxTokens = 256,
            temperature = 0.6,
            systemInstruction = SYSTEM_INSTRUCTION,
            verify = true,
            context = context,
        ) ?: return rawFallback(
            reason = "generation unavailable or unverified",
            matchId = matchId,
            ballNumber = ballNumber,
            snippet1 = snippet1,
            snippet2 = snippet2
        )

        AICacheService.setCommentary(result, cacheKey, permanent = isMatchComplete)
        return result
    }

    // 7a: Public cold Flow that streams commentary, trying providers in rotation
    fun streamCommentary(matchContext: MatchContext): Flow<String> = flow {
        // Same identity as the fuse key: keying the stream on over+ballNumber served the first
        // delivery of a no-ball pair back for the second.
        val cacheKey = deliveryCacheKey(
            "ai_commentary_stream",
            matchContext.matchId,
            matchContext.inningsId,
            matchContext.deliveryTimestamp,
            matchContext.snippet1,
            matchContext.snippet2
        )
        val cached = AICacheService.getCommentary(cacheKey)
        if (cached != null) {
            emit(cached)
            return@flow
        }

        val prompt = buildDeliveryPrompt(matchContext)

        // One emission, not a token stream. Generation moved to bouncer-generate-commentary when
        // the provider keys left the app, and a function returns a whole response — there is no SSE
        // endpoint to subscribe to. Nothing visible changes: both consumers already render the
        // arriving text through their own typewriter (MatchDetailViewModel's `_streamingCommentary`,
        // LiveActivityViewModel via LiveCommentaryManager), so the reveal was never the provider's
        // to drive.
        val generated = callGenerateCommentaryFunction(
            cacheKey = cacheKey,
            prompt = prompt,
            matchComplete = matchContext.isMatchComplete,
            maxTokens = 256,
            temperature = 0.6,
            systemInstruction = SYSTEM_INSTRUCTION,
            verify = true,
            context = matchContext,
        )
        if (generated == null) {
            sentryCaptureThrottled(
                key = "commentary.stream.unavailable",
                message = "Streaming commentary unavailable — the generation function produced nothing",
                data = mapOf("matchId" to matchContext.matchId, "ballNumber" to matchContext.ballNumber)
            )
            return@flow
        }
        emit(generated)
        // The function caches its own result server-side; this keeps the local tiers warm too.
        AICacheService.setCommentary(generated, cacheKey, permanent = matchContext.isMatchComplete)
        addBallToBuffer(matchContext.matchId, generated)
    }

    /** 7d: 24-line context window, with wicket/six lines repeated to weight them in the prompt. */
    private fun amplifiedLinesOf(commentaryLines: List<String>): List<String> =
        commentaryLines.take(24).flatMap { line ->
            if (line.contains("wicket", ignoreCase = true) || line.contains("six", ignoreCase = true)) {
                listOf(line, line)
            } else {
                listOf(line)
            }
        }

    private fun summaryCacheKey(
        matchId: String,
        over: Int,
        recapKey: String,
        lines: List<String>
    ): String = if (recapKey.isNotEmpty()) {
        "ai_summary:$matchId:$recapKey"
    } else {
        "ai_summary:$matchId:$over:${contentFingerprint(lines.joinToString(" "))}"
    }

    /**
     * A recap of the recent overs.
     *
     * [recapKey] identifies the passage of play being summarized — innings and balls-bowled — and
     * when supplied it replaces the content fingerprint in the cache key. The fingerprint was taken
     * over a rolling `takeLast(18)` window that shifted by one entry per poll, so consecutive calls
     * overlapped by 17 of 18 lines and still missed the cache every time, producing a fresh
     * paragraph describing almost exactly the same deliveries. Callers that have no ball position
     * (the strategic-insight path) leave it empty and keep the fingerprint.
     */
    suspend fun summarizeOversWithAI(
        commentaryLines: List<String>,
        matchId: String,
        over: Int,
        isMatchComplete: Boolean = false,
        recapKey: String = ""
    ): String {
        if (commentaryLines.isEmpty()) return "No recent commentary available."

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.commentary"
            message = "Summarizing overs with AI"
            level = SentryLevel.INFO
            setData("matchId", matchId)
            setData("over", over)
            setData("lineCount", commentaryLines.size)
        })

        val lines = amplifiedLinesOf(commentaryLines)
        val cacheKey = summaryCacheKey(matchId, over, recapKey, lines)
        AICacheService.getCommentary(cacheKey)?.let { return it }

        val numbered = lines.mapIndexed { idx, line -> "${idx + 1}. $line" }.joinToString("\n")
        val prompt = "You are a professional cricket commentator. Summarize the following ${lines.size} " +
            "ball-by-ball commentary lines into a single, engaging paragraph, highlighting key events and players.\n\n$numbered"

        // 7c: Token budget 256 → 512 for over summaries
        // No `verify` here: a recap has no per-ball source snippets to check against, so a verdict
        // could not mean anything and would only cost a second call.
        val result = callGenerateCommentaryFunction(cacheKey, prompt, isMatchComplete, maxTokens = 512, temperature = 0.4)
            // Generation failed (e.g. read-budget 402, or the function is down). Return the
            // placeholder WITHOUT caching, so this over is re-attempted instead of being poisoned.
            ?: run {
                sentryCaptureThrottled(
                    key = "commentary.recap.unavailable",
                    message = "Over recap unavailable — no provider produced a summary",
                    data = mapOf("matchId" to matchId, "over" to over)
                )
                return AICacheService.UNAVAILABLE_PLACEHOLDER
            }
        AICacheService.setCommentary(result, cacheKey, permanent = isMatchComplete)
        return result
    }

    /**
     * The one remote call this class makes.
     *
     * [verify] asks the function to fact-check its own output against [context]'s source snippets —
     * the hallucination guard that used to run here, now living where the provider keys do. It is
     * off for recap prompts, which have no snippets to check against and so cannot produce a
     * meaningful verdict.
     */
    @Suppress("LongParameterList")
    private suspend fun callGenerateCommentaryFunction(
        cacheKey: String,
        prompt: String,
        matchComplete: Boolean = false,
        maxTokens: Int = 512,
        temperature: Double = 0.7,
        systemInstruction: String = "",
        verify: Boolean = false,
        context: MatchContext? = null,
    ): String? {
        generateRemote?.let {
            return it(cacheKey, prompt, matchComplete, maxTokens, temperature, systemInstruction, verify, context)
        }
        val body = org.json.JSONObject().apply {
            put("cacheKey", cacheKey)
            put("prompt", prompt)
            put("matchComplete", matchComplete)
            put("maxTokens", maxTokens)
            put("temperature", temperature)
            if (systemInstruction.isNotEmpty()) put("systemInstruction", systemInstruction)
            if (verify) {
                put("verify", true)
                context?.let {
                    put("snippet1", it.snippet1)
                    put("snippet2", it.snippet2)
                    put("ball", it.ballNumber)
                    put("bowler", it.bowler)
                    put("batsman", it.batsman)
                }
            }
        }.toString()
        val response = AppwriteClient.executeFunction("bouncer-generate-commentary", body)
            ?: return null
        return try {
            org.json.JSONObject(response).optString("commentary").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            android.util.Log.w("CommentaryFusionService", "Commentary parse error: ${e.message}")
            sentryBreadcrumb("commentary_fusion", "Commentary response parse failed: ${e.message}", SentryLevel.WARNING)
            null
        }
    }

    /** Cancel the class-level scope; call when the service is torn down. */
    fun close() { serviceScope.cancel() }

    // Data class for streamCommentary context
    data class MatchContext(
        val matchId: String,
        val over: Int,
        val ballNumber: String,
        val snippet1: String,
        val snippet2: String,
        val oversSummary: String,
        val bowler: String,
        val batsman: String,
        val scoreContext: String,
        val totalOvers: Int = 20,
        val partnershipContext: String = "",
        val matchupContext: String = "",
        val isMatchComplete: Boolean = false,
        val currentRunRate: Double? = null,
        val requiredRunRate: Double? = null,
        val inningsId: Int = 0,
        val deliveryTimestamp: Long = 0L
    )
}
