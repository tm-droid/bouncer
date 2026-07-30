package com.tanush.bouncer.services

import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.network.HttpClientProvider
import com.tanush.bouncer.utils.BoundedLruCache
import com.tanush.bouncer.utils.BallMetric
import com.tanush.bouncer.utils.CommentaryFormatter
import com.tanush.bouncer.utils.ballMetricOfLine
import com.tanush.bouncer.utils.contentFingerprint
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SpanStatus
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap

class CommentaryFusionService(
    private val client: OkHttpClient = HttpClientProvider.sharedClient,
    // For testing: inject providers with non-empty keys to bypass the ApiKeys.isEmpty() guard.
    // Each triple is (providerName, apiKey, modelId); URL is auto-generated and overridden by fakeClient.
    testProviders: List<Triple<String, String, String>>? = null
) {
    private data class Provider(val name: String, val url: String, val key: String, val model: String)

    // Model ids are duplicated in appwrite/functions/match-sync/src/llm.js and
    // appwrite/functions/generate-commentary/src/main.js. There is no shared config channel between
    // Kotlin and two Node functions without codegen or a fetched manifest, and both cost more than a
    // once-per-bump edit in three known places — so bump all three together.
    private val providers: List<Provider> = testProviders?.map { (name, key, model) ->
        Provider(name, "http://test-$name", key, model)
    } ?: listOf(
        Provider("Groq",      "https://api.groq.com/openai/v1/chat/completions",    ApiKeys.groq,      "llama-3.3-70b-versatile"),
        Provider("Cerebras",  "https://api.cerebras.ai/v1/chat/completions",         ApiKeys.cerebras,  "llama-3.3-70b"),
        Provider("SambaNova", "https://api.sambanova.ai/v1/chat/completions",        ApiKeys.sambaNova, "Meta-Llama-3.3-70B-Instruct"),
    )
    private val providerCooldowns = ConcurrentHashMap<String, Long>()
    private val providerFailureCount = ConcurrentHashMap<String, Int>()

    /**
     * Open a backoff window for [providerName], skipping it until the window expires.
     *
     * Only a 429 used to reach here. A 500/502/503 or a socket timeout threw straight out of
     * [callProvider] / [streamFromProvider] with no cooldown recorded, so a provider that was not
     * rate-limiting but simply down got retried on *every ball*, and every ball paid its full
     * timeout before the chain fell through to the next provider.
     *
     * The window is opened at most once per outage. Per-ball coroutines run concurrently and would
     * otherwise each increment the attempt counter for the same failure, jumping the backoff several
     * steps at once — the `compute` block is what makes the check-then-open atomic.
     *
     * This deliberately does **not** copy [com.tanush.bouncer.network.CricInfoScraper]'s half-open
     * counter reset. That breaker trips on a failure *count*, so it must forgive the count to give
     * the source a second chance. This one backs off on the attempt *number*, so resetting it on
     * window expiry would flatten the exponential curve into a fixed 30-second retry. The counter is
     * cleared on success instead, which is the same intent expressed for a backoff breaker.
     */
    private fun openCooldown(providerName: String, reason: String) {
        var attempt = 0
        var cooldownMs = 0L
        providerCooldowns.compute(providerName) { _, existing ->
            val now = System.currentTimeMillis()
            if (existing != null && existing > now) return@compute existing
            attempt = providerFailureCount.merge(providerName, 1, Int::plus) ?: 1
            cooldownMs = backoffDelayMs(attempt, BASE_COOLDOWN_MS, MAX_COOLDOWN_MS)
            now + cooldownMs
        }
        if (cooldownMs == 0L) return
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.commentary"
            message = "$providerName $reason attempt=$attempt — ${cooldownMs}ms cooldown"
            level = SentryLevel.WARNING
        })
    }

    /** Clear both the cooldown window and the attempt counter after a provider answers. */
    private fun recordProviderSuccess(providerName: String) {
        providerFailureCount.remove(providerName)
        providerCooldowns.remove(providerName)
    }

    /**
     * Why a status code means "this provider is unavailable", or null when it does not.
     *
     * A 4xx other than 429 is our request being wrong — a bad model id, a malformed body, a revoked
     * key. Backing off would only delay a failure that repeats identically, so those still fall
     * through to the next provider without opening a window.
     */
    private fun cooldownReasonFor(code: Int): String? = when {
        code == HTTP_TOO_MANY_REQUESTS -> "rate-limited"
        code >= HTTP_SERVER_ERROR -> "server error $code"
        else -> null
    }

    /**
     * True for a read/connect timeout. [SocketTimeoutException] extends [InterruptedIOException], so
     * this covers both. Connection refused and DNS failures are excluded on purpose: those are the
     * device's own connectivity, and cooling down all three providers for it would leave commentary
     * dead for minutes after the network comes back.
     */
    private fun isTimeout(e: Throwable): Boolean = e is InterruptedIOException

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
    // Temp=0.0 verify calls are fully deterministic: same inputs → same PASS/FAIL every time.
    // Cache by content hash to avoid redundant AI calls on retry paths.
    // LinkedHashMap in insertion order — FIFO eviction. All access guarded by verifyCacheMutex.
    // Keyed on the full text, not its 32-bit hashCode: a collision there returned ANOTHER ball's
    // PASS/FAIL verdict. Bounded by the existing 512-entry FIFO eviction below, so a String key
    // costs no extra machinery and skips the hashing.
    private val verifyCache = LinkedHashMap<String, Boolean>(512)
    private val verifyCacheMutex = Mutex()
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
        // P2-6: Exponential backoff after a provider failure, with jitter.
        // attempt N → BASE*2^(N-1) + jitter, capped at MAX.
        const val BASE_COOLDOWN_MS = 30_000L
        const val MAX_COOLDOWN_MS = 300_000L

        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR = 500

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

            val result = callGenerateCommentaryFunction(cacheKey, prompt, isMatchComplete, maxTokens = 100, temperature = 0.7, systemInstruction = SYSTEM_INSTRUCTION)
                ?: callProviders(prompt, maxTokens = 100, temperature = 0.7, systemInstruction = SYSTEM_INSTRUCTION)
                ?: return@withContext FALLBACK_BALL_COMMENTARY
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
        val result = callGenerateCommentaryFunction(cacheKey, prompt, isMatchComplete, maxTokens = 80, temperature = 0.6, systemInstruction = EVENT_SYSTEM_INSTRUCTION)
            ?: callProviders(prompt, maxTokens = 80, temperature = 0.6, systemInstruction = EVENT_SYSTEM_INSTRUCTION)
            ?: return@withContext cleaned
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
        val previousBalls = getPreviousBalls(matchId)
        val previousContext = if (previousBalls.isEmpty()) ""
            else "\nPrevious balls: ${previousBalls.joinToString(", ")}"

        // 7b: Select phase-adaptive template. Parsed from the label, so "Ball 72" stays a Hundred
        // counter instead of being read as over 72.
        val phase = selectPhase(ballMetricOfLine(ballNumber), totalOvers)

        val contextExtras = buildString {
            if (partnershipContext.isNotEmpty()) appendLine("Partnership: $partnershipContext")
            if (matchupContext.isNotEmpty()) appendLine("H2H: $matchupContext")
            if (currentRunRate != null) appendLine("CRR: ${"%.2f".format(currentRunRate)}")
            if (requiredRunRate != null) appendLine("RRR: ${"%.2f".format(requiredRunRate)}")
        }.trimEnd()

        val ballField = ballFieldOf(ballNumber)

        val prompt = if (snippet1.isEmpty() || snippet2.isEmpty()) {
            singleSourcePromptForPhase(phase)
                .replace("{snippet}", snippet1.ifEmpty { snippet2 })
                .replace("{ball_field}", ballField)
                .replace("{bowler}", bowler)
                .replace("{batsman}", batsman)
                .replace("{score_context}", scoreContext)
                .replace("{context_extras}", contextExtras)
                .replace("{previous_balls_context}", previousContext)
        } else {
            fusionPromptForPhase(phase)
                .replace("{snippet1}", snippet1)
                .replace("{snippet2}", snippet2)
                .replace("{ball_field}", ballField)
                .replace("{bowler}", bowler)
                .replace("{batsman}", batsman)
                .replace("{score_context}", scoreContext)
                .replace("{context_extras}", contextExtras)
                .replace("{overs_summary}", oversSummary)
                .replace("{previous_balls_context}", previousContext)
        }

        val functionResult = callGenerateCommentaryFunction(cacheKey, prompt, isMatchComplete, maxTokens = 256, temperature = 0.6, systemInstruction = SYSTEM_INSTRUCTION)
        val (result, usedProviderName) = if (functionResult != null) {
            Pair(functionResult, "appwrite-function")
        } else {
            callProvidersTracked(prompt, maxTokens = 256, temperature = 0.6, systemInstruction = SYSTEM_INSTRUCTION)
                ?: return CommentaryFormatter.sanitize(snippet1.ifEmpty { snippet2 })
        }

        val verified = verifyCommentary(result, snippet1, snippet2, ballNumber, bowler, batsman)
        if (!verified) {
            // 7e: FAIL → retry with next available provider before falling back to raw snippet
            Log.w("CommentaryFusionService", "Hallucination check FAILED — retrying with next provider (used: $usedProviderName)")
            val retried = retryWithNextProvider(prompt, alreadyTriedProvider = usedProviderName, systemInstruction = SYSTEM_INSTRUCTION)
            if (retried != null) {
                val retriedVerified = verifyCommentary(retried, snippet1, snippet2, ballNumber, bowler, batsman)
                if (retriedVerified) {
                    AICacheService.setCommentary(retried, cacheKey, permanent = isMatchComplete)
                    return retried
                }
                // retried also unverified — fall through to raw snippet fallback
            }
            val fallback = CommentaryFormatter.sanitize(snippet1.ifEmpty { snippet2 })
            Log.w("CommentaryFusionService", "All providers exhausted — using raw snippet fallback")
            AICacheService.setCommentary(fallback, cacheKey, permanent = isMatchComplete)
            return fallback
        }

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

        val previousBalls = getPreviousBalls(matchContext.matchId)
        val previousContext = if (previousBalls.isEmpty()) ""
            else "\nPrevious balls: ${previousBalls.joinToString(", ")}"

        val phase = selectPhase(ballMetricOfLine(matchContext.ballNumber), matchContext.totalOvers)

        val streamContextExtras = buildString {
            if (matchContext.partnershipContext.isNotEmpty()) appendLine("Partnership: ${matchContext.partnershipContext}")
            if (matchContext.matchupContext.isNotEmpty()) appendLine("H2H: ${matchContext.matchupContext}")
            if (matchContext.currentRunRate != null) appendLine("CRR: ${"%.2f".format(matchContext.currentRunRate)}")
            if (matchContext.requiredRunRate != null) appendLine("RRR: ${"%.2f".format(matchContext.requiredRunRate)}")
        }.trimEnd()

        val streamBallField = ballFieldOf(matchContext.ballNumber)

        val prompt = if (matchContext.snippet1.isEmpty() || matchContext.snippet2.isEmpty()) {
            singleSourcePromptForPhase(phase)
                .replace("{snippet}", matchContext.snippet1.ifEmpty { matchContext.snippet2 })
                .replace("{ball_field}", streamBallField)
                .replace("{bowler}", matchContext.bowler)
                .replace("{batsman}", matchContext.batsman)
                .replace("{score_context}", matchContext.scoreContext)
                .replace("{context_extras}", streamContextExtras)
                .replace("{previous_balls_context}", previousContext)
        } else {
            fusionPromptForPhase(phase)
                .replace("{snippet1}", matchContext.snippet1)
                .replace("{snippet2}", matchContext.snippet2)
                .replace("{ball_field}", streamBallField)
                .replace("{bowler}", matchContext.bowler)
                .replace("{batsman}", matchContext.batsman)
                .replace("{score_context}", matchContext.scoreContext)
                .replace("{context_extras}", streamContextExtras)
                .replace("{overs_summary}", matchContext.oversSummary)
                .replace("{previous_balls_context}", previousContext)
        }

        val now = System.currentTimeMillis()
        val fullBuilder = StringBuilder()
        var succeeded = false
        var hasEmittedAny = false

        for (provider in providers) {
            if (!currentCoroutineContext().isActive) break
            if (provider.key.isEmpty()) continue
            val cooldownUntil = providerCooldowns[provider.name] ?: 0L
            if (cooldownUntil > now) continue

            val streamStartMs = System.currentTimeMillis()
            val streamSpan = Sentry.getSpan()?.startChild("gen_ai.streaming", provider.name)
            streamSpan?.setData("gen_ai.system", provider.name.lowercase())
            streamSpan?.setData("gen_ai.request.model", provider.model)
            try {
                streamFromProvider(provider, prompt, maxTokens = 256, temperature = 0.6f, systemInstruction = SYSTEM_INSTRUCTION)
                    .collect { chunk ->
                        if (currentCoroutineContext().isActive) {
                            emit(chunk)
                            fullBuilder.append(chunk)
                            hasEmittedAny = true
                        }
                    }
                val streamElapsedMs = System.currentTimeMillis() - streamStartMs
                streamSpan?.setData("ai.latency_ms", streamElapsedMs)
                streamSpan?.finish(SpanStatus.OK)
                Sentry.metrics().distribution("ai.stream_latency_ms", streamElapsedMs.toDouble(), "millisecond")
                recordProviderSuccess(provider.name)
                succeeded = true
                break
            } catch (e: Exception) {
                streamSpan?.finish(SpanStatus.INTERNAL_ERROR)
                if (e is CancellationException) throw e
                if (hasEmittedAny) {
                    // Partial output already sent to consumer — don't retry with another provider
                    throw e
                }
                fullBuilder.clear()
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "service.commentary"
                    message = "${provider.name} stream failed — ${e.message}"
                    level = SentryLevel.WARNING
                })
                // continue to next provider
            }
        }

        // Cache write after stream completes
        if (succeeded && fullBuilder.isNotEmpty()) {
            val fullText = fullBuilder.toString().trim()
            AICacheService.setCommentary(fullText, cacheKey, permanent = matchContext.isMatchComplete)
            addBallToBuffer(matchContext.matchId, fullText)
        }
    }

    // 7a: SSE streaming for a single provider
    private fun streamFromProvider(
        provider: Provider,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        systemInstruction: String = ""
    ): Flow<String> = flow {
        val messages = JSONArray()
        if (systemInstruction.isNotEmpty()) {
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemInstruction)
            })
        }
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })
        val body = JSONObject().apply {
            put("model", provider.model)
            put("messages", messages)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("stream", true)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(provider.url)
            .header("Authorization", "Bearer ${provider.key}")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        val response = try {
            withContext(Dispatchers.IO) { client.newCall(request).execute() }
        } catch (e: IOException) {
            if (isTimeout(e)) openCooldown(provider.name, "timed out (stream)")
            throw e
        }
        try {
            // Check HTTP status BEFORE reading the SSE stream
            val reason = cooldownReasonFor(response.code)
            if (reason != null) {
                openCooldown(provider.name, "$reason (stream)")
                throw IOException("${provider.name} $reason")
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

            val source = response.body?.source() ?: throw IOException("Null body from ${provider.name}")
            while (currentCoroutineContext().isActive) {
                val line = withContext(Dispatchers.IO) {
                    if (source.exhausted()) null else source.readUtf8Line()
                } ?: break

                if (line == "data: [DONE]") break
                if (!line.startsWith("data: ")) continue

                val jsonStr = line.removePrefix("data: ").trim()
                if (jsonStr.isEmpty()) continue

                val delta = try {
                    JSONObject(jsonStr)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("delta")
                        .optString("content", "")
                } catch (e: Exception) {
                    sentryBreadcrumb("commentary_fusion", "SSE delta parse failed: ${e.message}", SentryLevel.WARNING)
                    continue
                }
                if (delta.isNotEmpty()) emit(delta)
            }
        } catch (e: IOException) {
            // A read timeout part-way through the SSE stream is the same signal as one on connect.
            if (isTimeout(e)) openCooldown(provider.name, "timed out (stream)")
            throw e
        } finally {
            response.close()
        }
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
        val result = callGenerateCommentaryFunction(cacheKey, prompt, isMatchComplete, maxTokens = 512, temperature = 0.4)
            ?: callProviders(prompt, maxTokens = 512, temperature = 0.4)
            // Generation failed (e.g. read-budget 402 + all providers down). Return the placeholder
            // WITHOUT caching, so this over is re-attempted next time instead of being poisoned.
            ?: return AICacheService.UNAVAILABLE_PLACEHOLDER
        AICacheService.setCommentary(result, cacheKey, permanent = isMatchComplete)
        return result
    }

    // 7e: Retry generation with any provider other than the one that produced the hallucinated output
    private suspend fun retryWithNextProvider(
        prompt: String,
        alreadyTriedProvider: String,
        systemInstruction: String = ""
    ): String? = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            for (provider in providers) {
                if (provider.name == alreadyTriedProvider) continue
                if (provider.key.isEmpty()) continue
                val cooldownUntil = providerCooldowns[provider.name] ?: 0L
                if (cooldownUntil > now) continue
                try {
                    val result = callProvider(provider, prompt, maxTokens = 256, temperature = 0.6, systemInstruction)
                    Sentry.addBreadcrumb(Breadcrumb().apply {
                        category = "service.commentary"
                        message = "Hallucination retry succeeded with ${provider.name}"
                        level = SentryLevel.INFO
                    })
                    return@withContext result
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Sentry.addBreadcrumb(Breadcrumb().apply {
                        category = "service.commentary"
                        message = "${provider.name} retry failed — ${e.message}"
                        level = SentryLevel.WARNING
                    })
                }
            }
            null
        }

    private suspend fun verifyCommentary(
        aiOutput: String,
        snippet1: String,
        snippet2: String,
        ballNumber: String,
        bowler: String,
        batsman: String
    ): Boolean {
        val cacheKey = contentFingerprint(aiOutput, snippet1, snippet2, ballNumber, bowler, batsman)
        verifyCacheMutex.withLock { verifyCache[cacheKey] }?.let { return it }

        val prompt = VERIFICATION_PROMPT_TEMPLATE
            .replace("{snippet1}", snippet1)
            .replace("{snippet2}", snippet2)
            .replace("{ai_output}", aiOutput)
            .replace("{ball_number}", ballNumber)
            .replace("{bowler}", bowler)
            .replace("{batsman}", batsman)

        return try {
            // OkHttp enforces its own call/read timeout — no coroutine-level wrapper needed.
            // Using withTimeoutOrNull here races with StandardTestDispatcher's virtual-time
            // advancement when callProvidersTracked dispatches to Dispatchers.IO.
            val verdict = callProviders(prompt, maxTokens = 16, temperature = 0.0)
            if (verdict == null) {
                // P0-5: No provider available — fail closed. Caching unverified content is worse
                // than discarding it; the caller will retry or fall back to the raw snippet.
                Log.w("CommentaryFusionService", "No provider available for verification — failing closed (FAIL)")
                return false
            }
            val passed = verdict.trim().uppercase().startsWith("PASS")
            if (!passed) {
                Log.w("CommentaryFusionService", "Hallucination check FAILED — verdict: $verdict")
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "service.commentary"
                    message = "Hallucination verification failed"
                    level = SentryLevel.WARNING
                    setData("verdict", verdict ?: "null")
                    setData("aiOutput", aiOutput.take(200))
                })
            }
            // Evict oldest entry when cache exceeds 512 (LinkedHashMap.keys.firstOrNull() = oldest inserted entry, FIFO eviction).
            verifyCacheMutex.withLock {
                if (verifyCache.size >= 512) {
                    verifyCache.keys.firstOrNull()?.let { verifyCache.remove(it) }
                }
                verifyCache[cacheKey] = passed
            }
            passed
        } catch (e: Exception) {
            Log.w("CommentaryFusionService", "Verification call failed — failing closed (FAIL): ${e.message}")
            sentryBreadcrumb("commentary_fusion", "Verification API call failed: ${e.message}", SentryLevel.WARNING)
            false
        }
    }

    private suspend fun callGenerateCommentaryFunction(
        cacheKey: String,
        prompt: String,
        matchComplete: Boolean = false,
        maxTokens: Int = 512,
        temperature: Double = 0.7,
        systemInstruction: String = ""
    ): String? {
        val body = org.json.JSONObject().apply {
            put("cacheKey", cacheKey)
            put("prompt", prompt)
            put("matchComplete", matchComplete)
            put("maxTokens", maxTokens)
            put("temperature", temperature)
            if (systemInstruction.isNotEmpty()) put("systemInstruction", systemInstruction)
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

    private suspend fun callProviders(
        prompt: String,
        maxTokens: Int,
        temperature: Double,
        systemInstruction: String = ""
    ): String? = callProvidersTracked(prompt, maxTokens, temperature, systemInstruction)?.first

    // Returns Pair(content, providerName) so callers can track which provider ran (needed for 7e retry)
    private suspend fun callProvidersTracked(
        prompt: String,
        maxTokens: Int,
        temperature: Double,
        systemInstruction: String = ""
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        for (provider in providers) {
            if (provider.key.isEmpty()) continue
            val cooldownUntil = providerCooldowns[provider.name] ?: 0L
            if (cooldownUntil > now) {
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "service.commentary"
                    message = "${provider.name} cooling down, skipping"
                    level = SentryLevel.DEBUG
                })
                continue
            }
            try {
                val content = callProvider(provider, prompt, maxTokens, temperature, systemInstruction)
                return@withContext Pair(content, provider.name)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "service.commentary"
                    message = "${provider.name} failed — ${e.message}"
                    level = SentryLevel.WARNING
                })
            }
        }
        null
    }

    /**
     * The completion text from a provider response, or a throw when it is not usable.
     *
     * [CommentaryFormatter.stripPreamble] runs on the raw multi-line completion, before anything
     * collapses newlines. The server path has always done this (`llm.js stripPreamble`); this path
     * had no equivalent, which is how 76 rows of "Since there is no specific ball number..."
     * reached the shared Appwrite cache. A response that is *only* meta is rejected outright — a
     * refusal is a failed generation, and treating it as success caches it.
     */
    private fun usableContentOf(bodyText: String, providerName: String): String {
        val raw = JSONObject(bodyText)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
        val content = CommentaryFormatter.stripPreamble(raw).trim()
        if (content.isEmpty()) throw IOException("Empty content from $providerName")
        if (CommentaryFormatter.isMetaResponse(content)) {
            throw IOException("Meta/refusal response from $providerName")
        }
        return content
    }

    private fun callProvider(
        provider: Provider,
        prompt: String,
        maxTokens: Int,
        temperature: Double,
        systemInstruction: String = ""
    ): String {
        val startMs = System.currentTimeMillis()
        val span = Sentry.getSpan()?.startChild("gen_ai.chat", provider.name)
        span?.setData("gen_ai.system", provider.name.lowercase())
        span?.setData("gen_ai.request.model", provider.model)
        span?.setData("gen_ai.request.max_tokens", maxTokens)
        val messages = JSONArray()
        if (systemInstruction.isNotEmpty()) {
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemInstruction)
            })
        }
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })
        val body = JSONObject().apply {
            put("model", provider.model)
            put("messages", messages)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(provider.url)
            .header("Authorization", "Bearer ${provider.key}")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val reason = cooldownReasonFor(response.code)
                if (reason != null) {
                    span?.finish(
                        if (response.code == HTTP_TOO_MANY_REQUESTS) SpanStatus.RESOURCE_EXHAUSTED
                        else SpanStatus.INTERNAL_ERROR
                    )
                    openCooldown(provider.name, reason)
                    throw IOException("${provider.name} $reason")
                }
                if (!response.isSuccessful) {
                    span?.finish(SpanStatus.INTERNAL_ERROR)
                    throw IOException("HTTP ${response.code}")
                }
                val bodyText = response.body?.string() ?: throw IOException("Null body from ${provider.name}")
                // The enclosing catch finishes the span as INTERNAL_ERROR on any throw, so these
                // failures need no span handling of their own.
                val content = usableContentOf(bodyText, provider.name)
                val elapsedMs = System.currentTimeMillis() - startMs
                span?.setData("gen_ai.usage.output_tokens", content.length / 4)
                span?.finish(SpanStatus.OK)
                recordProviderSuccess(provider.name)
                Sentry.metrics().count("ai.request.success", 1.0)
                Sentry.metrics().distribution("ai.latency_ms", elapsedMs.toDouble(), "millisecond")
                Sentry.metrics().count("ai.tokens_generated", (content.length / 4).toDouble())
                return content
            }
        } catch (e: Exception) {
            span?.finish(SpanStatus.INTERNAL_ERROR)
            if (isTimeout(e)) openCooldown(provider.name, "timed out")
            throw e
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
