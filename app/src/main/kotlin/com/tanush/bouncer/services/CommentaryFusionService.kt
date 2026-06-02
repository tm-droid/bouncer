package com.tanush.bouncer.services

import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.network.HttpClientProvider
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
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

    // Concurrent access from per-ball coroutines — use ConcurrentHashMap + per-match mutex
    // so reads and writes never see a torn list.
    private val previousBallsBuffer = ConcurrentHashMap<String, MutableList<String>>()
    // Temp=0.0 verify calls are fully deterministic: same inputs → same PASS/FAIL every time.
    // Cache by content hash to avoid redundant AI calls on retry paths.
    // ConcurrentHashMap: point reads/writes never cross suspension points so no Mutex needed here.
    private val verifyCache = ConcurrentHashMap<Int, Boolean>(512)
    private val verifyCacheMutex = Mutex()
    private val bufferMutex = Mutex()
    private val maxBufferSize = 6

    // 7b: Phase-adaptive prompt selection
    private enum class PromptPhase { POWERPLAY, MIDDLE, DEATH, LAST_BALL }

    private fun selectPhase(ballNumber: Double, totalOvers: Int): PromptPhase {
        val completedOvers = ballNumber.toInt()
        val ballsInCurrentOver = ((ballNumber * 10).toInt() % 10)
        val ballsBowled = completedOvers * 6 + ballsInCurrentOver
        val ballsRemaining = totalOvers * 6 - ballsBowled
        return when {
            ballNumber < 6.0 -> PromptPhase.POWERPLAY
            ballsRemaining <= 1 -> PromptPhase.LAST_BALL
            ballsRemaining <= 24 -> PromptPhase.DEATH   // last 4 overs
            else -> PromptPhase.MIDDLE
        }
    }

    companion object {
        const val FALLBACK_BALL_COMMENTARY = "Ball delivered. Play continues."

        // 7b: FUSION prompt variants — one per phase
        const val FUSION_PROMPT_POWERPLAY = """
    You are a professional cricket commentator.
    Given the following ball-by-ball input and recent match context, write a single, natural-sounding commentary line for the current ball.

    Ball-by-ball input from two sources:
    Commentary from source 1: {snippet1}
    Commentary from source 2: {snippet2}

    Ball number: {ball_number}
    Bowler: {bowler}
    Batsman: {batsman}
    Current score/context: {score_context}
    {context_extras}

    Recent match context (last 3 overs summary):
    {overs_summary}
    {previous_balls_context}

    Instructions:
    - Start your commentary line with the ball number (e.g., 18.5: ...).
    - Use only the player names provided above or in the input/context. Do not invent or substitute player names.
    - Do not invent details about the play, score, or situation if not present in the input/context.
    - Paraphrase the ball input into a concise, engaging, and human-like sentence, as a real commentator would say on TV or radio.
    - If the match context (from the summary) suggests a shift in momentum, pressure, or a notable trend, you may add a brief, context-aware narrative or emotional remark (e.g., "It's not looking good for the Indian team here" or "The bowlers are on top at the moment").
    - Only add such narrative remarks if they fit naturally and are supported by the recent context—do not force them.
    - Otherwise, focus on clear, vivid, and accurate description of the current ball.
    - Avoid repetition and keep it concise.
    - Do not simply merge the two sources; pick the best or rephrase for freshness.
    - Field restrictions apply. Focus on aggressive intent, early wicket stakes, and powerplay context.
    """

        const val FUSION_PROMPT_MIDDLE = """
    You are a professional cricket commentator.
    Given the following ball-by-ball input and recent match context, write a single, natural-sounding commentary line for the current ball.

    Ball-by-ball input from two sources:
    Commentary from source 1: {snippet1}
    Commentary from source 2: {snippet2}

    Ball number: {ball_number}
    Bowler: {bowler}
    Batsman: {batsman}
    Current score/context: {score_context}
    {context_extras}

    Recent match context (last 3 overs summary):
    {overs_summary}
    {previous_balls_context}

    Instructions:
    - Start your commentary line with the ball number (e.g., 18.5: ...).
    - Use only the player names provided above or in the input/context. Do not invent or substitute player names.
    - Do not invent details about the play, score, or situation if not present in the input/context.
    - Paraphrase the ball input into a concise, engaging, and human-like sentence, as a real commentator would say on TV or radio.
    - If the match context (from the summary) suggests a shift in momentum, pressure, or a notable trend, you may add a brief, context-aware narrative or emotional remark (e.g., "It's not looking good for the Indian team here" or "The bowlers are on top at the moment").
    - Only add such narrative remarks if they fit naturally and are supported by the recent context—do not force them.
    - Otherwise, focus on clear, vivid, and accurate description of the current ball.
    - Avoid repetition and keep it concise.
    - Do not simply merge the two sources; pick the best or rephrase for freshness.
    - Focus on partnership momentum, dot ball pressure, and any turning-point significance.
    """

        const val FUSION_PROMPT_DEATH = """
    You are a professional cricket commentator.
    Given the following ball-by-ball input and recent match context, write a single, natural-sounding commentary line for the current ball.

    Ball-by-ball input from two sources:
    Commentary from source 1: {snippet1}
    Commentary from source 2: {snippet2}

    Ball number: {ball_number}
    Bowler: {bowler}
    Batsman: {batsman}
    Current score/context: {score_context}
    {context_extras}

    Recent match context (last 3 overs summary):
    {overs_summary}
    {previous_balls_context}

    Instructions:
    - Start your commentary line with the ball number (e.g., 18.5: ...).
    - Use only the player names provided above or in the input/context. Do not invent or substitute player names.
    - Do not invent details about the play, score, or situation if not present in the input/context.
    - Paraphrase the ball input into a concise, engaging, and human-like sentence, as a real commentator would say on TV or radio.
    - If the match context (from the summary) suggests a shift in momentum, pressure, or a notable trend, you may add a brief, context-aware narrative or emotional remark (e.g., "It's not looking good for the Indian team here" or "The bowlers are on top at the moment").
    - Only add such narrative remarks if they fit naturally and are supported by the recent context—do not force them.
    - Otherwise, focus on clear, vivid, and accurate description of the current ball.
    - Avoid repetition and keep it concise.
    - Do not simply merge the two sources; pick the best or rephrase for freshness.
    - Match is in death overs. Capture yorker/slog sweep tension, required rate urgency.
    """

        const val FUSION_PROMPT_LAST_BALL = """
    You are a professional cricket commentator.
    Given the following ball-by-ball input and recent match context, write a single, natural-sounding commentary line for the current ball.

    Ball-by-ball input from two sources:
    Commentary from source 1: {snippet1}
    Commentary from source 2: {snippet2}

    Ball number: {ball_number}
    Bowler: {bowler}
    Batsman: {batsman}
    Current score/context: {score_context}
    {context_extras}

    Recent match context (last 3 overs summary):
    {overs_summary}
    {previous_balls_context}

    Instructions:
    - Start your commentary line with the ball number (e.g., 18.5: ...).
    - Use only the player names provided above or in the input/context. Do not invent or substitute player names.
    - Do not invent details about the play, score, or situation if not present in the input/context.
    - Paraphrase the ball input into a concise, engaging, and human-like sentence, as a real commentator would say on TV or radio.
    - If the match context (from the summary) suggests a shift in momentum, pressure, or a notable trend, you may add a brief, context-aware narrative or emotional remark (e.g., "It's not looking good for the Indian team here" or "The bowlers are on top at the moment").
    - Only add such narrative remarks if they fit naturally and are supported by the recent context—do not force them.
    - Otherwise, focus on clear, vivid, and accurate description of the current ball.
    - Avoid repetition and keep it concise.
    - Do not simply merge the two sources; pick the best or rephrase for freshness.
    - This is the last ball. Use maximum tension language. Match outcome on the line.
    """

        // 7b: SINGLE_SOURCE prompt variants — one per phase
        const val SINGLE_SOURCE_PROMPT_POWERPLAY = """
    You are a professional cricket commentator. Given the following ball-by-ball input and recent match context, write a vivid, engaging, and concise commentary line for this specific ball, as if you are commentating live.

    Ball commentary: {snippet}
    Ball number: {ball_number}
    Bowler: {bowler}
    Batsman: {batsman}
    Current score/context: {score_context}
    {context_extras}
    {previous_balls_context}

    Instructions:
    - Start your commentary line with the ball number (e.g., 18.5: ...).
    - Use only the player names provided above or in the input/context. Do not invent or substitute player names.
    - Do not invent details about the play, score, or situation if not present in the input/context.
    - Paraphrase the input into a natural, commentator-style sentence.
    - If the recent context suggests a shift in momentum, pressure, or a notable trend, you may add a brief, context-aware narrative or emotional remark (e.g., "It's not looking good for the Indian team here").
    - Only add such narrative remarks if they fit naturally and are supported by the recent context—do not force them.
    - Otherwise, focus on clear, vivid, and accurate description of the current ball.
    - Avoid repetition and keep it concise.
    - Field restrictions apply. Focus on aggressive intent, early wicket stakes, and powerplay context.
    """

        const val SINGLE_SOURCE_PROMPT_MIDDLE = """
    You are a professional cricket commentator. Given the following ball-by-ball input and recent match context, write a vivid, engaging, and concise commentary line for this specific ball, as if you are commentating live.

    Ball commentary: {snippet}
    Ball number: {ball_number}
    Bowler: {bowler}
    Batsman: {batsman}
    Current score/context: {score_context}
    {context_extras}
    {previous_balls_context}

    Instructions:
    - Start your commentary line with the ball number (e.g., 18.5: ...).
    - Use only the player names provided above or in the input/context. Do not invent or substitute player names.
    - Do not invent details about the play, score, or situation if not present in the input/context.
    - Paraphrase the input into a natural, commentator-style sentence.
    - If the recent context suggests a shift in momentum, pressure, or a notable trend, you may add a brief, context-aware narrative or emotional remark (e.g., "It's not looking good for the Indian team here").
    - Only add such narrative remarks if they fit naturally and are supported by the recent context—do not force them.
    - Otherwise, focus on clear, vivid, and accurate description of the current ball.
    - Avoid repetition and keep it concise.
    - Focus on partnership momentum, dot ball pressure, and any turning-point significance.
    """

        const val SINGLE_SOURCE_PROMPT_DEATH = """
    You are a professional cricket commentator. Given the following ball-by-ball input and recent match context, write a vivid, engaging, and concise commentary line for this specific ball, as if you are commentating live.

    Ball commentary: {snippet}
    Ball number: {ball_number}
    Bowler: {bowler}
    Batsman: {batsman}
    Current score/context: {score_context}
    {context_extras}
    {previous_balls_context}

    Instructions:
    - Start your commentary line with the ball number (e.g., 18.5: ...).
    - Use only the player names provided above or in the input/context. Do not invent or substitute player names.
    - Do not invent details about the play, score, or situation if not present in the input/context.
    - Paraphrase the input into a natural, commentator-style sentence.
    - If the recent context suggests a shift in momentum, pressure, or a notable trend, you may add a brief, context-aware narrative or emotional remark (e.g., "It's not looking good for the Indian team here").
    - Only add such narrative remarks if they fit naturally and are supported by the recent context—do not force them.
    - Otherwise, focus on clear, vivid, and accurate description of the current ball.
    - Avoid repetition and keep it concise.
    - Match is in death overs. Capture yorker/slog sweep tension, required rate urgency.
    """

        const val SINGLE_SOURCE_PROMPT_LAST_BALL = """
    You are a professional cricket commentator. Given the following ball-by-ball input and recent match context, write a vivid, engaging, and concise commentary line for this specific ball, as if you are commentating live.

    Ball commentary: {snippet}
    Ball number: {ball_number}
    Bowler: {bowler}
    Batsman: {batsman}
    Current score/context: {score_context}
    {context_extras}
    {previous_balls_context}

    Instructions:
    - Start your commentary line with the ball number (e.g., 18.5: ...).
    - Use only the player names provided above or in the input/context. Do not invent or substitute player names.
    - Do not invent details about the play, score, or situation if not present in the input/context.
    - Paraphrase the input into a natural, commentator-style sentence.
    - If the recent context suggests a shift in momentum, pressure, or a notable trend, you may add a brief, context-aware narrative or emotional remark (e.g., "It's not looking good for the Indian team here").
    - Only add such narrative remarks if they fit naturally and are supported by the recent context—do not force them.
    - Otherwise, focus on clear, vivid, and accurate description of the current ball.
    - Avoid repetition and keep it concise.
    - This is the last ball. Use maximum tension language. Match outcome on the line.
    """

        const val VERIFICATION_PROMPT_TEMPLATE = """
    You are a cricket commentary fact-checker. Your job is to verify that an AI-generated commentary line is accurate and does not hallucinate.

    Original source commentary 1: {snippet1}
    Original source commentary 2: {snippet2}

    AI-generated commentary: {ai_output}

    Ball number: {ball_number}
    Bowler: {bowler}
    Batsman: {batsman}

    Check the AI-generated commentary against the original sources and answer ONLY with one of these two words:
    - "PASS" if the AI commentary is accurate, uses only correct player names from the sources, and does not invent any details not present in the original sources.
    - "FAIL" if the AI commentary contains hallucinated player names, invented match details, incorrect ball descriptions, or information not supported by the original sources.

    Your response must be exactly one word: PASS or FAIL.
    """

        // Legacy aliases kept for any external callers referencing the old constants
        const val FUSION_PROMPT_TEMPLATE = FUSION_PROMPT_MIDDLE
        const val SINGLE_SOURCE_PROMPT_TEMPLATE = SINGLE_SOURCE_PROMPT_MIDDLE
    }

    // 7b: Select the right prompt template based on phase
    private fun fusionPromptForPhase(phase: PromptPhase): String = when (phase) {
        PromptPhase.POWERPLAY  -> FUSION_PROMPT_POWERPLAY
        PromptPhase.MIDDLE     -> FUSION_PROMPT_MIDDLE
        PromptPhase.DEATH      -> FUSION_PROMPT_DEATH
        PromptPhase.LAST_BALL  -> FUSION_PROMPT_LAST_BALL
    }

    private fun singleSourcePromptForPhase(phase: PromptPhase): String = when (phase) {
        PromptPhase.POWERPLAY  -> SINGLE_SOURCE_PROMPT_POWERPLAY
        PromptPhase.MIDDLE     -> SINGLE_SOURCE_PROMPT_MIDDLE
        PromptPhase.DEATH      -> SINGLE_SOURCE_PROMPT_DEATH
        PromptPhase.LAST_BALL  -> SINGLE_SOURCE_PROMPT_LAST_BALL
    }

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
            val prompt = "You are a cricket commentator. One punchy sentence, max 15 words.\n" +
                "Ball $ballNumber: $rawSnippet$recentContext\nCommentary:"

            val result = callProviders(prompt, maxTokens = 50, temperature = 0.7)
                ?: return@withContext FALLBACK_BALL_COMMENTARY
            AICacheService.setCommentary(result, cacheKey, permanent = isMatchComplete)
            addBallToBuffer(matchId, result)
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
        isMatchComplete: Boolean = false
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
        val cacheKey = "ai_commentary:$matchId:$over:$contextHash"
        AICacheService.getCommentary(cacheKey)?.let { return it }

        val previousBalls = getPreviousBalls(matchId)
        val previousContext = if (previousBalls.isEmpty()) ""
            else "\nPrevious balls: ${previousBalls.joinToString(", ")}"

        // 7b: Select phase-adaptive template
        val ballNum = ballNumber.toDoubleOrNull() ?: over.toDouble()
        val phase = selectPhase(ballNum, totalOvers)

        val contextExtras = buildString {
            if (partnershipContext.isNotEmpty()) appendLine("Partnership: $partnershipContext")
            if (matchupContext.isNotEmpty()) appendLine("H2H: $matchupContext")
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

        val (result, usedProviderName) = callProvidersTracked(prompt, maxTokens = 256, temperature = 0.6)
            ?: return "[AI Fused] $snippet1 / $snippet2"

        val verified = verifyCommentary(result, snippet1, snippet2, ballNumber, bowler, batsman)
        if (!verified) {
            // 7e: FAIL → retry with next available provider before falling back to raw snippet
            Log.w("CommentaryFusionService", "Hallucination check FAILED — retrying with next provider (used: $usedProviderName)")
            val retried = retryWithNextProvider(prompt, alreadyTriedProvider = usedProviderName)
            if (retried != null) {
                AICacheService.setCommentary(retried, cacheKey, permanent = isMatchComplete)
                return retried
            }
            val fallback = snippet1.ifEmpty { snippet2 }
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

            try {
                streamFromProvider(provider, prompt, maxTokens = 256, temperature = 0.6f)
                    .collect { chunk ->
                        if (currentCoroutineContext().isActive) {
                            emit(chunk)
                            fullBuilder.append(chunk)
                            hasEmittedAny = true
                        }
                    }
                succeeded = true
                break
            } catch (e: Exception) {
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
        temperature: Float
    ): Flow<String> = flow {
        val body = JSONObject().apply {
            put("model", provider.model)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
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
                providerCooldowns[provider.name] = System.currentTimeMillis() + 30_000L
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "service.commentary"
                    message = "${provider.name} rate-limited (stream) — 30s cooldown"
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
        val result = callProviders(prompt, maxTokens = 512, temperature = 0.4) ?: "[Summary unavailable]"
        AICacheService.setCommentary(result, cacheKey, permanent = isMatchComplete)
        return result
    }

    // 7e: Retry generation with any provider other than the one that produced the hallucinated output
    private suspend fun retryWithNextProvider(prompt: String, alreadyTriedProvider: String): String? =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            for (provider in providers) {
                if (provider.name == alreadyTriedProvider) continue
                if (provider.key.isEmpty()) continue
                val cooldownUntil = providerCooldowns[provider.name] ?: 0L
                if (cooldownUntil > now) continue
                try {
                    val result = callProvider(provider, prompt, maxTokens = 256, temperature = 0.6)
                    Sentry.addBreadcrumb(Breadcrumb().apply {
                        category = "service.commentary"
                        message = "Hallucination retry succeeded with ${provider.name}"
                        level = SentryLevel.INFO
                    })
                    return@withContext result
                } catch (e: Exception) {
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
        verifyCache[cacheKey]?.let { return it }

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
            val passed = verdict?.trim()?.uppercase()?.startsWith("PASS") == true
            if (verdict == null) {
                Log.d("CommentaryFusionService", "No provider available for verification — assuming PASS")
                return true
            }
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
            // Evict oldest entries when cache exceeds 512. Use mutex to make check-then-act atomic.
            verifyCacheMutex.withLock {
                if (verifyCache.size >= 512) {
                    verifyCache.keys.firstOrNull()?.let { verifyCache.remove(it) }
                }
                verifyCache[cacheKey] = passed
            }
            passed
        } catch (e: Exception) {
            // API failure during verification — pass through rather than discard good commentary
            Log.w("CommentaryFusionService", "Verification call failed — assuming PASS: ${e.message}")
            true
        }
    }

    private suspend fun callProviders(prompt: String, maxTokens: Int, temperature: Double): String? =
        callProvidersTracked(prompt, maxTokens, temperature)?.first

    // Returns Pair(content, providerName) so callers can track which provider ran (needed for 7e retry)
    private suspend fun callProvidersTracked(
        prompt: String,
        maxTokens: Int,
        temperature: Double
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
                val content = callProvider(provider, prompt, maxTokens, temperature)
                return@withContext Pair(content, provider.name)
            } catch (e: Exception) {
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "service.commentary"
                    message = "${provider.name} failed — ${e.message}"
                    level = SentryLevel.WARNING
                })
            }
        }
        null
    }

    private fun callProvider(provider: Provider, prompt: String, maxTokens: Int, temperature: Double): String {
        val body = JSONObject().apply {
            put("model", provider.model)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
            put("max_tokens", maxTokens)
            put("temperature", temperature)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(provider.url)
            .header("Authorization", "Bearer ${provider.key}")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 429) {
                providerCooldowns[provider.name] = System.currentTimeMillis() + 30_000L
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "service.commentary"
                    message = "${provider.name} rate-limited — 30s cooldown"
                    level = SentryLevel.WARNING
                })
                throw IOException("Rate limited (429)")
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val content = JSONObject(response.body!!.string())
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
            if (content.isEmpty()) throw IOException("Empty content from ${provider.name}")
            Sentry.metrics().count("ai.request.success", 1.0)
            return content
        }
    }

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
        val isMatchComplete: Boolean = false
    )
}
