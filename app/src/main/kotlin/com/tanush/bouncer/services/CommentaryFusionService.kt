package com.tanush.bouncer.services

import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.network.HttpClientProvider
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
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
    private val client: OkHttpClient = HttpClientProvider.sharedClient
) {
    private data class Provider(val name: String, val url: String, val key: String, val model: String)

    private val providers = listOf(
        Provider("Groq",      "https://api.groq.com/openai/v1/chat/completions",    ApiKeys.groq,      "llama-3.3-70b-versatile"),
        Provider("Cerebras",  "https://api.cerebras.ai/v1/chat/completions",         ApiKeys.cerebras,  "llama-3.3-70b"),
        Provider("SambaNova", "https://api.sambanova.ai/v1/chat/completions",        ApiKeys.sambaNova, "Meta-Llama-3.3-70B-Instruct"),
    )
    private val providerCooldowns = ConcurrentHashMap<String, Long>()

    private val previousBallsBuffer = mutableMapOf<String, MutableList<String>>()
    private val maxBufferSize = 6

    companion object {
        const val FALLBACK_BALL_COMMENTARY = "Ball delivered. Play continues."

        const val FUSION_PROMPT_TEMPLATE = """
    You are a professional cricket commentator.
    Given the following ball-by-ball input and recent match context, write a single, natural-sounding commentary line for the current ball.

    Ball-by-ball input from two sources:
    Commentary from source 1: {snippet1}
    Commentary from source 2: {snippet2}

    Ball number: {ball_number}
    Bowler: {bowler}
    Batsman: {batsman}
    Current score/context: {score_context}

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

        const val SINGLE_SOURCE_PROMPT_TEMPLATE = """
    You are a professional cricket commentator. Given the following ball-by-ball input and recent match context, write a vivid, engaging, and concise commentary line for this specific ball, as if you are commentating live.

    Ball commentary: {snippet}
    Ball number: {ball_number}
    Bowler: {bowler}
    Batsman: {batsman}
    Current score/context: {score_context}
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
    """
    }

    fun addBallToBuffer(matchId: String, ball: String) {
        val buffer = previousBallsBuffer.getOrPut(matchId) { mutableListOf() }
        buffer.add(ball)
        if (buffer.size > maxBufferSize) buffer.removeAt(0)
    }

    fun getPreviousBalls(matchId: String): List<String> = previousBallsBuffer[matchId] ?: emptyList()

    suspend fun generateBallCommentary(matchId: String, ballNumber: String, rawSnippet: String): String =
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
            AICacheService.setCommentary(result, cacheKey)
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
        scoreContext: String
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

        val prompt = if (snippet1.isEmpty() || snippet2.isEmpty()) {
            SINGLE_SOURCE_PROMPT_TEMPLATE
                .replace("{snippet}", snippet1.ifEmpty { snippet2 })
                .replace("{ball_number}", ballNumber)
                .replace("{bowler}", bowler)
                .replace("{batsman}", batsman)
                .replace("{score_context}", scoreContext)
                .replace("{previous_balls_context}", previousContext)
        } else {
            FUSION_PROMPT_TEMPLATE
                .replace("{snippet1}", snippet1)
                .replace("{snippet2}", snippet2)
                .replace("{ball_number}", ballNumber)
                .replace("{bowler}", bowler)
                .replace("{batsman}", batsman)
                .replace("{score_context}", scoreContext)
                .replace("{overs_summary}", oversSummary)
                .replace("{previous_balls_context}", previousContext)
        }

        val result = callProviders(prompt, maxTokens = 256, temperature = 0.6)
            ?: return "[AI Fused] $snippet1 / $snippet2"

        val verified = verifyCommentary(result, snippet1, snippet2, ballNumber, bowler, batsman)
        if (!verified) {
            val fallback = snippet1.ifEmpty { snippet2 }
            Log.w("CommentaryFusionService", "Using raw snippet fallback after failed verification")
            AICacheService.setCommentary(fallback, cacheKey)
            return fallback
        }

        AICacheService.setCommentary(result, cacheKey)
        return result
    }

    suspend fun summarizeOversWithAI(commentaryLines: List<String>, matchId: String, over: Int): String {
        if (commentaryLines.isEmpty()) return "No recent commentary available."

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.commentary"
            message = "Summarizing overs with AI"
            level = SentryLevel.INFO
            setData("matchId", matchId)
            setData("over", over)
            setData("lineCount", commentaryLines.size)
        })

        val lines = commentaryLines.take(18)
        val contextHash = lines.joinToString(" ").hashCode().toString()
        val cacheKey = "ai_summary:$matchId:$over:$contextHash"
        AICacheService.getCommentary(cacheKey)?.let { return it }

        val numbered = lines.mapIndexed { idx, line -> "${idx + 1}. $line" }.joinToString("\n")
        val prompt = "You are a professional cricket commentator. Summarize the following ${lines.size} " +
            "ball-by-ball commentary lines into a single, engaging paragraph, highlighting key events and players.\n\n$numbered"

        val result = callProviders(prompt, maxTokens = 256, temperature = 0.4) ?: "[Summary unavailable]"
        AICacheService.setCommentary(result, cacheKey)
        return result
    }

    private suspend fun verifyCommentary(
        aiOutput: String,
        snippet1: String,
        snippet2: String,
        ballNumber: String,
        bowler: String,
        batsman: String
    ): Boolean {
        val prompt = VERIFICATION_PROMPT_TEMPLATE
            .replace("{snippet1}", snippet1)
            .replace("{snippet2}", snippet2)
            .replace("{ai_output}", aiOutput)
            .replace("{ball_number}", ballNumber)
            .replace("{bowler}", bowler)
            .replace("{batsman}", batsman)

        return try {
            val verdict = callProviders(prompt, maxTokens = 16, temperature = 0.0)
            val passed = verdict?.trim()?.uppercase()?.startsWith("PASS") == true
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
            passed
        } catch (e: Exception) {
            // API failure during verification — pass through rather than discard good commentary
            Log.w("CommentaryFusionService", "Verification call failed — assuming PASS: ${e.message}")
            true
        }
    }

    private suspend fun callProviders(prompt: String, maxTokens: Int, temperature: Double): String? =
        withContext(Dispatchers.IO) {
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
                    return@withContext callProvider(provider, prompt, maxTokens, temperature)
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
}
