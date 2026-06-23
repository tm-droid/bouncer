package com.tanush.bouncer.services

import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.network.HttpClientProvider
import com.tanush.bouncer.utils.CommentaryFormatter
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class CommentaryFusionService(
    private val client: OkHttpClient = HttpClientProvider.sharedClient,
    // For testing: inject providers with non-empty keys to bypass the ApiKeys.isEmpty() guard.
    // Each triple is (providerName, apiKey, modelId); URL is auto-generated and overridden by fakeClient.
    testProviders: List<Triple<String, String, String>>? = null
) {
    private data class Provider(val name: String, val url: String, val key: String, val model: String)

    private val providers: List<Provider> = testProviders?.map { (name, key, model) ->
        Provider(name, "http://test-$name", key, model)
    } ?: listOf(
        Provider("Groq",      "https://api.groq.com/openai/v1/chat/completions",    ApiKeys.groq,      "llama-3.3-70b-versatile"),
        Provider("Cerebras",  "https://api.cerebras.ai/v1/chat/completions",         ApiKeys.cerebras,  "llama-3.3-70b"),
        Provider("SambaNova", "https://api.sambanova.ai/v1/chat/completions",        ApiKeys.sambaNova, "Meta-Llama-3.3-70B-Instruct"),
    )
    private val providerCooldowns = ConcurrentHashMap<String, Long>()
    private val provider429Count = ConcurrentHashMap<String, Int>()

    // Concurrent access from per-ball coroutines — use ConcurrentHashMap + per-match mutex
    // so reads and writes never see a torn list.
    private val previousBallsBuffer = ConcurrentHashMap<String, MutableList<String>>()
    // Temp=0.0 verify calls are fully deterministic: same inputs → same PASS/FAIL every time.
    // Cache by content hash to avoid redundant AI calls on retry paths.
    // LinkedHashMap in insertion order — FIFO eviction. All access guarded by verifyCacheMutex.
    private val verifyCache = LinkedHashMap<Int, Boolean>(512)
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

    private fun selectPhase(ballNumber: Double, totalOvers: Int): PromptPhase {
        val completedOvers = ballNumber.toInt()
        val ballsInCurrentOver = ((ballNumber * 10).toInt() % 10)
        val ballsBowled = completedOvers * CricketConstants.BALLS_PER_OVER + ballsInCurrentOver
        val totalBalls = totalOvers * CricketConstants.BALLS_PER_OVER
        val ballsRemaining = totalBalls - ballsBowled
        val format = CricketConstants.formatFromInningsBalls(totalBalls)
        val powerplayEnd = CricketConstants.powerplayEndBall(format)
        val deathBalls = when (format) {
            CricketConstants.MatchFormat.ODI -> CricketConstants.ODI_DEATH_OVERS * CricketConstants.BALLS_PER_OVER
            else -> CricketConstants.T20_DEATH_OVERS * CricketConstants.BALLS_PER_OVER
        }
        return when {
            ballsBowled < powerplayEnd -> PromptPhase.POWERPLAY
            ballsRemaining <= 1 -> PromptPhase.LAST_BALL
            ballsRemaining <= deathBalls -> PromptPhase.DEATH
            else -> PromptPhase.MIDDLE
        }
    }

    companion object {
        const val FALLBACK_BALL_COMMENTARY = "Ball delivered. Play continues."
        // P2-6: Exponential backoff after 429 with jitter. attempt N → BASE*2^(N-1) + jitter, capped at MAX.
        const val BASE_COOLDOWN_MS = 30_000L
        const val MAX_COOLDOWN_MS = 300_000L

        // Sent as role:"system" in all ball-by-ball LLM calls.
        const val SYSTEM_INSTRUCTION =
            "You are a professional cricket broadcast commentator. Write exactly one vivid, natural commentary line.\n" +
            "STRICT FORMAT: Begin with the ball number (e.g., 18.5: ...). One sentence only. No ellipsis at the end.\n" +
            "HALLUCINATION RULES: Use ONLY player names and match facts present in the input. Never invent runs, wickets, boundaries, or events not stated in the input. The atmosphere is yours — the facts are not.\n" +
            "STYLE: Cinematic, broadcast-quality. Sharp active verbs. Specific detail from the input. No clichés."

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

            Ball: {ball_number} | Bowler: {bowler} | Batsman: {batsman}
            Score: {score_context}
            {context_extras}
            Recent context (last 3 overs):
            {overs_summary}
            {previous_balls_context}

            Do not simply merge the two sources; pick the best or rephrase for freshness.
            {phase_instruction}""".trimIndent()

        val SINGLE_SOURCE_USER_TEMPLATE = """
            Ball commentary: {snippet}

            Ball: {ball_number} | Bowler: {bowler} | Batsman: {batsman}
            Score: {score_context}
            {context_extras}
            {previous_balls_context}

            {phase_instruction}""".trimIndent()

        val VERIFICATION_PROMPT_TEMPLATE = """
            You are a cricket commentary fact-checker. Verify that the AI-generated commentary does not hallucinate.

            Source 1: {snippet1}
            Source 2: {snippet2}
            AI output: {ai_output}
            Ball: {ball_number} | Bowler: {bowler} | Batsman: {batsman}

            Answer ONLY "PASS" if accurate and uses only correct names from the sources, or "FAIL" if it contains hallucinated names, invented details, or unsupported information.
            One word: PASS or FAIL.""".trimIndent()

        // Legacy aliases kept for any external callers referencing the old constants
        val FUSION_PROMPT_TEMPLATE = FUSION_USER_TEMPLATE
        val SINGLE_SOURCE_PROMPT_TEMPLATE = SINGLE_SOURCE_USER_TEMPLATE
    }

    // 7b: Select the right prompt template based on phase
    private fun phaseInstructionFor(phase: PromptPhase): String = when (phase) {
        PromptPhase.POWERPLAY -> "Field restrictions apply. Focus on aggressive intent, early wicket stakes, and powerplay context."
        PromptPhase.MIDDLE    -> "Focus on partnership momentum, dot ball pressure, and any turning-point significance."
        PromptPhase.DEATH     -> "Death overs: capture yorker/slog sweep tension and required rate urgency."
        PromptPhase.LAST_BALL -> "Last ball: use maximum tension language — match outcome on the line."
    }

    private fun fusionPromptForPhase(phase: PromptPhase): String =
        FUSION_USER_TEMPLATE.replace("{phase_instruction}", phaseInstructionFor(phase))

    private fun singleSourcePromptForPhase(phase: PromptPhase): String =
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
        isMatchComplete: Boolean = false
    ): String =
        withContext(Dispatchers.IO) {
            val cacheKey = "ball_ai:$matchId:$ballNumber"
            AICacheService.getCommentary(cacheKey)?.let { return@withContext it }

            val previousBalls = getPreviousBalls(matchId)
            val recentContext = if (previousBalls.isNotEmpty())
                "\nRecent: ${previousBalls.takeLast(3).joinToString(" | ")}" else ""
            val cleanSnippet = CommentaryFormatter.sanitize(rawSnippet)
            val prompt = buildString {
                append("Ball $ballNumber: $cleanSnippet")
                if (recentContext.isNotEmpty()) append(recentContext)
                append("\nWrite one vivid broadcast commentary line. Start with $ballNumber: at the beginning.")
            }

            val result = callGenerateCommentaryFunction(cacheKey, prompt, isMatchComplete, maxTokens = 100, temperature = 0.7, systemInstruction = SYSTEM_INSTRUCTION)
                ?: callProviders(prompt, maxTokens = 100, temperature = 0.7, systemInstruction = SYSTEM_INSTRUCTION)
                ?: return@withContext FALLBACK_BALL_COMMENTARY
            AICacheService.setCommentary(result, cacheKey, permanent = isMatchComplete)
            addBallToBuffer(matchId, result)
            result
        }

    suspend fun rewriteEventCommentary(
        matchId: String,
        rawEvent: String,
        isMatchComplete: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val cleaned = CommentaryFormatter.sanitize(rawEvent)
        if (cleaned.isBlank()) return@withContext rawEvent
        val cacheKey = "event_ai:$matchId:${cleaned.hashCode()}"
        AICacheService.getCommentary(cacheKey)?.let { return@withContext it }

        val prompt = "Match event: $cleaned\nRewrite as one broadcast commentary sentence."
        val result = callGenerateCommentaryFunction(cacheKey, prompt, isMatchComplete, maxTokens = 80, temperature = 0.6, systemInstruction = EVENT_SYSTEM_INSTRUCTION)
            ?: callProviders(prompt, maxTokens = 80, temperature = 0.6, systemInstruction = EVENT_SYSTEM_INSTRUCTION)
            ?: return@withContext cleaned
        AICacheService.setCommentary(result, cacheKey, permanent = isMatchComplete)
        result
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
        requiredRunRate: Double? = null
    ): String {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.commentary"
            message = "Fusing commentary snippets"
            level = SentryLevel.INFO
            setData("matchId", matchId)
            setData("over", over)
            setData("ballNumber", ballNumber)
        })

        val contextHash = (snippet1 + snippet2 + oversSummary).hashCode().toString()
        // ballNumber included in key: two dot-balls in the same over with identical snippets
        // must not collide and serve the wrong commentary for ball B.
        val cacheKey = "ai_commentary:$matchId:$over:$ballNumber:$contextHash"
        AICacheService.getCommentary(cacheKey)?.let { return it }

        // In-flight dedup: if another coroutine is already computing this key, await its result
        // instead of firing a duplicate LLM call. serviceScope.async() parents the Deferred to
        // the class-level scope so cancellation of any individual caller does not cancel the
        // shared work — each awaiter observes cancellation independently on await().
        val deferred: Deferred<String> = inFlightMutex.withLock {
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

        // 7b: Select phase-adaptive template
        val ballNum = ballNumber.toDoubleOrNull() ?: over.toDouble()
        val phase = selectPhase(ballNum, totalOvers)

        val contextExtras = buildString {
            if (partnershipContext.isNotEmpty()) appendLine("Partnership: $partnershipContext")
            if (matchupContext.isNotEmpty()) appendLine("H2H: $matchupContext")
            if (currentRunRate != null) appendLine("CRR: ${"%.2f".format(currentRunRate)}")
            if (requiredRunRate != null) appendLine("RRR: ${"%.2f".format(requiredRunRate)}")
        }.trimEnd()

        val prompt = if (snippet1.isEmpty() || snippet2.isEmpty()) {
            singleSourcePromptForPhase(phase)
                .replace("{snippet}", snippet1.ifEmpty { snippet2 })
                .replace("{ball_number}", ballNumber)
                .replace("{bowler}", bowler)
                .replace("{batsman}", batsman)
                .replace("{score_context}", scoreContext)
                .replace("{context_extras}", contextExtras)
                .replace("{previous_balls_context}", previousContext)
        } else {
            fusionPromptForPhase(phase)
                .replace("{snippet1}", snippet1)
                .replace("{snippet2}", snippet2)
                .replace("{ball_number}", ballNumber)
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
        val cacheKey = "ai_commentary_stream:${matchContext.matchId}:${matchContext.over}:${matchContext.ballNumber}"
        val cached = AICacheService.getCommentary(cacheKey)
        if (cached != null) {
            emit(cached)
            return@flow
        }

        val previousBalls = getPreviousBalls(matchContext.matchId)
        val previousContext = if (previousBalls.isEmpty()) ""
            else "\nPrevious balls: ${previousBalls.joinToString(", ")}"

        val ballNum = matchContext.ballNumber.toDoubleOrNull() ?: matchContext.over.toDouble()
        val phase = selectPhase(ballNum, matchContext.totalOvers)

        val streamContextExtras = buildString {
            if (matchContext.partnershipContext.isNotEmpty()) appendLine("Partnership: ${matchContext.partnershipContext}")
            if (matchContext.matchupContext.isNotEmpty()) appendLine("H2H: ${matchContext.matchupContext}")
            if (matchContext.currentRunRate != null) appendLine("CRR: ${"%.2f".format(matchContext.currentRunRate)}")
            if (matchContext.requiredRunRate != null) appendLine("RRR: ${"%.2f".format(matchContext.requiredRunRate)}")
        }.trimEnd()

        val prompt = if (matchContext.snippet1.isEmpty() || matchContext.snippet2.isEmpty()) {
            singleSourcePromptForPhase(phase)
                .replace("{snippet}", matchContext.snippet1.ifEmpty { matchContext.snippet2 })
                .replace("{ball_number}", matchContext.ballNumber)
                .replace("{bowler}", matchContext.bowler)
                .replace("{batsman}", matchContext.batsman)
                .replace("{score_context}", matchContext.scoreContext)
                .replace("{context_extras}", streamContextExtras)
                .replace("{previous_balls_context}", previousContext)
        } else {
            fusionPromptForPhase(phase)
                .replace("{snippet1}", matchContext.snippet1)
                .replace("{snippet2}", matchContext.snippet2)
                .replace("{ball_number}", matchContext.ballNumber)
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
                provider429Count.remove(provider.name)
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

        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
        try {
            // Check HTTP status BEFORE reading the SSE stream
            if (response.code == 429) {
                val attempt = provider429Count.merge(provider.name, 1, Int::plus) ?: 1
                val cooldownMs = minOf(BASE_COOLDOWN_MS shl minOf(attempt - 1, 4), MAX_COOLDOWN_MS) + Random.nextLong(0, BASE_COOLDOWN_MS)
                providerCooldowns[provider.name] = System.currentTimeMillis() + cooldownMs
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "service.commentary"
                    message = "${provider.name} rate-limited (stream) attempt=$attempt — ${cooldownMs}ms cooldown"
                    level = SentryLevel.WARNING
                })
                throw IOException("Rate limited (429)")
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
                } catch (_: Exception) {
                    continue
                }
                if (delta.isNotEmpty()) emit(delta)
            }
        } finally {
            response.close()
        }
    }

    suspend fun summarizeOversWithAI(
        commentaryLines: List<String>,
        matchId: String,
        over: Int,
        isMatchComplete: Boolean = false
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

        // 7d: Expand context window from 18 → 24 lines; amplify wicket/six events
        val rawLines = commentaryLines.take(24)
        val amplifiedLines = mutableListOf<String>()
        for (line in rawLines) {
            amplifiedLines.add(line)
            // Intentional: wicket/six events appear twice to amplify their signal weight in the LLM context
            if (line.contains("wicket", ignoreCase = true) || line.contains("six", ignoreCase = true)) {
                amplifiedLines.add(line)
            }
        }
        val lines = amplifiedLines

        val contextHash = lines.joinToString(" ").hashCode().toString()
        val cacheKey = "ai_summary:$matchId:$over:$contextHash"
        AICacheService.getCommentary(cacheKey)?.let { return it }

        val numbered = lines.mapIndexed { idx, line -> "${idx + 1}. $line" }.joinToString("\n")
        val prompt = "You are a professional cricket commentator. Summarize the following ${lines.size} " +
            "ball-by-ball commentary lines into a single, engaging paragraph, highlighting key events and players.\n\n$numbered"

        // 7c: Token budget 256 → 512 for over summaries
        val result = callGenerateCommentaryFunction(cacheKey, prompt, isMatchComplete, maxTokens = 512, temperature = 0.4)
            ?: callProviders(prompt, maxTokens = 512, temperature = 0.4)
            ?: "[Summary unavailable]"
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
        val cacheKey = (aiOutput + snippet1 + snippet2 + ballNumber + bowler + batsman).hashCode()
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
            // P0-5: API failure during verification — fail closed; don't cache unverified content.
            Log.w("CommentaryFusionService", "Verification call failed — failing closed (FAIL): ${e.message}")
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
                if (response.code == 429) {
                    span?.finish(SpanStatus.RESOURCE_EXHAUSTED)
                    val attempt = provider429Count.merge(provider.name, 1, Int::plus) ?: 1
                    val cooldownMs = minOf(BASE_COOLDOWN_MS shl minOf(attempt - 1, 4), MAX_COOLDOWN_MS) + Random.nextLong(0, BASE_COOLDOWN_MS)
                    providerCooldowns[provider.name] = System.currentTimeMillis() + cooldownMs
                    Sentry.addBreadcrumb(Breadcrumb().apply {
                        category = "service.commentary"
                        message = "${provider.name} rate-limited attempt=$attempt — ${cooldownMs}ms cooldown"
                        level = SentryLevel.WARNING
                    })
                    throw IOException("Rate limited (429)")
                }
                if (!response.isSuccessful) {
                    span?.finish(SpanStatus.INTERNAL_ERROR)
                    throw IOException("HTTP ${response.code}")
                }
                val content = JSONObject(response.body?.string() ?: throw IOException("Null body from ${provider.name}"))
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                if (content.isEmpty()) {
                    span?.finish(SpanStatus.INTERNAL_ERROR)
                    throw IOException("Empty content from ${provider.name}")
                }
                val elapsedMs = System.currentTimeMillis() - startMs
                span?.setData("gen_ai.usage.output_tokens", content.length / 4)
                span?.finish(SpanStatus.OK)
                provider429Count.remove(provider.name)
                Sentry.metrics().count("ai.request.success", 1.0)
                Sentry.metrics().distribution("ai.latency_ms", elapsedMs.toDouble(), "millisecond")
                Sentry.metrics().count("ai.tokens_generated", (content.length / 4).toDouble())
                return content
            }
        } catch (e: Exception) {
            span?.finish(SpanStatus.INTERNAL_ERROR)
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
        val requiredRunRate: Double? = null
    )
}
